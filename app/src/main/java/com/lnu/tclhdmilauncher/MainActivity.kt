package com.lnu.tclhdmilauncher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.annotation.SuppressLint
import android.content.Intent
import android.media.tv.TvContract
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lnu.tclhdmilauncher.accessibility.AccessibilityHelper
import com.lnu.tclhdmilauncher.applist.AppListActivity
import com.lnu.tclhdmilauncher.cec.CecLogReaderService
import com.lnu.tclhdmilauncher.launcher.LauncherScreen
import com.lnu.tclhdmilauncher.launcher.LauncherTheme
import com.lnu.tclhdmilauncher.settings.OobeActivity
import com.lnu.tclhdmilauncher.settings.SettingsActivity
import com.lnu.tclhdmilauncher.settings.SettingsRepository
import com.lnu.tclhdmilauncher.shizuku.ShizukuHelper

/**
 * TV Material launcher UI; countdown, CEC coordination and HDMI launching stay
 * in the Activity so recomposition cannot restart timers or hardware sessions.
 */
class MainActivity : ComponentActivity() {

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
                } catch (e: ActivityNotFoundException) {
                    // 繼續嘗試下一個候選 Intent
                    Log.w(TAG, "Settings candidate not found: ${e.message}")
                } catch (e: SecurityException) {
                    Log.w(TAG, "Settings candidate not allowed: ${e.message}")
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

    private var countdownText by mutableStateOf("")
    private var focusRequestGeneration by mutableIntStateOf(0)
    private var focusRequestPort by mutableIntStateOf(3)
    private var focusedPort: Int? = null
    private var defaultPort by mutableIntStateOf(3)
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
                // Keep the countdown paused while CEC selects an input, then resume automatically.
                handler.postDelayed(this, 1000L)
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
        CecLogReaderService.restartLogcatReader(this)

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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // The Home launcher must not finish when Back is pressed.
            }
        })
        updateCountdownText()
        setContent {
            LauncherTheme {
                LauncherScreen(
                    defaultPort = defaultPort,
                    countdownText = countdownText,
                    countdownProgress = if (!isAppMode && !isCancelled && countdownDuration > 0) {
                        ((countdownDuration - secondsLeft + 1f) / countdownDuration).coerceIn(0f, 1f)
                    } else null,
                    focusRequestGeneration = focusRequestGeneration,
                    onPortClick = { port -> cancelTimer(); switchTo(port, fromTimer = false) },
                    onPortLongClick = ::setDefault,
                    onSettingsClick = ::openSettings,
                    onAppsClick = { isCancelled = false; openAppList(immediate = false) },
                    onFocusedPortChanged = { focusedPort = it },
                    focusRequestPort = focusRequestPort,
                )
            }
        }
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
        requestPortFocus(defaultPort)
    }

    private fun requestPortFocus(port: Int) {
        focusRequestPort = port
        focusRequestGeneration++
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

    // Keep cached countdown strings; Compose observes only the displayed text.
    private fun updateCountdownText() {
        countdownText = if (isAppMode) {
            val autoLabel = SettingsRepository.getAutoOpenLabel(this)
            if (autoLabel.isNotBlank()) {
                getString(R.string.app_mode_active_with_auto_app, autoLabel)
            } else {
                textAppModeActive
            }
        } else if (isCancelled) {
            textCancelled
        } else if (countdownDuration <= 0) {
            textDisabledCache.getOrElse(defaultPort) { textDisabledCache[3] }
        } else {
            val port = if (defaultPort in 1..3) defaultPort else 3
            val sec = if (secondsLeft in 1..30) secondsLeft else 0
            if (sec > 0) countdownTextCache[port][sec] else textCancelled
        }
    }

    private fun setDefault(port: Int) {
        defaultPort = port
        SettingsRepository.setDefaultPort(this, port)
        cancelTimer()
        countdownText = getString(R.string.msg_set_default, port)
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
        if (isCancelled || isFinishing || !isActivityResumed || !hasWindowFocus()) return
        if (TclHdmiApplication.isCecInputOverrideActive()) {
            Log.i(TAG, "CEC input override is active; deferring default-port countdown")
            updateCountdownText()
            handler.postDelayed(tickRunnable, 1000L)
            return
        }
        if (secondsLeft <= 0) secondsLeft = countdownDuration
        updateCountdownText()
        handler.postDelayed(tickRunnable, 1000L)
    }

    private fun cancelTimer() {
        isCancelled = true
        handler.removeCallbacks(tickRunnable)
    }

    // Lint 誤報：ComponentActivity.dispatchKeyEvent 實為公開 API（非受限）
    @SuppressLint("RestrictedApi")
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
                    // Keep source-key cycling correct before Compose applies the focus request.
                    focusedPort = pressedPort
                    requestPortFocus(pressedPort)
                    switchTo(pressedPort, fromTimer = false)
                }
                return true
            }

            if (keyCode == KeyEvent.KEYCODE_TV_INPUT ||
                keyCode == KeyEvent.KEYCODE_AVR_INPUT ||
                keyCode == KeyEvent.KEYCODE_STB_INPUT) {
                val currentPort = focusedPort ?: defaultPort
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
                        countdownText = textCancelled
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
            // Skip the window transition: animating a Compose window against the TvView player
            // window stutters on the TV's GPU.
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Switch failed: ${e.message}")
        }
    }

}
