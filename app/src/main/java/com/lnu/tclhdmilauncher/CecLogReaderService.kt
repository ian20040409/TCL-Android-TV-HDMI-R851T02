package com.lnu.tclhdmilauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvInputManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader

class CecLogReaderService : Service() {

    private var logcatProcess: Process? = null
    private var isReading = false
    private var isCecReceiverRegistered = false

    // TvInputManager：用來偵測哪個 HDMI port 狀態變化為 CONNECTED，判斷正確的 CEC 來源
    private var tvInputManager: TvInputManager? = null
    @Volatile private var pendingCecSwitchTime: Long = 0
    private val cecHandler = Handler(Looper.getMainLooper())
    private var pendingFallbackRunnable: Runnable? = null

    /**
     * TvInputCallback：監聽 HDMI 輸入狀態變化。
     * 當 CEC 裝置喚醒時，其 HDMI port 狀態會從 STANDBY → CONNECTED，
     * 我們藉此判斷 CEC 訊號來自哪個 port。
     */
    private val tvInputCallback = object : TvInputManager.TvInputCallback() {
        override fun onInputStateChanged(inputId: String, state: Int) {
            val port = inputIdToPort(inputId) ?: return
            val stateStr = inputStateToString(state)
            Log.i(TAG, "TvInputCallback: HDMI $port → $stateStr")
            CecDebugLog.add(this@CecLogReaderService, "InputState: HDMI $port → $stateStr")

            // 如果有等待中的 CEC 切換（MSG_VIEW_ON 後 5 秒內）且此 port 剛變為 CONNECTED
            if (state == TvInputManager.INPUT_STATE_CONNECTED &&
                System.currentTimeMillis() - pendingCecSwitchTime < 5000) {
                Log.i(TAG, "CEC TvInput 狀態匹配 → 切換至 HDMI $port")
                cancelPendingFallback()
                pendingCecSwitchTime = 0
                TclHdmiApplication.lastCecWakeTime = System.currentTimeMillis()
                switchHdmiPort(this@CecLogReaderService, port, "CEC TvInput")
            }
        }
    }

    /**
     * 動態 BroadcastReceiver：監聽系統 CEC 喚醒廣播 com.tcl.action.cec.MSG_VIEW_ON
     *
     * 因為 Android 8.0+ 禁止靜態 BroadcastReceiver 接收自定義隱式廣播，
     * 當 App 進程在待機期間被殺死時，AndroidManifest 中的 BootAndWakeReceiver
     * 無法收到 MSG_VIEW_ON（log 顯示 "Background execution not allowed"）。
     *
     * 解決方式：在前台服務中註冊動態接收器。
     * 前台服務在 Android 系統中享有最高優先級，不會被 LMK 殺死，
     * 因此動態接收器能在待機喚醒時正常運作。
     */
    private val cecBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return

            // 將 intent 所有 extras 記錄下來，方便在 CEC Debug 頁面排查
            val extras = intent.extras
            val extraInfo = if (extras != null && !extras.isEmpty) {
                extras.keySet().joinToString(", ") { key -> "$key=${extras.get(key)}" }
            } else {
                "no extras"
            }

            when (action) {
                "com.tcl.action.cec.MSG_VIEW_ON" -> {
                    Log.i(TAG, "（前台服務）收到 MSG_VIEW_ON ($extraInfo)")
                    TclHdmiApplication.lastCecWakeTime = System.currentTimeMillis()
                    TclHdmiApplication.wakeScreen(context)

                    // 記錄目前各 HDMI port 的輸入狀態
                    val stateLog = queryAndLogInputStates()

                    // 1. 嘗試從 intent extras 取得 port
                    val portFromExtras = extractPortFromIntent(intent)
                    if (portFromExtras != null) {
                        CecDebugLog.add(context, "MSG_VIEW_ON extras → HDMI $portFromExtras ($stateLog)")
                        switchHdmiPort(context, portFromExtras, "MSG_VIEW_ON")
                        return
                    }

                    // 2. 嘗試透過 TvInputManager 查詢唯一 CONNECTED 的 HDMI port
                    val singlePort = findSingleConnectedPort()
                    if (singlePort != null) {
                        CecDebugLog.add(context, "MSG_VIEW_ON single=$singlePort ($stateLog)")
                        switchHdmiPort(context, singlePort, "MSG_VIEW_ON (single)")
                        return
                    }

                    // 3. 無法立即判定 → 等待 TvInputCallback 偵測狀態變化（最多 1.5 秒後 fallback）
                    // 或是等待 Logcat 攔截到 <Active Source> 進行精準切換
                    pendingCecSwitchTime = System.currentTimeMillis()
                    cancelPendingFallback()
                    val svc = this@CecLogReaderService
                    val fallbackPort = SettingsRepository.getDefaultPort(context)
                    pendingFallbackRunnable = Runnable {
                        if (pendingCecSwitchTime > 0) {
                            pendingCecSwitchTime = 0
                            val p = findSingleConnectedPort() ?: fallbackPort
                            CecDebugLog.add(svc, "MSG_VIEW_ON fallback → HDMI $p ($stateLog)")
                            switchHdmiPort(svc, p, "MSG_VIEW_ON fallback")
                        }
                    }
                    cecHandler.postDelayed(pendingFallbackRunnable!!, 1500)
                    CecDebugLog.add(context, "MSG_VIEW_ON ($stateLog) → waiting for TvInput state/logcat...")
                }

                "com.tcl.action.cec.MSG_ACTIVE_SOURCE" -> {
                    Log.i(TAG, "（前台服務）收到 MSG_ACTIVE_SOURCE ($extraInfo)")
                    CecDebugLog.add(context, "MSG_ACTIVE_SOURCE ($extraInfo)")
                    TclHdmiApplication.lastCecWakeTime = System.currentTimeMillis()
                    TclHdmiApplication.wakeScreen(context)
                    extractPortFromIntent(intent)?.let { switchHdmiPort(context, it, "MSG_ACTIVE_SOURCE") }
                }

                "com.tcl.action.cec.MSG_ROUTING_CHANGE",
                "com.tcl.action.cec.MSG_SET_STREAM_PATH" -> {
                    val short = action.substringAfterLast(".")
                    Log.i(TAG, "（前台服務）收到 $short ($extraInfo)")
                    CecDebugLog.add(context, "$short ($extraInfo)")
                    TclHdmiApplication.lastCecWakeTime = System.currentTimeMillis()
                    TclHdmiApplication.wakeScreen(context)
                    extractPortFromIntent(intent)?.let { switchHdmiPort(context, it, short) }
                }

                "com.tcl.voicestandby" -> {
                    Log.i(TAG, "（前台服務）收到 com.tcl.voicestandby 廣播！準備關閉螢幕...")
                    CecDebugLog.add(context, "MSG_STANDBY (com.tcl.voicestandby) → requesting sleep")
                    goToSleep()
                }
            }
        }
    }

    companion object {
        private const val TAG = "CecLogReaderService"
        private const val CHANNEL_ID = "CecLogReaderChannel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "CecLogReaderService onCreate")
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CEC 喚醒與待機服務")
            .setContentText("正在背景監控 HDMI CEC 訊號...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
        startLogcatReader()

        // 註冊動態 CEC 廣播接收器（解決 Android O+ 靜態接收器被封鎖的問題）
        if (!isCecReceiverRegistered) {
            val cecFilter = IntentFilter().apply {
                addAction("com.tcl.action.cec.MSG_VIEW_ON")
                addAction("com.tcl.action.cec.MSG_ACTIVE_SOURCE")
                addAction("com.tcl.action.cec.MSG_ROUTING_CHANGE")
                addAction("com.tcl.action.cec.MSG_SET_STREAM_PATH")
                addAction("com.tcl.voicestandby") // 由 TclPowerManagerService 發出的待機廣播
            }
            // MSG_VIEW_ON originates from TCL's system process, so the dynamically
            // registered receiver must be exported on Android 13+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(cecBroadcastReceiver, cecFilter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(cecBroadcastReceiver, cecFilter)
            }
            isCecReceiverRegistered = true
            Log.i(TAG, "已註冊動態 CEC 廣播接收器 (MSG_VIEW_ON, MSG_ACTIVE_SOURCE, MSG_ROUTING_CHANGE, MSG_SET_STREAM_PATH, voicestandby)")
        }

        // 註冊 TvInputManager callback 監聽 HDMI 輸入狀態變化
        try {
            tvInputManager = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            tvInputManager?.registerCallback(tvInputCallback, cecHandler)
            val stateLog = queryAndLogInputStates()
            Log.i(TAG, "已註冊 TvInputCallback ($stateLog)")
        } catch (e: Exception) {
            Log.w(TAG, "TvInputManager 不可用: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "CecLogReaderService onStartCommand")
        return START_STICKY
    }

    private fun startLogcatReader() {
        if (isReading) return
        isReading = true

        Thread {
            try {
                // 清除之前的 log 雖然有時候不可靠，但還是執行一下
                Runtime.getRuntime().exec("logcat -c").waitFor()
                
                // 讀取包含 HDMI CEC 的 log，移除 -T 1 避免部分設備上直接 exit
                // 多加幾個 TCL 韌體可能使用的 CEC 相關 tag
                val command = arrayOf("logcat", "-v", "time", "-s",
                    "HdmiCecController", "HdmiCecLocalDeviceTv",
                    "HdmiCecLocalDevice", "HdmiControlService",
                    "HdmiCecNetwork", "HdmiCecMessage")
                logcatProcess = Runtime.getRuntime().exec(command)
                val reader = BufferedReader(InputStreamReader(logcatProcess?.inputStream))

                var line: String? = ""
                while (isReading && reader.readLine().also { line = it } != null) {
                    line?.let { processLogLine(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading logcat", e)
            } finally {
                isReading = false
            }
        }.start()
    }

    private fun processLogLine(line: String) {
        if (line.contains("command:<")) CecDebugLog.add(this, line.takeLast(220))
        if (line.contains("command:<Image View On>")) {
            Log.i(TAG, "偵測到 CEC 喚醒訊號 (Image View On)！準備喚醒螢幕...")
            TclHdmiApplication.lastCecWakeTime = System.currentTimeMillis()
            TclHdmiApplication.wakeScreen(this)
            // 不再喚醒到 Launcher，避免覆蓋原生 CEC 訊號源
        } else if (line.contains("command:<Active Source>")) {
            // Active Source carries the source physical address, e.g. 10 00 for
            // HDMI 1 or 30 00 for HDMI 3.  Its first nibble is the TV input port.
            switchToPhysicalAddress(line, "CEC Active Source", 0)
        } else if (line.contains("command:<Routing Change>")) {
            // Routing Change contains old and new physical addresses.  Use the
            // new address (the third parameter) when a source uses routing rather
            // than broadcasting Active Source.
            switchToPhysicalAddress(line, "CEC Routing Change", 2)
        } else if (line.contains("command:<InActive Source>")) {
            Log.i(TAG, "偵測到 CEC 待機訊號 (InActive Source)！準備關閉螢幕...")
            goToSleep()
        } else if (line.contains("command:<Standby>")) {
            // Report Power Status 01 is only a response to the TV's periodic
            // polling request, not a CEC Standby command.  Treating it as Standby
            // caused a screen-lock attempt every time the TV polled a sleeping
            // playback device.
            Log.i(TAG, "偵測到 CEC Standby 指令，準備關閉螢幕...")
            goToSleep()
        }
    }

    private fun switchToPhysicalAddress(line: String, event: String, addressParameterIndex: Int) {
        val params = "params:((?: [0-9a-fA-F]{2})+)".toRegex()
            .find(line)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.split(Regex("\\s+"))
            ?: return
        val firstAddressByte = params.getOrNull(addressParameterIndex) ?: return
        val port = firstAddressByte.firstOrNull()?.digitToIntOrNull(16)
        if (port == null || port !in 1..4) {
            Log.w(TAG, "$event has unsupported physical address byte: $firstAddressByte")
            return
        }

        Log.i(TAG, "偵測到 $event（HDMI $port）")
        TclHdmiApplication.lastCecWakeTime = System.currentTimeMillis()
        TclHdmiApplication.wakeScreen(this)

        if (port in 1..3) {
            cancelPendingFallback() // 取消 MSG_VIEW_ON 的倒數，避免切換衝突
            Log.i(TAG, "CEC 主動切換至 HDMI $port")
            CecDebugLog.add(this, "$event → HDMI $port (switching)")
            try {
                val intent = HdmiViewerActivity.createIntent(this, port)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch HdmiViewerActivity for port $port", e)
            }
        } else {
            Log.i(TAG, "HDMI $port 超出支援範圍，僅喚醒螢幕")
            CecDebugLog.add(this, "$event → HDMI $port (unsupported port, wake only)")
        }
    }

    /**
     * 嘗試從 TCL CEC 廣播的 intent extras 解析出 HDMI port 號 (1~3)。
     * 因為 TCL 未公開文件，所以嘗試所有可能的 key 名稱。
     */
    private fun extractPortFromIntent(intent: Intent): Int? {
        for (key in arrayOf("port", "hdmi_port", "source_port", "input_port", "hdmi_id")) {
            val v = intent.getIntExtra(key, -1)
            if (v in 1..3) return v
        }
        // 嘗試從 physical address 推算 port（首 nibble = port）
        val physAddr = intent.getIntExtra("physical_address", -1)
        if (physAddr > 0) {
            val p = (physAddr shr 12) and 0xF
            if (p in 1..3) return p
        }
        // 嘗試字串型態的 physical address（例如 "1000", "3000"）
        for (key in arrayOf("physical_address", "phyAddr", "address")) {
            val str = intent.getStringExtra(key)
            if (str != null) {
                val p = str.firstOrNull()?.digitToIntOrNull(16)
                if (p != null && p in 1..3) return p
            }
        }
        return null
    }

    /**
     * 主動啟動 HdmiViewerActivity 切換至指定 HDMI port。
     */
    private fun switchHdmiPort(context: Context, port: Int, event: String) {
        if (port !in 1..3) return
        Log.i(TAG, "CEC $event → 主動切換至 HDMI $port")
        CecDebugLog.add(context, "$event → HDMI $port (switching)")
        try {
            val switchIntent = HdmiViewerActivity.createIntent(context, port)
            switchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(switchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch HdmiViewerActivity for port $port", e)
        }
    }

    // ── TvInputManager 輔助方法 ──────────────────────────────────────────────

    private fun inputIdToPort(inputId: String): Int? = when (inputId) {
        HdmiViewerActivity.HW_HDMI1 -> 1
        HdmiViewerActivity.HW_HDMI2 -> 2
        HdmiViewerActivity.HW_HDMI3 -> 3
        else -> null
    }

    private fun inputStateToString(state: Int): String = when (state) {
        TvInputManager.INPUT_STATE_CONNECTED -> "ON"
        TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> "STANDBY"
        TvInputManager.INPUT_STATE_DISCONNECTED -> "OFF"
        else -> "?($state)"
    }

    /** 查詢並記錄所有 HDMI port 目前的輸入狀態 */
    private fun queryAndLogInputStates(): String {
        val tim = tvInputManager ?: return "no TvInputManager"
        return buildString {
            for ((inputId, port) in arrayOf(
                HdmiViewerActivity.HW_HDMI1 to 1,
                HdmiViewerActivity.HW_HDMI2 to 2,
                HdmiViewerActivity.HW_HDMI3 to 3,
            )) {
                try {
                    val state = tim.getInputState(inputId)
                    if (isNotEmpty()) append(" ")
                    append("H$port=${inputStateToString(state)}")
                } catch (_: Exception) {}
            }
        }.ifEmpty { "query failed" }
    }

    /** 如果恰好只有一個 HDMI port 為 CONNECTED，回傳該 port；否則 null */
    private fun findSingleConnectedPort(): Int? {
        val tim = tvInputManager ?: return null
        val connected = mutableListOf<Int>()
        for ((inputId, port) in arrayOf(
            HdmiViewerActivity.HW_HDMI1 to 1,
            HdmiViewerActivity.HW_HDMI2 to 2,
            HdmiViewerActivity.HW_HDMI3 to 3,
        )) {
            try {
                if (tim.getInputState(inputId) == TvInputManager.INPUT_STATE_CONNECTED) {
                    connected.add(port)
                }
            } catch (_: Exception) {}
        }
        return if (connected.size == 1) connected[0] else null
    }

    private fun cancelPendingFallback() {
        pendingFallbackRunnable?.let { cecHandler.removeCallbacks(it) }
        pendingFallbackRunnable = null
    }

    private fun goToSleep() {
        if (!MainActivity.isAccessibilityServiceEnabled(this)) {
            Log.w(TAG, "無障礙服務未啟用，無法執行休眠指令")
            CecDebugLog.add(this, "Standby failed: WakeGuard Accessibility Service is disabled")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(this, "無法休眠：請至系統設定開啟無障礙服務", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        CecDebugLog.add(this, "Standby / Inactive Source → request screen lock")
        // 利用無障礙服務模擬按下電源鍵 (如果是 Android 9+)
        val intent = Intent(this, WakeAccessibilityService::class.java)
        intent.action = "ACTION_SLEEP"
        intent.putExtra("RETRY_COUNT", 0)
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "CecLogReaderService onDestroy")
        isReading = false
        logcatProcess?.destroy()
        cancelPendingFallback()

        // 反註冊 TvInputManager callback
        try {
            tvInputManager?.unregisterCallback(tvInputCallback)
        } catch (_: Exception) {}

        // 反註冊動態 CEC 廣播接收器
        if (isCecReceiverRegistered) {
            try {
                unregisterReceiver(cecBroadcastReceiver)
            } catch (_: Exception) {}
            isCecReceiverRegistered = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "CEC 監控服務",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}

/** Small persistent on-device buffer for CEC troubleshooting. */
object CecDebugLog {
    private const val PREFS = "cec_debug"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 60

    private val deque = java.util.ArrayDeque<String>()
    private var isInitialized = false

    @Synchronized
    private fun ensureInitialized(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_EVENTS, "") ?: ""
        saved.lines().filter { it.isNotBlank() }.forEach { deque.add(it) }
        isInitialized = true
    }

    @Synchronized
    fun add(context: Context, message: String) {
        ensureInitialized(context)
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date())
        val entry = "$timestamp  $message"
        deque.add(entry)
        while (deque.size > MAX_EVENTS) {
            deque.removeFirst()
        }
        val entries = deque.joinToString("\n")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_EVENTS, entries).apply()
    }

    @Synchronized
    fun read(context: Context): String {
        ensureInitialized(context)
        return if (deque.isEmpty()) "No CEC events recorded yet." else deque.joinToString("\n")
    }

    @Synchronized
    fun clear(context: Context) {
        deque.clear()
        isInitialized = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_EVENTS).apply()
    }
}
