package com.lnu.tclhdmilauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import rikka.shizuku.Shizuku

/**
 * Shizuku API 檢測與 APK 自動下載/安裝 Activity
 * 採用 Android TV 官方 Leanback GuidedStepSupportFragment 設計
 */
class ShizukuSettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, ShizukuSettingsFragment(), android.R.id.content)
        }
    }

    class ShizukuSettingsFragment : GuidedStepSupportFragment() {

        companion object {
            private const val REQUEST_CODE_SHIZUKU = 1001

            private const val ACTION_STATUS = 1L
            private const val ACTION_REQUEST_PERMISSION = 2L
            private const val ACTION_GRANT_APP_PERMS = 3L
            private const val ACTION_LAUNCH_SHIZUKU = 4L
            private const val ACTION_DOWNLOAD_APK = 5L
            private const val ACTION_REFRESH = 7L
            private const val ACTION_INSTALL_LOCAL_APK = 8L
            private const val ACTION_CEC_DEBUG = 9L

        }

        private val mainHandler = Handler(Looper.getMainLooper())
        private var isDownloading = false

        private val onRequestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_SHIZUKU) {
                mainHandler.post {
                    if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        context?.let { ShizukuHelper.tryGrantPermissions(it) }
                    }
                    refreshUI()
                }
            }
        }

        private val onBinderReceivedListener = Shizuku.OnBinderReceivedListener {
            mainHandler.post {
                context?.let { ShizukuHelper.tryGrantPermissions(it) }
                refreshUI()
            }
        }

        private val onBinderDeadListener = Shizuku.OnBinderDeadListener {
            mainHandler.post {
                refreshUI()
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            try {
                Shizuku.addRequestPermissionResultListener(onRequestPermissionResultListener)
                Shizuku.addBinderReceivedListener(onBinderReceivedListener)
                Shizuku.addBinderDeadListener(onBinderDeadListener)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }



        override fun onDestroy() {
            super.onDestroy()
            try {
                Shizuku.removeRequestPermissionResultListener(onRequestPermissionResultListener)
                Shizuku.removeBinderReceivedListener(onBinderReceivedListener)
                Shizuku.removeBinderDeadListener(onBinderDeadListener)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mainHandler.removeCallbacksAndMessages(null)
        }

        override fun onResume() {
            super.onResume()
            refreshUI()
        }



        override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
            val ctx = requireContext()
            return GuidanceStylist.Guidance(
                getString(R.string.shizuku_settings_title),
                getString(R.string.shizuku_settings_hint),
                DeviceHelper.getBrandTitle(ctx),
                null
            )
        }

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            actions.addAll(buildActions(requireContext()))
        }

        private fun refreshUI() {
            val ctx = context ?: return
            if (!isAdded || activity == null || activity?.isFinishing == true) return
            setActions(buildActions(ctx))
        }

        private fun buildActions(ctx: Context): List<GuidedAction> {
            val actions = mutableListOf<GuidedAction>()

            val isInstalled = ShizukuHelper.isShizukuInstalled(ctx)
            val isRunning = ShizukuHelper.isShizukuRunning()
            val isGranted = ShizukuHelper.isShizukuPermissionGranted()
            val version = ShizukuHelper.getShizukuVersion()

            // 1. 狀態摘要 Card
            val statusTitle = getString(R.string.shizuku_status_title)
            val statusDesc = when {
                isRunning && isGranted -> getString(R.string.shizuku_status_running_granted, if (version > 0) "v$version" else "")
                isRunning && !isGranted -> getString(R.string.shizuku_status_running_not_granted)
                isInstalled -> getString(R.string.shizuku_status_installed_not_running)
                else -> getString(R.string.shizuku_status_not_installed)
            }

            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_STATUS)
                    .title(statusTitle)
                    .description(statusDesc)
                    .focusable(false)
                    .build()
            )

            // 2. 請求 Shizuku 授權 (當 Shizuku 運作中但未授權時)
            if (isRunning && !isGranted) {
                actions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_REQUEST_PERMISSION)
                        .title(getString(R.string.shizuku_action_request_perm))
                        .description(getString(R.string.shizuku_action_request_perm_desc))
                        .build()
                )
            }

            // 3. 透過 Shizuku 授予系統權限 (READ_LOGS / DUMP)
            if (isRunning && isGranted) {
                actions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_GRANT_APP_PERMS)
                        .title(getString(R.string.shizuku_action_grant_system_perms))
                        .description(getString(R.string.shizuku_action_grant_system_perms_desc))
                        .build()
                )
            }

            // 4. 開啟 Shizuku App (當已安裝 Shizuku 時)
            if (isInstalled) {
                actions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_LAUNCH_SHIZUKU)
                        .title(getString(R.string.shizuku_action_launch_app))
                        .description(getString(R.string.shizuku_action_launch_app_desc))
                        .build()
                )
            }



            // 6. 檢測是否存在已下載的本機 APK 檔案
            val localApk = ShizukuHelper.getDownloadedApkFile(ctx)
            if (localApk != null) {
                actions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_INSTALL_LOCAL_APK)
                        .title(getString(R.string.shizuku_action_install_local_apk))
                        .description(getString(R.string.shizuku_action_install_local_apk_desc))
                        .build()
                )
            }

            // 7. 下載 / 重新下載最新版 Shizuku APK
            val downloadTitleRes = if (localApk != null) R.string.shizuku_action_redownload_apk else R.string.shizuku_action_download_apk
            val downloadDescRes = if (localApk != null) R.string.shizuku_action_redownload_apk_desc else R.string.shizuku_action_download_apk_desc

            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_DOWNLOAD_APK)
                    .title(getString(downloadTitleRes))
                    .description(if (isDownloading) getString(R.string.shizuku_downloading) else getString(downloadDescRes))
                    .build()
            )

            // 8. CEC 除錯日誌
            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_CEC_DEBUG)
                    .title(getString(R.string.cec_debug_title))
                    .description(getString(R.string.cec_debug_desc))
                    .build()
            )

            // 9. 重新整理
            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_REFRESH)
                    .title(getString(R.string.shizuku_action_refresh))
                    .description(getString(R.string.shizuku_action_refresh_desc))
                    .build()
            )

            return actions
        }

        override fun onGuidedActionClicked(action: GuidedAction) {
            val ctx = requireContext()
            when (action.id) {
                ACTION_REQUEST_PERMISSION -> {
                    ShizukuHelper.requestShizukuPermission(REQUEST_CODE_SHIZUKU)
                }
                ACTION_GRANT_APP_PERMS -> {
                    ShizukuHelper.tryGrantPermissions(ctx)
                    Toast.makeText(ctx, R.string.shizuku_toast_granted_system_perms, Toast.LENGTH_SHORT).show()
                    refreshUI()
                }
                ACTION_LAUNCH_SHIZUKU -> {
                    val intent = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (intent != null) {
                        startActivity(intent)
                    } else {
                        Toast.makeText(ctx, R.string.shizuku_toast_cannot_launch, Toast.LENGTH_SHORT).show()
                    }
                }

                ACTION_INSTALL_LOCAL_APK -> {
                    val apkFile = ShizukuHelper.getDownloadedApkFile(ctx)
                    if (apkFile != null) {
                        ShizukuHelper.installApkFile(ctx, apkFile)
                    } else {
                        Toast.makeText(ctx, R.string.shizuku_fetch_failed, Toast.LENGTH_SHORT).show()
                        refreshUI()
                    }
                }
                ACTION_DOWNLOAD_APK -> {
                    if (isDownloading) return
                    startApkDownload(ctx, action)
                }
                ACTION_CEC_DEBUG -> {
                    startActivity(Intent(ctx, CecDebugActivity::class.java))
                }
                ACTION_REFRESH -> {
                    refreshUI()
                    Toast.makeText(ctx, R.string.shizuku_toast_refreshed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun startApkDownload(ctx: Context, action: GuidedAction) {
            isDownloading = true
            action.description = getString(R.string.shizuku_fetching_link)
            notifyActionChanged(findActionPositionById(ACTION_DOWNLOAD_APK))

            ShizukuHelper.fetchLatestShizukuApk { downloadUrl ->
                if (downloadUrl.isNullOrEmpty()) {
                    mainHandler.post {
                        isDownloading = false
                        action.description = getString(R.string.shizuku_fetch_failed)
                        notifyActionChanged(findActionPositionById(ACTION_DOWNLOAD_APK))
                        Toast.makeText(ctx, R.string.shizuku_fetch_failed, Toast.LENGTH_SHORT).show()
                    }
                    return@fetchLatestShizukuApk
                }

                mainHandler.post {
                    action.description = getString(R.string.shizuku_starting_download)
                    notifyActionChanged(findActionPositionById(ACTION_DOWNLOAD_APK))
                }

                ShizukuHelper.downloadAndInstallApk(
                    context = ctx,
                    downloadUrl = downloadUrl,
                    onProgress = { progressStr ->
                        mainHandler.post {
                            action.description = "${getString(R.string.shizuku_downloading)}: $progressStr"
                            notifyActionChanged(findActionPositionById(ACTION_DOWNLOAD_APK))
                        }
                    },
                    onComplete = { success, msg ->
                        mainHandler.post {
                            isDownloading = false
                            if (success) {
                                action.description = getString(R.string.shizuku_download_success)
                                Toast.makeText(ctx, R.string.shizuku_download_success, Toast.LENGTH_SHORT).show()
                                refreshUI()
                            } else {
                                action.description = "${getString(R.string.shizuku_download_failed)}: $msg"
                                Toast.makeText(ctx, "${getString(R.string.shizuku_download_failed)}: $msg", Toast.LENGTH_LONG).show()
                                notifyActionChanged(findActionPositionById(ACTION_DOWNLOAD_APK))
                            }
                        }
                    }
                )
            }
        }
    }
}
