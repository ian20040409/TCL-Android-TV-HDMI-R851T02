package com.lnu.tclhdmilauncher.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.IOException
import rikka.shizuku.Shizuku

object ShizukuHelper {
    private const val TAG = "ShizukuHelper"

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun isShizukuPermissionGranted(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun getShizukuVersion(): Int {
        return try {
            if (Shizuku.pingBinder()) Shizuku.getVersion() else -1
        } catch (e: Exception) {
            -1
        }
    }

    fun requestShizukuPermission(requestCode: Int) {
        if (Shizuku.pingBinder()) {
            try {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(requestCode)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting Shizuku permission", e)
            }
        }
    }

    fun tryGrantPermissions(context: Context) {
        if (!Shizuku.pingBinder()) {
            Log.d(TAG, "Shizuku is not running")
            return
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Shizuku permission not granted to app")
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
                    val exitCode = executeShellCommand("pm grant $packageName $permission")
                    if (exitCode == 0) {
                        Log.i(TAG, "Successfully granted $permission via Shizuku")
                    } else {
                        Log.e(TAG, "Failed to grant $permission via Shizuku, exit: $exitCode")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error granting $permission via Shizuku", e)
                }
            }
        }
    }

    private val newProcessMethod by lazy {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        method
    }

    /**
     * 執行 Shizuku Shell 指令
     */
    fun executeShellCommand(command: String): Int {
        if (!Shizuku.pingBinder()) return -1
        return try {
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process
            
            process.waitFor()
            process.exitValue()
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    /**
     * 透過 Shizuku 停用或啟用 App (與 Hail 相同功能)
     */
    fun setAppDisabled(packageName: String, disabled: Boolean): Boolean {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        if (disabled) {
            executeShellCommand("am force-stop $packageName")
            return executeShellCommand("pm disable-user --user 0 $packageName") == 0
        } else {
            return executeShellCommand("pm enable --user 0 $packageName") == 0
        }
    }

    private val networkExecutor = java.util.concurrent.Executors.newCachedThreadPool()

    /**
     * 透過 GitHub API 自動獲取最新版 Shizuku 的 APK 下載連結
     */
    fun fetchLatestShizukuApk(onResult: (String?) -> Unit) {
        networkExecutor.execute {
            try {
                val url = java.net.URL("https://api.github.com/repos/RikkaApps/Shizuku/releases/latest")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "TCL-HDMI-Launcher")
                
                if (connection.responseCode == 200) {
                    val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = org.json.JSONObject(jsonResponse)
                    val assets = jsonObject.getJSONArray("assets")
                    
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk")) {
                            val downloadUrl = asset.getString("browser_download_url")
                            onResult(downloadUrl)
                            return@execute
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

    fun getDownloadedApkFile(context: Context): java.io.File? {
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir
        val apkFile = java.io.File(downloadsDir, "Shizuku_latest.apk")
        if (apkFile.exists() && apkFile.length() > 0) {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (info != null) {
                return apkFile
            }
        }
        return null
    }

    fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        networkExecutor.execute {
            try {
                onProgress("Connecting...")
                var currentUrl = downloadUrl
                var connection: java.net.HttpURLConnection
                var redirects = 0
                while (true) {
                    val url = java.net.URL(currentUrl)
                    connection = url.openConnection() as java.net.HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.setRequestProperty("User-Agent", "TCL-HDMI-Launcher")
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    connection.connect()

                    val status = connection.responseCode
                    if (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == java.net.HttpURLConnection.HTTP_MOVED_PERM ||
                        status == java.net.HttpURLConnection.HTTP_SEE_OTHER
                    ) {
                        val newUrl = connection.getHeaderField("Location")
                        if (!newUrl.isNullOrEmpty() && redirects < 5) {
                            currentUrl = newUrl
                            redirects++
                            continue
                        }
                    }
                    break
                }

                if (connection.responseCode != 200) {
                    onComplete(false, "HTTP ${connection.responseCode}")
                    return@execute
                }

                var totalSize = connection.contentLengthLong
                if (totalSize <= 0) {
                    totalSize = connection.getHeaderFieldLong("Content-Length", -1L)
                }

                val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    ?: context.cacheDir
                val apkFile = java.io.File(downloadsDir, "Shizuku_latest.apk")
                val tempFile = java.io.File(downloadsDir, "Shizuku_latest.apk.tmp")
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                val inputStream = connection.inputStream
                val outputStream = java.io.FileOutputStream(tempFile)
                val buffer = ByteArray(16384)
                var downloaded = 0L
                var read: Int
                var lastProgressTime = 0L

                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    downloaded += read

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastProgressTime >= 150 || (totalSize > 0 && downloaded == totalSize)) {
                        lastProgressTime = currentTime
                        val downloadedMb = downloaded / 1048576.0
                        val progressMsg = if (totalSize > 0) {
                            val totalMb = totalSize / 1048576.0
                            val percent = ((downloaded * 100) / totalSize).toInt()
                            String.format(java.util.Locale.US, "%.1f MB / %.1f MB (%d%%)", downloadedMb, totalMb, percent)
                        } else {
                            String.format(java.util.Locale.US, "%.1f MB", downloadedMb)
                        }
                        onProgress(progressMsg)
                    }
                }

                outputStream.flush()
                try {
                    outputStream.fd.sync()
                } catch (e: IOException) {
                    Log.w(TAG, "fd.sync() failed: ${e.message}")
                }
                outputStream.close()
                inputStream.close()

                if (apkFile.exists()) {
                    apkFile.delete()
                }
                tempFile.renameTo(apkFile)

                onProgress("Installing...")
                installApkFile(context, apkFile)
                onComplete(true, apkFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                onComplete(false, e.localizedMessage)
            }
        }
    }

    fun installApkFile(context: Context, apkFile: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK via Intent", e)
        }
    }
}
