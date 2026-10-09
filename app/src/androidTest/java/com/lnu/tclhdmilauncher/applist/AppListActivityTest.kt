package com.lnu.tclhdmilauncher.applist

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lnu.tclhdmilauncher.R
import com.lnu.tclhdmilauncher.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Activity without opening apps or executing package-management actions. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppListActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs = context.getSharedPreferences("hdmi_prefs", Context.MODE_PRIVATE)
    private val safetyHandler = Handler(Looper.getMainLooper())
    private val disableAutoOpen = Runnable { SettingsRepository.setAppModeEnabled(context, false) }
    private var scenario: ActivityScenario<AppListActivity>? = null
    private var savedPreferences: Map<String, Any?>? = null
    private var originalDelay = 3
    private lateinit var apps: Map<String, InstalledApp>

    private val appRow = SemanticsMatcher("an app-list row") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("app-list-row-") == true
    }
    private val menuOption = SemanticsMatcher("an app-list menu option") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("app-list-menu-option-") == true
    }
    private val countdown = SemanticsMatcher("the localized auto-open countdown") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { text ->
            (0..30).any { seconds ->
                text.text == context.getString(R.string.auto_open_countdown_banner, seconds, COUNTDOWN_LABEL)
            }
        } == true
    }

    @Before
    fun preparePreferences() {
        savedPreferences = PREFERENCE_KEYS.associateWith { prefs.all[it] }
        originalDelay = SettingsRepository.getAutoOpenDelaySeconds(context)
        SettingsRepository.setAppModeEnabled(context, false)
        SettingsRepository.setAutoOpenApp(context, "", "")
        SettingsRepository.setAutoOpenDelaySeconds(context, 30)
        assertTrue("Persist test settings before launching the Activity", prefs.edit().commit())
        apps = discoverApps()
        assumeTrue("No enabled launcher package other than the app under test", apps.values.any { it.enabled })
    }

    @After
    fun closeActivityAndRestorePreferences() {
        try {
            // Disable the launch path before teardown, even when an assertion failed mid-countdown.
            if (savedPreferences != null) SettingsRepository.setAppModeEnabled(context, false)
            scenario?.close()
        } finally {
            scenario = null
            instrumentation.runOnMainSync { safetyHandler.removeCallbacks(disableAutoOpen) }
            savedPreferences?.let { saved ->
                // Public setters restore the repository's caches; the final edit restores absent keys too.
                SettingsRepository.setAutoOpenApp(
                    context,
                    saved["auto_open_pkg"] as? String ?: "",
                    saved["auto_open_label"] as? String ?: "",
                )
                SettingsRepository.setAutoOpenDelaySeconds(context, originalDelay)
                SettingsRepository.setAppModeEnabled(context, saved["app_mode"] as? Boolean ?: false)
                val editor = prefs.edit()
                saved.forEach { (key, value) -> restorePreference(editor, key, value) }
                assertTrue("Restore exactly the touched preference keys", editor.commit())
            }
        }
    }

    @Test
    fun realAppMenuMatchesInstalledApplicationFlagsAndBackRestoresRow() {
        launch()
        val app = focusedApp()
        openAppMenu()
        val expected = buildList {
            add(R.string.menu_open)
            add(R.string.menu_batch_select)
            add(R.string.menu_set_auto_open)
            if (!app.isSystem) add(R.string.menu_uninstall)
            if (app.isDisableable) add(if (app.enabled) R.string.menu_freeze else R.string.menu_unfreeze)
            add(R.string.menu_app_info)
        }
        expected.forEachIndexed { index, resource ->
            composeRule.onNodeWithTag("app-list-menu").performScrollToIndex(index)
            composeRule.onNodeWithTag("app-list-menu-option-$index")
                .assertTextContains(context.getString(resource))
        }
        composeRule.onNodeWithTag("app-list-menu-option-${expected.size}").assertDoesNotExist()
        pressRemote(KeyEvent.KEYCODE_BACK)
        composeRule.onNodeWithTag("app-list-menu").assertDoesNotExist()
        assertEquals(app.packageName, focusedApp().packageName)
        assertActivityAlive()
    }

    @Test
    fun batchSelectAllAndCancelUseRealActivitySelectionState() {
        launch()
        openAppMenu()
        chooseSafeMenuOption(R.string.menu_batch_select)
        primary().assertIsFocused().assertTextContains(context.getString(R.string.btn_batch_action))
        composeRule.onNodeWithText(context.getString(R.string.title_selected_count, 1)).assertExists()
        pressRemote(KeyEvent.KEYCODE_DPAD_CENTER)
        chooseSafeMenuOption(R.string.menu_select_all)
        composeRule.onNodeWithText(context.getString(R.string.title_selected_count, apps.size)).assertExists()
        primary().assertIsFocused()
        pressRemote(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNode(appRow and isFocused()).assertIsSelected()
        requestPrimaryFocus()
        pressRemote(KeyEvent.KEYCODE_DPAD_RIGHT)
        secondary().assertIsFocused().assertTextContains(context.getString(R.string.btn_cancel_selection))
        pressRemote(KeyEvent.KEYCODE_DPAD_CENTER)
        primary().assertTextContains(context.getString(R.string.btn_settings))
        secondary().assertTextContains(context.getString(R.string.btn_hdmi_selector))
        pressRemote(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNode(appRow and isFocused()).assertIsNotSelected()
        assertActivityAlive()
    }

    @Test
    fun setAndClearAutoOpenUpdateRepositoryPreferencesAndRowBadge() {
        launch()
        val app = focusedApp()
        openAppMenu()
        chooseSafeMenuOption(R.string.menu_set_auto_open)
        assertEquals(app.packageName, SettingsRepository.getAutoOpenPackage(context))
        assertEquals(app.label, SettingsRepository.getAutoOpenLabel(context))
        assertTrue(SettingsRepository.isAppModeEnabled(context))
        assertEquals(app.packageName, prefs.getString("auto_open_pkg", null))
        assertEquals(app.label, prefs.getString("auto_open_label", null))
        assertTrue(prefs.getBoolean("app_mode", false))
        composeRule.onNode(appRow and isFocused()).assertTextContains(context.getString(R.string.badge_auto_open))
        openAppMenu()
        chooseSafeMenuOption(R.string.menu_clear_auto_open)
        assertEquals("", SettingsRepository.getAutoOpenPackage(context))
        assertEquals("", SettingsRepository.getAutoOpenLabel(context))
        assertEquals("", prefs.getString("auto_open_pkg", null))
        assertEquals("", prefs.getString("auto_open_label", null))
        composeRule.onNode(appRow and isFocused() and hasText(context.getString(R.string.badge_auto_open)))
            .assertDoesNotExist()
        assertActivityAlive()
    }

    @Test
    fun pauseAndStopClearForegroundFlagThenResumeReloadsIconsAndKeepsUiOperable() {
        launch()
        waitForForeground()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { cachedIconPackages().isNotEmpty() }
        scenario!!.moveToState(Lifecycle.State.STARTED)
        assertFalse(AppListActivity.isForegroundFocused)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        waitForForeground()
        // STARTED only pauses. CREATED additionally exercises onStop's icon-cache eviction.
        scenario!!.moveToState(Lifecycle.State.CREATED)
        assertFalse(AppListActivity.isForegroundFocused)
        assertTrue(cachedIconPackages().isEmpty())
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        waitForRows()
        waitForForeground()
        focusAnAppRow()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { cachedIconPackages().isNotEmpty() }
        openAppMenu()
        pressRemote(KeyEvent.KEYCODE_BACK)
        composeRule.onNode(appRow and isFocused()).assertIsFocused()
        assertActivityAlive()
    }

    @Test
    fun directionalRemoteKeyCancelsConfiguredCountdownWithoutLeavingActivity() {
        launchWithCountdown()
        pressRemote(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.onNode(countdown).assertDoesNotExist()
        assertActivityAlive()
        waitForRows()
        focusAnAppRow()
        openAppMenu()
        pressRemote(KeyEvent.KEYCODE_BACK)
        assertActivityAlive()
    }

    @Test
    fun firstRemoteBackOnlyCancelsCountdownAndDoesNotReturnToMainActivity() {
        launchWithCountdown()
        pressRemote(KeyEvent.KEYCODE_BACK)
        composeRule.onNode(countdown).assertDoesNotExist()
        assertActivityAlive()
        waitForRows()
        focusAnAppRow()
        openAppMenu()
        // This Back belongs to the dialog, never to the Activity's HDMI-return action.
        pressRemote(KeyEvent.KEYCODE_BACK)
        composeRule.onNodeWithTag("app-list-menu").assertDoesNotExist()
        assertActivityAlive()
    }

    private fun launch() {
        launchScenario()
        waitForRows()
        focusAnAppRow()
    }

    private fun launchScenario() {
        scenario = ActivityScenario.launch<AppListActivity>(Intent(context, AppListActivity::class.java))
    }

    private fun launchWithCountdown() {
        val eligible = apps.values.first { it.enabled }
        // A stalled test must not allow an installed package to auto-launch after thirty ticks.
        safetyHandler.postDelayed(disableAutoOpen, SAFETY_TIMEOUT_MS)
        SettingsRepository.setAutoOpenApp(context, eligible.packageName, COUNTDOWN_LABEL)
        SettingsRepository.setAppModeEnabled(context, true)
        launchScenario()
        // Cancel before waiting for app discovery/icon work, keeping well inside the safety deadline.
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(countdown).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForRows() {
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(hasText(context.getString(R.string.app_list_loading)))
                .fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodes(appRow).fetchSemanticsNodes().isNotEmpty()
        }
    }


    private fun focusAnAppRow() {
        requestPrimaryFocus()
        pressRemote(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(appRow and isFocused()).fetchSemanticsNodes().size == 1
        }
    }

    private fun focusedApp(): InstalledApp {
        val texts = composeRule.onNode(appRow and isFocused()).fetchSemanticsNode()
            .config[SemanticsProperties.Text].map { it.text }
        return apps.values.single { app ->
            texts.any { it == app.packageName || it == app.packageName + context.getString(R.string.app_frozen_suffix) }
        }
    }

    private fun openAppMenu() {
        // Never release a synthetic short center press on an app row: that would launch it.
        composeRule.onNode(appRow and isFocused()).performSemanticsAction(SemanticsActions.OnLongClick) {
            assertTrue(it())
        }
        composeRule.onNodeWithTag("app-list-menu-option-0").assertIsFocused()
    }

    private fun chooseSafeMenuOption(resource: Int) {
        require(resource in SAFE_MENU_OPTIONS)
        val option = composeRule.onNode(menuOption and hasText(context.getString(resource)))
        option.performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        option.assertIsFocused()
        pressRemote(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.onNodeWithTag("app-list-menu").assertDoesNotExist()
    }

    private fun requestPrimaryFocus() {
        primary().performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        primary().assertIsFocused()
    }

    private fun primary() = composeRule.onNodeWithTag("app-list-primary")
    private fun secondary() = composeRule.onNodeWithTag("app-list-secondary")

    private fun pressRemote(keyCode: Int) {
        require(keyCode in setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK,
        ))
        val downTime = SystemClock.uptimeMillis()
        fun event(action: Int) = KeyEvent(
            downTime, SystemClock.uptimeMillis(), action, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD,
        )
        try {
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event(KeyEvent.ACTION_DOWN), true))
        } finally {
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event(KeyEvent.ACTION_UP), true))
        }
        composeRule.waitForIdle()
    }

    private fun assertActivityAlive() {
        assertEquals(Lifecycle.State.RESUMED, scenario!!.state)
        scenario!!.onActivity {
            assertFalse(it.isFinishing)
            assertFalse(it.isDestroyed)
        }
        primary().assertExists()
    }

    private fun waitForForeground() {
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { AppListActivity.isForegroundFocused }
    }

    private fun cachedIconPackages(): Set<String> {
        var packages = emptySet<String>()
        scenario!!.onActivity { activity ->
            // Read-only inspection: bitmap images have no public semantics identifying cache eviction.
            val field = AppListActivity::class.java.getDeclaredField("iconCache").apply { isAccessible = true }
            packages = (field.get(activity) as Map<*, *>).keys.filterIsInstance<String>().toSet()
        }
        return packages
    }

    private fun discoverApps(): Map<String, InstalledApp> {
        val pm = context.packageManager
        val result = linkedMapOf<String, InstalledApp>()
        listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).forEach { category ->
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(category), PackageManager.MATCH_DISABLED_COMPONENTS,
            ).forEach resolveLoop@ { resolve ->
                val activity = resolve.activityInfo ?: return@resolveLoop
                val info = activity.applicationInfo ?: return@resolveLoop
                val pkg = activity.packageName
                if (pkg != context.packageName && pkg !in result) {
                    val label = try {
                        resolve.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                            ?: pm.getApplicationLabel(info).toString()
                    } catch (_: Exception) {
                        pkg
                    }
                    val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    result[pkg] = InstalledApp(
                        pkg, label, info.enabled, system,
                        system && (info.flags and ApplicationInfo.FLAG_PERSISTENT) == 0,
                    )
                }
            }
        }
        return result
    }

    private fun restorePreference(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            null -> editor.remove(key)
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            else -> error("Unexpected preference type for $key: ${value.javaClass.name}")
        }
    }

    private data class InstalledApp(
        val packageName: String,
        val label: String,
        val enabled: Boolean,
        val isSystem: Boolean,
        val isDisableable: Boolean,
    )

    private companion object {
        const val WAIT_TIMEOUT_MS = 5_000L
        const val SAFETY_TIMEOUT_MS = 10_000L
        const val COUNTDOWN_LABEL = "Activity integration countdown"
        val PREFERENCE_KEYS = listOf("app_mode", "auto_open_pkg", "auto_open_label", "auto_open_delay")
        val SAFE_MENU_OPTIONS = setOf(
            R.string.menu_batch_select, R.string.menu_select_all,
            R.string.menu_set_auto_open, R.string.menu_clear_auto_open,
        )
    }
}
