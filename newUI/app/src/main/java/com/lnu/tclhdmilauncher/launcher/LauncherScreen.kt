package com.lnu.tclhdmilauncher.launcher


import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.lnu.tclhdmilauncher.R

@Composable
internal fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB8C7FF),
            onPrimary = Color(0xFF172D60),
            primaryContainer = Color(0xFF304578),
            onPrimaryContainer = Color(0xFFDAE1FF),
            secondary = Color(0xFFC2C7D8),
            background = Color.Black,
            onBackground = Color(0xFFE6E1E8),
            surface = Color(0xFF111216),
            onSurface = Color(0xFFE6E1E8),
            surfaceVariant = Color(0xFF24252C),
            onSurfaceVariant = Color(0xFFC5C3CC),
        ),
        content = content,
    )
}

@Composable
internal fun LauncherScreen(
    defaultPort: Int,
    countdownText: String,
    focusRequestGeneration: Int,
    onPortClick: (Int) -> Unit,
    onPortLongClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onAppsClick: () -> Unit,
    onFocusedPortChanged: (Int?) -> Unit,
    focusRequestPort: Int = defaultPort,
    countdownProgress: Float? = null,
) {

    val portFocus = remember { List(3) { FocusRequester() } }
    val settingsFocus = remember { FocusRequester() }
    val appsFocus = remember { FocusRequester() }
    var focusedPort by remember { mutableIntStateOf(defaultPort.coerceIn(1, 3)) }
    // Not snapshot state: it changes on every focus move and is only needed when a focus
    // search runs, so reading it in composition would recompose the whole screen per D-pad step.
    val lastFocusedPort = remember(defaultPort) { intArrayOf(defaultPort.coerceIn(1, 3)) }

    // Text/default-port updates must not steal focus from the user's selection.
    LaunchedEffect(focusRequestGeneration) {
        val port = focusRequestPort.coerceIn(1, 3)
        lastFocusedPort[0] = port
        portFocus[port - 1].requestFocus()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = Color.Black,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onSettingsClick,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier
                        .focusRequester(settingsFocus)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            down = portFocus[lastFocusedPort[0] - 1]
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
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {

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
                        countdownProgress?.let { progress ->
                            ActivityCountdownProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
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
                            colors = CardDefaults.colors(
                                containerColor = Color(0xFF17181D),
                                contentColor = Color(0xFFD9DCE5),
                                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                pressedContainerColor = MaterialTheme.colorScheme.primary,
                                pressedContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            border = CardDefaults.border(
                                focusedBorder = Border.None,
                                pressedBorder = Border.None,
                            ),
                            scale = CardDefaults.scale(focusedScale = 1.08f, pressedScale = 0.98f),
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
                                        focusedPort = port
                                        lastFocusedPort[0] = port
                                        onFocusedPortChanged(port)
                                    } else if (focusedPort == port) {
                                        focusedPort = 0
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
                                if (port == defaultPort) {
                                    val badgeColor = if (focusedPort == port) Color.White else Color(0xFF8FDBFF)
                                    Row(
                                        modifier = Modifier
                                            .background(
                                                color = if (focusedPort == port) {
                                                    Color.White.copy(alpha = 0.16f)
                                                } else {
                                                    Color(0xFF10314B)
                                                },
                                                shape = RoundedCornerShape(50),
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(badgeColor, CircleShape),
                                        )
                                        Text(
                                            text = stringResource(R.string.card_default_badge),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = badgeColor,
                                        )
                                    }
                                }
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
                            up = portFocus[lastFocusedPort[0] - 1]
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
