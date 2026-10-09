package com.lnu.tclhdmilauncher.cec

import android.content.Context
import com.lnu.tclhdmilauncher.settings.SettingsRepository
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 裝置端的小型持久化 CEC 事件緩衝區，供 [CecDebugActivity] 排查問題。
 *
 * 這份資料僅用於除錯，與使用者設定（[SettingsRepository]）刻意分開儲存。
 */
object CecDebugLog {
    private const val PREFS = "cec_debug"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 60

    private val deque = ArrayDeque<String>()
    private var isInitialized = false

    @Synchronized
    private fun ensureInitialized(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_EVENTS, "") ?: ""
        saved.lines().filter { it.isNotBlank() }.forEach { deque.add(it) }
        isInitialized = true
    }

    @Synchronized
    fun add(context: Context, message: String) {
        ensureInitialized(context)
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        deque.add("$timestamp  $message")
        while (deque.size > MAX_EVENTS) {
            deque.removeFirst()
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_EVENTS, deque.joinToString("\n"))
            .apply()
    }

    @Synchronized
    fun read(context: Context): String {
        ensureInitialized(context)
        return if (deque.isEmpty()) "No CEC events recorded yet." else deque.joinToString("\n")
    }

    @Synchronized
    fun clear(context: Context) {
        deque.clear()
        isInitialized = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_EVENTS).apply()
    }
}
