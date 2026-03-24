package com.example.samsungremotetvandroid.presentation.remote

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            SecondaryActionButton(
                text = "D-pad Up",
                onClick = { viewModel.sendRemoteKey(RemoteKey.D_PAD_UP) },
                modifier = Modifier.weight(1f)
            )
            SecondaryActionButton(
                text = "Volume +",
                onClick = { viewModel.sendRemoteKey(RemoteKey.VOLUME_UP) },
                modifier = Modifier.weight(1f)
            )
        }

        SecondaryActionButton(
            text = "Media Play/Pause",
            onClick = { viewModel.sendRemoteKey(RemoteKey.MEDIA_PLAY_PAUSE) }
        )

        Text(
            text = "Quick Launch remains curated shortcuts, not installed-app enumeration.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
