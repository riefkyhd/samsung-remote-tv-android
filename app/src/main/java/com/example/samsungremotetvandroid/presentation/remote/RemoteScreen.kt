package com.example.samsungremotetvandroid.presentation.remote

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsungremotetvandroid.R
import com.example.samsungremotetvandroid.core.design.SamsungSpacing
import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.presentation.common.PrimaryActionButton
import com.example.samsungremotetvandroid.presentation.common.SecondaryActionButton

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val diagnosticsSummary by viewModel.diagnosticsSummary.collectAsStateWithLifecycle()
    val diagnosticsEvents by viewModel.diagnosticsEvents.collectAsStateWithLifecycle()
    val lastErrorSummary by viewModel.lastErrorSummary.collectAsStateWithLifecycle()
    val pendingPin by viewModel.pendingPin.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val emitControlHaptic = {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.releaseAllHeldKeys()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SamsungSpacing.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingMd)
    ) {
        Text(text = "Remote", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(SamsungSpacing.SpacingMd),
                verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
            ) {
                Text(
                    text = "Connection State",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = connectionStateLabel(connectionState),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Connected is distinct from Ready to keep state truthful.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            PrimaryActionButton(
                text = "Connect",
                onClick = viewModel::connectFirstSavedTv,
                modifier = Modifier.weight(1f)
            )
            SecondaryActionButton(
                text = "Disconnect",
                onClick = viewModel::disconnect,
                modifier = Modifier.weight(1f)
            )
        }

        if (connectionState is ConnectionState.PinRequired) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(SamsungSpacing.SpacingMd),
                    verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
                ) {
                    Text(
                        text = stringResource(id = R.string.remote_pin_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(id = R.string.remote_pin_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pendingPin,
                        onValueChange = viewModel::updatePendingPin,
                        label = { Text(text = stringResource(id = R.string.remote_pin_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
                    ) {
                        PrimaryActionButton(
                            text = stringResource(id = R.string.remote_pin_submit),
                            onClick = viewModel::submitEncryptedPin,
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryActionButton(
                            text = stringResource(id = R.string.remote_pin_cancel),
                            onClick = viewModel::cancelEncryptedPairing,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Text(
            text = "D-pad",
            style = MaterialTheme.typography.titleMedium
        )

        HoldRepeatSecondaryActionButton(
            text = "Up",
            onPressStart = {
                emitControlHaptic()
                viewModel.startKeyHold(RemoteKey.D_PAD_UP)
            },
            onPressEnd = {
                viewModel.stopKeyHold(RemoteKey.D_PAD_UP)
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            HoldRepeatSecondaryActionButton(
                text = "Left",
                onPressStart = {
                    emitControlHaptic()
                    viewModel.startKeyHold(RemoteKey.D_PAD_LEFT)
                },
                onPressEnd = {
                    viewModel.stopKeyHold(RemoteKey.D_PAD_LEFT)
                },
                modifier = Modifier.weight(1f)
            )
            SecondaryActionButton(
                text = "OK",
                onClick = {
                    emitControlHaptic()
                    viewModel.sendRemoteKey(RemoteKey.OK)
                },
                modifier = Modifier.weight(1f)
            )
            HoldRepeatSecondaryActionButton(
                text = "Right",
                onPressStart = {
                    emitControlHaptic()
                    viewModel.startKeyHold(RemoteKey.D_PAD_RIGHT)
                },
                onPressEnd = {
                    viewModel.stopKeyHold(RemoteKey.D_PAD_RIGHT)
                },
                modifier = Modifier.weight(1f)
            )
        }

        HoldRepeatSecondaryActionButton(
            text = "Down",
            onPressStart = {
                emitControlHaptic()
                viewModel.startKeyHold(RemoteKey.D_PAD_DOWN)
            },
            onPressEnd = {
                viewModel.stopKeyHold(RemoteKey.D_PAD_DOWN)
            }
        )

        Text(
            text = "Volume / Media / Power",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            HoldRepeatSecondaryActionButton(
                text = "Volume +",
                onPressStart = {
                    emitControlHaptic()
                    viewModel.startKeyHold(RemoteKey.VOLUME_UP)
                },
                onPressEnd = {
                    viewModel.stopKeyHold(RemoteKey.VOLUME_UP)
                },
                modifier = Modifier.weight(1f)
            )
            HoldRepeatSecondaryActionButton(
                text = "Volume -",
                onPressStart = {
                    emitControlHaptic()
                    viewModel.startKeyHold(RemoteKey.VOLUME_DOWN)
                },
                onPressEnd = {
                    viewModel.stopKeyHold(RemoteKey.VOLUME_DOWN)
                },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            SecondaryActionButton(
                text = "Play/Pause",
                onClick = {
                    emitControlHaptic()
                    viewModel.sendRemoteKey(RemoteKey.MEDIA_PLAY_PAUSE)
                },
                modifier = Modifier.weight(1f)
            )
            SecondaryActionButton(
                text = stringResource(id = R.string.remote_power_best_effort_label),
                onClick = {
                    emitControlHaptic()
                    viewModel.sendRemoteKey(RemoteKey.POWER)
                },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = stringResource(id = R.string.remote_power_best_effort_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(id = R.string.remote_quick_launch_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )

        SecondaryActionButton(
            text = stringResource(id = R.string.remote_quick_launch_unavailable_label),
            onClick = {
                emitControlHaptic()
                viewModel.showQuickLaunchUnavailable()
            }
        )

        Text(
            text = stringResource(id = R.string.remote_installed_app_unsupported_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isDebuggable) {
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

                    val recentEvents = diagnosticsEvents.takeLast(8)
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
private fun HoldRepeatSecondaryActionButton(
    text: String,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    SecondaryActionButton(
        text = text,
        onClick = {},
        modifier = modifier.pointerInput(onPressStart, onPressEnd) {
            detectTapGestures(
                onPress = {
                    onPressStart()
                    try {
                        tryAwaitRelease()
                    } finally {
                        onPressEnd()
                    }
                }
            )
        }
    )
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
