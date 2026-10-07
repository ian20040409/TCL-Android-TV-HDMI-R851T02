package com.lnu.tclhdmilauncher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * 待機喚醒與 Home 鍵映射無障礙服務 (Wake & Home Button Mapper Accessibility Service)
 *
 * 核心功能：
 * 1. 待機睡眠 (STR) 喚醒監聽：透過系統級常駐監聽 ACTION_SCREEN_ON，喚醒時自動拉回 Launcher。
 * 2. Home 鍵重定向 (Button Mapper)：
 *    - 攔截遙控器 KEYCODE_HOME / KEYCODE_TV_HOME 按鍵事件
 *    - 監聽全域視窗狀態變更（TYPE_WINDOW_STATE_CHANGED），當原生/系統桌面（如 TCL 桌面、Google TV 桌面）
 *      試圖奪取焦點時，微秒級自動重定向至本 HDMI Launcher。
 */
class WakeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WakeAccessibility"

        @Volatile
        private var temporarilyIgnoredPackage: String? = null
        @Volatile
        private var temporarilyIgnoredExpiry: Long = 0L

        fun temporarilyIgnorePackage(pkg: String) {
            temporarilyIgnoredPackage = pkg
            temporarilyIgnoredExpiry = System.currentTimeMillis() + 5000L
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isReceiverRegistered = false
    private var lastHomeRedirectTime = 0L

    private val stockLauncherPackages = HashSet<String>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.tcl.action.cec.MSG_VIEW_ON" -> {
                    Log.i(TAG, "（無障礙服務）收到系統 MSG_VIEW_ON 廣播，觸發 CEC 喚醒")
                    TclHdmiApplication.lastCecWakeTime = System.currentTimeMillis()
                    TclHdmiApplication.wakeScreen(context)
                    // 不切回 Launcher，保留原生 CEC 訊號源切換
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    if (System.currentTimeMillis() - TclHdmiApplication.lastCecWakeTime < 15000) {
                        Log.i(TAG, "Ignoring wake to launcher in AccessibilityService because CEC woke the screen recently.")
                        return
                    }
                    Log.i(TAG, "Screen ON / User present detected via AccessibilityService, waking to Launcher...")
                    triggerWakeToLauncher(context)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "WakeAccessibilityService connected")
        updateStockLauncherPackages()

        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction("com.tcl.action.cec.MSG_VIEW_ON")
            }
            // TCL sends MSG_VIEW_ON from a different process.  Declare this
            // receiver exported on Android 13+ so the broadcast is deliverable.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    private var lastSleepRequestTime = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_SLEEP") {
            val retryCount = intent.getIntExtra("RETRY_COUNT", 0)
            if (retryCount > 3) {
                Log.w(TAG, "休眠重試次數達上限 ($retryCount)，放棄重試。")
                return super.onStartCommand(intent, flags, startId)
            }

            // Debounce logic for the initial sleep request (retryCount == 0)
            if (retryCount == 0) {
                val now = System.currentTimeMillis()
                if (now - lastSleepRequestTime < 5000L) {
                    Log.i(TAG, "忽略休眠請求：5 秒內已有其他休眠請求正在處理。")
                    return super.onStartCommand(intent, flags, startId)
                }
                lastSleepRequestTime = now
            }

            Log.i(TAG, "收到休眠請求，嘗試關閉螢幕 (第 $retryCount 次重試)")
            
            Thread {
                val success = PowerHelper.sleepScreen(this)
                
                // 根據使用者要求：間隔 1 秒重試，直到螢幕關閉
                handler.postDelayed({
                    val displayManager = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                    val isScreenOn = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY).state != android.view.Display.STATE_OFF
                    if (isScreenOn) {
                        Log.i(TAG, "螢幕尚未關閉，1 秒後重試休眠指令...")
                        val retryIntent = Intent(this, WakeAccessibilityService::class.java).apply {
                            action = "ACTION_SLEEP"
                            putExtra("RETRY_COUNT", retryCount + 1)
                        }
                        startService(retryIntent)
                    } else {
                        Log.i(TAG, "螢幕已成功關閉，停止重試。")
                    }
                }, 1000)
            }.start()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateStockLauncherPackages() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolvedList = packageManager.queryIntentActivities(intent, 0)
            stockLauncherPackages.clear()
            for (resolveInfo in resolvedList) {
                val pkg = resolveInfo.activityInfo?.packageName
                if (!pkg.isNullOrEmpty() && pkg != packageName) {
                    // Exclude settings apps that might have CATEGORY_HOME as a fallback
                    if (!pkg.contains("settings") && pkg != "com.tcl.tvmanager") {
                        stockLauncherPackages.add(pkg)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query system home launchers: ${e.message}")
        }

        // TCL / Google TV / Android TV 常見系統桌面保底
        stockLauncherPackages.add("com.google.android.tvlauncher")
        stockLauncherPackages.add("com.google.android.apps.tv.launcherx")
        stockLauncherPackages.add("com.tcl.launcher")
        stockLauncherPackages.add("com.tcl.home")
        stockLauncherPackages.add("com.tcl.waterfall.launcher")
        stockLauncherPackages.add("com.android.launcher")
        stockLauncherPackages.add("com.android.launcher3")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkgName = event.packageName?.toString() ?: return
        if (pkgName == packageName) return

        // 如果是剛從我們 App 啟動的，給予 5 秒的豁免期，不要攔截
        if (pkgName == temporarilyIgnoredPackage && System.currentTimeMillis() < temporarilyIgnoredExpiry) {
            return
        }

        // 偵測是否切換至其他系統原生桌面（即遙控器按下了 Home 鍵）
        if (stockLauncherPackages.contains(pkgName)) {
            val now = System.currentTimeMillis()
            
            if (now - TclHdmiApplication.lastCecWakeTime < 15000) {
                Log.i(TAG, "Ignoring system launcher window because CEC woke the screen recently.")
                return
            }
            
            if (now - lastHomeRedirectTime > 400L) {
                lastHomeRedirectTime = now
                Log.i(TAG, "System launcher window detected ($pkgName), remapping Home to HDMI Launcher...")
                TclHdmiApplication.wakeToLauncher(this)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        // The input/source key is often mapped as TV_INPUT; some TCL remotes use
        // AV_INPUT or STB_INPUT.  Outside this app, open our HDMI launcher rather
        // than allowing the stock input picker to take over.
        if (SettingsRepository.isButtonMapperEnabled(this) && SettingsRepository.isInputButtonOverrideEnabled(this) &&
            (keyCode == KeyEvent.KEYCODE_TV_INPUT ||
            keyCode == KeyEvent.KEYCODE_AVR_INPUT ||
            keyCode == KeyEvent.KEYCODE_STB_INPUT)) {
            if (MainActivity.isForegroundFocused || HdmiViewerActivity.isForegroundFocused) {
                return false // Let our active UI cycle to the next HDMI input.
            }
            if (event.action == KeyEvent.ACTION_UP) {
                Log.i(TAG, "Input/source key intercepted; opening HDMI Launcher")
                TclHdmiApplication.wakeToLauncher(this)
            }
            return true
        }

        // 攔截常見 TV 遙控器 Home 鍵與電視首頁鍵
        if (SettingsRepository.isButtonMapperEnabled(this) && SettingsRepository.isHomeButtonOverrideEnabled(this) &&
            (keyCode == KeyEvent.KEYCODE_HOME ||
            keyCode == KeyEvent.KEYCODE_GUIDE ||
            keyCode == KeyEvent.KEYCODE_TV)) {
            
            if (System.currentTimeMillis() - TclHdmiApplication.lastCecWakeTime < 15000) {
                Log.i(TAG, "Ignoring Home/Guide key completely because of recent CEC wake.")
                return true // 攔截並丟棄，防止原生系統跳回桌面
            }

            if (event.action == KeyEvent.ACTION_UP) {
                val now = System.currentTimeMillis()
                if (now - lastHomeRedirectTime > 400L) {
                    lastHomeRedirectTime = now
                    Log.i(TAG, "Home key intercepted in onKeyEvent, redirecting to HDMI Launcher...")
                    TclHdmiApplication.wakeToLauncher(this)
                }
            }
            return true // 攔截消耗按鍵事件，防止原生系統桌面開啟
        }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }

    private fun triggerWakeToLauncher(context: Context) {
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Log.e(TAG, "performGlobalAction failed: ${e.message}")
        }
        TclHdmiApplication.wakeToLauncher(context)
    }
}
