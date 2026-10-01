package com.lnu.tclhdmilauncher

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.ArrayMap
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import java.text.Collator
import java.util.concurrent.Executors

/**
 * App 模式專屬獨立設定頁面 (AppModeSettingsActivity)
 *
 * 獨立於 SettingsActivity，專門負責 App 模式偏好與系統捷徑：
 * - 採用 Android TV 官方標準 Leanback GuidedStepSupportFragment 設計
 * - App 模式啟動開關（開啟/關閉）
 * - 只有在 App 模式開啟 (Enabled) 時，才展開更多選項：
 *   • 開機 / 喚醒自動啟動 App 選擇（原生深色對話框，含清單快取與排序）
 *   • 自動啟動延遲倒數秒數設定
 * - 待機喚醒與 Home 鍵保障（無障礙服務捷徑）
 * - TCL 電視設定與 Android 系統設定捷徑
 */
class AppModeSettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, AppModeSettingsFragment(), android.R.id.content)
        }
    }

    class AppModeSettingsFragment : GuidedStepSupportFragment() {

        companion object {
            private const val ACTION_APP_MODE_STATUS = 1L
            private const val ACTION_AUTO_OPEN_APP = 2L
            private const val ACTION_AUTO_OPEN_DELAY = 3L

            private const val ACTION_APP_MODE_ENABLE = 1001L
            private const val ACTION_APP_MODE_DISABLE = 1002L
        }

        private data class InstalledApp(val pkg: String, val label: String)
        private val cachedAppList = ArrayList<InstalledApp>()
        private var isAppsLoaded = false

        private var appPickerDialog: AlertDialog? = null
        private var delayDialog: AlertDialog? = null

        private val bgExecutor = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())

        override fun onDestroy() {
            super.onDestroy()
            appPickerDialog?.dismiss()
            delayDialog?.dismiss()
            bgExecutor.shutdownNow()
            mainHandler.removeCallbacksAndMessages(null)
        }

        override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
            val ctx = requireContext()
            return GuidanceStylist.Guidance(
                getString(R.string.app_mode_settings_title),
                getString(R.string.app_mode_settings_hint),
                DeviceHelper.getBrandTitle(ctx),
                null
            )
        }

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            val ctx = requireContext()
            actions.addAll(buildActions(ctx))
            loadInstalledAppsAsync()
        }

        private fun buildActions(ctx: Context): List<GuidedAction> {
            val actions = mutableListOf<GuidedAction>()

            // 1. App 模式開關
            val appMode = MainActivity.isAppModeEnabled(ctx)
            val appModeSubActions = mutableListOf(
                GuidedAction.Builder(ctx)
                    .id(ACTION_APP_MODE_ENABLE)
                    .title(getString(R.string.setting_state_on))
                    .description(getString(R.string.setting_app_mode_desc_on))
                    .build(),
                GuidedAction.Builder(ctx)
                    .id(ACTION_APP_MODE_DISABLE)
                    .title(getString(R.string.setting_state_off))
                    .description(getString(R.string.setting_app_mode_desc_off))
                    .build()
            )
            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_APP_MODE_STATUS)
                    .title(getString(R.string.setting_app_mode_title))
                    .description(if (appMode) getString(R.string.setting_app_mode_desc_on) else getString(R.string.setting_app_mode_desc_off))
                    .subActions(appModeSubActions)
                    .build()
            )

            // 只有在 App 模式開啟 (Enabled) 時，才顯示更多選項（自動啟動 App、自動啟動倒數秒數）
            if (appMode) {
                // 2. 開機 / 喚醒自動啟動 App
                val autoPkg = MainActivity.getAutoOpenPackage(ctx)
                val autoLabel = MainActivity.getAutoOpenLabel(ctx)
                val autoText = if (autoPkg.isNotBlank()) {
                    autoLabel.ifBlank { autoPkg }
                } else {
                    getString(R.string.dialog_auto_open_none)
                }
                actions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_AUTO_OPEN_APP)
                        .title(getString(R.string.setting_auto_open_title))
                        .description(autoText)
                        .build()
                )

                // 3. 自動啟動倒數秒數
                val delaySeconds = MainActivity.getAutoOpenDelaySeconds(ctx)
                actions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_AUTO_OPEN_DELAY)
                        .title(getString(R.string.setting_auto_open_delay_title))
                        .description(getString(R.string.setting_auto_open_delay_desc, delaySeconds))
                        .build()
                )
            }

            return actions
        }

        override fun onResume() {
            super.onResume()
            val ctx = context ?: return

            // 重新刷新 Action 列表，確保 App 模式開關變化時及時展開或收合更多選項
            setActions(buildActions(ctx))
        }

        override fun onGuidedActionClicked(action: GuidedAction) {
            when (action.id) {
                ACTION_AUTO_OPEN_APP -> {
                    showAppPickerDialog()
                }
                ACTION_AUTO_OPEN_DELAY -> {
                    showDelayDialog()
                }
            }
        }

        override fun onSubGuidedActionClicked(action: GuidedAction): Boolean {
            val ctx = requireContext()
            when (action.id) {
                ACTION_APP_MODE_ENABLE -> {
                    MainActivity.setAppModeEnabled(ctx, true)
                    setActions(buildActions(ctx))
                    return true
                }
                ACTION_APP_MODE_DISABLE -> {
                    MainActivity.setAppModeEnabled(ctx, false)
                    setActions(buildActions(ctx))
                    return true
                }
            }
            return super.onSubGuidedActionClicked(action)
        }

        private fun showDelayDialog() {
            val ctx = context ?: return
            delayDialog?.dismiss()

            val currentDelay = MainActivity.getAutoOpenDelaySeconds(ctx)
            val options = listOf(
                1 to getString(R.string.dialog_option_1s),
                2 to getString(R.string.dialog_option_2s),
                3 to getString(R.string.dialog_option_3s_default),
                5 to getString(R.string.dialog_option_5s),
                10 to getString(R.string.dialog_option_10s),
                15 to getString(R.string.dialog_option_15s),
                30 to getString(R.string.dialog_option_30s)
            )

            val labels = options.map { it.second }.toTypedArray()
            val currentIndex = options.indexOfFirst { it.first == currentDelay }.let {
                if (it != -1) it else 2 // 預設 3 秒 (index 2)
            }

            delayDialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.dialog_auto_open_delay_title))
                .setSingleChoiceItems(labels, currentIndex) { d, which ->
                    val selectedDelay = options[which].first
                    MainActivity.setAutoOpenDelaySeconds(ctx, selectedDelay)

                    val action = findActionById(ACTION_AUTO_OPEN_DELAY)
                    if (action != null) {
                        action.description = getString(R.string.setting_auto_open_delay_desc, selectedDelay)
                        notifyActionChanged(findActionPositionById(ACTION_AUTO_OPEN_DELAY))
                    }
                    d.dismiss()
                }
                .setNegativeButton(getString(R.string.dialog_cancel)) { d, _ ->
                    d.dismiss()
                }
                .create().also { it.show() }
        }

        private fun showAppPickerDialog() {
            val ctx = context ?: return
            appPickerDialog?.dismiss()

            val currentPkg = MainActivity.getAutoOpenPackage(ctx)
            val displayList = ArrayList<InstalledApp>(cachedAppList.size + 1)
            displayList.add(InstalledApp("", getString(R.string.dialog_auto_open_none)))
            displayList.addAll(cachedAppList)

            val labels = displayList.map { it.label }.toTypedArray()
            val currentIndex = displayList.indexOfFirst { it.pkg == currentPkg }.let {
                if (it != -1) it else 0
            }

            appPickerDialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.dialog_select_auto_open_app))
                .setSingleChoiceItems(labels, currentIndex) { d, which ->
                    val selected = displayList[which]
                    MainActivity.setAutoOpenApp(ctx, selected.pkg, selected.label)
                    if (selected.pkg.isNotBlank() && !MainActivity.isAppModeEnabled(ctx)) {
                        MainActivity.setAppModeEnabled(ctx, true)
                        setActions(buildActions(ctx))
                    } else {
                        val action = findActionById(ACTION_AUTO_OPEN_APP)
                        if (action != null) {
                            action.description = if (selected.pkg.isNotBlank()) selected.label else getString(R.string.dialog_auto_open_none)
                            notifyActionChanged(findActionPositionById(ACTION_AUTO_OPEN_APP))
                        }
                    }
                    d.dismiss()
                }
                .setNegativeButton(getString(R.string.dialog_cancel)) { d, _ ->
                    d.dismiss()
                }
                .create().also { it.show() }
        }

        private fun loadInstalledAppsAsync() {
            if (isAppsLoaded) return

            bgExecutor.execute {
                val ctx = context ?: return@execute
                val pm = ctx.packageManager
                val selfPkg = ctx.packageName

                val leanbackResolves = pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
                )
                val mobileResolves = pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
                )

                val resolvedMap = ArrayMap<String, ResolveInfo>(64)
                for (ri in leanbackResolves) {
                    val pkg = ri.activityInfo?.packageName ?: continue
                    if (pkg != selfPkg && !resolvedMap.containsKey(pkg)) {
                        resolvedMap[pkg] = ri
                    }
                }
                for (ri in mobileResolves) {
                    val pkg = ri.activityInfo?.packageName ?: continue
                    if (pkg != selfPkg && !resolvedMap.containsKey(pkg)) {
                        resolvedMap[pkg] = ri
                    }
                }

                val list = ArrayList<InstalledApp>(resolvedMap.size)
                for (i in 0 until resolvedMap.size) {
                    val ri = resolvedMap.valueAt(i)
                    val actInfo = ri.activityInfo ?: continue
                    val appInfo = actInfo.applicationInfo ?: continue
                    val pkg = actInfo.packageName

                    val label = try {
                        ri.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                            ?: pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        pkg
                    }
                    list.add(InstalledApp(pkg, label))
                }

                val col = Collator.getInstance()
                list.sortWith { a, b -> col.compare(a.label, b.label) }

                mainHandler.post {
                    if (!isAdded || activity == null || activity?.isFinishing == true) return@post
                    cachedAppList.clear()
                    cachedAppList.addAll(list)
                    isAppsLoaded = true
                }
            }
        }
    }
}
