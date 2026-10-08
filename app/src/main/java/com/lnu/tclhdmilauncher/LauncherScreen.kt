package com.lnu.tclhdmilauncher

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme

@Composable
internal fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@Composable
internal fun LauncherScreen(
    brandTitle: String,
    defaultPort: Int,
    countdownText: String,
    focusRequestGeneration: Int,
    onPortClick: (Int) -> Unit,
    onPortLongClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onAppsClick: () -> Unit,
    onFocusedPortChanged: (Int?) -> Unit,
    focusRequestPort: Int = defaultPort,
) {
    val portFocus = remember { List(3) { FocusRequester() } }
    val settingsFocus = remember { FocusRequester() }
    val appsFocus = remember { FocusRequester() }
    var lastFocusedPort by remember(defaultPort) { mutableIntStateOf(defaultPort.coerceIn(1, 3)) }
    val returnFocus = portFocus[lastFocusedPort - 1]

    // Text/default-port updates must not steal focus from the user's selection.
    LaunchedEffect(focusRequestGeneration) {
        val port = focusRequestPort.coerceIn(1, 3)
        lastFocusedPort = port
        portFocus[port - 1].requestFocus()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = brandTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onSettingsClick,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier
                        .focusRequester(settingsFocus)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            down = returnFocus
                        }
                        .onFocusChanged { if (it.isFocused) onFocusedPortChanged(null) },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.settings_48px),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.btn_settings))
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.main_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.timer_48px),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = countdownText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .weight(1f)
                                // Retain the mouse/touch settings shortcut without adding a D-pad stop.
                                .pointerInput(onSettingsClick) { detectTapGestures { onSettingsClick() } }
                                .semantics {
                                    onClick { onSettingsClick(); true }
                                },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    for (port in 1..3) {
                        Card(
                            onClick = { onPortClick(port) },
                            onLongClick = { onPortLongClick(port) },
                            modifier = Modifier
                                .weight(1f)
                                .height(176.dp)
                                .focusRequester(portFocus[port - 1])
                                .focusProperties {
                                    up = settingsFocus
                                    down = appsFocus
                                    left = if (port > 1) portFocus[port - 2] else FocusRequester.Cancel
                                    right = if (port < 3) portFocus[port] else FocusRequester.Cancel
                                }
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        lastFocusedPort = port
                                        onFocusedPortChanged(port)
                                    }
                                },
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.settings_input_hdmi_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                )
                                Text(
                                    text = "HDMI $port",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = if (port == defaultPort) {
                                        stringResource(R.string.card_default_badge)
                                    } else {
                                        ""
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onAppsClick,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier
                        .focusRequester(appsFocus)
                        .focusProperties {
                            up = returnFocus
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                        .onFocusChanged { if (it.isFocused) onFocusedPortChanged(null) },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.apps_48px),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.btn_apps))
                }
            }

            Text(
                text = stringResource(R.string.bottom_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun LauncherScreenPreview() {
    LauncherTheme {
        LauncherScreen(
            brandTitle = "TCL HDMI Launcher",
            defaultPort = 3,
            countdownText = stringResource(R.string.countdown_active, 3, 3),
            focusRequestGeneration = 0,
            onPortClick = {},
            onPortLongClick = {},
            onSettingsClick = {},
            onAppsClick = {},
            onFocusedPortChanged = {},
        )
    }
}
