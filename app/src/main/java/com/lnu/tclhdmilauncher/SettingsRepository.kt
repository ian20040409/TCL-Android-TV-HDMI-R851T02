package com.lnu.tclhdmilauncher

import android.content.Context

object SettingsRepository {
    private const val PREFS_NAME = "hdmi_prefs"
    private const val KEY_DEFAULT_PORT = "default_port"
    private const val KEY_COUNTDOWN_SECONDS = "countdown_seconds"
    private const val KEY_APP_MODE = "app_mode"
    private const val KEY_AUTO_OPEN_PKG = "auto_open_pkg"
    private const val KEY_AUTO_OPEN_LABEL = "auto_open_label"
    private const val KEY_AUTO_OPEN_DELAY = "auto_open_delay"
    private const val KEY_SIGNAL_SEARCH_SCREEN = "signal_search_screen"
    private const val KEY_OOBE_COMPLETED = "oobe_completed"
    private const val KEY_BUTTON_MAPPER_ENABLED = "button_mapper_enabled"
    private const val KEY_OVERRIDE_HOME_BUTTON = "override_home_button"
    private const val KEY_OVERRIDE_INPUT_BUTTON = "override_input_button"
    private const val KEY_AUTO_SLEEP_SECONDS = "auto_sleep_seconds"
    
    private const val DEFAULT_COUNTDOWN_SECONDS = 3
    private const val DEFAULT_AUTO_SLEEP_SECONDS = 30

    @Volatile private var cachedDefaultPort: Int? = null
    @Volatile private var cachedCountdownSeconds: Int? = null
    @Volatile private var cachedAppMode: Boolean? = null
    @Volatile private var cachedAutoOpenPkg: String? = null
    @Volatile private var cachedAutoOpenLabel: String? = null
    @Volatile private var cachedAutoOpenDelay: Int? = null
    @Volatile private var cachedSignalSearchScreen: Boolean? = null
    @Volatile private var cachedOobeCompleted: Boolean? = null
    @Volatile private var cachedAutoSleepSeconds: Int? = null

    fun isOobeCompleted(context: Context): Boolean {
        cachedOobeCompleted?.let { return it }
        val completed = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OOBE_COMPLETED, false)
        cachedOobeCompleted = completed
        return completed
    }

    fun setOobeCompleted(context: Context, completed: Boolean) {
        cachedOobeCompleted = completed
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_OOBE_COMPLETED, completed).apply()
    }

    fun resetAllSettings(context: Context) {
        cachedDefaultPort = null
        cachedCountdownSeconds = null
        cachedAppMode = null
        cachedAutoOpenPkg = null
        cachedAutoOpenLabel = null
        cachedAutoOpenDelay = null
        cachedSignalSearchScreen = null
        cachedOobeCompleted = null
        cachedAutoSleepSeconds = null

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("app_list_recent", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun getDefaultPort(context: Context): Int {
        cachedDefaultPort?.let { return it }
        val port = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DEFAULT_PORT, 3)
        cachedDefaultPort = port
        return port
    }

    fun setDefaultPort(context: Context, port: Int) {
        cachedDefaultPort = port
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DEFAULT_PORT, port).apply()
    }

    fun isButtonMapperEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BUTTON_MAPPER_ENABLED, true)

    fun setButtonMapperEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_BUTTON_MAPPER_ENABLED, enabled).apply()
    }

    fun isHomeButtonOverrideEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OVERRIDE_HOME_BUTTON, true)

    fun setHomeButtonOverrideEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_OVERRIDE_HOME_BUTTON, enabled).apply()
    }

    fun isInputButtonOverrideEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OVERRIDE_INPUT_BUTTON, true)

    fun setInputButtonOverrideEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_OVERRIDE_INPUT_BUTTON, enabled).apply()
    }

    fun getCountdownSeconds(context: Context): Int {
        cachedCountdownSeconds?.let { return it }
        val sec = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_COUNTDOWN_SECONDS, DEFAULT_COUNTDOWN_SECONDS)
        cachedCountdownSeconds = sec
        return sec
    }

    fun setCountdownSeconds(context: Context, seconds: Int) {
        cachedCountdownSeconds = seconds
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_COUNTDOWN_SECONDS, seconds).apply()
    }

    fun getAutoSleepSeconds(context: Context): Int {
        cachedAutoSleepSeconds?.let { return it }
        val sec = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_AUTO_SLEEP_SECONDS, DEFAULT_AUTO_SLEEP_SECONDS)
        cachedAutoSleepSeconds = sec
        return sec
    }

    fun setAutoSleepSeconds(context: Context, seconds: Int) {
        cachedAutoSleepSeconds = seconds
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_AUTO_SLEEP_SECONDS, seconds).apply()
    }

    fun isAppModeEnabled(context: Context): Boolean {
        cachedAppMode?.let { return it }
        val enabled = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_MODE, false)
        cachedAppMode = enabled
        return enabled
    }

    fun setAppModeEnabled(context: Context, enabled: Boolean) {
        cachedAppMode = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_APP_MODE, enabled).apply()
    }

    fun getAutoOpenPackage(context: Context): String {
        cachedAutoOpenPkg?.let { return it }
        val pkg = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AUTO_OPEN_PKG, "") ?: ""
        cachedAutoOpenPkg = pkg
        return pkg
    }

    fun getAutoOpenLabel(context: Context): String {
        cachedAutoOpenLabel?.let { return it }
        val label = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AUTO_OPEN_LABEL, "") ?: ""
        cachedAutoOpenLabel = label
        return label
    }

    fun setAutoOpenApp(context: Context, pkg: String, label: String) {
        cachedAutoOpenPkg = pkg
        cachedAutoOpenLabel = label
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_AUTO_OPEN_PKG, pkg)
            .putString(KEY_AUTO_OPEN_LABEL, label)
            .apply()
    }

    fun isSignalSearchScreenEnabled(context: Context): Boolean {
        cachedSignalSearchScreen?.let { return it }
        val enabled = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SIGNAL_SEARCH_SCREEN, true)
        cachedSignalSearchScreen = enabled
        return enabled
    }

    fun setSignalSearchScreenEnabled(context: Context, enabled: Boolean) {
        cachedSignalSearchScreen = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SIGNAL_SEARCH_SCREEN, enabled).apply()
    }

    fun getAutoOpenDelaySeconds(context: Context): Int {
        cachedAutoOpenDelay?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sec = if (prefs.contains(KEY_AUTO_OPEN_DELAY)) {
            prefs.getInt(KEY_AUTO_OPEN_DELAY, DEFAULT_COUNTDOWN_SECONDS)
        } else {
            prefs.getInt(KEY_COUNTDOWN_SECONDS, DEFAULT_COUNTDOWN_SECONDS)
        }
        cachedAutoOpenDelay = sec
        return if (sec >= 0) sec else DEFAULT_COUNTDOWN_SECONDS
    }

    fun setAutoOpenDelaySeconds(context: Context, seconds: Int) {
        cachedAutoOpenDelay = seconds
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_AUTO_OPEN_DELAY, seconds).apply()
    }
}
