package com.lnu.tclhdmilauncher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.tv.TvContract
import android.media.tv.TvView
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 原生硬體 HDMI 直通播放器 (HdmiViewerActivity)
 *
 * 核心特色：
 * 1. 100% 透過 Android 官方 TV Input Framework (TvView) 驅動硬體 Passthrough，完全脫離 com.tcl.tv
 * 2. 支援硬體 HDR10、Dolby Vision、4K 60Hz 直通解碼
 * 3. 遙控器 1 / 2 / 3 數字鍵直接切換訊號源（無需返回桌面）
 * 4. 遙控器 SETTINGS 鍵呼叫原廠畫質設定，BACK / MENU 返回 Launcher
 * 5. 全面靜默直通：0 View 覆蓋、0 Toast 浮層，徹底杜絕 TCL 電視 GPU 浮層淡出殘留黑影與半透明快取卡死問題
 */
class HdmiViewerActivity : Activity() {

    companion object {
        private const val TAG = "HdmiViewerActivity"
        const val EXTRA_PORT = "port"

        // TCL R851T02 實機硬體訊號源 ID (dumpsys tv_input)
        const val HW_HDMI1 = "com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744128"
        const val HW_HDMI2 = "com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744384"
        const val HW_HDMI3 = "com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744640"

        fun createIntent(context: Context, port: Int): Intent {
            return Intent(context, HdmiViewerActivity::class.java).apply {
                putExtra(EXTRA_PORT, port)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        @Volatile
        var isForegroundFocused: Boolean = false
            internal set
    }

    private lateinit var tvView: TvView
    private var signalOverlay: LinearLayout? = null
    private var tvSignalStatus: TextView? = null
    private var currentPort = 3
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val maxRetries = 3
    private var isVideoAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 螢幕常亮與全螢幕沉浸
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        tvView = TvView(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFocusable = true
            isFocusableInTouchMode = true
            setCallback(object : TvView.TvInputCallback() {
                override fun onConnectionFailed(inputId: String?) {
                    Log.e(TAG, "TvView onConnectionFailed: $inputId")
                    updateSignalOverlay(getString(R.string.hdmi_no_signal, currentPort))
                    handleTuneFailure()
                }

                override fun onDisconnected(inputId: String?) {
                    Log.w(TAG, "TvView onDisconnected: $inputId")
                }

                override fun onVideoAvailable(inputId: String?) {
                    Log.i(TAG, "TvView onVideoAvailable: $inputId (video rendering active)")
                    retryCount = 0
                    isVideoAvailable = true
                    hideSignalOverlay()
                    tvView.post { tvView.requestFocus() }
                }

                override fun onVideoUnavailable(inputId: String?, reason: Int) {
                    Log.w(TAG, "TvView onVideoUnavailable: inputId=$inputId, reason=$reason")
                    isVideoAvailable = false
                    updateSignalOverlay(getString(R.string.hdmi_searching_signal, currentPort))
                    // reason=0 (REASON_UNKNOWN): 通常為底層驅動剛釋放舊 Session 的短暫衝突，延遲 600ms 自動自我修復重新調諧
                    if (reason == 0 && !isFinishing && isForegroundFocused) {
                        Log.i(TAG, "Hardware decoder busy/recovering, auto re-tuning in 600ms...")
                        handler.removeCallbacksAndMessages(null)
                        handler.postDelayed({
                            if (!isVideoAvailable && !isFinishing && isForegroundFocused) {
                                tuneToPort(currentPort)
                            }
                        }, 600L)
                    }
                }
            })
        }

        // 使用 FrameLayout 包裹 TvView 與訊號搜尋 Overlay
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        root.addView(tvView)

        // 建構訊號搜尋 Overlay（半透明黑底 + 居中文字）
        val overlay = buildSignalOverlay()
        signalOverlay = overlay
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        overlay.visibility = View.GONE

        setContentView(root)
        tvView.requestFocus()

        resolveAndTune(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        val newPort = intent?.getIntExtra(EXTRA_PORT, -1) ?: -1
        if (newPort != -1 && newPort == currentPort) {
            Log.i(TAG, "onNewIntent: Ignoring request for port $newPort because it is already active.")
            return
        }
        
        intent?.let { resolveAndTune(it) }
    }

    private fun resolveAndTune(intent: Intent) {
        val portFromExtra = intent.getIntExtra(EXTRA_PORT, -1)
        if (portFromExtra in 1..3) {
            tuneToPort(portFromExtra)
            return
        }

        val data: Uri? = intent.data
        if (data != null) {
            val uriStr = data.toString()
            val detectedPort = when {
                uriStr.contains("1413744128") -> 1
                uriStr.contains("1413744384") -> 2
                uriStr.contains("1413744640") -> 3
                uriStr.contains("HDMI1") -> 1
                uriStr.contains("HDMI2") -> 2
                uriStr.contains("HDMI3") -> 3
                else -> 3
            }
            tuneToPort(detectedPort)
            return
        }

        tuneToPort(currentPort)
    }

    private fun tuneToPort(port: Int) {
        currentPort = port
        isVideoAvailable = false
        val inputId = when (port) {
            1 -> HW_HDMI1
            2 -> HW_HDMI2
            else -> HW_HDMI3
        }

        Log.i(TAG, "Tuning TvView to HDMI $port ($inputId)...")
        updateSignalOverlay(getString(R.string.hdmi_searching_signal, port))

        try {
            // 注意：絕不可在此處同步呼叫 tvView.reset()！
            // reset() 為非同步釋放，會導致底層 HAL grantMediaResource 拋出 NullPointerException 並卡死畫面。
            val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
            tvView.tune(inputId, uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to tune to $inputId: ${e.message}", e)
            handleTuneFailure()
        }
    }

    private fun handleTuneFailure() {
        if (retryCount < maxRetries) {
            retryCount++
            Log.i(TAG, "Retrying tune in 1500ms (attempt $retryCount/$maxRetries)...")
            handler.postDelayed({
                tuneToPort(currentPort)
            }, 1500L)
        } else {
            Log.w(TAG, "Max retries reached for HDMI $currentPort")
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> {
                    tuneToPort(1)
                    return true
                }
                KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> {
                    tuneToPort(2)
                    return true
                }
                KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> {
                    tuneToPort(3)
                    return true
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> {
                    finish()
                    return true
                }
                KeyEvent.KEYCODE_SETTINGS -> {
                    launchTclSettings()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun launchTclSettings() {
        val pm = packageManager
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
                candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(candidate)
                return
            } catch (_: Exception) {
            }
        }
    }

    // ── 訊號搜尋 Overlay ──────────────────────────────────────────────────
    private fun buildSignalOverlay(): LinearLayout {
        val density = resources.displayMetrics.density
        fun dp(v: Float): Int = (v * density + 0.5f).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xE6000000.toInt()) // 90% 不透明黑底
            isClickable = false
            isFocusable = false
        }

        val tvStatus = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFE2E8F0.toInt())
            gravity = Gravity.CENTER
        }
        tvSignalStatus = tvStatus
        container.addView(tvStatus, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        return container
    }

    private fun updateSignalOverlay(text: String) {
        if (!MainActivity.isSignalSearchScreenEnabled(this)) {
            signalOverlay?.visibility = View.GONE
            return
        }
        tvSignalStatus?.text = text
        signalOverlay?.visibility = View.VISIBLE
    }

    private fun hideSignalOverlay() {
        signalOverlay?.visibility = View.GONE
    }

    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LOW_PROFILE
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    override fun onStart() {
        super.onStart()
        // 若從背景（例如按 Home 或 Settings 後）返回前台且畫面未激活，重新調諧
        if (!isVideoAvailable && !isFinishing) {
            tvView.post {
                tuneToPort(currentPort)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        isForegroundFocused = true
        tvView.post { tvView.requestFocus() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        isForegroundFocused = hasFocus
        if (hasFocus) {
            hideSystemUI()
            tvView.requestFocus()
        }
    }

    override fun onPause() {
        super.onPause()
        isForegroundFocused = false
        handler.removeCallbacksAndMessages(null)
    }

    override fun onStop() {
        super.onStop()
        isForegroundFocused = false
        handler.removeCallbacksAndMessages(null)
        // 離開前台時釋放 TvView 硬體 Session，避免與其他 App 或重入時搶奪硬體解碼器
        try {
            tvView.reset()
        } catch (_: Exception) {}
        isVideoAvailable = false
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            tvView.reset()
        } catch (_: Exception) {}
    }
}
