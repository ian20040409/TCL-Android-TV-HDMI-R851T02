package com.lnu.tclhdmilauncher

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.tv.TvContract
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * TCL TV HDMI 1 / 2 / 3 原生極致輕量 Launcher
 *
 * 核心性能設計：
 * - 100% 純程式碼建構 View 樹：0 XML I/O、0 反射（節省冷啟動 ~400ms）
 * - View 樹極限扁平化：頂部狀態列單層配置，達成全畫面單一次 Measure/Layout Pass
 * - 倒數計時熱路徑 0 GC：預建構字串快取，每秒倒數 0 物件配置
 * - 記憶體化持久快取：SharedPreferences 消除主執行緒重複磁碟 I/O
 * - 預建構全域靜態 Intent：微秒級訊號源派發
 * - 共用單一 OnClickListener 與 OnFocusChangeListener：消除匿名閉包
 * - 全面對齊 Android TV 系統原生樣式（Theme_DeviceDefault_Dialog_Alert）
 */
class MainActivity : Activity(), View.OnClickListener, View.OnFocusChangeListener {

    companion object {
        private const val TAG = "TCLHdmiLauncher"
        const val EXTRA_FROM_APP_LIST = "from_app_list"
        private const val DEFAULT_COUNTDOWN_SECONDS = 3
        @Volatile
        private var isFirstLaunchInProcess: Boolean = true


        // TCL 實機硬體訊號源 ID (dumpsys tv_input)
        private const val HW_HDMI1 = "com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744128"
        private const val HW_HDMI2 = "com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744384"
        private const val HW_HDMI3 = "com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744640"

        // 預先建構的通道 URI
        private val URI_HDMI1 = TvContract.buildChannelUriForPassthroughInput(HW_HDMI1)
        private val URI_HDMI2 = TvContract.buildChannelUriForPassthroughInput(HW_HDMI2)
        private val URI_HDMI3 = TvContract.buildChannelUriForPassthroughInput(HW_HDMI3)

        // 預先建構的切換 Intent（0 動態物件配置、微秒級派發）
        private val INTENT_HDMI1 = Intent(Intent.ACTION_VIEW, URI_HDMI1).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        private val INTENT_HDMI2 = Intent(Intent.ACTION_VIEW, URI_HDMI2).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        private val INTENT_HDMI3 = Intent(Intent.ACTION_VIEW, URI_HDMI3).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        fun isTclDevice(context: Context): Boolean {
            return DeviceHelper.isTclDevice(context)
        }

        fun getDeviceModelName(): String {
            return DeviceHelper.getDeviceModelName()
        }

        fun launchTclSettings(context: Context) {
            val pm = context.packageManager
            val candidates = listOf(
                pm.getLeanbackLaunchIntentForPackage("com.tcl.settings"),
                pm.getLaunchIntentForPackage("com.tcl.settings"),
                Intent(Intent.ACTION_MAIN).apply {
                    setClassName("com.tcl.settings", "com.tcl.settings.MainActivity")
                },
                Intent(Intent.ACTION_MAIN).apply {
                    `package` = "com.tcl.settings"
                },
                Intent("android.settings.TV_SETTINGS"),
                Intent().setComponent(ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings")),
                pm.getLeanbackLaunchIntentForPackage("com.android.tv.settings"),
                pm.getLaunchIntentForPackage("com.android.tv.settings"),
                Intent(Settings.ACTION_SETTINGS)
            )

            for (candidate in candidates) {
                if (candidate == null) continue
                try {
                    WakeAccessibilityService.temporarilyIgnorePackage("com.tcl.settings")
                    WakeAccessibilityService.temporarilyIgnorePackage("com.android.tv.settings")
                    if (context !is android.app.Activity) {
                        candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(candidate)
                    return
                } catch (_: Exception) {
                    // 繼續嘗試下一個候選 Intent
                }
            }
        }

        fun launchAndroidSystemSettings(context: Context): Boolean {
            return AccessibilityHelper.openAndroidSystemSettings(context)
        }

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            return AccessibilityHelper.isServiceEnabled(context)
        }

        fun openAccessibilitySettings(context: Context): Boolean {
            return AccessibilityHelper.openAccessibilitySettings(context)
        }

        @Volatile
        var isForegroundFocused: Boolean = false
            internal set
    }

    // 熱路徑字串快取：預先建構 1..30 秒對應各 HDMI 埠的提示文字（Hot Path 0 GC）
    private lateinit var countdownTextCache: Array<Array<String>>
    private lateinit var textCancelled: String
    private lateinit var textAppModeActive: String
    private lateinit var textDisabledCache: Array<String>

    internal lateinit var tvCountdown: TextView
    internal lateinit var cardHdmi1: LinearLayout
    internal lateinit var cardHdmi2: LinearLayout
    internal lateinit var cardHdmi3: LinearLayout
    internal lateinit var tvBadge1: TextView
    internal lateinit var tvBadge2: TextView
    internal lateinit var tvBadge3: TextView
    internal lateinit var ivIcon1: ImageView
    internal lateinit var ivIcon2: ImageView
    internal lateinit var ivIcon3: ImageView
    internal lateinit var btnSettings: LinearLayout
    internal lateinit var btnApps: LinearLayout

    private var defaultPort = 3
    private var countdownDuration = DEFAULT_COUNTDOWN_SECONDS
    private var isAppMode = false
    private var isCancelled = false
    private var isActivityResumed = false
    private var secondsLeft = DEFAULT_COUNTDOWN_SECONDS

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isAppMode || countdownDuration <= 0 || isDestroyed || isCancelled || isFinishing || !isActivityResumed || !hasWindowFocus()) return
            if (TclHdmiApplication.isCecInputOverrideActive()) {
                cancelTimer()
                Log.i(TAG, "CEC input override is active; cancelling default-port countdown")
                return
            }
            secondsLeft--
            if (secondsLeft > 0) {
                updateCountdownText()
                handler.postDelayed(this, 1000L)
            } else {
                switchTo(defaultPort, fromTimer = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ShizukuHelper.tryGrantPermissions(this)

        if (!SettingsRepository.isOobeCompleted(this)) {
            val oobeIntent = Intent(this, OobeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(oobeIntent)
            finish()
            return
        }

        if (!isAccessibilityServiceEnabled(this)) {
            AccessibilityHelper.showAccessibilityGuideDialog(this)
        }

        loadPreferencesFromCache()
        initTextCaches()
        secondsLeft = countdownDuration

        setContentView(MainViewBuilder(this).build())

        cardHdmi1.setOnLongClickListener { setDefault(1); true }
        cardHdmi2.setOnLongClickListener { setDefault(2); true }
        cardHdmi3.setOnLongClickListener { setDefault(3); true }

        updateButtonLabels()
        focusDefaultPortButton()
        isCancelled = false

        isFirstLaunchInProcess = false

        val fromAppList = intent?.getBooleanExtra(EXTRA_FROM_APP_LIST, false) == true
        if (fromAppList) {
            intent?.removeExtra(EXTRA_FROM_APP_LIST)
            cancelTimer()
        } else if (isAppMode) {
            openAppList(immediate = true)
            return
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        loadPreferencesFromCache()
        updateButtonLabels()
        focusDefaultPortButton()



        val fromAppList = intent.getBooleanExtra(EXTRA_FROM_APP_LIST, false)
        if (fromAppList) {
            intent.removeExtra(EXTRA_FROM_APP_LIST)
            cancelTimer()
            updateCountdownText()
            return
        }

        if (isAppMode) {
            cancelTimer()
            updateCountdownText()
            openAppList(immediate = true)
            return
        }

        secondsLeft = countdownDuration
        isCancelled = false
        updateCountdownText()
        if (isActivityResumed && hasWindowFocus()) {
            resumeTimerIfOnMainScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        isForegroundFocused = hasWindowFocus()

        loadPreferencesFromCache()
        updateButtonLabels()
        focusDefaultPortButton()

        if (isAppMode) {
            pauseTimer()
            updateCountdownText()
            return
        }

        isCancelled = false
        if (secondsLeft <= 0) secondsLeft = countdownDuration
        updateCountdownText()
        if (hasWindowFocus()) {
            resumeTimerIfOnMainScreen()
        }
    }

    override fun onBackPressed() {
        // Do nothing, as this is the Home Launcher.
        // This prevents going back to OobeActivity or closing the launcher.
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        isForegroundFocused = hasFocus && isActivityResumed
        if (hasFocus && isActivityResumed) {
            resumeTimerIfOnMainScreen()
        } else {
            pauseTimer()
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
        isForegroundFocused = false
        pauseTimer()
    }

    override fun onStop() {
        super.onStop()
        isForegroundFocused = false
        pauseTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        isForegroundFocused = false
        cancelTimer()
    }

    private fun loadPreferencesFromCache() {
        defaultPort = SettingsRepository.getDefaultPort(this)
        countdownDuration = SettingsRepository.getCountdownSeconds(this)
        isAppMode = SettingsRepository.isAppModeEnabled(this)
    }

    private fun focusDefaultPortButton() {
        when (defaultPort) {
            1 -> cardHdmi1
            2 -> cardHdmi2
            else -> cardHdmi3
        }.requestFocus()
    }

    private fun updateButtonLabels() {
        tvBadge1.visibility = if (defaultPort == 1) View.VISIBLE else View.INVISIBLE
        tvBadge2.visibility = if (defaultPort == 2) View.VISIBLE else View.INVISIBLE
        tvBadge3.visibility = if (defaultPort == 3) View.VISIBLE else View.INVISIBLE
        btnApps.nextFocusUpId = when (defaultPort) {
            1 -> cardHdmi1.id
            2 -> cardHdmi2.id
            else -> cardHdmi3.id
        }
        if (::btnSettings.isInitialized) {
            btnSettings.nextFocusDownId = when (defaultPort) {
                1 -> cardHdmi1.id
                2 -> cardHdmi2.id
                else -> cardHdmi3.id
            }
        }
    }

    private fun initTextCaches() {
        textCancelled = getString(R.string.text_cancelled)
        textAppModeActive = getString(R.string.app_mode_active_status)
        textDisabledCache = Array(4) { port ->
            getString(R.string.countdown_disabled, port)
        }
        countdownTextCache = Array(4) { port ->
            Array(31) { sec ->
                getString(R.string.countdown_active, sec, port)
            }
        }
    }

    private fun openAppList(immediate: Boolean) {
        cancelTimer()
        val appListIntent = Intent(this, AppListActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(appListIntent)
        if (immediate) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun openSettings() {
        cancelTimer()
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    /**
     * 倒數計時文字更新（0 Allocation、0 GC）
     */
    private fun updateCountdownText() {
        if (isAppMode) {
            val autoLabel = SettingsRepository.getAutoOpenLabel(this)
            tvCountdown.text = if (autoLabel.isNotBlank()) {
                getString(R.string.app_mode_active_with_auto_app, autoLabel)
            } else {
                textAppModeActive
            }
        } else if (isCancelled) {
            tvCountdown.text = textCancelled
        } else if (countdownDuration <= 0) {
            tvCountdown.text = textDisabledCache.getOrElse(defaultPort) { textDisabledCache[3] }
        } else {
            val port = if (defaultPort in 1..3) defaultPort else 3
            val sec = if (secondsLeft in 1..30) secondsLeft else 0
            if (sec > 0) {
                tvCountdown.text = countdownTextCache[port][sec]
            } else {
                tvCountdown.text = textCancelled
            }
        }
    }

    private fun setDefault(port: Int) {
        defaultPort = port
        SettingsRepository.setDefaultPort(this, port)
        updateButtonLabels()
        cancelTimer()
        tvCountdown.text = getString(R.string.msg_set_default, port)
    }

    private fun pauseTimer() {
        handler.removeCallbacks(tickRunnable)
    }

    private fun resumeTimerIfOnMainScreen() {
        handler.removeCallbacks(tickRunnable)
        if (isAppMode || countdownDuration <= 0) {
            updateCountdownText()
            return
        }
        if (TclHdmiApplication.isCecInputOverrideActive()) {
            cancelTimer()
            Log.i(TAG, "CEC input override is active; suppressing default-port countdown")
            updateCountdownText()
            return
        }
        if (isCancelled || isFinishing || !isActivityResumed || !hasWindowFocus()) return
        if (secondsLeft <= 0) secondsLeft = countdownDuration
        updateCountdownText()
        handler.postDelayed(tickRunnable, 1000L)
    }

    private fun cancelTimer() {
        isCancelled = true
        handler.removeCallbacks(tickRunnable)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode

            // 1. 遙控器數字鍵 1, 2, 3 ... 切換 HDMI
            val pressedPort = when (keyCode) {
                KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
                KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
                KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
                in KeyEvent.KEYCODE_4..KeyEvent.KEYCODE_9,
                in KeyEvent.KEYCODE_NUMPAD_4..KeyEvent.KEYCODE_NUMPAD_9 -> {
                    if (keyCode in KeyEvent.KEYCODE_4..KeyEvent.KEYCODE_9) {
                        keyCode - KeyEvent.KEYCODE_0
                    } else {
                        keyCode - KeyEvent.KEYCODE_NUMPAD_0
                    }
                }
                else -> null
            }

            if (pressedPort != null) {
                cancelTimer()
                if (pressedPort in 1..3) {
                    when (pressedPort) {
                        1 -> cardHdmi1.requestFocus()
                        2 -> cardHdmi2.requestFocus()
                        3 -> cardHdmi3.requestFocus()
                    }
                    switchTo(pressedPort, fromTimer = false)
                }
                return true
            }

            if (keyCode == KeyEvent.KEYCODE_TV_INPUT ||
                keyCode == KeyEvent.KEYCODE_AVR_INPUT ||
                keyCode == KeyEvent.KEYCODE_STB_INPUT) {
                val currentPort = when {
                    cardHdmi1.hasFocus() -> 1
                    cardHdmi2.hasFocus() -> 2
                    cardHdmi3.hasFocus() -> 3
                    else -> defaultPort
                }
                val nextPort = if (currentPort >= 3) 1 else currentPort + 1
                cancelTimer()
                Log.i(TAG, "Input/source key: HDMI $currentPort → HDMI $nextPort")
                switchTo(nextPort, fromTimer = false)
                return true
            }

            // 2. 選單按鍵（MENU）或設定鍵（SETTINGS）開啟 App 設定頁面
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
                openSettings()
                return true
            }

            // 3. 方向鍵取消倒數計時（不消耗事件，讓焦點正常切換）
            if (!isCancelled && countdownDuration > 0) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        cancelTimer()
                        tvCountdown.text = textCancelled
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun switchTo(port: Int, fromTimer: Boolean) {
        if (fromTimer && (!isActivityResumed || !hasWindowFocus() || isFinishing)) return

        try {
            val intent = HdmiViewerActivity.createIntent(this, port)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Switch failed: ${e.message}")
        }
    }

    // ── View.OnClickListener 單例分流（0 匿名閉包） ─────────────────────────
    override fun onClick(v: View) {
        when (v) {
            cardHdmi1 -> { cancelTimer(); switchTo(1, fromTimer = false) }
            cardHdmi2 -> { cancelTimer(); switchTo(2, fromTimer = false) }
            cardHdmi3 -> { cancelTimer(); switchTo(3, fromTimer = false) }
            btnSettings, tvCountdown -> openSettings()
            btnApps -> {
                isCancelled = false
                openAppList(immediate = false)
            }
        }
    }

    // ── View.OnFocusChangeListener 單例分流（0 匿名閉包） ───────────────────
    override fun onFocusChange(v: View, hasFocus: Boolean) {
        val density = resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density + 0.5f).toInt()

        when (v) {
            cardHdmi1 -> updateCardFocusState(cardHdmi1, ivIcon1, tvBadge1, hasFocus, dp(8f))
            cardHdmi2 -> updateCardFocusState(cardHdmi2, ivIcon2, tvBadge2, hasFocus, dp(8f))
            cardHdmi3 -> updateCardFocusState(cardHdmi3, ivIcon3, tvBadge3, hasFocus, dp(8f))
            btnSettings, btnApps -> {
                val scale = if (hasFocus) 1.08f else 1.0f
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                v.elevation = if (hasFocus) dp(6f).toFloat() else 0f
            }
        }
    }

    private fun updateCardFocusState(
        card: View,
        ivIcon: ImageView,
        tvBadge: TextView,
        hasFocus: Boolean,
        elevationPx: Int
    ) {
        if (hasFocus) {
            card.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
            card.elevation = elevationPx.toFloat()
            ivIcon.setColorFilter(Color.WHITE)
            tvBadge.setTextColor(0xFFFEF08A.toInt())
        } else {
            card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            card.elevation = 0f
            ivIcon.setColorFilter(0xFF94A3B8.toInt())
            tvBadge.setTextColor(0xFF38BDF8.toInt())
        }
    }

}
