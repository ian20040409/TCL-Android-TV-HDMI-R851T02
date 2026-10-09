package com.lnu.tclhdmilauncher.applist

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Checkbox
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.lnu.tclhdmilauncher.R
import com.lnu.tclhdmilauncher.launcher.ActivityCountdownProgressIndicator

@Composable
internal fun AppListHeader(
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    primaryModifier: Modifier,
    secondaryModifier: Modifier,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (isMultiSelectMode) {
                stringResource(R.string.title_selected_count, selectedCount)
            } else stringResource(R.string.app_list_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onPrimaryClick, modifier = primaryModifier) {
            Text(stringResource(if (isMultiSelectMode) R.string.btn_batch_action else R.string.btn_settings))
        }
        OutlinedButton(onClick = onSecondaryClick, modifier = secondaryModifier) {
            Text(stringResource(if (isMultiSelectMode) R.string.btn_cancel_selection else R.string.btn_hdmi_selector))
        }
    }
}

@Composable
internal fun AppListCountdownStatus(countdownText: String, countdownProgress: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(countdownText, style = MaterialTheme.typography.bodyLarge)
        countdownProgress?.let { progress ->
            ActivityCountdownProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun AppListSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
internal fun AppListEntryRow(
    item: AppListItem.App,
    icon: Bitmap?,
    isSelected: Boolean,
    isAutoOpen: Boolean,
    isMultiSelectMode: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val imageBitmap = remember(icon) { icon?.asImageBitmap() }

    ListItem(
        selected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = {
            Text(
                if (item.appInfo.enabled) item.label
                else stringResource(R.string.app_disabled_suffix, item.label),
            )
        },
        supportingContent = {
            Text(
                item.packageName + if (item.appInfo.enabled) ""
                else stringResource(R.string.app_frozen_suffix),
            )
        },
        leadingContent = {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.apps_48px),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isAutoOpen) Text(stringResource(R.string.badge_auto_open))
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                }
            }
        },
        modifier = modifier,
    )
}
