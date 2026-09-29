package com.lnu.tclhdmilauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader

class CecLogReaderService : Service() {

    private var logcatProcess: Process? = null
    private var isReading = false

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
                
                // 讀取包含 HDMI CEC 的 log，使用 -T 1 確保只讀取新的 log
                val command = arrayOf("logcat", "-v", "time", "-T", "1", "-s", "HdmiCecController", "HdmiCecLocalDeviceTv")
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
        if (line.contains("command:<Image View On>")) {
            Log.i(TAG, "偵測到 CEC 喚醒訊號 (Image View On)！準備喚醒螢幕...")
            TclHdmiApplication.lastCecWakeTime = System.currentTimeMillis()
            TclHdmiApplication.wakeScreen(this)
            // 不再喚醒到 Launcher，避免覆蓋原生 CEC 訊號源
        } else if (line.contains("command:<Active Source>")) {
            val regex = "params: ([0-9a-fA-F]{2})".toRegex()
            val match = regex.find(line)
            if (match != null) {
                val param1 = match.groupValues[1]
                val portChar = param1[0]
                val port = portChar.toString().toIntOrNull()
                if (port != null && port in 1..4) {
                    Log.i(TAG, "偵測到 CEC Active Source，準備喚醒螢幕並切換至 HDMI $port")
                    TclHdmiApplication.lastCecWakeTime = System.currentTimeMillis()
                    TclHdmiApplication.wakeScreen(this)
                    val intent = HdmiViewerActivity.createIntent(this, port)
                    startActivity(intent)
                }
            }
        } else if (line.contains("command:<InActive Source>")) {
            Log.i(TAG, "偵測到 CEC 待機訊號 (InActive Source)！準備關閉螢幕...")
            goToSleep()
        } else if (line.contains("command:<Standby>") || (line.contains("command:<Report Power Status>") && line.contains("params: 01"))) {
            Log.i(TAG, "偵測到 CEC 待機訊號 (Standby / Power Status 01)！準備關閉螢幕...")
            goToSleep()
        }
    }

    private fun goToSleep() {
        // 利用無障礙服務模擬按下電源鍵 (如果是 Android 9+)
        val intent = Intent(this, WakeAccessibilityService::class.java)
        intent.action = "ACTION_SLEEP"
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "CecLogReaderService onDestroy")
        isReading = false
        logcatProcess?.destroy()
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
