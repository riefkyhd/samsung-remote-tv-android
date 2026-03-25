package com.example.samsungremotetvandroid.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsungremotetvandroid.R
import com.example.samsungremotetvandroid.core.design.SamsungSpacing
import com.example.samsungremotetvandroid.domain.model.SamsungTv
import com.example.samsungremotetvandroid.presentation.common.DestructiveActionButton
import com.example.samsungremotetvandroid.presentation.common.SecondaryActionButton

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val savedTvs by viewModel.savedTvs.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(SamsungSpacing.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingMd)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = stringResource(id = R.string.common_back)
                    )
                }
                Text(
                    text = stringResource(id = R.string.nav_settings),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = SamsungSpacing.SpacingXs)
                )
            }
        }

        item {
            Text(
                text = stringResource(id = R.string.settings_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Text(
                text = stringResource(id = R.string.settings_saved_count, savedTvs.size),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (savedTvs.isEmpty()) {
            item {
                Text(
                    text = stringResource(id = R.string.settings_no_saved_tvs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(savedTvs, key = { it.id }) { tv ->
                SettingsTvCard(
                    tv = tv,
                    forgetPairingLabel = viewModel.forgetPairingButtonLabel(tv.id),
                    forgetPairingEnabled = !viewModel.isPairingCleared(tv.id),
                    onForgetPairing = { viewModel.forgetPairing(tv.id) },
                    onRemoveDevice = { viewModel.removeDevice(tv.id) }
                )
            }
        }
    }

    if (uiState.alertMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAlert,
            title = {
                Text(text = uiState.alertTitle ?: stringResource(id = R.string.common_error))
            },
            text = {
                Text(text = uiState.alertMessage.orEmpty())
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissAlert) {
                    Text(text = stringResource(id = R.string.common_ok))
                }
            }
        )
    }
}

@Composable
private fun SettingsTvCard(
    tv: SamsungTv,
    forgetPairingLabel: String,
    forgetPairingEnabled: Boolean,
    onForgetPairing: () -> Unit,
    onRemoveDevice: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(SamsungSpacing.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            Text(
                text = tv.displayName,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = tv.ipAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(id = R.string.settings_saved_tv_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
            ) {
                SecondaryActionButton(
                    text = forgetPairingLabel,
                    enabled = forgetPairingEnabled,
                    onClick = onForgetPairing,
                    modifier = Modifier.weight(1f)
                )

                DestructiveActionButton(
                    text = stringResource(id = R.string.settings_remove_device),
                    onClick = onRemoveDevice,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
