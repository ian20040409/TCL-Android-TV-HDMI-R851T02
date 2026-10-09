package com.lnu.tclhdmilauncher


import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.leanback.preference.LeanbackSettingsFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen


class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : LeanbackSettingsFragmentCompat() {
        override fun onPreferenceStartInitialScreen() {
            startPreferenceFragment(PrefsFragment())
        }

        override fun onPreferenceStartFragment(
            caller: PreferenceFragmentCompat,
            pref: Preference
        ): Boolean {
            return false
        }

        override fun onPreferenceStartScreen(
            caller: PreferenceFragmentCompat,
            pref: PreferenceScreen
        ): Boolean {
            val frag = PrefsFragment().apply {
                arguments = Bundle().apply {
                    putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
                }
            }
            startPreferenceFragment(frag)
            return true
        }
    }

    class PrefsFragment : LeanbackPreferenceFragmentCompat() {

        private var countdownDialog: AlertDialog? = null
        private var autoSleepDialog: AlertDialog? = null
        private var defaultPortDialog: AlertDialog? = null
        private var resetDialog: AlertDialog? = null
        private val mainHandler = Handler(Looper.getMainLooper())

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_main, rootKey)
            updatePreferences()
        }

        override fun onResume() {
            super.onResume()
            updatePreferences()
        }

        private fun updatePreferences() {
            val ctx = requireContext()
            val pm = preferenceManager

            pm.findPreference<Preference>("pref_app_mode")?.summary = getAppModeSummaryText(ctx)

            val defaultPort = SettingsRepository.getDefaultPort(ctx)
            pm.findPreference<Preference>("pref_default_port")?.summary = getString(R.string.setting_default_port_desc, defaultPort)

            val countdown = SettingsRepository.getCountdownSeconds(ctx)
            pm.findPreference<Preference>("pref_countdown")?.summary = if (countdown <= 0) {
                getString(R.string.setting_countdown_desc_off)
            } else {
                getString(R.string.setting_countdown_desc, countdown)
            }

            val autoSleep = SettingsRepository.getAutoSleepSeconds(ctx)
            pm.findPreference<Preference>("pref_auto_sleep")?.summary = if (autoSleep <= 0) {
                getString(R.string.setting_auto_sleep_desc_off)
            } else {
                getString(R.string.setting_auto_sleep_desc, autoSleep)
            }

            val signalSearch = SettingsRepository.isSignalSearchScreenEnabled(ctx)
            pm.findPreference<Preference>("pref_signal_search")?.summary = getString(if (signalSearch) R.string.setting_signal_search_desc_on else R.string.setting_signal_search_desc_off)

            val wakeGuardPref = pm.findPreference<Preference>("pref_wake_guard")
            val wakeGuard = AccessibilityHelper.isServiceEnabled(ctx)
            wakeGuardPref?.summary = getString(if (wakeGuard) R.string.setting_wake_guard_desc_on else R.string.setting_wake_guard_desc_off)

            pm.findPreference<Preference>("pref_button_mapper")?.summary = buttonMapperSummary(ctx)

            val tclPref = pm.findPreference<Preference>("pref_tcl_settings")
            val isTcl = DeviceHelper.isTclDevice(ctx)
            tclPref?.isVisible = isTcl
            if (isTcl) {
                tclPref?.isEnabled = true
                tclPref?.summary = getString(R.string.setting_tcl_settings_desc)
            }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            val ctx = requireContext()
            when (preference.key) {
                "pref_app_mode" -> {
                    startActivity(Intent(ctx, AppModeSettingsActivity::class.java))
                }
                "pref_countdown" -> {
                    showCountdownDialog()
                }
                "pref_auto_sleep" -> {
                    showAutoSleepDialog()
                }
                "pref_default_port" -> {
                    showDefaultPortDialog()
                }
                "pref_signal_search" -> {
                    val newValue = !SettingsRepository.isSignalSearchScreenEnabled(ctx)
                    SettingsRepository.setSignalSearchScreenEnabled(ctx, newValue)
                    updatePreferences()
                }
                "pref_wake_guard" -> {
                    AccessibilityHelper.openAccessibilitySettings(ctx)
                }
                "pref_cec_debug" -> {
                    startActivity(Intent(ctx, CecDebugActivity::class.java))
                }
                "pref_button_mapper" -> {
                    startActivity(Intent(ctx, ButtonMapperSettingsActivity::class.java))
                }
                "pref_shizuku" -> {
                    startActivity(Intent(ctx, ShizukuSettingsActivity::class.java))
                }
                "pref_tcl_settings" -> {
                    MainActivity.launchTclSettings(ctx)
                }
                "pref_android_settings" -> {
                    MainActivity.launchAndroidSystemSettings(ctx)
                }
                "pref_reset_app" -> {
                    showResetAppDialog()
                }
            }
            return super.onPreferenceTreeClick(preference)
        }

        private fun showResetAppDialog() {
            val ctx = requireContext()
            resetDialog?.dismiss()
            resetDialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.dialog_reset_app_title)
                .setMessage(R.string.dialog_reset_app_msg)
                .setPositiveButton(R.string.dialog_reset_app_confirm) { d, _ ->
                    d.dismiss()
                    SettingsRepository.resetAllSettings(ctx)
                    Toast.makeText(ctx.applicationContext, R.string.toast_app_reset_completed, Toast.LENGTH_SHORT).show()
                    val oobeIntent = Intent(ctx, OobeActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(oobeIntent)
                    activity?.finish()
                }
                .setNegativeButton(R.string.dialog_cancel) { d, _ ->
                    d.dismiss()
                }
                .show()
        }

        private fun showCountdownDialog() {
            val ctx = requireContext()
            val current = SettingsRepository.getCountdownSeconds(ctx)
            val options = intArrayOf(0, 1, 2, 3, 5, 10, 15, 30)
            val titles = arrayOf(
                getString(R.string.dialog_option_off),
                getString(R.string.dialog_option_1s),
                getString(R.string.dialog_option_2s),
                getString(R.string.dialog_option_3s_default),
                getString(R.string.dialog_option_5s),
                getString(R.string.dialog_option_10s),
                getString(R.string.dialog_option_15s),
                getString(R.string.dialog_option_30s)
            )

            var selectedIndex = options.indexOf(current)
            if (selectedIndex < 0) selectedIndex = 3

            countdownDialog?.dismiss()
            countdownDialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.dialog_countdown_title)
                .setSingleChoiceItems(titles, selectedIndex) { dialog, which ->
                    val newSeconds = options[which]
                    SettingsRepository.setCountdownSeconds(ctx, newSeconds)
                    if (newSeconds == 0) {
                        Toast.makeText(ctx, R.string.toast_countdown_off, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, getString(R.string.toast_countdown_set, newSeconds), Toast.LENGTH_SHORT).show()
                    }
                    updatePreferences()
                    
                    mainHandler.postDelayed({ dialog.dismiss() }, 200)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }

        private fun showAutoSleepDialog() {
            val ctx = requireContext()
            val current = SettingsRepository.getAutoSleepSeconds(ctx)
            val options = intArrayOf(0, 1, 15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200)
            val titles = options.map { sec ->
                when {
                    sec == 0 -> getString(R.string.setting_auto_sleep_desc_off)
                    sec < 60 -> "$sec s"
                    sec % 3600 == 0 -> "${sec / 3600} h"
                    else -> "${sec / 60} min"
                }
            }.toTypedArray()

            var selectedIndex = options.indexOf(current)
            if (selectedIndex < 0) selectedIndex = 3 // Default to 30s if not found

            autoSleepDialog?.dismiss()
            autoSleepDialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.dialog_auto_sleep_title)
                .setSingleChoiceItems(titles, selectedIndex) { dialog, which ->
                    val newSeconds = options[which]
                    SettingsRepository.setAutoSleepSeconds(ctx, newSeconds)
                    updatePreferences()
                    mainHandler.postDelayed({ dialog.dismiss() }, 200)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }

        private fun showDefaultPortDialog() {
            val ctx = requireContext()
            val current = SettingsRepository.getDefaultPort(ctx)
            val options = intArrayOf(1, 2, 3)
            val titles = arrayOf(
                getString(R.string.port_hdmi_1),
                getString(R.string.port_hdmi_2),
                getString(R.string.port_hdmi_3)
            )

            var selectedIndex = options.indexOf(current)
            if (selectedIndex < 0) selectedIndex = 0

            defaultPortDialog?.dismiss()
            defaultPortDialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.dialog_default_port_title)
                .setSingleChoiceItems(titles, selectedIndex) { dialog, which ->
                    val newPort = options[which]
                    SettingsRepository.setDefaultPort(ctx, newPort)
                    updatePreferences()
                    mainHandler.postDelayed({ dialog.dismiss() }, 200)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }

        private fun buttonMapperSummary(ctx: Context): String {
            if (!SettingsRepository.isButtonMapperEnabled(ctx)) {
                return getString(R.string.setting_state_off)
            }
            val home = if (SettingsRepository.isHomeButtonOverrideEnabled(ctx)) getString(R.string.setting_state_on) else getString(R.string.setting_state_off)
            val input = if (SettingsRepository.isInputButtonOverrideEnabled(ctx)) getString(R.string.setting_state_on) else getString(R.string.setting_state_off)
            return getString(R.string.setting_button_mapper_summary, home, input)
        }

        private fun getAppModeSummaryText(ctx: Context): String {
            val appMode = SettingsRepository.isAppModeEnabled(ctx)
            val autoPkg = SettingsRepository.getAutoOpenPackage(ctx)
            val autoLabel = SettingsRepository.getAutoOpenLabel(ctx)
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

        override fun onDestroy() {
            super.onDestroy()
            countdownDialog?.dismiss()
            autoSleepDialog?.dismiss()
            defaultPortDialog?.dismiss()
            resetDialog?.dismiss()
        }
    }
}