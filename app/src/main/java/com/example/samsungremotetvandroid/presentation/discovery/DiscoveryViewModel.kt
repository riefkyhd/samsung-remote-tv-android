package com.example.samsungremotetvandroid.presentation.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.usecase.ConnectToTvUseCase
import com.example.samsungremotetvandroid.domain.usecase.ObserveConnectionStateUseCase
import com.example.samsungremotetvandroid.domain.usecase.ObserveDiscoveredTvsUseCase
import com.example.samsungremotetvandroid.domain.usecase.ObserveSavedTvsUseCase
import com.example.samsungremotetvandroid.domain.usecase.ScanDiscoveryUseCase
import com.example.samsungremotetvandroid.domain.usecase.ScanManualIpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val observeSavedTvsUseCase: ObserveSavedTvsUseCase,
    private val observeDiscoveredTvsUseCase: ObserveDiscoveredTvsUseCase,
    private val observeConnectionStateUseCase: ObserveConnectionStateUseCase,
    private val scanDiscoveryUseCase: ScanDiscoveryUseCase,
    private val scanManualIpUseCase: ScanManualIpUseCase,
    private val connectToTvUseCase: ConnectToTvUseCase
) : ViewModel() {
    val savedTvs = observeSavedTvsUseCase()
    val discoveredTvs = observeDiscoveredTvsUseCase()
    private val connectionState = observeConnectionStateUseCase()

    private val uiStateFlow = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = uiStateFlow.asStateFlow()

    fun onScreenVisible() {
        if (!uiStateFlow.value.hasScanned) {
            refreshDiscovery()
        }
    }

    fun refreshDiscovery() {
        if (uiStateFlow.value.isScanning) {
            return
        }

        viewModelScope.launch {
            uiStateFlow.update { current ->
                current.copy(isScanning = true)
            }

            runCatching {
                scanDiscoveryUseCase()
            }

            uiStateFlow.update { current ->
                current.copy(
                    isScanning = false,
                    hasScanned = true
                )
            }
        }
    }

    fun openManualIpDialog() {
        uiStateFlow.update { current ->
            current.copy(showManualIpDialog = true)
        }
    }

    fun closeManualIpDialog() {
        uiStateFlow.update { current ->
            current.copy(showManualIpDialog = false)
        }
    }

    fun updateManualIpAddress(newValue: String) {
        uiStateFlow.update { current ->
            current.copy(manualIpAddress = newValue)
        }
    }

    fun dismissMessage() {
        uiStateFlow.update { current ->
            current.copy(message = null)
        }
    }

    fun submitManualIp(onConnected: () -> Unit = {}) {
        val trimmed = uiStateFlow.value.manualIpAddress.trim()

        if (trimmed.isEmpty()) {
            showMessage("Please enter an IP address.")
            return
        }

        if (!isValidIpv4Address(trimmed)) {
            showMessage("Please enter a valid IPv4 address.")
            return
        }

        viewModelScope.launch {
            val discoveredTv = runCatching {
                scanManualIpUseCase(trimmed)
            }.getOrElse { error ->
                showMessage(error.message ?: "Could not reach a compatible Samsung TV at that IP.")
                return@launch
            }

            uiStateFlow.update { current ->
                current.copy(
                    showManualIpDialog = false,
                    manualIpAddress = ""
                )
            }

            connectToTv(discoveredTv.id, onConnected)
        }
    }

    fun connect(tvId: String, onConnected: () -> Unit = {}) {
        viewModelScope.launch {
            connectToTv(tvId, onConnected)
        }
    }

    private suspend fun connectToTv(tvId: String, onConnected: () -> Unit) {
        connectToTvUseCase(tvId)

        when (val state = connectionState.value) {
            is ConnectionState.Ready -> onConnected()
            is ConnectionState.Error -> showMessage(state.message)
            else -> Unit
        }
    }

    private fun showMessage(message: String) {
        uiStateFlow.update { current ->
            current.copy(message = message)
        }
    }
}

data class DiscoveryUiState(
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    val showManualIpDialog: Boolean = false,
    val manualIpAddress: String = "",
    val message: String? = null
)

internal fun isValidIpv4Address(value: String): Boolean {
    val parts = value.split(".")
    if (parts.size != 4) {
        return false
    }

    return parts.all { part ->
        val octet = part.toIntOrNull() ?: return@all false
        octet in 0..255
    }
}
