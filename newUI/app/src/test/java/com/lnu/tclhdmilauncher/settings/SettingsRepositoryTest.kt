package com.lnu.tclhdmilauncher.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var context: FakeContext

    @Before
    fun setUp() {
        context = FakeContext()
        // SettingsRepository 是 singleton 且帶有記憶體快取，每個測試前先清空
        SettingsRepository.resetAllSettings(context)
        context = FakeContext()
    }

    @Test
    fun `defaults are returned when nothing is stored`() {
        assertEquals(3, SettingsRepository.getDefaultPort(context))
        assertEquals(3, SettingsRepository.getCountdownSeconds(context))
        assertEquals(30, SettingsRepository.getAutoSleepSeconds(context))
        assertFalse(SettingsRepository.isAppModeEnabled(context))
        assertFalse(SettingsRepository.isOobeCompleted(context))
        assertTrue(SettingsRepository.isSignalSearchScreenEnabled(context))
        assertTrue(SettingsRepository.isButtonMapperEnabled(context))
        assertTrue(SettingsRepository.isHomeButtonOverrideEnabled(context))
        assertTrue(SettingsRepository.isInputButtonOverrideEnabled(context))
        assertEquals("", SettingsRepository.getAutoOpenPackage(context))
        assertEquals("", SettingsRepository.getAutoOpenLabel(context))
    }

    @Test
    fun `values written are read back and persisted`() {
        SettingsRepository.setDefaultPort(context, 2)
        SettingsRepository.setCountdownSeconds(context, 7)
        SettingsRepository.setAppModeEnabled(context, true)
        SettingsRepository.setAutoOpenApp(context, "com.example.app", "Example")

        assertEquals(2, SettingsRepository.getDefaultPort(context))
        assertEquals(7, SettingsRepository.getCountdownSeconds(context))
        assertTrue(SettingsRepository.isAppModeEnabled(context))
        assertEquals("com.example.app", SettingsRepository.getAutoOpenPackage(context))
        assertEquals("Example", SettingsRepository.getAutoOpenLabel(context))

        val stored = context.prefsFor("hdmi_prefs")
        assertEquals(2, stored.getInt("default_port", -1))
        assertEquals("com.example.app", stored.getString("auto_open_pkg", null))
    }

    @Test
    fun `uncached settings reflect the underlying storage`() {
        SettingsRepository.setButtonMapperEnabled(context, false)
        SettingsRepository.setHomeButtonOverrideEnabled(context, false)
        SettingsRepository.setInputButtonOverrideEnabled(context, false)

        assertFalse(SettingsRepository.isButtonMapperEnabled(context))
        assertFalse(SettingsRepository.isHomeButtonOverrideEnabled(context))
        assertFalse(SettingsRepository.isInputButtonOverrideEnabled(context))
    }

    @Test
    fun `auto open delay falls back to legacy countdown when unset`() {
        context.prefsFor("hdmi_prefs").edit().putInt("countdown_seconds", 9).apply()
        assertEquals(9, SettingsRepository.getAutoOpenDelaySeconds(context))
    }

    @Test
    fun `auto open delay prefers its own value`() {
        context.prefsFor("hdmi_prefs").edit()
            .putInt("countdown_seconds", 9)
            .putInt("auto_open_delay", 5)
            .apply()
        assertEquals(5, SettingsRepository.getAutoOpenDelaySeconds(context))
    }

    @Test
    fun `negative auto open delay falls back to default`() {
        context.prefsFor("hdmi_prefs").edit().putInt("auto_open_delay", -4).apply()
        assertEquals(3, SettingsRepository.getAutoOpenDelaySeconds(context))
        // 快取後第二次讀取也必須維持一致
        assertEquals(3, SettingsRepository.getAutoOpenDelaySeconds(context))
    }

    @Test
    fun `recent packages round trip and ignore blanks`() {
        assertEquals(emptyList<String>(), SettingsRepository.getRecentPackages(context))

        SettingsRepository.setRecentPackages(context, listOf("a.b.c", "d.e.f"))
        assertEquals(listOf("a.b.c", "d.e.f"), SettingsRepository.getRecentPackages(context))

        context.prefsFor("app_list_recent").edit().putString("recent_pkgs", "a||b|").apply()
        assertEquals(listOf("a", "b"), SettingsRepository.getRecentPackages(context))
    }

    @Test
    fun `reset clears settings, caches and recents`() {
        SettingsRepository.setDefaultPort(context, 1)
        SettingsRepository.setOobeCompleted(context, true)
        SettingsRepository.setRecentPackages(context, listOf("x.y"))

        SettingsRepository.resetAllSettings(context)

        assertEquals(3, SettingsRepository.getDefaultPort(context))
        assertFalse(SettingsRepository.isOobeCompleted(context))
        assertEquals(emptyList<String>(), SettingsRepository.getRecentPackages(context))
    }

    // ── 測試替身 ────────────────────────────────────────────────────────────

    private class FakeContext : ContextWrapper(null) {
        private val stores = mutableMapOf<String, FakeSharedPreferences>()

        fun prefsFor(name: String): SharedPreferences = stores.getOrPut(name) { FakeSharedPreferences() }

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefsFor(name)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { removals += key }
            override fun clear() = apply { clearAll = true }

            override fun commit(): Boolean {
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                data.putAll(pending)
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
