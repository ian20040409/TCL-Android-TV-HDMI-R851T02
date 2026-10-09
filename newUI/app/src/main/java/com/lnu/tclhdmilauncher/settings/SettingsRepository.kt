package com.lnu.tclhdmilauncher.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * 所有使用者設定的唯一存取入口。
 *
 * 其他類別不應直接呼叫 `getSharedPreferences`，以免 key 分散、難以追蹤。
 * 部分設定會在喚醒 / 開機路徑被頻繁讀取，因此以 [Cache] 在記憶體中快取。
 */
object SettingsRepository {
    private const val PREFS_NAME = "hdmi_prefs"
    private const val PREFS_RECENT_NAME = "app_list_recent"

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
    private const val KEY_RECENT_PKGS = "recent_pkgs"

    private const val RECENT_SEPARATOR = "|"

    private const val DEFAULT_PORT = 3
    private const val DEFAULT_COUNTDOWN_SECONDS = 3
    private const val DEFAULT_AUTO_SLEEP_SECONDS = 30

    private class Cache<T : Any> {
        @Volatile private var value: T? = null

        inline fun get(load: () -> T): T = value ?: load().also { value = it }

        fun set(newValue: T) {
            value = newValue
        }

        fun clear() {
            value = null
        }
    }

    private val cachedDefaultPort = Cache<Int>()
    private val cachedCountdownSeconds = Cache<Int>()
    private val cachedAppMode = Cache<Boolean>()
    private val cachedAutoOpenPkg = Cache<String>()
    private val cachedAutoOpenLabel = Cache<String>()
    private val cachedAutoOpenDelay = Cache<Int>()
    private val cachedSignalSearchScreen = Cache<Boolean>()
    private val cachedOobeCompleted = Cache<Boolean>()
    private val cachedAutoSleepSeconds = Cache<Int>()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun recentPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_RECENT_NAME, Context.MODE_PRIVATE)

    private inline fun edit(context: Context, block: SharedPreferences.Editor.() -> Unit) {
        prefs(context).edit().apply(block).apply()
    }

    // ── 初次設定精靈 ─────────────────────────────────────────────────────────

    fun isOobeCompleted(context: Context): Boolean = cachedOobeCompleted.get {
        prefs(context).getBoolean(KEY_OOBE_COMPLETED, false)
    }

    fun setOobeCompleted(context: Context, completed: Boolean) {
        cachedOobeCompleted.set(completed)
        edit(context) { putBoolean(KEY_OOBE_COMPLETED, completed) }
    }

    fun resetAllSettings(context: Context) {
        cachedDefaultPort.clear()
        cachedCountdownSeconds.clear()
        cachedAppMode.clear()
        cachedAutoOpenPkg.clear()
        cachedAutoOpenLabel.clear()
        cachedAutoOpenDelay.clear()
        cachedSignalSearchScreen.clear()
        cachedOobeCompleted.clear()
        cachedAutoSleepSeconds.clear()

        edit(context) { clear() }
        recentPrefs(context).edit().clear().apply()
    }

    // ── HDMI ────────────────────────────────────────────────────────────────

    fun getDefaultPort(context: Context): Int = cachedDefaultPort.get {
        prefs(context).getInt(KEY_DEFAULT_PORT, DEFAULT_PORT)
    }

    fun setDefaultPort(context: Context, port: Int) {
        cachedDefaultPort.set(port)
        edit(context) { putInt(KEY_DEFAULT_PORT, port) }
    }

    fun getCountdownSeconds(context: Context): Int = cachedCountdownSeconds.get {
        prefs(context).getInt(KEY_COUNTDOWN_SECONDS, DEFAULT_COUNTDOWN_SECONDS)
    }

    fun setCountdownSeconds(context: Context, seconds: Int) {
        cachedCountdownSeconds.set(seconds)
        edit(context) { putInt(KEY_COUNTDOWN_SECONDS, seconds) }
    }

    fun getAutoSleepSeconds(context: Context): Int = cachedAutoSleepSeconds.get {
        prefs(context).getInt(KEY_AUTO_SLEEP_SECONDS, DEFAULT_AUTO_SLEEP_SECONDS)
    }

    fun setAutoSleepSeconds(context: Context, seconds: Int) {
        cachedAutoSleepSeconds.set(seconds)
        edit(context) { putInt(KEY_AUTO_SLEEP_SECONDS, seconds) }
    }

    fun isSignalSearchScreenEnabled(context: Context): Boolean = cachedSignalSearchScreen.get {
        prefs(context).getBoolean(KEY_SIGNAL_SEARCH_SCREEN, true)
    }

    fun setSignalSearchScreenEnabled(context: Context, enabled: Boolean) {
        cachedSignalSearchScreen.set(enabled)
        edit(context) { putBoolean(KEY_SIGNAL_SEARCH_SCREEN, enabled) }
    }

    // ── 遙控器按鍵對應 ───────────────────────────────────────────────────────

    fun isButtonMapperEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BUTTON_MAPPER_ENABLED, true)

    fun setButtonMapperEnabled(context: Context, enabled: Boolean) {
        edit(context) { putBoolean(KEY_BUTTON_MAPPER_ENABLED, enabled) }
    }

    fun isHomeButtonOverrideEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERRIDE_HOME_BUTTON, true)

    fun setHomeButtonOverrideEnabled(context: Context, enabled: Boolean) {
        edit(context) { putBoolean(KEY_OVERRIDE_HOME_BUTTON, enabled) }
    }

    fun isInputButtonOverrideEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERRIDE_INPUT_BUTTON, true)

    fun setInputButtonOverrideEnabled(context: Context, enabled: Boolean) {
        edit(context) { putBoolean(KEY_OVERRIDE_INPUT_BUTTON, enabled) }
    }

    // ── App 模式 ────────────────────────────────────────────────────────────

    fun isAppModeEnabled(context: Context): Boolean = cachedAppMode.get {
        prefs(context).getBoolean(KEY_APP_MODE, false)
    }

    fun setAppModeEnabled(context: Context, enabled: Boolean) {
        cachedAppMode.set(enabled)
        edit(context) { putBoolean(KEY_APP_MODE, enabled) }
    }

    fun getAutoOpenPackage(context: Context): String = cachedAutoOpenPkg.get {
        prefs(context).getString(KEY_AUTO_OPEN_PKG, "") ?: ""
    }

    fun getAutoOpenLabel(context: Context): String = cachedAutoOpenLabel.get {
        prefs(context).getString(KEY_AUTO_OPEN_LABEL, "") ?: ""
    }

    fun setAutoOpenApp(context: Context, pkg: String, label: String) {
        cachedAutoOpenPkg.set(pkg)
        cachedAutoOpenLabel.set(label)
        edit(context) {
            putString(KEY_AUTO_OPEN_PKG, pkg)
            putString(KEY_AUTO_OPEN_LABEL, label)
        }
    }

    /** 尚未單獨設定時，沿用舊版的 [KEY_COUNTDOWN_SECONDS]；負值視為無效並退回預設值。 */
    fun getAutoOpenDelaySeconds(context: Context): Int = cachedAutoOpenDelay.get {
        val prefs = prefs(context)
        val sec = if (prefs.contains(KEY_AUTO_OPEN_DELAY)) {
            prefs.getInt(KEY_AUTO_OPEN_DELAY, DEFAULT_COUNTDOWN_SECONDS)
        } else {
            prefs.getInt(KEY_COUNTDOWN_SECONDS, DEFAULT_COUNTDOWN_SECONDS)
        }
        if (sec >= 0) sec else DEFAULT_COUNTDOWN_SECONDS
    }

    fun setAutoOpenDelaySeconds(context: Context, seconds: Int) {
        cachedAutoOpenDelay.set(seconds)
        edit(context) { putInt(KEY_AUTO_OPEN_DELAY, seconds) }
    }

    // ── 最近使用的 App ───────────────────────────────────────────────────────

    /** 最近啟動的套件名稱，最新的在前。 */
    fun getRecentPackages(context: Context): List<String> {
        val raw = recentPrefs(context).getString(KEY_RECENT_PKGS, "") ?: ""
        return if (raw.isBlank()) emptyList()
        else raw.split(RECENT_SEPARATOR).filter { it.isNotBlank() }
    }

    fun setRecentPackages(context: Context, packages: List<String>) {
        recentPrefs(context).edit()
            .putString(KEY_RECENT_PKGS, packages.joinToString(RECENT_SEPARATOR))
            .apply()
    }
}
