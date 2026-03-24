package com.example.samsungremotetvandroid.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsungremotetvandroid.core.design.SamsungSpacing
import com.example.samsungremotetvandroid.presentation.common.DestructiveActionButton
import com.example.samsungremotetvandroid.presentation.common.SecondaryActionButton

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val savedTvs by viewModel.savedTvs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SamsungSpacing.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(SamsungSpacing.SpacingMd)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

        Text(
            text = "Forget Pairing and Remove Device stay explicit and distinct.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Saved TVs: ${savedTvs.size}",
            style = MaterialTheme.typography.titleMedium
        )

        SecondaryActionButton(
            text = "Rename First Saved TV",
            onClick = viewModel::renameFirstTv
        )

        SecondaryActionButton(
            text = "Forget Pairing (First TV)",
            onClick = viewModel::forgetPairingForFirstTv
        )

        DestructiveActionButton(
            text = "Remove Device (First TV)",
            onClick = viewModel::removeFirstTv
        )
    }
}
