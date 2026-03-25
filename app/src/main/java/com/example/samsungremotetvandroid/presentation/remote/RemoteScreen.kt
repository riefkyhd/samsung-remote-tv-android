package com.example.samsungremotetvandroid.presentation.remote

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsungremotetvandroid.R
import com.example.samsungremotetvandroid.core.design.SamsungRadii
import com.example.samsungremotetvandroid.core.design.SamsungSpacing
import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.model.SamsungTv

@Composable
fun RemoteScreen(
    modifier: Modifier = Modifier,
    onOpenDiscovery: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: RemoteViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val availableTvs by viewModel.availableTvs.collectAsStateWithLifecycle()
    val selectedTvId by viewModel.selectedTvId.collectAsStateWithLifecycle()
    val diagnosticsSummary by viewModel.diagnosticsSummary.collectAsStateWithLifecycle()
    val diagnosticsEvents by viewModel.diagnosticsEvents.collectAsStateWithLifecycle()
    val lastErrorSummary by viewModel.lastErrorSummary.collectAsStateWithLifecycle()
    val pendingPin by viewModel.pendingPin.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val connectInFlight by viewModel.connectInFlight.collectAsStateWithLifecycle()

    val selectedTv = availableTvs.firstOrNull { it.id == selectedTvId }
    val controlsEnabled = connectionState is ConnectionState.Ready && !connectInFlight
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.releaseAllHeldKeys()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SamsungSpacing.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingMd)
        ) {
            RemoteTopBar(
                selectedTv = selectedTv,
                onOpenDiscovery = onOpenDiscovery,
                onOpenSettings = onOpenSettings
            )

            RemoteStatusCard(
                connectionState = connectionState,
                selectedTv = selectedTv,
                controlsEnabled = controlsEnabled
            )

            TargetSelectorCard(
                availableTvs = availableTvs,
                selectedTvId = selectedTvId,
                connectInFlight = connectInFlight,
                onSelectTv = viewModel::selectTv,
                onConnect = viewModel::connectSelectedTv,
                onDisconnect = viewModel::disconnect
            )

            ControlsCard(
                enabled = controlsEnabled,
                onTap = { key ->
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.sendRemoteKey(key)
                },
                onHoldStart = { key ->
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.startKeyHold(key)
                },
                onHoldStop = viewModel::stopKeyHold,
                onQuickLaunchUnavailable = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.showQuickLaunchUnavailable()
                }
            )

            if (isDebuggable) {
                DiagnosticsCard(
                    diagnosticsSummary = diagnosticsSummary,
                    lastErrorSummary = lastErrorSummary,
                    recentEvents = diagnosticsEvents.takeLast(8)
                )
            }

            Spacer(modifier = Modifier.size(SamsungSpacing.SpacingMd))
        }
    }

    if (connectionState is ConnectionState.PinRequired) {
        PinEntryDialog(
            pendingPin = pendingPin,
            busy = connectInFlight,
            onPinChanged = viewModel::updatePendingPin,
            onSubmitPin = viewModel::submitEncryptedPin,
            onCancelPairing = viewModel::cancelEncryptedPairing
        )
    }

    userMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUserMessage,
            title = {
                Text(text = stringResource(id = R.string.common_error))
            },
            text = {
                Text(text = message)
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissUserMessage) {
                    Text(text = stringResource(id = R.string.common_ok))
                }
            }
        )
    }
}

@Composable
private fun RemoteTopBar(
    selectedTv: SamsungTv?,
    onOpenDiscovery: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenDiscovery) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(id = R.string.common_back)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = selectedTv?.displayName ?: stringResource(id = R.string.remote_title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = selectedTv?.ipAddress ?: stringResource(id = R.string.remote_header_no_target),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(id = R.string.nav_settings)
            )
        }
    }
}

@Composable
private fun RemoteStatusCard(
    connectionState: ConnectionState,
    selectedTv: SamsungTv?,
    controlsEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        )
    ) {
        Column(
            modifier = Modifier.padding(SamsungSpacing.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            Text(
                text = connectionStateLabel(connectionState),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = selectedTv?.let {
                    stringResource(id = R.string.remote_header_target, it.displayName)
                } ?: stringResource(id = R.string.remote_header_no_target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (controlsEnabled) {
                    stringResource(id = R.string.remote_controls_ready_hint)
                } else {
                    stringResource(id = R.string.remote_controls_locked_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PinEntryDialog(
    pendingPin: String,
    busy: Boolean,
    onPinChanged: (String) -> Unit,
    onSubmitPin: () -> Unit,
    onCancelPairing: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = stringResource(id = R.string.remote_pin_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
            ) {
                Text(
                    text = stringResource(id = R.string.remote_pin_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pendingPin,
                    onValueChange = onPinChanged,
                    enabled = !busy,
                    label = { Text(text = stringResource(id = R.string.remote_pin_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmitPin,
                enabled = !busy
            ) {
                Text(
                    text = if (busy) {
                        stringResource(id = R.string.remote_connecting_button)
                    } else {
                        stringResource(id = R.string.remote_pin_submit)
                    }
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancelPairing,
                enabled = !busy
            ) {
                Text(text = stringResource(id = R.string.remote_pin_cancel))
            }
        }
    )
}

@Composable
private fun TargetSelectorCard(
    availableTvs: List<SamsungTv>,
    selectedTvId: String?,
    connectInFlight: Boolean,
    onSelectTv: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(SamsungSpacing.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            Text(
                text = stringResource(id = R.string.remote_target_tv_title),
                style = MaterialTheme.typography.titleMedium
            )

            if (availableTvs.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.remote_target_tv_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                availableTvs.forEachIndexed { index, tv ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    TargetTvRow(
                        tv = tv,
                        selected = tv.id == selectedTvId,
                        onSelect = { onSelectTv(tv.id) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
            ) {
                Button(
                    onClick = onConnect,
                    enabled = !connectInFlight,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (connectInFlight) {
                            stringResource(id = R.string.remote_connecting_button)
                        } else {
                            stringResource(id = R.string.remote_connect_button)
                        }
                    )
                }
                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = !connectInFlight,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(id = R.string.remote_disconnect_button))
                }
            }
        }
    }
}

@Composable
private fun ControlsCard(
    enabled: Boolean,
    onTap: (RemoteKey) -> Unit,
    onHoldStart: (RemoteKey) -> Unit,
    onHoldStop: (RemoteKey) -> Unit,
    onQuickLaunchUnavailable: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
        )
    ) {
        Column(
            modifier = Modifier.padding(SamsungSpacing.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.remote_controls_title),
                style = MaterialTheme.typography.titleMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)) {
                IconControlButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Home,
                            contentDescription = null
                        )
                    },
                    onClick = { onTap(RemoteKey.HOME) },
                    enabled = enabled
                )
                IconControlButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = null
                        )
                    },
                    onClick = { onTap(RemoteKey.MENU) },
                    enabled = enabled
                )
                IconControlButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.PowerSettingsNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = { onTap(RemoteKey.POWER) },
                    enabled = enabled
                )
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(SamsungRadii.RadiusLg))
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SamsungSpacing.SpacingMd),
                    verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HoldIconControlButton(
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = null
                            )
                        },
                        enabled = enabled,
                        onPressStart = { onHoldStart(RemoteKey.D_PAD_UP) },
                        onPressEnd = { onHoldStop(RemoteKey.D_PAD_UP) }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)) {
                        HoldIconControlButton(
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowLeft,
                                    contentDescription = null
                                )
                            },
                            enabled = enabled,
                            onPressStart = { onHoldStart(RemoteKey.D_PAD_LEFT) },
                            onPressEnd = { onHoldStop(RemoteKey.D_PAD_LEFT) }
                        )
                        IconControlButton(
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null
                                )
                            },
                            onClick = { onTap(RemoteKey.OK) },
                            enabled = enabled,
                            emphasized = true
                        )
                        HoldIconControlButton(
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            },
                            enabled = enabled,
                            onPressStart = { onHoldStart(RemoteKey.D_PAD_RIGHT) },
                            onPressEnd = { onHoldStop(RemoteKey.D_PAD_RIGHT) }
                        )
                    }

                    HoldIconControlButton(
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null
                            )
                        },
                        enabled = enabled,
                        onPressStart = { onHoldStart(RemoteKey.D_PAD_DOWN) },
                        onPressEnd = { onHoldStop(RemoteKey.D_PAD_DOWN) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)) {
                IconControlButton(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null
                        )
                    },
                    onClick = { onTap(RemoteKey.BACK) },
                    enabled = enabled
                )
                IconControlButton(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                            contentDescription = null
                        )
                    },
                    onClick = { onTap(RemoteKey.EXIT) },
                    enabled = enabled
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)) {
                HoldIconControlButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.VolumeUp,
                            contentDescription = null
                        )
                    },
                    enabled = enabled,
                    onPressStart = { onHoldStart(RemoteKey.VOLUME_UP) },
                    onPressEnd = { onHoldStop(RemoteKey.VOLUME_UP) }
                )
                HoldIconControlButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.VolumeDown,
                            contentDescription = null
                        )
                    },
                    enabled = enabled,
                    onPressStart = { onHoldStart(RemoteKey.VOLUME_DOWN) },
                    onPressEnd = { onHoldStop(RemoteKey.VOLUME_DOWN) }
                )
                IconControlButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.VolumeOff,
                            contentDescription = null
                        )
                    },
                    onClick = { onTap(RemoteKey.MUTE) },
                    enabled = enabled
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)) {
                IconControlButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.SkipPrevious,
                            contentDescription = null
                        )
                    },
                    onClick = { onTap(RemoteKey.MEDIA_PLAY_PAUSE) },
                    enabled = enabled
                )
                IconControlButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Pause,
                            contentDescription = null
                        )
                    },
                    onClick = { onTap(RemoteKey.MEDIA_PLAY_PAUSE) },
                    enabled = enabled
                )
                IconControlButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.SkipNext,
                            contentDescription = null
                        )
                    },
                    onClick = { onTap(RemoteKey.MEDIA_PLAY_PAUSE) },
                    enabled = enabled
                )
            }

            Text(
                text = stringResource(id = R.string.remote_power_best_effort_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onQuickLaunchUnavailable,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            ) {
                Text(text = stringResource(id = R.string.remote_quick_launch_unavailable_label))
            }

            Text(
                text = stringResource(id = R.string.remote_installed_app_unsupported_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(
    diagnosticsSummary: String,
    lastErrorSummary: String?,
    recentEvents: List<String>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(SamsungSpacing.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            Text(
                text = stringResource(id = R.string.remote_diagnostics_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = diagnosticsSummary,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = stringResource(
                    id = R.string.remote_diagnostics_last_error,
                    lastErrorSummary ?: stringResource(id = R.string.remote_diagnostics_none)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (recentEvents.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.remote_diagnostics_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recentEvents.forEach { event ->
                    Text(
                        text = event,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetTvRow(
    tv: SamsungTv,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SamsungSpacing.SpacingXs),
        horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingXs)
        ) {
            Text(
                text = tv.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${tv.ipAddress} • ${tv.protocol.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Button(
            onClick = onSelect,
            shape = RoundedCornerShape(SamsungRadii.RadiusLg),
            colors = if (selected) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(
                text = if (selected) {
                    stringResource(id = R.string.remote_target_tv_selected)
                } else {
                    stringResource(id = R.string.remote_target_tv_use)
                }
            )
        }
    }
}

@Composable
private fun IconControlButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean,
    emphasized: Boolean = false
) {
    val container = if (emphasized) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    }
    val content = if (emphasized) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape),
        color = container,
        contentColor = content,
        shadowElevation = 0.dp
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            icon()
        }
    }
}

@Composable
private fun HoldIconControlButton(
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .pointerInput(onPressStart, onPressEnd, enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) {
                            return@detectTapGestures
                        }
                        onPressStart()
                        try {
                            tryAwaitRelease()
                        } finally {
                            onPressEnd()
                        }
                    }
                )
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

private fun connectionStateLabel(state: ConnectionState): String {
    return when (state) {
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Connecting -> "Connecting"
        is ConnectionState.Pairing -> "Pairing (${state.countdownSeconds}s)"
        is ConnectionState.PinRequired -> "Pin Required (${state.countdownSeconds}s)"
        is ConnectionState.ConnectedNotReady -> "Connected (Not Ready)"
        is ConnectionState.Ready -> "Ready"
        is ConnectionState.Error -> "Error: ${state.message}"
    }
}
