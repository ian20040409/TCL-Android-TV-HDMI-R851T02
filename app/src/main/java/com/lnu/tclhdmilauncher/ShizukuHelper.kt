package com.lnu.tclhdmilauncher

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuHelper {
    private const val TAG = "ShizukuHelper"

    /**
     * =========================================================================
     * Shizuku 下載與版本設定 (Shizuku Download Configuration)
     * =========================================================================
     * 以後若要更換 Shizuku 來源、版本或直接下載連結，只需修改此處設定即可快速生效！
     */
    object DownloadConfig {
        /** GitHub 倉庫 (擁有者/專案名稱) */
        const val GITHUB_REPO = "thedjchi/Shizuku"

        /** GitHub 專案首頁網址 */
        const val REPO_URL = "https://github.com/$GITHUB_REPO"

        /**
         * GitHub API 最新 Release 查詢網址
         * 優先透過 GitHub API 動態取得最新發布的 APK 下載連結
         */
        const val API_LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

        /**
         * 備用 / 預設 APK 直接下載連結 (Fallback URL)
         * 當 GitHub API 遭遇頻率限制 (例如未登入時的 403 Rate Limit)、逾時或網路問題時，
         * 會自動回退使用此直接下載連結，確保電視端下載與安裝不中斷。
         */
        const val DIRECT_APK_URL = "https://github.com/thedjchi/Shizuku/releases/download/v13.7.0-thedjchi/shizuku-v13.7.0-thedjchi.apk"

        /** 本地下載儲存檔案名稱 */
        const val APK_FILE_NAME = "Shizuku_latest.apk"
    }

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

        grantAccessibilityService(context)
    }

    /**
     * Enable WakeAccessibilityService through the shell-owned secure settings.
     * Keep any accessibility services the user has already enabled.
     */
    private fun grantAccessibilityService(context: Context) {
        if (AccessibilityHelper.isServiceEnabled(context)) {
            Log.d(TAG, "WakeAccessibilityService is already enabled")
            return
        }

        val service = "${context.packageName}/${WakeAccessibilityService::class.java.name}"
        val dollar = '$'
        val command = """
            current=$dollar(settings get secure enabled_accessibility_services 2>/dev/null)
            service='$service'
            case ":${dollar}current:" in
                *":${dollar}service:"*) ;;
                *)
                    if [ -z "${dollar}current" ] || [ "${dollar}current" = "null" ]; then
                        current="${dollar}service"
                    else
                        current="${dollar}current:${dollar}service"
                    fi
                    settings put secure enabled_accessibility_services "${dollar}current" || exit ${dollar}?
                    ;;
            esac
            settings put secure accessibility_enabled 1
        """.trimIndent()

        try {
            val exitCode = executeShellCommand(command)
            if (exitCode == 0) {
                Log.i(TAG, "Enabled WakeAccessibilityService via Shizuku shell")
            } else {
                Log.e(TAG, "Failed to enable WakeAccessibilityService via Shizuku, exit: $exitCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling WakeAccessibilityService via Shizuku", e)
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

    /** Start a long-running shell process through Shizuku (for example, logcat). */
    fun startShellProcess(command: String): Process? {
        if (!isShizukuPermissionGranted()) return null
        return try {
            newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Shizuku shell process", e)
            null
        }
    }

    /**
     * 執行 Shizuku Shell 指令
     */
    fun executeShellCommand(command: String): Int {
        val process = startShellProcess(command) ?: return -1
        return try {
            process.waitFor()
            process.exitValue()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Shizuku shell command", e)
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
     * 透過 GitHub API 自動獲取最新版 Shizuku 的 APK 下載連結。
     * 若 API 遭遇頻率限制 (例如未登入時的 403 Rate Limit)、逾時或網路問題，會自動回退使用 DownloadConfig.DIRECT_APK_URL。
     */
    fun fetchLatestShizukuApk(onResult: (String?) -> Unit) {
        networkExecutor.execute {
            var resolvedUrl: String? = null
            try {
                val apiUrl = DownloadConfig.API_LATEST_RELEASE_URL
                if (apiUrl.isNotBlank()) {
                    val url = java.net.URL(apiUrl)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    connection.setRequestProperty("User-Agent", "TCL-HDMI-Launcher")

                    if (connection.responseCode == 200) {
                        val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonObject = org.json.JSONObject(jsonResponse)
                        val assets = jsonObject.optJSONArray("assets")
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val assetName = asset.optString("name", "")
                                if (assetName.endsWith(".apk")) {
                                    val downloadUrl = asset.optString("browser_download_url")
                                    if (downloadUrl.isNotBlank()) {
                                        Log.i(TAG, "Found latest APK via GitHub API: $downloadUrl")
                                        resolvedUrl = downloadUrl
                                        break
                                    }
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "GitHub API returned HTTP ${connection.responseCode}, falling back to direct URL")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query GitHub API: ${e.message}, falling back to direct URL")
            }

            // 若 API 解析成功則使用，否則自動回退至備用直接下載連結
            val finalUrl = resolvedUrl ?: DownloadConfig.DIRECT_APK_URL.takeIf { it.isNotBlank() }
            if (finalUrl != null) {
                Log.i(TAG, "Resolved Shizuku download URL: $finalUrl")
            } else {
                Log.e(TAG, "No download URL available for Shizuku")
            }
            onResult(finalUrl)
        }
    }

    fun getDownloadedApkFile(context: Context): java.io.File? {
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir
        val candidates = listOf(
            java.io.File(downloadsDir, DownloadConfig.APK_FILE_NAME),
            java.io.File(downloadsDir, "Shizuku_latest.apk")
        ).distinct()
        val pm = context.packageManager
        for (apkFile in candidates) {
            if (apkFile.exists() && apkFile.length() > 0) {
                val info = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
                if (info != null) {
                    return apkFile
                }
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
                val apkFile = java.io.File(downloadsDir, DownloadConfig.APK_FILE_NAME)
                val tempFile = java.io.File(downloadsDir, "${DownloadConfig.APK_FILE_NAME}.tmp")
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
                } catch (_: Exception) {}
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
