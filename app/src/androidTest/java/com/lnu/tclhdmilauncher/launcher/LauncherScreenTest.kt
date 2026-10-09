package com.lnu.tclhdmilauncher.launcher

import android.view.ViewConfiguration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lnu.tclhdmilauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class LauncherScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val inputs = mutableStateOf(
        Inputs(countdownText = context.getString(R.string.countdown_active, 5, 2)),
    )
    private val portClicks = mutableListOf<Int>()
    private val portLongClicks = mutableListOf<Int>()
    private val focusedPorts = mutableListOf<Int?>()
    private var settingsClicks = 0
    private var appsClicks = 0

    @Test
    fun initialFocusIsDefaultPortOne() = assertInitialFocus(1)

    @Test
    fun initialFocusIsDefaultPortTwo() = assertInitialFocus(2)

    @Test
    fun initialFocusIsDefaultPortThree() = assertInitialFocus(3)

    @Test
    fun horizontalNavigationStopsAtFirstAndLastCards() {
        render(defaultPort = 2)

        press(Key.DirectionLeft)
        assertFocusedPort(1)
        press(Key.DirectionLeft)
        assertFocusedPort(1)
        press(Key.DirectionRight)
        assertFocusedPort(2)
        press(Key.DirectionRight)
        assertFocusedPort(3)
        press(Key.DirectionRight)
        assertFocusedPort(3)
        press(Key.DirectionLeft)
        assertFocusedPort(2)
        assertNoActions()
    }

    @Test
    fun upFromNondefaultCardGoesToSettingsAndDownReturnsToSelectedPort() {
        render(defaultPort = 2)
        press(Key.DirectionRight)
        assertFocusedPort(3)

        press(Key.DirectionUp)
        assertSettingsFocused()
        press(Key.DirectionDown)
        assertFocusedPort(3)
        assertNoActions()
    }

    @Test
    fun downFromNondefaultCardGoesToAppsAndUpReturnsToSelectedPort() {
        render(defaultPort = 2)
        press(Key.DirectionLeft)
        assertFocusedPort(1)

        press(Key.DirectionDown)
        assertAppsFocused()
        press(Key.DirectionUp)
        assertFocusedPort(1)
        assertNoActions()
    }

    @Test
    fun countdownChangesDoNotStealCardFocus() {
        render(defaultPort = 2)
        press(Key.DirectionRight)
        assertFocusedPort(3)
        val updatedText = context.getString(R.string.countdown_active, 1, 2)

        updateInputs { it.copy(countdownText = updatedText) }

        composeRule.onNodeWithText(updatedText).assertIsDisplayed()
        assertFocusedPort(3)
        assertNoActions()
    }

    @Test
    fun countdownChangesDoNotStealSettingsFocus() {
        render(defaultPort = 2)
        press(Key.DirectionUp)
        assertSettingsFocused()
        val updatedText = context.getString(R.string.text_cancelled)

        updateInputs { it.copy(countdownText = updatedText) }

        composeRule.onNodeWithText(updatedText).assertIsDisplayed()
        assertSettingsFocused()
        assertNoActions()
    }

    @Test
    fun defaultChangesDoNotStealCardFocus() {
        render(defaultPort = 2)
        press(Key.DirectionRight)
        assertFocusedPort(3)

        updateInputs { it.copy(defaultPort = 1) }

        assertDefaultBadge(1)
        assertFocusedPort(3)
        assertNoActions()
    }

    @Test
    fun defaultChangesDoNotStealSettingsFocusAndDownUsesLatestDefault() {
        render(defaultPort = 2)
        press(Key.DirectionUp)
        assertSettingsFocused()

        updateInputs { it.copy(defaultPort = 3) }

        assertSettingsFocused()
        press(Key.DirectionDown)
        assertFocusedPort(3)
        assertNoActions()
    }

    @Test
    fun defaultChangesDoNotStealAppsFocusAndUpUsesLatestDefault() {
        render(defaultPort = 2)
        press(Key.DirectionDown)
        assertAppsFocused()

        updateInputs { it.copy(defaultPort = 1) }

        assertAppsFocused()
        press(Key.DirectionUp)
        assertFocusedPort(1)
        assertNoActions()
    }

    @Test
    fun focusRequestGenerationReturnsToLatestDefaultFromCardAndSettings() {
        render(defaultPort = 2)
        press(Key.DirectionRight)
        assertFocusedPort(3)
        updateInputs { it.copy(defaultPort = 1) }
        assertFocusedPort(3)

        updateInputs { it.copy(focusRequestGeneration = it.focusRequestGeneration + 1) }
        assertFocusedPort(1)

        press(Key.DirectionUp)
        assertSettingsFocused()
        updateInputs { it.copy(defaultPort = 3) }
        assertSettingsFocused()

        updateInputs { it.copy(focusRequestGeneration = it.focusRequestGeneration + 1) }
        assertFocusedPort(3)
        assertNoActions()
    }

    @Test
    fun explicitShortcutFocusRequestSelectsNondefaultPort() {
        render(defaultPort = 3)
        assertFocusedPort(3)

        updateInputs {
            it.copy(focusRequestPort = 1, focusRequestGeneration = it.focusRequestGeneration + 1)
        }

        assertFocusedPort(1)
        assertDefaultBadge(3)
        assertNoActions()
    }

    @Test
    fun explicitRequestToAlreadyFocusedPortUpdatesReturnFocusAfterDefaultChanges() {
        render(defaultPort = 2)
        press(Key.DirectionRight)
        assertFocusedPort(3)
        updateInputs { it.copy(defaultPort = 1) }

        updateInputs {
            it.copy(focusRequestPort = 3, focusRequestGeneration = it.focusRequestGeneration + 1)
        }

        assertFocusedPort(3)
        press(Key.DirectionUp)
        assertSettingsFocused()
        press(Key.DirectionDown)
        assertFocusedPort(3)
        press(Key.DirectionDown)
        assertAppsFocused()
        press(Key.DirectionUp)
        assertFocusedPort(3)
        assertNoActions()
    }

    @Test
    fun centerClickReportsEachSelectedPortWithoutLongClick() {
        render(defaultPort = 1)

        for (port in 1..3) {
            assertFocusedPort(port)
            press(Key.DirectionCenter)
            if (port < 3) press(Key.DirectionRight)
        }

        composeRule.runOnIdle {
            assertEquals(listOf(1, 2, 3), portClicks)
            assertTrue(portLongClicks.isEmpty())
            assertEquals(0, settingsClicks)
            assertEquals(0, appsClicks)
        }
    }

    @Test
    fun heldCenterCallsLongClickForSelectedPortAndDoesNotClickOnRelease() {
        render(defaultPort = 1)
        press(Key.DirectionRight)
        assertFocusedPort(2)
        val holdDurationMillis = maxOf(ViewConfiguration.getLongPressTimeout().toLong(), 500L) + 100L

        // Compose UI 1.10.4 injects repeat key-down events after 500 ms. TV Material 1.1.0
        // handles repeatCount == 1 as long-click and suppresses the release click.
        composeRule.onRoot().performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(holdDurationMillis)
            keyUp(Key.DirectionCenter)
        }

        assertFocusedPort(2)
        composeRule.runOnIdle {
            assertEquals(listOf(2), portLongClicks)
            assertTrue(portClicks.isEmpty())
            assertEquals(0, settingsClicks)
            assertEquals(0, appsClicks)
        }
    }

    @Test
    fun settingsAndAppsCenterClicksInvokeOnlyTheirCallbacks() {
        render(defaultPort = 2)
        press(Key.DirectionUp)
        assertSettingsFocused()
        press(Key.DirectionCenter)
        composeRule.runOnIdle {
            assertEquals(1, settingsClicks)
            assertEquals(0, appsClicks)
        }

        press(Key.DirectionDown)
        assertFocusedPort(2)
        press(Key.DirectionDown)
        assertAppsFocused()
        press(Key.DirectionCenter)

        composeRule.runOnIdle {
            assertEquals(1, settingsClicks)
            assertEquals(1, appsClicks)
            assertTrue(portClicks.isEmpty())
            assertTrue(portLongClicks.isEmpty())
        }
    }

    @Test
    fun countdownClickInvokesSettingsShortcutWithoutChangingCardFocus() {
        render(defaultPort = 2)

        composeRule.onNodeWithText(inputs.value.countdownText).performClick()

        assertFocusedPort(2)
        composeRule.runOnIdle {
            assertEquals(1, settingsClicks)
            assertEquals(0, appsClicks)
            assertTrue(portClicks.isEmpty())
            assertTrue(portLongClicks.isEmpty())
        }
    }

    private fun assertInitialFocus(defaultPort: Int) {
        render(defaultPort)
        composeRule.onNodeWithText(context.getString(R.string.home_subtitle)).assertIsDisplayed()
        composeRule.onNodeWithText(inputs.value.countdownText).assertIsDisplayed()
        assertFocusedPort(defaultPort)
        assertDefaultBadge(defaultPort)
        assertNoActions()
    }

    private fun render(defaultPort: Int) {
        inputs.value = inputs.value.copy(defaultPort = defaultPort)
        composeRule.setContent {
            val current = inputs.value
            LauncherTheme {
                LauncherScreen(
                    defaultPort = current.defaultPort,
                    countdownText = current.countdownText,
                    focusRequestGeneration = current.focusRequestGeneration,
                    onPortClick = { portClicks.add(it) },
                    onPortLongClick = { portLongClicks.add(it) },
                    onSettingsClick = { settingsClicks++ },
                    onAppsClick = { appsClicks++ },
                    onFocusedPortChanged = { focusedPorts.add(it) },
                    focusRequestPort = current.focusRequestPort ?: current.defaultPort,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun updateInputs(update: (Inputs) -> Inputs) {
        composeRule.runOnIdle { inputs.value = update(inputs.value) }
        composeRule.waitForIdle()
    }

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
    }

    // HDMI labels are literal, locale-independent identifiers in LauncherScreen.
    // All localized UI text is obtained from the target application's resources.
    private fun portCard(port: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasText("HDMI $port") and hasClickAction())

    private fun assertFocusedPort(port: Int) {
        portCard(port).assertIsFocused()
        composeRule.runOnIdle {
            assertTrue("Expected a focused-port callback", focusedPorts.isNotEmpty())
            assertEquals(port, focusedPorts.last())
        }
    }

    private fun assertSettingsFocused() {
        composeRule.onNodeWithText(context.getString(R.string.btn_settings)).assertIsFocused()
        assertNoncardFocusCallback()
    }

    private fun assertAppsFocused() {
        composeRule.onNodeWithText(context.getString(R.string.btn_apps)).assertIsFocused()
        assertNoncardFocusCallback()
    }

    private fun assertNoncardFocusCallback() {
        composeRule.runOnIdle {
            assertTrue("Expected a noncard focus callback", focusedPorts.isNotEmpty())
            assertEquals(null, focusedPorts.last())
        }
    }

    private fun assertDefaultBadge(port: Int) {
        composeRule.onNode(
            hasText("HDMI $port") and hasText(context.getString(R.string.card_default_badge)),
        ).assertExists()
    }

    private fun assertNoActions() {
        composeRule.runOnIdle {
            assertTrue(portClicks.isEmpty())
            assertTrue(portLongClicks.isEmpty())
            assertEquals(0, settingsClicks)
            assertEquals(0, appsClicks)
        }
    }

    private data class Inputs(
        val defaultPort: Int = 2,
        val countdownText: String,
        val focusRequestGeneration: Int = 0,
        val focusRequestPort: Int? = null,
    )
}
