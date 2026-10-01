package com.lnu.tclhdmilauncher

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction

/**
 * TCL TV HDMI Launcher 一般設定頁面 (SettingsActivity)
 *
 * 採用 Android TV 官方標準 Leanback GuidedStepSupportFragment 設計：
 * - 100% Android TV 原生視覺與遙控器焦點導航規範
 * - 左側清晰導引看板（標題、描述、圖示）
 * - 「App 模式設定」作為專屬入口，點擊直達獨立的 AppModeSettingsActivity
 * - 「自動倒數秒數」與「預設訊號源」點擊彈出系統原生 AlertDialog 單選設定框
 * - 「訊號搜尋畫面」支援次級選單流暢切換開關
 * - 整合無障礙防拔除統一導航邏輯 (AccessibilityHelper)
 * - TCL 電視與 Android 系統設定快捷入口
 */
class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, MainSettingsFragment(), android.R.id.content)
        }
    }

    class MainSettingsFragment : GuidedStepSupportFragment() {

        companion object {
            private const val ACTION_APP_MODE_PARENT = 1L
            private const val ACTION_COUNTDOWN = 2L
            private const val ACTION_DEFAULT_PORT = 3L
            private const val ACTION_SIGNAL_SEARCH_PARENT = 4L
            private const val ACTION_WAKE_GUARD = 5L
            private const val ACTION_TCL_SETTINGS = 6L
            private const val ACTION_ANDROID_SETTINGS = 7L
            private const val ACTION_RESET_APP = 8L
            private const val ACTION_CEC_DEBUG = 9L
            private const val ACTION_BUTTON_MAPPER = 10L
            private const val ACTION_MAPPER_HOME_ON = 1001L
            private const val ACTION_MAPPER_HOME_OFF = 1002L
            private const val ACTION_MAPPER_INPUT_ON = 1003L
            private const val ACTION_MAPPER_INPUT_OFF = 1004L
            private const val ACTION_MAPPER_ACCESSIBILITY = 1005L

            private const val ACTION_SIGNAL_SEARCH_ENABLE = 5001L
            private const val ACTION_SIGNAL_SEARCH_DISABLE = 5002L
        }

        private var countdownDialog: AlertDialog? = null
        private var defaultPortDialog: AlertDialog? = null
        private var resetDialog: AlertDialog? = null

        private val mainHandler = Handler(Looper.getMainLooper())

        override fun onDestroy() {
            super.onDestroy()
            countdownDialog?.dismiss()
            defaultPortDialog?.dismiss()
            resetDialog?.dismiss()
            mainHandler.removeCallbacksAndMessages(null)
        }

        override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
            return GuidanceStylist.Guidance(
                getString(R.string.settings_title),
                getString(R.string.settings_hint),
                getString(R.string.brand_name),
                requireContext().getDrawable(R.drawable.settings_48px)
            )
        }

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            val ctx = requireContext()

            // 1. App 模式設定入口 (點擊進入專屬獨立的 AppModeSettingsActivity)
            val appModeDesc = getAppModeSummaryText(ctx)
            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_APP_MODE_PARENT)
                    .title(getString(R.string.app_mode_settings_title))
                    .description(appModeDesc)
                    .icon(ctx.getDrawable(R.drawable.apps_48px))
                    .build()
            )

            // 2. 自動倒數秒數 (點擊彈出 AlertDialog 單選框)
            val countdown = MainActivity.getCountdownSeconds(ctx)
            val countdownSummary = if (countdown <= 0) {
                getString(R.string.setting_countdown_desc_off)
            } else {
                getString(R.string.setting_countdown_desc, countdown)
            }
            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_COUNTDOWN)
                    .title(getString(R.string.setting_countdown_title))
                    .description(countdownSummary)
                    .icon(ctx.getDrawable(R.drawable.timer_48px))
                    .build()
            )

            // 3. 預設訊號源 (點擊彈出 AlertDialog 單選框)
            val defaultPort = MainActivity.getDefaultPort(ctx)
            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_DEFAULT_PORT)
                    .title(getString(R.string.setting_default_port_title))
                    .description(getString(R.string.setting_default_port_desc, defaultPort))
                    .icon(ctx.getDrawable(R.drawable.settings_input_hdmi_24px))
                    .build()
            )

            // 4. 訊號搜尋畫面 (進入後顯示開啟和關閉按鈕)
            val signalSearch = MainActivity.isSignalSearchScreenEnabled(ctx)
            val signalSubActions = mutableListOf<GuidedAction>()
            signalSubActions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_SIGNAL_SEARCH_ENABLE)
                    .title(getString(R.string.setting_state_on))
                    .description(getString(R.string.setting_signal_search_desc_on))
                    .build()
            )
            signalSubActions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_SIGNAL_SEARCH_DISABLE)
                    .title(getString(R.string.setting_state_off))
                    .description(getString(R.string.setting_signal_search_desc_off))
                    .build()
            )
            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_SIGNAL_SEARCH_PARENT)
                    .title(getString(R.string.setting_signal_search_title))
                    .description(getString(if (signalSearch) R.string.setting_signal_search_desc_on else R.string.setting_signal_search_desc_off))
                    .icon(ctx.getDrawable(R.drawable.cable_48px))
                    .subActions(signalSubActions)
                    .build()
            )

            // 5. 待機喚醒與 Home 鍵保障 (無障礙)
            val wakeGuard = AccessibilityHelper.isServiceEnabled(ctx)
            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_WAKE_GUARD)
                    .title(getString(R.string.setting_wake_guard_title))
                    .description(getString(if (wakeGuard) R.string.setting_wake_guard_desc_on else R.string.setting_wake_guard_desc_off))
                    .icon(ctx.getDrawable(R.drawable.accessibility_new_48px))
                    .build()
            )

            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_CEC_DEBUG)
                    .title("CEC 偵錯記錄")
                    .description("查看 CEC 喚醒、輸入切換與待機事件")
                    .icon(ctx.getDrawable(R.drawable.settings_input_hdmi_24px))
                    .build()
            )

            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_BUTTON_MAPPER)
                    .title(getString(R.string.setting_button_mapper_title))
                    .description(buttonMapperSummary(ctx))
                    .icon(ctx.getDrawable(R.drawable.accessibility_new_48px))
                    .subActions(buttonMapperSubActions(ctx))
                    .build()
            )

            // 6. TCL 電視設定
            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_TCL_SETTINGS)
                    .title(getString(R.string.setting_tcl_settings_title))
                    .description(getString(R.string.setting_tcl_settings_desc))
                    .icon(ctx.getDrawable(R.drawable.open_in_new_48px))
                    .build()
            )

            // 7. Android 系統設定
            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_ANDROID_SETTINGS)
                    .title(getString(R.string.setting_android_settings_title))
                    .description(getString(R.string.setting_android_settings_desc))
                    .icon(ctx.getDrawable(R.drawable.open_in_new_48px))
                    .build()
            )

            // 8. 重設 App
            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_RESET_APP)
                    .title(getString(R.string.setting_reset_app_title))
                    .description(getString(R.string.setting_reset_app_desc))
                    .icon(ctx.getDrawable(R.drawable.delete_48px))
                    .build()
            )
        }

        override fun onResume() {
            super.onResume()
            val ctx = context ?: return

            // 喚醒保護狀態動態更新
            val wakeGuard = AccessibilityHelper.isServiceEnabled(ctx)
            val wakeAction = findActionById(ACTION_WAKE_GUARD)
            if (wakeAction != null) {
                wakeAction.description = getString(if (wakeGuard) R.string.setting_wake_guard_desc_on else R.string.setting_wake_guard_desc_off)
                notifyActionChanged(findActionPositionById(ACTION_WAKE_GUARD))
            }

            // App 模式狀態動態更新
            val appModeAction = findActionById(ACTION_APP_MODE_PARENT)
            if (appModeAction != null) {
                appModeAction.description = getAppModeSummaryText(ctx)
                notifyActionChanged(findActionPositionById(ACTION_APP_MODE_PARENT))
            }
            val mapperAction = findActionById(ACTION_BUTTON_MAPPER)
            if (mapperAction != null) {
                mapperAction.description = buttonMapperSummary(ctx)
                notifyActionChanged(findActionPositionById(ACTION_BUTTON_MAPPER))
            }
        }

        override fun onGuidedActionClicked(action: GuidedAction) {
            val ctx = requireContext()
            when (action.id) {
                ACTION_APP_MODE_PARENT -> {
                    startActivity(Intent(ctx, AppModeSettingsActivity::class.java))
                }
                ACTION_COUNTDOWN -> {
                    showCountdownDialog()
                }
                ACTION_DEFAULT_PORT -> {
                    showDefaultPortDialog()
                }
                ACTION_WAKE_GUARD -> {
                    AccessibilityHelper.openAccessibilitySettings(ctx)
                }
                ACTION_CEC_DEBUG -> {
                    startActivity(Intent(ctx, CecDebugActivity::class.java))
                }
                ACTION_BUTTON_MAPPER -> {
                    AccessibilityHelper.openAccessibilitySettings(ctx)
                }
                ACTION_TCL_SETTINGS -> {
                    MainActivity.launchTclSettings(ctx)
                }
                ACTION_ANDROID_SETTINGS -> {
                    MainActivity.launchAndroidSystemSettings(ctx)
                }
                ACTION_RESET_APP -> {
                    showResetAppDialog()
                }
            }
        }

        private fun buttonMapperSummary(ctx: Context): String {
            val home = if (MainActivity.isHomeButtonOverrideEnabled(ctx)) getString(R.string.setting_state_on) else getString(R.string.setting_state_off)
            val input = if (MainActivity.isInputButtonOverrideEnabled(ctx)) getString(R.string.setting_state_on) else getString(R.string.setting_state_off)
            return getString(R.string.setting_button_mapper_summary, home, input)
        }

        private fun buttonMapperSubActions(ctx: Context) = listOf(
            GuidedAction.Builder(ctx).id(ACTION_MAPPER_HOME_ON).title(getString(R.string.setting_mapper_home_on)).build(),
            GuidedAction.Builder(ctx).id(ACTION_MAPPER_HOME_OFF).title(getString(R.string.setting_mapper_home_off)).build(),
            GuidedAction.Builder(ctx).id(ACTION_MAPPER_INPUT_ON).title(getString(R.string.setting_mapper_input_on)).build(),
            GuidedAction.Builder(ctx).id(ACTION_MAPPER_INPUT_OFF).title(getString(R.string.setting_mapper_input_off)).build(),
            GuidedAction.Builder(ctx).id(ACTION_MAPPER_ACCESSIBILITY).title(getString(R.string.setting_mapper_accessibility)).build()
        )

        private fun showResetAppDialog() {
            val ctx = context ?: return
            resetDialog?.dismiss()

            resetDialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.dialog_reset_app_title))
                .setMessage(getString(R.string.dialog_reset_app_msg))
                .setPositiveButton(getString(R.string.dialog_reset_app_confirm)) { d, _ ->
                    d.dismiss()
                    MainActivity.resetAllSettings(ctx)
                    Toast.makeText(ctx.applicationContext, R.string.toast_app_reset_completed, Toast.LENGTH_SHORT).show()
                    val oobeIntent = Intent(ctx, OobeActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(oobeIntent)
                    activity?.finish()
                }
                .setNegativeButton(getString(R.string.dialog_cancel)) { d, _ ->
                    d.dismiss()
                }
                .create().also { dialog ->
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
                    }
                    dialog.show()
                }
        }

        private fun showCountdownDialog() {
            val ctx = context ?: return
            countdownDialog?.dismiss()

            val current = MainActivity.getCountdownSeconds(ctx)
            val secondsOptions = listOf(
                0 to getString(R.string.dialog_option_off),
                1 to getString(R.string.dialog_option_1s),
                2 to getString(R.string.dialog_option_2s),
                3 to getString(R.string.dialog_option_3s_default),
                5 to getString(R.string.dialog_option_5s),
                10 to getString(R.string.dialog_option_10s),
                15 to getString(R.string.dialog_option_15s),
                30 to getString(R.string.dialog_option_30s)
            )

            val labels = secondsOptions.map { it.second }.toTypedArray()
            val currentIndex = secondsOptions.indexOfFirst { it.first == current }.let {
                if (it != -1) it else 3
            }

            countdownDialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.dialog_countdown_title))
                .setSingleChoiceItems(labels, currentIndex) { d, which ->
                    val selectedSeconds = secondsOptions[which].first
                    MainActivity.setCountdownSeconds(ctx, selectedSeconds)

                    val action = findActionById(ACTION_COUNTDOWN)
                    if (action != null) {
                        action.description = if (selectedSeconds <= 0) {
                            getString(R.string.setting_countdown_desc_off)
                        } else {
                            getString(R.string.setting_countdown_desc, selectedSeconds)
                        }
                        notifyActionChanged(findActionPositionById(ACTION_COUNTDOWN))
                    }
                    d.dismiss()
                }
                .setNegativeButton(getString(R.string.dialog_cancel)) { d, _ ->
                    d.dismiss()
                }
                .create().also { it.show() }
        }

        private fun showDefaultPortDialog() {
            val ctx = context ?: return
            defaultPortDialog?.dismiss()

            val currentPort = MainActivity.getDefaultPort(ctx)
            val ports = listOf(
                1 to getString(R.string.port_hdmi_1),
                2 to getString(R.string.port_hdmi_2),
                3 to getString(R.string.port_hdmi_3)
            )
            val labels = ports.map { it.second }.toTypedArray()
            val currentIndex = ports.indexOfFirst { it.first == currentPort }.let {
                if (it != -1) it else 2
            }

            defaultPortDialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.dialog_default_port_title))
                .setSingleChoiceItems(labels, currentIndex) { d, which ->
                    val selectedPort = ports[which].first
                    MainActivity.setDefaultPort(ctx, selectedPort)

                    val action = findActionById(ACTION_DEFAULT_PORT)
                    if (action != null) {
                        action.description = getString(R.string.setting_default_port_desc, selectedPort)
                        notifyActionChanged(findActionPositionById(ACTION_DEFAULT_PORT))
                    }
                    d.dismiss()
                }
                .setNegativeButton(getString(R.string.dialog_cancel)) { d, _ ->
                    d.dismiss()
                }
                .create().also { it.show() }
        }

        override fun onSubGuidedActionClicked(action: GuidedAction): Boolean {
            val ctx = requireContext()

            // 訊號搜尋次選項
            when (action.id) {
                ACTION_MAPPER_HOME_ON -> MainActivity.setHomeButtonOverrideEnabled(ctx, true)
                ACTION_MAPPER_HOME_OFF -> MainActivity.setHomeButtonOverrideEnabled(ctx, false)
                ACTION_MAPPER_INPUT_ON -> MainActivity.setInputButtonOverrideEnabled(ctx, true)
                ACTION_MAPPER_INPUT_OFF -> MainActivity.setInputButtonOverrideEnabled(ctx, false)
                ACTION_MAPPER_ACCESSIBILITY -> AccessibilityHelper.openAccessibilitySettings(ctx)
                ACTION_SIGNAL_SEARCH_ENABLE -> {
                    MainActivity.setSignalSearchScreenEnabled(ctx, true)
                    val parentAction = findActionById(ACTION_SIGNAL_SEARCH_PARENT)
                    if (parentAction != null) {
                        parentAction.description = getString(R.string.setting_signal_search_desc_on)
                        notifyActionChanged(findActionPositionById(ACTION_SIGNAL_SEARCH_PARENT))
                    }
                    return true
                }
                ACTION_SIGNAL_SEARCH_DISABLE -> {
                    MainActivity.setSignalSearchScreenEnabled(ctx, false)
                    val parentAction = findActionById(ACTION_SIGNAL_SEARCH_PARENT)
                    if (parentAction != null) {
                        parentAction.description = getString(R.string.setting_signal_search_desc_off)
                        notifyActionChanged(findActionPositionById(ACTION_SIGNAL_SEARCH_PARENT))
                    }
                    return true
                }
            }

            if (action.id in ACTION_MAPPER_HOME_ON..ACTION_MAPPER_ACCESSIBILITY) {
                findActionById(ACTION_BUTTON_MAPPER)?.let {
                    it.description = buttonMapperSummary(ctx)
                    notifyActionChanged(findActionPositionById(ACTION_BUTTON_MAPPER))
                }
                return true
            }

            return super.onSubGuidedActionClicked(action)
        }

        private fun getAppModeSummaryText(ctx: Context): String {
            val appMode = MainActivity.isAppModeEnabled(ctx)
            val autoPkg = MainActivity.getAutoOpenPackage(ctx)
            val autoLabel = MainActivity.getAutoOpenLabel(ctx)
            val autoText = if (autoPkg.isNotBlank()) {
                autoLabel.ifBlank { autoPkg }
            } else {
                getString(R.string.dialog_auto_open_none)
            }
            return if (appMode) {
                "${getString(R.string.setting_app_mode_desc_on)} • ${getString(R.string.setting_auto_open_title)}: $autoText"
            } else {
                getString(R.string.setting_app_mode_desc_off)
            }
        }
    }
}
