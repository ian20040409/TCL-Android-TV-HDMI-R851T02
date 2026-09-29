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
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * 原生硬體 HDMI 直通播放器 (HdmiViewerActivity)
 *
 * 核心特色：
 * 1. 100% 透過 Android 官方 TV Input Framework (TvView) 驅動硬體 Passthrough，完全脫離 com.tcl.tv
 * 2. 支援硬體 HDR10、Dolby Vision、4K 60Hz 直通解碼
 * 3. 內建遙控器 1 / 2 / 3 數字鍵直接切換訊號源（無需返回桌面）
 * 4. 內建冷開機硬體重試機制（容錯最多 3 次）
 * 5. 按返回鍵或選單鍵無縫返回 Launcher
 * 6. 純 CPU 黑色蓋板：搜尋訊號時用純黑覆蓋畫面防止前一個 App 殘影；出訊號時直接 removeView 徹底拔除，保證 0 殘留
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

    private lateinit var rootLayout: FrameLayout
    private lateinit var tvView: TvView
    private var blackCoverLayout: FrameLayout? = null
    private var statusProgressBar: ProgressBar? = null
    private var statusTextView: TextView? = null

    private var currentPort = 3
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val maxRetries = 3
    private var isVideoAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 螢幕常亮與隱藏系統 UI（全螢幕沉浸）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        hideSystemUI()

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        tvView = TvView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFocusable = true
            isFocusableInTouchMode = true
            setCallback(object : TvView.TvInputCallback() {
                override fun onConnectionFailed(inputId: String?) {
                    Log.e(TAG, "TvView onConnectionFailed: $inputId")
                    showNoSignal()
                    handleTuneFailure()
                }

                override fun onDisconnected(inputId: String?) {
                    Log.w(TAG, "TvView onDisconnected: $inputId")
                    showNoSignal()
                }

                override fun onVideoAvailable(inputId: String?) {
                    Log.i(TAG, "TvView onVideoAvailable: $inputId (video rendering active)")
                    retryCount = 0
                    if (!isVideoAvailable) {
                        isVideoAvailable = true
                        onSignalReady()
                    }
                    tvView.post { tvView.requestFocus() }
                }

                override fun onVideoUnavailable(inputId: String?, reason: Int) {
                    Log.w(TAG, "TvView onVideoUnavailable: inputId=$inputId, reason=$reason")
                }
            })
        }

        rootLayout.addView(tvView)
        setContentView(rootLayout)
        tvView.requestFocus()

        resolveAndTune(intent)
    }

    /**
     * 建立純黑不透明全螢幕蓋板（CPU 繪製，無透明度），遮住前一個 App 殘影
     */
    private fun showBlackCover(port: Int) {
        if (blackCoverLayout == null) {
            val density = resources.displayMetrics.density

            blackCoverLayout = FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK) // 純黑不透明遮擋
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }

            val infoContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    gravity = Gravity.TOP or Gravity.START
                    val margin = (40 * density).toInt()
                    setMargins(margin, margin, margin, margin)
                }
            }

            statusProgressBar = ProgressBar(this).apply {
                val size = (28 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (14 * density).toInt()
                }
                isIndeterminate = true
            }

            statusTextView = TextView(this).apply {
                setTextColor(0xFFFDE047.toInt()) // 琥珀黃
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.DEFAULT_BOLD
                text = getString(R.string.hdmi_searching_signal, port)
            }

            infoContainer.addView(statusProgressBar)
            infoContainer.addView(statusTextView)
            blackCoverLayout?.addView(infoContainer)

            rootLayout.addView(blackCoverLayout)
        } else {
            statusProgressBar?.visibility = View.VISIBLE
            statusTextView?.setTextColor(0xFFFDE047.toInt())
            statusTextView?.text = getString(R.string.hdmi_searching_signal, port)
        }
    }

    /**
     * 訊號已到達：顯示綠色已連線，並在極短時間後從 View Tree 徹底 removeView 移除黑幕！
     * 不用 alpha 動畫，保證 0 殘留、0 浮層、100% 露出底下硬體 TvView！
     */
    private fun onSignalReady() {
        statusProgressBar?.visibility = View.GONE
        statusTextView?.setTextColor(0xFF86EFAC.toInt()) // 綠色
        statusTextView?.text = getString(R.string.hdmi_signal_connected, currentPort)

        handler.postDelayed({
            removeBlackCover()
        }, 800L)
    }

    private fun showNoSignal() {
        showBlackCover(currentPort)
        statusProgressBar?.visibility = View.GONE
        statusTextView?.setTextColor(0xFFF87171.toInt()) // 紅色
        statusTextView?.text = getString(R.string.hdmi_no_signal, currentPort)
    }

    private fun removeBlackCover() {
        blackCoverLayout?.let {
            rootLayout.removeView(it)
            blackCoverLayout = null
            statusProgressBar = null
            statusTextView = null
            Log.i(TAG, "blackCoverLayout completely removed from root layout")
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
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

        // 搜尋訊號前先鋪上純黑蓋板（防殘留）
        showBlackCover(port)

        try {
            tvView.reset()
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
            showNoSignal()
            Toast.makeText(this, getString(R.string.toast_switch_failed, currentPort), Toast.LENGTH_SHORT).show()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> {
                    Toast.makeText(this, getString(R.string.toast_switching_hdmi, 1), Toast.LENGTH_SHORT).show()
                    tuneToPort(1)
                    return true
                }
                KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> {
                    Toast.makeText(this, getString(R.string.toast_switching_hdmi, 2), Toast.LENGTH_SHORT).show()
                    tuneToPort(2)
                    return true
                }
                KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> {
                    Toast.makeText(this, getString(R.string.toast_switching_hdmi, 3), Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            tvView.reset()
        } catch (_: Exception) {}
    }
}
