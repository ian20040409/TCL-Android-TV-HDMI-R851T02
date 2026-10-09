package com.lnu.tclhdmilauncher.applist

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.lnu.tclhdmilauncher.R
import kotlin.reflect.KProperty
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal sealed interface AppListItem {
    data class Section(val title: String) : AppListItem
    data class App(
        val label: String,
        val packageName: String,
        val componentName: ComponentName,
        val isLeanback: Boolean,
        val appInfo: ApplicationInfo,
        val isSystem: Boolean,
        val isDisableable: Boolean,
    ) : AppListItem
}

internal data class AppListMenuOption(val title: String, val action: () -> Unit)
internal data class AppListDialog(val title: String, val options: List<AppListMenuOption>)

@Composable
internal fun AppListScreen(
    items: List<AppListItem>,
    icons: Map<String, Bitmap>,
    isLoading: Boolean,
    countdownText: String?,
    isMultiSelectMode: Boolean,
    selectedPackages: Set<String>,
    autoOpenPackage: String,
    headerFocusGeneration: Int,
    dialog: AppListDialog?,
    onAppClick: (AppListItem.App) -> Unit,
    onAppLongClick: (AppListItem.App) -> Unit,
    onIconNeeded: (AppListItem.App) -> Unit,
    onSettingsClick: () -> Unit,
    onHdmiClick: () -> Unit,
    onBatchClick: () -> Unit,
    onCancelSelection: () -> Unit,
    onDialogDismiss: () -> Unit,
    countdownProgress: Float? = null,
) {
    // Occurrences distinguish a recent shortcut from the same component in its category.
    val keys = remember(items) {
        val occurrences = mutableMapOf<String, Int>()
        items.map { item ->
            val identity = when (item) {
                is AppListItem.Section -> "section:${item.title}"
                is AppListItem.App -> "app:${item.componentName.flattenToString()}"
            }
            val occurrence = occurrences.getOrDefault(identity, 0)
            occurrences[identity] = occurrence + 1
            "$identity:$occurrence"
        }
    }
    val appIndices = remember(items) { items.indices.filter { items[it] is AppListItem.App } }
    val rowFocus = remember(keys) { keys.associateWith { FocusRequester() } }
    val primaryFocus = remember { FocusRequester() }
    val secondaryFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    // Plain (non-snapshot) holders: written on every focus move, so they must not
    // trigger recomposition of the whole list.
    val lastRowKeyRef = remember { PlainRef<String?>(null) }
    val focusTargetRef = remember { PlainRef<String?>(null) }
    var lastRowKey: String? by lastRowKeyRef
    var focusTarget: String? by focusTargetRef
    var initialFocusHandled by remember { mutableStateOf(false) }
    var headerUsedDuringLoad by remember { mutableStateOf(false) }
    var loadingWasActive by remember { mutableStateOf(false) }
    var loadingRestoreTarget by remember { mutableStateOf<String?>(null) }
    // Capture before removing the focused lazy row can transfer focus to the header.
    val loadingEntryTarget = remember(isLoading) { focusTarget }
    var handledGeneration by remember { mutableStateOf(headerFocusGeneration) }
    var dialogWasOpen by remember { mutableStateOf(false) }
    val currentDialog by rememberUpdatedState(dialog)

    suspend fun focusRow(index: Int) {
        focusLazyItem(listState, index, keys[index], rowFocus.getValue(keys[index]))
    }

    fun returnToList() {
        if (isLoading) return
        val index = keys.indexOf(lastRowKey).takeIf { it in appIndices } ?: appIndices.firstOrNull()
        navigationJob?.cancel()
        if (index != null) {
            navigationJob = scope.launch { if (currentDialog == null) focusRow(index) }
        }
    }

    suspend fun restoreFocus(target: String?) {
        when (target) {
            "primary" -> primaryFocus.requestFocus()
            "secondary" -> secondaryFocus.requestFocus()
            else -> {
                val index = keys.indexOf(target).takeIf { it in appIndices } ?: appIndices.firstOrNull()
                if (index != null) focusRow(index) else primaryFocus.requestFocus()
            }
        }
    }

    LaunchedEffect(keys, isLoading, headerFocusGeneration, dialog != null) {
        navigationJob?.cancel()
        if (isLoading && !loadingWasActive) {
            loadingWasActive = true
            loadingRestoreTarget = loadingEntryTarget
            headerUsedDuringLoad = false
        }
        if (dialog != null) {
            dialogWasOpen = true
            return@LaunchedEffect
        }
        if (handledGeneration != headerFocusGeneration) {
            handledGeneration = headerFocusGeneration
            initialFocusHandled = true
            dialogWasOpen = false
            if (isLoading) headerUsedDuringLoad = true else loadingWasActive = false
            primaryFocus.requestFocus()
        } else if (isLoading) {
            dialogWasOpen = false
            if (focusTarget == "secondary") secondaryFocus.requestFocus() else primaryFocus.requestFocus()
        } else if (loadingWasActive) {
            loadingWasActive = false
            dialogWasOpen = false
            val restoreTarget = if (initialFocusHandled) loadingRestoreTarget else null
            if (appIndices.isNotEmpty()) initialFocusHandled = true
            if (headerUsedDuringLoad) {
                if (focusTarget == "secondary") secondaryFocus.requestFocus() else primaryFocus.requestFocus()
            } else restoreFocus(restoreTarget)
        } else if (dialogWasOpen) {
            dialogWasOpen = false
            restoreFocus(focusTarget)
        } else if (!initialFocusHandled) {
            if (appIndices.isNotEmpty()) {
                initialFocusHandled = true
                if (!headerUsedDuringLoad) focusRow(appIndices.first())
            } else if (focusTarget == null) {
                primaryFocus.requestFocus()
            }
        } else if (focusTarget != "primary" && focusTarget != "secondary" && focusTarget !in keys) {
            restoreFocus(focusTarget)
        }
    }

    fun headerModifier(primary: Boolean): Modifier = Modifier
        .testTag(if (primary) "app-list-primary" else "app-list-secondary")
        .focusRequester(if (primary) primaryFocus else secondaryFocus)
        .focusProperties {
            up = FocusRequester.Cancel
            left = if (primary) FocusRequester.Cancel else primaryFocus
            right = if (primary) secondaryFocus else FocusRequester.Cancel
            down = FocusRequester.Cancel
        }
        .onFocusChanged {
            if (it.isFocused) {
                val target = if (primary) "primary" else "secondary"
                focusTarget = target
            }
        }
        .onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && isLoading) headerUsedDuringLoad = true
            if (it.key == Key.DirectionDown) {
                if (it.type == KeyEventType.KeyDown && !isLoading) returnToList()
                true
            } else false
        }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppListHeader(
                isMultiSelectMode = isMultiSelectMode,
                selectedCount = selectedPackages.size,
                primaryModifier = headerModifier(primary = true),
                secondaryModifier = headerModifier(primary = false),
                onPrimaryClick = {
                    if (isLoading) headerUsedDuringLoad = true
                    if (isMultiSelectMode) onBatchClick() else onSettingsClick()
                },
                onSecondaryClick = {
                    if (isLoading) headerUsedDuringLoad = true
                    if (isMultiSelectMode) onCancelSelection() else onHdmiClick()
                },
            )
            if (countdownText != null) {
                AppListCountdownStatus(countdownText, countdownProgress)
            }
            if (isLoading) Text(stringResource(R.string.app_list_loading))
            if (!isLoading && appIndices.isEmpty()) Text(stringResource(R.string.app_list_empty))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("app-list"),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(if (isLoading) emptyList() else items, key = { index, _ -> keys[index] }) { index, item ->
                    when (item) {
                        is AppListItem.Section -> AppListSectionHeader(item.title)
                        is AppListItem.App -> {
                            val bitmap = icons[item.packageName]
                            val iconCallback by rememberUpdatedState(onIconNeeded)
                            // Lazy composition bounds requests to visible/near-visible rows. A cache
                            // eviction changes bitmap back to null and must trigger another request.
                            LaunchedEffect(item, bitmap) {
                                snapshotFlow {
                                    listState.layoutInfo.visibleItemsInfo.any { it.key == keys[index] }
                                }.first { it }
                                if (bitmap == null) iconCallback(item)
                            }
                            AppListEntryRow(
                                item = item,
                                icon = bitmap,
                                isSelected = isMultiSelectMode && item.packageName in selectedPackages,
                                isAutoOpen = item.packageName == autoOpenPackage,
                                isMultiSelectMode = isMultiSelectMode,
                                onClick = { onAppClick(item) },
                                onLongClick = { onAppLongClick(item) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("app-list-row-$index")
                                    .focusRequester(rowFocus.getValue(keys[index]))
                                    .focusProperties {
                                        left = FocusRequester.Cancel
                                        right = FocusRequester.Cancel
                                        up = FocusRequester.Cancel
                                        down = FocusRequester.Cancel
                                    }
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            lastRowKey = keys[index]
                                            focusTarget = keys[index]
                                        }
                                    }
                                    .onPreviewKeyEvent {
                                        when (it.key) {
                                            Key.DirectionUp, Key.DirectionDown -> {
                                                if (it.type == KeyEventType.KeyDown) {
                                                    navigationJob?.cancel()
                                                    val position = appIndices.indexOf(index)
                                                    val next = position + if (it.key == Key.DirectionUp) -1 else 1
                                                    if (next < 0) primaryFocus.requestFocus()
                                                    else if (next < appIndices.size) {
                                                        navigationJob = scope.launch { focusRow(appIndices[next]) }
                                                    }
                                                }
                                                true
                                            }
                                            Key.DirectionLeft, Key.DirectionRight -> true
                                            else -> false
                                        }
                                    },
                            )
                        }
                    }
                }
            }
            if (!isMultiSelectMode) Text(stringResource(R.string.app_list_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (dialog != null) AppListMenu(dialog, onDialogDismiss)
}

private class PlainRef<T>(var value: T) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
        value = newValue
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.isConfirmKey() =
    key == Key.Enter || key == Key.DirectionCenter || key == Key.NumPadEnter

// FocusRequester cannot target an uncomposed lazy item. Wait for its layout before
// requesting focus; the same path handles both D-pad scrolling and dialog restoration.
private suspend fun focusLazyItem(state: LazyListState, index: Int, key: Any, requester: FocusRequester) {
    // Fast path: an already laid-out row can take focus immediately. Waiting a frame on every
    // D-pad step made held Up/Down feel laggy; focus itself brings the row into view.
    if (state.layoutInfo.visibleItemsInfo.any { it.key == key }) {
        if (runCatching { requester.requestFocus() }.isSuccess) return
    }
    state.scrollToItem(index)
    snapshotFlow { state.layoutInfo.visibleItemsInfo.any { it.key == key } }.first { it }
    withFrameNanos { }
    requester.requestFocus()
}

@Composable
private fun AppListMenu(dialog: AppListDialog, onDismiss: () -> Unit) {
    val focus = remember(dialog.options.size) { List(dialog.options.size) { FocusRequester() } }
    val closeFocus = remember { FocusRequester() }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    // The dialog opens while the user is still holding OK (long press). Swallow that
    // press's repeats and release so they cannot click the auto-focused first option.
    // A fresh press (repeatCount == 0) arms the dialog.
    val armedRef = remember { PlainRef(false) }
    var armed: Boolean by armedRef
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(dialog) {
            if (focus.isEmpty()) {
                withFrameNanos { }
                closeFocus.requestFocus()
            } else focusLazyItem(state, 0, 0, focus.first())
        }
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .onPreviewKeyEvent {
                    if (it.key == Key.Back) {
                        if (it.type == KeyEventType.KeyUp) onDismiss()
                        true
                    } else if (it.isConfirmKey() && !armed) {
                        if (it.type == KeyEventType.KeyUp) armed = true
                        else if (it.nativeKeyEvent.repeatCount == 0) armed = true
                        !armed || it.type == KeyEventType.KeyUp
                    } else false
                },
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(dialog.title, style = MaterialTheme.typography.headlineSmall)
                LazyColumn(
                    state = state,
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp).testTag("app-list-menu"),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(dialog.options, key = { index, _ -> index }) { index, option ->
                        Button(
                            onClick = { onDismiss(); option.action() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app-list-menu-option-$index")
                                .focusRequester(focus[index])
                                .focusProperties {
                                    left = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                }
                                .onPreviewKeyEvent {
                                    when (it.key) {
                                        Key.DirectionUp, Key.DirectionDown -> {
                                            if (it.type == KeyEventType.KeyDown) {
                                                val next = index + if (it.key == Key.DirectionUp) -1 else 1
                                                navigationJob?.cancel()
                                                if (next == focus.size) closeFocus.requestFocus()
                                                else if (next in focus.indices) navigationJob = scope.launch {
                                                    focusLazyItem(state, next, next, focus[next])
                                                }
                                            }
                                            true
                                        }
                                        Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                },
                        ) { Text(option.title) }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .testTag("app-list-menu-cancel")
                        .focusRequester(closeFocus)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                        .onPreviewKeyEvent {
                            when (it.key) {
                                Key.DirectionUp -> {
                                    if (it.type == KeyEventType.KeyDown && focus.isNotEmpty()) {
                                        navigationJob?.cancel()
                                        navigationJob = scope.launch {
                                            val last = focus.lastIndex
                                            focusLazyItem(state, last, last, focus[last])
                                        }
                                    }
                                    true
                                }
                                Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
                                else -> false
                            }
                        },
                ) { Text(stringResource(R.string.dialog_cancel)) }
            }
        }
    }
}
