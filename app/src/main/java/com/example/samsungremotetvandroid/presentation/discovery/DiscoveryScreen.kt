package com.example.samsungremotetvandroid.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsungremotetvandroid.R
import com.example.samsungremotetvandroid.core.design.SamsungSpacing
import com.example.samsungremotetvandroid.domain.model.SamsungTv
import com.example.samsungremotetvandroid.presentation.common.PrimaryActionButton
import com.example.samsungremotetvandroid.presentation.common.SecondaryActionButton

@Composable
fun DiscoveryScreen(
    onOpenRemote: () -> Unit = {},
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val savedTvs by viewModel.savedTvs.collectAsStateWithLifecycle()
    val discoveredTvs by viewModel.discoveredTvs.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val visibleDiscoveredTvs = discoveredTvs.filter { discoveredTv ->
        savedTvs.none { savedTv ->
            savedTv.ipAddress == discoveredTv.ipAddress
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(SamsungSpacing.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingMd)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)) {
                Text(
                    text = stringResource(id = R.string.discovery_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(id = R.string.discovery_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
                ) {
                    SecondaryActionButton(
                        text = if (uiState.isScanning) {
                            stringResource(id = R.string.discovery_scanning)
                        } else {
                            stringResource(id = R.string.discovery_scan_network)
                        },
                        onClick = viewModel::refreshDiscovery,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryActionButton(
                        text = stringResource(id = R.string.discovery_add_manually),
                        onClick = viewModel::openManualIpDialog,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (savedTvs.isEmpty() && visibleDiscoveredTvs.isEmpty() && !uiState.isScanning && uiState.hasScanned) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(SamsungSpacing.SpacingMd),
                        verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingXs)
                    ) {
                        Text(
                            text = stringResource(id = R.string.discovery_no_tvs_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(id = R.string.discovery_no_tvs_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(id = R.string.discovery_saved_section),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (savedTvs.isEmpty()) {
            item {
                Text(
                    text = stringResource(id = R.string.discovery_no_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(savedTvs, key = { it.id }) { tv ->
                TvCard(
                    tv = tv,
                    actionLabel = stringResource(id = R.string.common_open),
                    onAction = { viewModel.connect(tv.id, onOpenRemote) }
                )
            }
        }

        item {
            Text(
                text = stringResource(id = R.string.discovery_discovered_section),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (uiState.isScanning) {
            item {
                Text(
                    text = stringResource(id = R.string.discovery_scanning_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (visibleDiscoveredTvs.isEmpty()) {
            item {
                Text(
                    text = stringResource(id = R.string.discovery_no_new),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(visibleDiscoveredTvs, key = { it.id }) { tv ->
                TvCard(
                    tv = tv,
                    actionLabel = stringResource(id = R.string.common_connect),
                    onAction = { viewModel.connect(tv.id, onOpenRemote) }
                )
            }
        }
    }

    if (uiState.showManualIpDialog) {
        AlertDialog(
            onDismissRequest = viewModel::closeManualIpDialog,
            title = {
                Text(text = stringResource(id = R.string.discovery_manual_title))
            },
            text = {
                OutlinedTextField(
                    value = uiState.manualIpAddress,
                    onValueChange = viewModel::updateManualIpAddress,
                    label = { Text(text = stringResource(id = R.string.discovery_manual_ip_label)) },
                    placeholder = {
                        Text(text = stringResource(id = R.string.discovery_manual_ip_placeholder))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.submitManualIp(onOpenRemote) }) {
                    Text(text = stringResource(id = R.string.common_connect))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeManualIpDialog) {
                    Text(text = stringResource(id = R.string.common_cancel))
                }
            }
        )
    }

    uiState.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text(text = stringResource(id = R.string.common_error)) },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessage) {
                    Text(text = stringResource(id = R.string.common_ok))
                }
            }
        )
    }
}

@Composable
private fun TvCard(
    tv: SamsungTv,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(SamsungSpacing.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tv.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = tv.protocol.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = tv.ipAddress,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PrimaryActionButton(
                text = actionLabel,
                onClick = onAction
            )
        }
    }
}
