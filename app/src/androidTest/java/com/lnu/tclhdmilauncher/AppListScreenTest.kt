package com.lnu.tclhdmilauncher

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.view.ViewConfiguration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val inputs = mutableStateOf(Inputs())
    private val clicks = mutableListOf<String>()
    private val longClicks = mutableListOf<String>()
    private val iconRequests = mutableListOf<String>()
    private val events = mutableListOf<String>()

    @Test
    fun categorizedListPreservesSectionTextAndPackageSubtitles() {
        val sections = listOf(
            R.string.section_recent,
            R.string.section_tv_apps,
            R.string.section_mobile_apps,
            R.string.section_system_apps,
            R.string.section_frozen_apps,
        )
        render(items = sections.flatMapIndexed { index, resource ->
            listOf(AppListItem.Section(context.getString(resource, 1)), app(index))
        })

        sections.forEachIndexed { index, resource ->
            composeRule.onNodeWithTag("app-list").performScrollToIndex(index * 2)
            composeRule.onNodeWithText(context.getString(resource, 1)).assertIsDisplayed()
            row(index * 2 + 1).assertTextContains(app(index).label)
                .assertTextContains(app(index).packageName)
        }
    }

    @Test
    fun firstAppInitiallyFocusedAndSectionsAreSkipped() {
        render(items = listOf(AppListItem.Section("TV"), app(0), AppListItem.Section("Mobile"), app(1)))
        row(1).assertIsFocused()
        press(Key.DirectionDown)
        row(3).assertIsFocused()
        press(Key.DirectionUp)
        row(1).assertIsFocused()
        assertNoAppActions()
    }

    @Test
    fun dpadStopsAtListAndHeaderBoundariesWithoutWrapping() {
        render()
        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        row(0).assertIsFocused()
        press(Key.DirectionUp)
        primary().assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionUp)
        primary().assertIsFocused()
        press(Key.DirectionRight)
        secondary().assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionUp)
        secondary().assertIsFocused()
        press(Key.DirectionDown)
        row(0).assertIsFocused()
        press(Key.DirectionDown)
        row(1).assertIsFocused()
        press(Key.DirectionDown)
        row(1).assertIsFocused()
        assertNoAppActions()
    }

    @Test
    fun headerDownReturnsToLastFocusedRowNotFirstRow() {
        render()
        press(Key.DirectionDown)
        row(1).assertIsFocused()
        requestPrimaryFocus()
        press(Key.DirectionRight)
        secondary().assertIsFocused()
        press(Key.DirectionDown)
        row(1).assertIsFocused()
    }

    @Test
    fun duplicateRecentComponentHasIndependentKeyAndFocus() {
        val sameApp = app(0)
        render(items = listOf(
            AppListItem.Section("Recent"), sameApp,
            AppListItem.Section("TV"), sameApp, app(1),
        ))
        row(1).assertIsFocused()
        press(Key.DirectionDown)
        row(3).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        row(4).assertIsFocused()
        composeRule.runOnIdle { assertEquals(listOf(sameApp.packageName), clicks) }
    }

    @Test
    fun manyRowsScrollAndHeaderReturnRecomposesDisposedTarget() {
        render(items = List(70) { app(it) })
        repeat(45) { press(Key.DirectionDown) }
        row(45).assertIsFocused().assertIsDisplayed()
        requestPrimaryFocus()
        composeRule.onNodeWithTag("app-list").performScrollToIndex(0)
        press(Key.DirectionDown)
        row(45).assertIsFocused().assertIsDisplayed()
        press(Key.DirectionUp)
        row(44).assertIsFocused()
        assertNoAppActions()
    }

    @Test
    fun centerClickReportsFocusedAppOnly() {
        render()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        composeRule.runOnIdle {
            assertEquals(listOf(app(1).packageName), clicks)
            assertTrue(longClicks.isEmpty())
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun heldCenterOnFrozenRowInvokesLongClickWithoutShortClickOnRelease() {
        render(items = listOf(app(0, enabled = false)))
        val duration = maxOf(ViewConfiguration.getLongPressTimeout().toLong(), 500L) + 100L
        // The stock TV component recognizes the first repeat and suppresses release click.
        composeRule.onRoot().performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(duration)
            keyUp(Key.DirectionCenter)
        }
        row(0).assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(listOf(app(0).packageName), longClicks)
            assertTrue(clicks.isEmpty())
        }
    }

    @Test
    fun normalHeaderInvokesSettingsAndHdmiCallbacks() {
        render()
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        composeRule.runOnIdle { assertEquals(listOf("settings", "hdmi"), events) }
        assertNoAppActions()
    }

    @Test
    fun selectionHeaderGenerationBadgeAndFrozenRowsRemainOperable() {
        val frozen = app(1, enabled = false)
        render(items = listOf(app(0), frozen))
        update {
            it.copy(
                isMultiSelectMode = true,
                selectedPackages = setOf(frozen.packageName),
                autoOpenPackage = frozen.packageName,
                headerFocusGeneration = 1,
            )
        }
        primary().assertIsFocused().assertTextContains(context.getString(R.string.btn_batch_action))
        secondary().assertTextContains(context.getString(R.string.btn_cancel_selection))
        composeRule.onNodeWithText(context.getString(R.string.title_selected_count, 1)).assertIsDisplayed()
        row(1).assertIsSelected()
            .assertTextContains(context.getString(R.string.app_disabled_suffix, frozen.label))
            .assertTextContains(frozen.packageName + context.getString(R.string.app_frozen_suffix))
            .assertTextContains(context.getString(R.string.badge_auto_open))
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        row(0).assertIsFocused()
        press(Key.DirectionDown)
        row(1).assertIsFocused()
        press(Key.DirectionCenter)
        composeRule.runOnIdle {
            assertEquals(listOf("batch", "cancel"), events)
            assertEquals(listOf(frozen.packageName), clicks)
        }
    }

    @Test
    fun headerGenerationAndCountdownUpdatesDoNotOtherwiseStealFocus() {
        render()
        press(Key.DirectionDown)
        update { it.copy(countdownText = "Countdown updated", autoOpenPackage = app(0).packageName) }
        row(1).assertIsFocused()
        composeRule.onNodeWithText("Countdown updated").assertIsDisplayed()
        update { it.copy(headerFocusGeneration = it.headerFocusGeneration + 1) }
        primary().assertIsFocused()
        press(Key.DirectionDown)
        row(1).assertIsFocused()
    }

    @Test
    fun loadingStartsOnHeaderThenFocusesFirstAppWhenReady() {
        render(items = listOf(app(0)), isLoading = true)
        primary().assertIsFocused()
        row(0).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.app_list_loading)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.app_list_empty)).assertDoesNotExist()
        update { it.copy(items = listOf(AppListItem.Section("TV"), app(0)), isLoading = false) }
        row(1).assertIsFocused()
    }

    @Test
    fun headerNavigationWhileLoadingPreventsInitialFocusTheft() {
        render(items = emptyList(), isLoading = true)
        press(Key.DirectionRight)
        secondary().assertIsFocused()
        update { it.copy(items = listOf(app(0)), isLoading = false) }
        secondary().assertIsFocused()
        press(Key.DirectionDown)
        row(0).assertIsFocused()
    }

    @Test
    fun refreshExcludesPriorRowsAndRestoresTheirFocusWhenReady() {
        render()
        press(Key.DirectionDown)
        row(1).assertIsFocused()
        update { it.copy(isLoading = true) }
        row(0).assertDoesNotExist()
        row(1).assertDoesNotExist()
        primary().assertIsFocused()
        composeRule.onNodeWithText(context.getString(R.string.app_list_loading)).assertIsDisplayed()
        assertNoAppActions()
        update { it.copy(isLoading = false) }
        row(1).assertIsFocused().assertIsDisplayed()
        press(Key.DirectionCenter)
        composeRule.runOnIdle {
            assertEquals(listOf(app(1).packageName), clicks)
            assertTrue(longClicks.isEmpty())
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun headerUseDuringRefreshBlocksDownAndPreventsAutomaticRowRestoration() {
        render()
        press(Key.DirectionDown)
        update { it.copy(isLoading = true) }
        primary().assertIsFocused()
        press(Key.DirectionDown)
        primary().assertIsFocused()
        row(0).assertDoesNotExist()
        row(1).assertDoesNotExist()
        press(Key.DirectionRight)
        secondary().assertIsFocused()
        press(Key.DirectionCenter)
        assertNoAppActions()
        update { it.copy(isLoading = false) }
        secondary().assertIsFocused()
        press(Key.DirectionDown)
        row(1).assertIsFocused()
        press(Key.DirectionCenter)
        composeRule.runOnIdle {
            assertEquals(listOf("hdmi"), events)
            assertEquals(listOf(app(1).packageName), clicks)
            assertTrue(longClicks.isEmpty())
        }
    }

    @Test
    fun emptyAndRefreshKeepHeaderUsableWhenRowsAreRemoved() {
        render()
        press(Key.DirectionDown)
        update { it.copy(isLoading = true) }
        row(1).assertDoesNotExist()
        primary().assertIsFocused()
        update { it.copy(items = emptyList()) }
        primary().assertIsFocused()
        update { it.copy(isLoading = false) }
        primary().assertIsFocused()
        composeRule.onNodeWithText(context.getString(R.string.app_list_empty)).assertIsDisplayed()
        press(Key.DirectionDown)
        primary().assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        composeRule.runOnIdle { assertEquals(listOf("hdmi"), events) }
    }

    @Test
    fun removedFocusedRowGetsAnOperableReplacement() {
        render()
        press(Key.DirectionDown)
        update { it.copy(items = listOf(app(0))) }
        row(0).assertIsFocused()
        press(Key.DirectionUp)
        primary().assertIsFocused()
    }

    @Test
    fun iconsAreRequestedOnlyWhenVisibleAndAgainAfterCacheEviction() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        inputs.value = inputs.value.copy(icons = mapOf(app(0).packageName to bitmap))
        render(items = List(60) { app(it) })
        composeRule.runOnIdle {
            assertFalse(iconRequests.contains(app(0).packageName))
            assertFalse(iconRequests.contains(app(59).packageName))
        }
        update { it.copy(icons = emptyMap()) }
        composeRule.waitUntil { iconRequests.contains(app(0).packageName) }
        composeRule.onNodeWithTag("app-list").performScrollToIndex(59)
        composeRule.waitUntil { iconRequests.contains(app(59).packageName) }
        composeRule.runOnIdle {
            assertEquals(1, iconRequests.count { it == app(0).packageName })
        }
    }

    @Test
    fun dialogFocusesFirstActionDismissesBeforeCallbackAndRestoresRow() {
        render()
        press(Key.DirectionDown)
        showMenu(listOf("Open", "App Info"))
        menuOption(0).assertIsFocused()
        press(Key.DirectionUp)
        menuOption(0).assertIsFocused()
        press(Key.DirectionDown)
        menuOption(1).assertIsFocused()
        press(Key.DirectionDown)
        composeRule.onNodeWithTag("app-list-menu-cancel").assertIsFocused()
        press(Key.DirectionUp)
        menuOption(1).assertIsFocused()
        press(Key.DirectionCenter)
        composeRule.onNodeWithTag("app-list-menu").assertDoesNotExist()
        row(1).assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(listOf("dismiss", "action:App Info"), events)
            assertTrue(clicks.isEmpty())
        }
    }

    @Test
    fun dialogBackRestoresHeaderAndDoesNotInvokeAction() {
        render()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        showMenu(listOf("Open"))
        menuOption(0).assertIsFocused()
        press(Key.Back)
        composeRule.onNodeWithTag("app-list-menu").assertDoesNotExist()
        secondary().assertIsFocused()
        composeRule.runOnIdle { assertEquals(listOf("dismiss"), events) }
    }

    @Test
    fun longDialogScrollsToLastActionAndStopsAtBoundaries() {
        render()
        showMenu(List(35) { "Action $it" })
        repeat(34) { press(Key.DirectionDown) }
        menuOption(34).assertIsFocused().assertIsDisplayed()
        press(Key.DirectionDown)
        composeRule.onNodeWithTag("app-list-menu-cancel").assertIsFocused().assertIsDisplayed()
        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        composeRule.onNodeWithTag("app-list-menu-cancel").assertIsFocused()
        composeRule.onNodeWithTag("app-list-menu").performScrollToIndex(0)
        press(Key.DirectionUp)
        menuOption(34).assertIsFocused().assertIsDisplayed()
        press(Key.DirectionCenter)
        row(0).assertIsFocused()
        composeRule.runOnIdle { assertEquals(listOf("dismiss", "action:Action 34"), events) }
    }

    @Test
    fun dialogCancelDismissesWithoutInvokingOptionAndRestoresRow() {
        render()
        press(Key.DirectionDown)
        showMenu(listOf("Open", "App Info"))
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        composeRule.onNodeWithTag("app-list-menu-cancel")
            .assertIsFocused()
            .assertTextContains(context.getString(R.string.dialog_cancel))
        press(Key.DirectionCenter)
        composeRule.onNodeWithTag("app-list-menu").assertDoesNotExist()
        row(1).assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(listOf("dismiss"), events)
            assertTrue(clicks.isEmpty())
            assertTrue(longClicks.isEmpty())
        }
    }

    @Test
    fun emptyDialogHasFocusableBoundedDismissButton() {
        render(items = emptyList())
        showMenu(emptyList())
        composeRule.onNodeWithText(context.getString(R.string.dialog_cancel)).assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        composeRule.onNodeWithTag("app-list-menu-cancel").assertIsFocused()
        press(Key.DirectionCenter)
        primary().assertIsFocused()
        composeRule.runOnIdle { assertEquals(listOf("dismiss"), events) }
    }

    @Test
    fun dialogDismissAfterRefreshRestoresExistingRowOrHeader() {
        render()
        press(Key.DirectionDown)
        showMenu(listOf("Open"))
        update { it.copy(items = emptyList(), isLoading = true) }
        menuOption(0).assertIsFocused()
        press(Key.Back)
        composeRule.onNodeWithTag("app-list-menu").assertDoesNotExist()
        primary().assertIsFocused()
        press(Key.DirectionRight)
        secondary().assertIsFocused()
        composeRule.runOnIdle { assertEquals(listOf("dismiss"), events) }
    }

    private fun render(items: List<AppListItem> = listOf(app(0), app(1)), isLoading: Boolean = false) {
        inputs.value = inputs.value.copy(items = items, isLoading = isLoading)
        composeRule.setContent {
            val current = inputs.value
            LauncherTheme {
                AppListScreen(
                    items = current.items,
                    icons = current.icons,
                    isLoading = current.isLoading,
                    countdownText = current.countdownText,
                    isMultiSelectMode = current.isMultiSelectMode,
                    selectedPackages = current.selectedPackages,
                    autoOpenPackage = current.autoOpenPackage,
                    headerFocusGeneration = current.headerFocusGeneration,
                    dialog = current.dialog,
                    onAppClick = { clicks.add(it.packageName) },
                    onAppLongClick = { longClicks.add(it.packageName) },
                    onIconNeeded = { iconRequests.add(it.packageName) },
                    onSettingsClick = { events.add("settings") },
                    onHdmiClick = { events.add("hdmi") },
                    onBatchClick = { events.add("batch") },
                    onCancelSelection = { events.add("cancel") },
                    onDialogDismiss = {
                        events.add("dismiss")
                        inputs.value = inputs.value.copy(dialog = null)
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun showMenu(titles: List<String>) {
        update {
            it.copy(dialog = AppListDialog("App options", titles.map { title ->
                AppListMenuOption(title) {
                    assertEquals(null, inputs.value.dialog)
                    events.add("action:$title")
                }
            }))
        }
    }

    private fun update(transform: (Inputs) -> Inputs) {
        composeRule.runOnIdle { inputs.value = transform(inputs.value) }
        composeRule.waitForIdle()
    }

    private fun press(key: Key) {
        val target = if (inputs.value.dialog != null) composeRule.onNodeWithTag("app-list-menu")
        else composeRule.onRoot()
        target.performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun requestPrimaryFocus() {
        primary().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        primary().assertIsFocused()
    }

    private fun row(index: Int) = composeRule.onNodeWithTag("app-list-row-$index")
    private fun primary() = composeRule.onNodeWithTag("app-list-primary")
    private fun secondary() = composeRule.onNodeWithTag("app-list-secondary")
    private fun menuOption(index: Int) = composeRule.onNodeWithTag("app-list-menu-option-$index")

    private fun assertNoAppActions() {
        composeRule.runOnIdle {
            assertTrue(clicks.isEmpty())
            assertTrue(longClicks.isEmpty())
        }
    }

    private fun app(index: Int, enabled: Boolean = true): AppListItem.App {
        val packageName = "test.app$index"
        return AppListItem.App(
            label = "Application $index",
            packageName = packageName,
            componentName = ComponentName(packageName, "$packageName.MainActivity"),
            isLeanback = true,
            appInfo = ApplicationInfo().apply { this.packageName = packageName; this.enabled = enabled },
            isSystem = false,
            isDisableable = true,
        )
    }

    private data class Inputs(
        val items: List<AppListItem> = emptyList(),
        val icons: Map<String, Bitmap> = emptyMap(),
        val isLoading: Boolean = false,
        val countdownText: String? = null,
        val isMultiSelectMode: Boolean = false,
        val selectedPackages: Set<String> = emptySet(),
        val autoOpenPackage: String = "",
        val headerFocusGeneration: Int = 0,
        val dialog: AppListDialog? = null,
    )
}
