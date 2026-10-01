package com.lnu.tclhdmilauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction

/**
 * 首次開箱設定精靈 (OOBE) Activity
 * 基於 Android TV Leanback GuidedStepSupportFragment 設計
 * 規範：
 * 1. 不使用單選框 (Radio Button) 與 Emoji 特殊符號。
 * 2. 步驟 2/3：訊號源移除括號，僅保留「HDMI 1」、「HDMI 2」、「HDMI 3」，並增加「關閉自動啟動」。
 * 3. 步驟 3/3：點選無障礙守護時，若無直達無障礙頁面，自動打開 Android 原生系統設定。
 */
class OobeActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, WelcomeStepFragment(), android.R.id.content)
        }
    }

    // ── 步驟 1：模式選擇 ──────────────────────────────────────────────────
    class WelcomeStepFragment : GuidedStepSupportFragment() {

        companion object {
            private const val ACTION_PURE_MONITOR = 1L
            private const val ACTION_APP_MODE = 2L
            private const val ACTION_SKIP = 3L
        }

        override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
            return GuidanceStylist.Guidance(
                getString(R.string.oobe_welcome_title),
                getString(R.string.oobe_welcome_description),
                getString(R.string.oobe_welcome_breadcrumb),
                requireContext().getDrawable(R.drawable.apps_48px)
            )
        }

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            val ctx = requireContext()

            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_PURE_MONITOR)
                    .title(getString(R.string.oobe_mode_pure_title))
                    .description(getString(R.string.oobe_mode_pure_desc))
                    .build()
            )

            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_APP_MODE)
                    .title(getString(R.string.oobe_mode_app_title))
                    .description(getString(R.string.oobe_mode_app_desc))
                    .build()
            )

            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_SKIP)
                    .title(getString(R.string.oobe_skip))
                    .build()
            )
        }

        override fun onGuidedActionClicked(action: GuidedAction) {
            val ctx = requireContext()
            when (action.id) {
                ACTION_PURE_MONITOR -> {
                    MainActivity.setAppModeEnabled(ctx, false)
                    add(parentFragmentManager, ConfigStepFragment())
                }
                ACTION_APP_MODE -> {
                    MainActivity.setAppModeEnabled(ctx, true)
                    add(parentFragmentManager, ConfigStepFragment())
                }
                ACTION_SKIP -> {
                    MainActivity.setOobeCompleted(ctx, true)
                    val intent = Intent(ctx, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                    activity?.finish()
                }
            }
        }
    }

    // ── 步驟 2：預設訊號源與自動啟動設定 ────────────────────────────────────
    class ConfigStepFragment : GuidedStepSupportFragment() {

        companion object {
            private const val ACTION_PORT_PARENT = 1L
            private const val ACTION_COUNTDOWN_PARENT = 2L
            private const val ACTION_PORT_BASE = 100L
            private const val ACTION_COUNTDOWN_BASE = 200L
            private const val ACTION_NEXT = 300L
        }

        private var selectedPort = 3
        private var selectedCountdown = 3

        override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
            return GuidanceStylist.Guidance(
                getString(R.string.oobe_config_title),
                getString(R.string.oobe_config_description),
                getString(R.string.oobe_config_breadcrumb),
                requireContext().getDrawable(R.drawable.cable_48px)
            )
        }

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            val ctx = requireContext()
            selectedPort = MainActivity.getDefaultPort(ctx)
            selectedCountdown = MainActivity.getCountdownSeconds(ctx)

            // HDMI 1 ~ 3 選項（移除所有括號附註，只保留乾淨的 HDMI 1 / HDMI 2 / HDMI 3）
            val portSubActions = mutableListOf<GuidedAction>()
            for (p in 1..3) {
                val label = when (p) {
                    1 -> getString(R.string.port_hdmi_1)
                    2 -> getString(R.string.port_hdmi_2)
                    3 -> getString(R.string.port_hdmi_3)
                    else -> "HDMI $p"
                }
                portSubActions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_PORT_BASE + p)
                        .title(label)
                        .build()
                )
            }

            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_PORT_PARENT)
                    .title(getString(R.string.oobe_port_prompt, selectedPort))
                    .subActions(portSubActions)
                    .build()
            )

            // 倒數秒數選項（增加關閉自動啟動，且無括號）
            val countdownSubActions = mutableListOf<GuidedAction>()
            val countdownOptions = listOf(0, 1, 2, 3, 5, 10)
            for (sec in countdownOptions) {
                val label = if (sec == 0) {
                    getString(R.string.oobe_countdown_off)
                } else {
                    "${sec}s"
                }
                countdownSubActions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_COUNTDOWN_BASE + sec)
                        .title(label)
                        .build()
                )
            }

            val countdownDisplay = if (selectedCountdown <= 0) {
                getString(R.string.oobe_countdown_off)
            } else {
                "${selectedCountdown}s"
            }

            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_COUNTDOWN_PARENT)
                    .title(getString(R.string.oobe_countdown_prompt, countdownDisplay))
                    .subActions(countdownSubActions)
                    .build()
            )

            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_NEXT)
                    .title(getString(R.string.oobe_action_next))
                    .build()
            )
        }

        override fun onSubGuidedActionClicked(action: GuidedAction): Boolean {
            val ctx = requireContext()
            if (action.id in ACTION_PORT_BASE + 1..ACTION_PORT_BASE + 3) {
                selectedPort = (action.id - ACTION_PORT_BASE).toInt()
                MainActivity.setDefaultPort(ctx, selectedPort)
                val parentAction = findActionById(ACTION_PORT_PARENT)
                parentAction?.title = getString(R.string.oobe_port_prompt, selectedPort)
                notifyActionChanged(findActionPositionById(ACTION_PORT_PARENT))
                return true
            } else if (action.id in ACTION_COUNTDOWN_BASE..ACTION_COUNTDOWN_BASE + 10) {
                selectedCountdown = (action.id - ACTION_COUNTDOWN_BASE).toInt()
                MainActivity.setCountdownSeconds(ctx, selectedCountdown)
                val display = if (selectedCountdown <= 0) {
                    getString(R.string.oobe_countdown_off)
                } else {
                    "${selectedCountdown}s"
                }
                val parentAction = findActionById(ACTION_COUNTDOWN_PARENT)
                parentAction?.title = getString(R.string.oobe_countdown_prompt, display)
                notifyActionChanged(findActionPositionById(ACTION_COUNTDOWN_PARENT))
                return true
            }
            return super.onSubGuidedActionClicked(action)
        }

        override fun onGuidedActionClicked(action: GuidedAction) {
            if (action.id == ACTION_NEXT) {
                add(parentFragmentManager, PermissionsStepFragment())
            }
        }
    }

    // ── 步驟 3：無障礙與按鍵覆蓋 ───────────────────────────────────────────
    class PermissionsStepFragment : GuidedStepSupportFragment() {

        companion object {
            private const val ACTION_FINISH = 1L
            private const val ACTION_OPEN_ACCESSIBILITY = 2L
            private const val ACTION_BUTTON_MAPPER = 3L
        }

        override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
            return GuidanceStylist.Guidance(
                getString(R.string.oobe_system_title),
                getString(R.string.oobe_system_description),
                getString(R.string.oobe_system_breadcrumb),
                requireContext().getDrawable(R.drawable.accessibility_new_48px)
            )
        }

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            val ctx = requireContext()
            val hasAccessibility = AccessibilityHelper.isServiceEnabled(ctx)

            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_FINISH)
                    .title(getString(R.string.oobe_action_finish))
                    .build()
            )

            if (!hasAccessibility) {
                actions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_OPEN_ACCESSIBILITY)
                        .title(getString(R.string.oobe_action_wake_guard))
                        .icon(ctx.getDrawable(R.drawable.open_in_new_48px))
                        .build()
                )
            } else {
                actions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_BUTTON_MAPPER)
                        .title(getString(R.string.setting_button_mapper_title))
                        .description(getString(R.string.setting_button_mapper_desc))
                        .icon(ctx.getDrawable(R.drawable.open_in_new_48px))
                        .build()
                )
            }
        }

        override fun onResume() {
            super.onResume()
            // 如果從設定回來，刷新列表
            setActions(mutableListOf())
            onCreateActions(actions, null)
        }

        override fun onGuidedActionClicked(action: GuidedAction) {
            val ctx = requireContext()
            when (action.id) {
                ACTION_FINISH -> {
                    MainActivity.setOobeCompleted(ctx, true)
                    Toast.makeText(ctx, "Setup Completed", Toast.LENGTH_SHORT).show()
                    val intent = Intent(ctx, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                    activity?.finish()
                }
                ACTION_OPEN_ACCESSIBILITY -> {
                    AccessibilityHelper.openAccessibilitySettings(ctx)
                }
                ACTION_BUTTON_MAPPER -> {
                    val intent = Intent(ctx, ButtonMapperSettingsActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }
}
