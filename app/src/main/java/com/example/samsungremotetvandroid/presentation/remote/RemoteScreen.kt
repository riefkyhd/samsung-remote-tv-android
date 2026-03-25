package com.example.samsungremotetvandroid.presentation.remote

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
    val context = LocalContext.current
    val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

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

        Text(
            text = "D-pad",
            style = MaterialTheme.typography.titleMedium
        )

        SecondaryActionButton(
            text = "Up",
            onClick = { viewModel.sendRemoteKey(RemoteKey.D_PAD_UP) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            SecondaryActionButton(
                text = "Left",
                onClick = { viewModel.sendRemoteKey(RemoteKey.D_PAD_LEFT) },
                modifier = Modifier.weight(1f)
            )
            SecondaryActionButton(
                text = "OK",
                onClick = { viewModel.sendRemoteKey(RemoteKey.OK) },
                modifier = Modifier.weight(1f)
            )
            SecondaryActionButton(
                text = "Right",
                onClick = { viewModel.sendRemoteKey(RemoteKey.D_PAD_RIGHT) },
                modifier = Modifier.weight(1f)
            )
        }

        SecondaryActionButton(
            text = "Down",
            onClick = { viewModel.sendRemoteKey(RemoteKey.D_PAD_DOWN) }
        )

        Text(
            text = "Volume / Media / Power",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            SecondaryActionButton(
                text = "Volume +",
                onClick = { viewModel.sendRemoteKey(RemoteKey.VOLUME_UP) },
                modifier = Modifier.weight(1f)
            )
            SecondaryActionButton(
                text = "Volume -",
                onClick = { viewModel.sendRemoteKey(RemoteKey.VOLUME_DOWN) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            SecondaryActionButton(
                text = "Play/Pause",
                onClick = { viewModel.sendRemoteKey(RemoteKey.MEDIA_PLAY_PAUSE) },
                modifier = Modifier.weight(1f)
            )
            SecondaryActionButton(
                text = "Power",
                onClick = { viewModel.sendRemoteKey(RemoteKey.POWER) },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Quick Launch remains curated shortcuts, not installed-app enumeration.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
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
}

private fun connectionStateLabel(state: ConnectionState): String {
    return when (state) {
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Connecting -> "Connecting"
        is ConnectionState.ConnectedNotReady -> "Connected (Not Ready)"
        is ConnectionState.Ready -> "Ready"
        is ConnectionState.Error -> "Error: ${state.message}"
    }
}
