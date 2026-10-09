package com.lnu.tclhdmilauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.lnu.tclhdmilauncher.applist.AppListActivity
import com.lnu.tclhdmilauncher.cec.CecLogReaderService
import com.lnu.tclhdmilauncher.power.PowerHelper

/**
 * 應用程式全域 Application
 *
 * 註冊全域動態 BroadcastReceiver 監聽螢幕點亮 (ACTION_SCREEN_ON) 與喚醒。
 * 當電視從待機睡眠模式 (STR / Suspend-to-RAM) 喚醒時，即使未開啟無障礙服務，
 * 只要本 App 程序存活於記憶體中，便能即時偵測螢幕喚醒並強制拉回 Launcher。
 */
class TclHdmiApplication : Application() {

    companion object {
        private const val TAG = "TclHdmiApp"

        @Volatile
        var lastCecWakeTime: Long = 0

        /** Prevent the launcher's default-port timer from overriding a CEC input. */
        fun isCecInputOverrideActive(): Boolean =
            System.currentTimeMillis() - lastCecWakeTime < 15_000L

        /**
         * 強制啟動並拉回 Launcher 至最前景（依 App Mode 設定進入 AppListActivity 或 MainActivity）
         */
        fun wakeToLauncher(context: Context) {
            val homeIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
            }
            try {
                context.startActivity(homeIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MainActivity: ${e.message}")
            }
        }

        fun wakeScreen(context: Context) {
            PowerHelper.wakeScreen(context)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRetryRunnable: Runnable? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // 螢幕熄滅時，取消等待中的重新聚焦重試任務，避免在待機中誤觸發
                    cancelPendingRetries()
                }

                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT,
                "com.tcl.action.cec.MSG_VIEW_ON" -> {
                    Log.i(TAG, "Screen ON / Wake / CEC broadcast received: ${intent.action}")

                    if (intent.action == "com.tcl.action.cec.MSG_VIEW_ON") {
                        Log.i(TAG, "Native MSG_VIEW_ON received, updating lastCecWakeTime and waking screen only.")
                        lastCecWakeTime = System.currentTimeMillis()
                        wakeScreen(context)
                        return // 直接返回，不要強制切回 Launcher，保留給 CEC 訊號源
                    }

                    if (System.currentTimeMillis() - lastCecWakeTime < 3000) {
                        Log.i(TAG, "Ignoring wake to launcher because CEC woke the screen recently.")
                        return
                    }

                    Log.i(TAG, "waking to Launcher...")
                    // 1. 立即喚醒拉回
                    wakeToLauncher(context)

                    // 2. 針對 TCL 等電視底層 TV 輸入服務的延遲初始化，於 400ms 與 800ms 進行重試奪回
                    cancelPendingRetries()
                    val retryAction = object : Runnable {
                        var retriesLeft = 2
                        override fun run() {
                            val isFocused = MainActivity.isForegroundFocused || AppListActivity.isForegroundFocused || HdmiViewerActivity.isForegroundFocused
                            if (!isFocused && retriesLeft > 0) {
                                retriesLeft--
                                Log.i(TAG, "Launcher not yet focused, re-asserting focus...")
                                wakeToLauncher(context)
                                handler.postDelayed(this, 400L)
                            }
                        }
                    }
                    pendingRetryRunnable = retryAction
                    handler.postDelayed(retryAction, 400L)
                }
            }
        }
    }

    private fun cancelPendingRetries() {
        pendingRetryRunnable?.let {
            handler.removeCallbacks(it)
            pendingRetryRunnable = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction("com.tcl.action.cec.MSG_VIEW_ON")
        }
        // The filter includes TCL's cross-process CEC broadcast.  Android 13+
        // requires this explicit flag for non-system-only dynamic receivers.
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        // 啟動 CEC 監控服務
        try {
            val serviceIntent = Intent(this, CecLogReaderService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CecLogReaderService", e)
        }
    }
}
