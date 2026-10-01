package com.lnu.tclhdmilauncher

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuHelper {
    private const val TAG = "ShizukuHelper"

    fun tryGrantPermissions(context: Context) {
        if (!Shizuku.pingBinder()) {
            Log.d(TAG, "Shizuku is not running")
            return
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Shizuku permission not granted to app")
            // Can request permission here if needed
            return
        }

        val packageName = context.packageName
        val permissionsToGrant = listOf(
            "android.permission.READ_LOGS",
            "android.permission.DUMP"
        )

        for (permission in permissionsToGrant) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                try {
                    // newProcess is private in Shizuku 13.1.5, use reflection as a workaround for simple commands
                    val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                        "newProcess",
                        Array<String>::class.java,
                        Array<String>::class.java,
                        String::class.java
                    )
                    newProcessMethod.isAccessible = true
                    val process = newProcessMethod.invoke(
                        null,
                        arrayOf("sh", "-c", "pm grant $packageName $permission"),
                        null,
                        null
                    ) as Process
                    
                    process.waitFor()
                    if (process.exitValue() == 0) {
                        Log.i(TAG, "Successfully granted $permission via Shizuku")
                    } else {
                        Log.e(TAG, "Failed to grant $permission via Shizuku, exit: ${process.exitValue()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error granting $permission via Shizuku", e)
                }
            }
        }
    }

    /**
     * 透過 GitHub API 自動獲取最新版 Shizuku 的 APK 下載連結
     */
    fun fetchLatestShizukuApk(onResult: (String?) -> Unit) {
        kotlin.concurrent.thread {
            try {
                // 修正了 API 網址，指向 Shizuku 的 latest release
                val url = java.net.URL("https://api.github.com/repos/RikkaApps/Shizuku/releases/latest")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "TCL-HDMI-Launcher")
                
                if (connection.responseCode == 200) {
                    val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = org.json.JSONObject(jsonResponse)
                    val assets = jsonObject.getJSONArray("assets")
                    
                    // 迴圈尋找 APK 檔案
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk")) {
                            val downloadUrl = asset.getString("browser_download_url")
                            onResult(downloadUrl) // 成功拿到最新 APK 網址
                            return@thread
                        }
                    }
                }
                onResult(null)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }
    }
}
