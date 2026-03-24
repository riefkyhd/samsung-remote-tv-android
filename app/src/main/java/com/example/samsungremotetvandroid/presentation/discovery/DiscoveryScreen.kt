package com.example.samsungremotetvandroid.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsungremotetvandroid.core.design.SamsungSpacing
import com.example.samsungremotetvandroid.presentation.common.PrimaryActionButton
import com.example.samsungremotetvandroid.presentation.common.SecondaryActionButton

@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val savedTvs by viewModel.savedTvs.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(SamsungSpacing.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingMd)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingSm)) {
                Text(text = "Discovery", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Saved TVs and discovered TVs remain explicit sections. " +
                        "Manual IP and network discovery behavior are coming in the next phase.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SecondaryActionButton(
                    text = "Manual IP (Placeholder)",
                    onClick = {}
                )
            }
        }

        if (savedTvs.isEmpty()) {
            item {
                Text(
                    text = "No saved TVs yet.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            items(savedTvs, key = { it.id }) { tv ->
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
                            text = "Connect",
                            onClick = { viewModel.connect(tv.id) }
                        )
                    }
                }
            }
        }
    }
}
