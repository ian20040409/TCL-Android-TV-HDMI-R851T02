package com.lnu.tclhdmilauncher.settings

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.lnu.tclhdmilauncher.DeviceHelper
import com.lnu.tclhdmilauncher.R
import com.lnu.tclhdmilauncher.accessibility.AccessibilityHelper

/**
 * Button Mapper 專屬獨立設定頁面
 */
class ButtonMapperSettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, ButtonMapperSettingsFragment(), android.R.id.content)
        }
    }

    class ButtonMapperSettingsFragment : GuidedStepSupportFragment() {

        companion object {
            private const val ACTION_MAPPER_MASTER = 1L
            private const val ACTION_MAPPER_HOME_TOGGLE = 2L
            private const val ACTION_MAPPER_INPUT_TOGGLE = 3L
            private const val ACTION_MAPPER_ACCESSIBILITY = 4L

            private const val ACTION_MAPPER_ENABLE = 1001L
            private const val ACTION_MAPPER_DISABLE = 1002L
        }

        override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
            val ctx = requireContext()
            return GuidanceStylist.Guidance(
                getString(R.string.setting_button_mapper_title),
                getString(R.string.setting_button_mapper_desc),
                DeviceHelper.getBrandTitle(ctx),
                null
            )
        }

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            actions.addAll(buildActions(requireContext()))
        }

        private fun buildActions(ctx: Context): List<GuidedAction> {
            val actions = mutableListOf<GuidedAction>()
            val hasAccessibility = AccessibilityHelper.isServiceEnabled(ctx)
            val isMasterEnabled = SettingsRepository.isButtonMapperEnabled(ctx)

            // 1. Master Toggle (SubActions for On/Off)
            val masterSubActions = mutableListOf(
                GuidedAction.Builder(ctx)
                    .id(ACTION_MAPPER_ENABLE)
                    .title(getString(R.string.setting_state_on))
                    .build(),
                GuidedAction.Builder(ctx)
                    .id(ACTION_MAPPER_DISABLE)
                    .title(getString(R.string.setting_state_off))
                    .build()
            )

            actions.add(
                GuidedAction.Builder(ctx)
                    .id(ACTION_MAPPER_MASTER)
                    .title(getString(R.string.setting_button_mapper_title))
                    .description(getString(if (isMasterEnabled) R.string.setting_state_on else R.string.setting_state_off))
                    .subActions(masterSubActions)
                    .build()
            )

            if (isMasterEnabled) {
                val isHomeEnabled = SettingsRepository.isHomeButtonOverrideEnabled(ctx)
                actions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_MAPPER_HOME_TOGGLE)
                        .title(getString(R.string.setting_mapper_home_title))
                        .description(getString(if (isHomeEnabled) R.string.setting_state_on else R.string.setting_state_off))
                        .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                        .checked(isHomeEnabled)
                        .build()
                )

                val isInputEnabled = SettingsRepository.isInputButtonOverrideEnabled(ctx)
                actions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_MAPPER_INPUT_TOGGLE)
                        .title(getString(R.string.setting_mapper_input_title))
                        .description(getString(if (isInputEnabled) R.string.setting_state_on else R.string.setting_state_off))
                        .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                        .checked(isInputEnabled)
                        .build()
                )
            }

            if (!hasAccessibility) {
                actions.add(
                    GuidedAction.Builder(ctx)
                        .id(ACTION_MAPPER_ACCESSIBILITY)
                        .title(getString(R.string.setting_mapper_accessibility))
                        .build()
                )
            }

            return actions
        }

        override fun onResume() {
            super.onResume()
            setActions(buildActions(requireContext()))
        }

        private fun showAccessibilityRequiredDialog(ctx: Context) {
            AccessibilityHelper.showAccessibilityGuideDialog(ctx)
        }

        override fun onGuidedActionClicked(action: GuidedAction) {
            val ctx = requireContext()
            when (action.id) {
                ACTION_MAPPER_HOME_TOGGLE -> {
                    if (action.isChecked && !AccessibilityHelper.isServiceEnabled(ctx)) {
                        action.isChecked = false
                        action.description = getString(R.string.setting_state_off)
                        notifyActionChanged(findActionPositionById(ACTION_MAPPER_HOME_TOGGLE))
                        SettingsRepository.setHomeButtonOverrideEnabled(ctx, false)
                        showAccessibilityRequiredDialog(ctx)
                    } else {
                        SettingsRepository.setHomeButtonOverrideEnabled(ctx, action.isChecked)
                        action.description = getString(if (action.isChecked) R.string.setting_state_on else R.string.setting_state_off)
                        notifyActionChanged(findActionPositionById(ACTION_MAPPER_HOME_TOGGLE))
                    }
                }
                ACTION_MAPPER_INPUT_TOGGLE -> {
                    if (action.isChecked && !AccessibilityHelper.isServiceEnabled(ctx)) {
                        action.isChecked = false
                        action.description = getString(R.string.setting_state_off)
                        notifyActionChanged(findActionPositionById(ACTION_MAPPER_INPUT_TOGGLE))
                        SettingsRepository.setInputButtonOverrideEnabled(ctx, false)
                        showAccessibilityRequiredDialog(ctx)
                    } else {
                        SettingsRepository.setInputButtonOverrideEnabled(ctx, action.isChecked)
                        action.description = getString(if (action.isChecked) R.string.setting_state_on else R.string.setting_state_off)
                        notifyActionChanged(findActionPositionById(ACTION_MAPPER_INPUT_TOGGLE))
                    }
                }
                ACTION_MAPPER_ACCESSIBILITY -> {
                    AccessibilityHelper.openAccessibilitySettings(ctx)
                }
            }
        }

        override fun onSubGuidedActionClicked(action: GuidedAction): Boolean {
            val ctx = requireContext()
            when (action.id) {
                ACTION_MAPPER_ENABLE -> {
                    if (!AccessibilityHelper.isServiceEnabled(ctx)) {
                        showAccessibilityRequiredDialog(ctx)
                    } else {
                        SettingsRepository.setButtonMapperEnabled(ctx, true)
                        setActions(buildActions(ctx))
                    }
                    return true
                }
                ACTION_MAPPER_DISABLE -> {
                    SettingsRepository.setButtonMapperEnabled(ctx, false)
                    setActions(buildActions(ctx))
                    return true
                }
            }
            return super.onSubGuidedActionClicked(action)
        }
    }
}
