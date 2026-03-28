package com.example.samsungremotetvandroid.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsungremotetvandroid.R
import com.example.samsungremotetvandroid.core.design.SamsungRadii
import com.example.samsungremotetvandroid.core.design.SamsungSpacing
import com.example.samsungremotetvandroid.domain.model.SamsungTv
import com.example.samsungremotetvandroid.domain.model.TvProtocol

@Composable
fun DiscoveryScreen(
    onOpenRemote: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(SamsungSpacing.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingMd)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResourceSafe(R.string.nav_settings)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)) {
                        ActionPillButton(
                            label = if (uiState.isScanning) {
                                stringResourceSafe(R.string.discovery_scanning)
                            } else {
                                stringResourceSafe(R.string.discovery_scan_network)
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            enabled = !uiState.isConnecting,
                            onClick = viewModel::refreshDiscovery
                        )
                        ActionPillButton(
                            label = stringResourceSafe(R.string.discovery_add_manually),
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            enabled = !uiState.isConnecting,
                            onClick = viewModel::openManualIpDialog
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingXs)) {
                    Text(
                        text = stringResourceSafe(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResourceSafe(R.string.discovery_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.isConnecting) {
                item {
                    Text(
                        text = stringResourceSafe(R.string.discovery_connecting_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Text(
                    text = stringResourceSafe(R.string.discovery_saved_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                DeviceListCard(
                    tvs = savedTvs,
                    emptyText = stringResourceSafe(R.string.discovery_no_saved),
                    actionLabel = stringResourceSafe(R.string.common_open),
                    connectingTvId = uiState.connectingTvId,
                    enabled = !uiState.isConnecting,
                    stateColor = MaterialTheme.colorScheme.tertiary,
                    onAction = { tvId -> viewModel.connect(tvId, onOpenRemote) }
                )
            }

            item {
                Text(
                    text = stringResourceSafe(R.string.discovery_discovered_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                DeviceListCard(
                    tvs = visibleDiscoveredTvs,
                    emptyText = if (uiState.isScanning) {
                        stringResourceSafe(R.string.discovery_scanning_detail)
                    } else {
                        stringResourceSafe(R.string.discovery_no_new)
                    },
                    actionLabel = stringResourceSafe(R.string.common_connect),
                    connectingTvId = uiState.connectingTvId,
                    enabled = !uiState.isConnecting,
                    stateColor = MaterialTheme.colorScheme.primary,
                    onAction = { tvId -> viewModel.connect(tvId, onOpenRemote) }
                )
            }

        }
    }

    if (uiState.showManualIpDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isConnecting) {
                    viewModel.closeManualIpDialog()
                }
            },
            title = {
                Text(text = stringResourceSafe(R.string.discovery_manual_title))
            },
            text = {
                OutlinedTextField(
                    value = uiState.manualIpAddress,
                    onValueChange = viewModel::updateManualIpAddress,
                    label = { Text(text = stringResourceSafe(R.string.discovery_manual_ip_label)) },
                    placeholder = {
                        Text(text = stringResourceSafe(R.string.discovery_manual_ip_placeholder))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !uiState.isConnecting
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isConnecting,
                    onClick = { viewModel.submitManualIp(onOpenRemote) }
                ) {
                    Text(
                        text = if (uiState.isConnecting) {
                            stringResourceSafe(R.string.discovery_scanning)
                        } else {
                            stringResourceSafe(R.string.common_connect)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isConnecting,
                    onClick = viewModel::closeManualIpDialog
                ) {
                    Text(text = stringResourceSafe(R.string.common_cancel))
                }
            }
        )
    }

    uiState.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text(text = stringResourceSafe(R.string.common_error)) },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessage) {
                    Text(text = stringResourceSafe(R.string.common_ok))
                }
            }
        )
    }
}

@Composable
private fun ActionPillButton(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = SamsungSpacing.SpacingMd,
            vertical = SamsungSpacing.SpacingSm
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Text(text = label)
        }
    }
}

@Composable
private fun DeviceListCard(
    tvs: List<SamsungTv>,
    emptyText: String,
    actionLabel: String,
    connectingTvId: String?,
    enabled: Boolean,
    stateColor: androidx.compose.ui.graphics.Color,
    onAction: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        if (tvs.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(SamsungSpacing.SpacingMd)
            )
            return@Card
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            tvs.forEachIndexed { index, tv ->
                if (index > 0) {
                    HorizontalDivider()
                }

                DeviceRow(
                    tv = tv,
                    actionLabel = actionLabel,
                    isConnecting = connectingTvId == tv.id,
                    enabled = enabled,
                    stateColor = stateColor,
                    onAction = { onAction(tv.id) }
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    tv: SamsungTv,
    actionLabel: String,
    isConnecting: Boolean,
    enabled: Boolean,
    stateColor: androidx.compose.ui.graphics.Color,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SamsungSpacing.SpacingMd),
        horizontalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(stateColor)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = tv.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${tv.modelName} • ${tv.ipAddress}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = protocolLabel(tv.protocol),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onAction,
            enabled = enabled,
            shape = RoundedCornerShape(SamsungRadii.RadiusLg)
        ) {
            Text(
                text = if (isConnecting) {
                    stringResourceSafe(R.string.discovery_connecting_label)
                } else {
                    actionLabel
                }
            )
        }
    }
}

private fun protocolLabel(protocol: TvProtocol): String {
    return when (protocol) {
        TvProtocol.MODERN -> "Modern"
        TvProtocol.LEGACY_ENCRYPTED -> "Encrypted"
        TvProtocol.LEGACY_REMOTE -> "Legacy"
    }
}

@Composable
private fun stringResourceSafe(id: Int): String {
    return androidx.compose.ui.res.stringResource(id = id)
}
