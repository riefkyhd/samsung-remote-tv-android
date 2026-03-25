package com.example.samsungremotetvandroid.presentation.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsCategory
import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsTracker
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
    private val connectToTvUseCase: ConnectToTvUseCase,
    private val diagnosticsTracker: DiagnosticsTracker
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
        if (uiStateFlow.value.isScanning || uiStateFlow.value.isConnecting) {
            return
        }

        diagnosticsTracker.log(
            category = DiagnosticsCategory.LIFECYCLE,
            message = "discovery refresh requested"
        )

        viewModelScope.launch {
            uiStateFlow.update { current ->
                current.copy(isScanning = true)
            }

            runCatching {
                scanDiscoveryUseCase()
            }.onFailure { error ->
                diagnosticsTracker.recordError(
                    context = "discovery_refresh",
                    errorMessage = error.message ?: "discovery refresh failed"
                )
            }

            uiStateFlow.update { current ->
                current.copy(
                    isScanning = false,
                    hasScanned = true
                )
            }

            diagnosticsTracker.log(
                category = DiagnosticsCategory.LIFECYCLE,
                message = "discovery refresh completed",
                metadata = mapOf("discoveredCount" to discoveredTvs.value.size.toString())
            )
        }
    }

    fun openManualIpDialog() {
        if (uiStateFlow.value.isConnecting) {
            return
        }
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
        if (uiStateFlow.value.isConnecting) {
            return
        }
        val trimmed = uiStateFlow.value.manualIpAddress.trim()

        if (trimmed.isEmpty()) {
            diagnosticsTracker.recordError(
                context = "manual_ip_validation",
                errorMessage = "manual ip is required"
            )
            showMessage("Please enter an IP address.")
            return
        }

        if (!isValidIpv4Address(trimmed)) {
            diagnosticsTracker.recordError(
                context = "manual_ip_validation",
                errorMessage = "manual ip is invalid",
                metadata = mapOf("ipAddress" to trimmed)
            )
            showMessage("Please enter a valid IPv4 address.")
            return
        }

        viewModelScope.launch {
            setConnecting(tvId = "manual_$trimmed")
            val discoveredTv = runCatching {
                scanManualIpUseCase(trimmed)
            }.getOrElse { error ->
                diagnosticsTracker.recordError(
                    context = "manual_ip_scan",
                    errorMessage = error.message
                        ?: "could not reach compatible samsung tv at provided ip",
                    metadata = mapOf("ipAddress" to trimmed)
                )
                showMessage(error.message ?: "Could not reach a compatible Samsung TV at that IP.")
                setConnecting(tvId = null)
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
        if (uiStateFlow.value.isConnecting) {
            return
        }
        viewModelScope.launch {
            connectToTv(tvId, onConnected)
        }
    }

    private suspend fun connectToTv(tvId: String, onConnected: () -> Unit) {
        setConnecting(tvId)
        diagnosticsTracker.log(
            category = DiagnosticsCategory.RECONNECT,
            message = "discovery connect requested",
            metadata = mapOf("tvId" to tvId)
        )
        val connectResult = runCatching {
            connectToTvUseCase(tvId)
        }
        val connectError = connectResult.exceptionOrNull()
        if (connectError != null) {
            diagnosticsTracker.recordError(
                context = "discovery_connect",
                errorMessage = connectError.message ?: "failed to connect selected tv",
                metadata = mapOf("tvId" to tvId)
            )
            showMessage(connectError.message ?: "Unable to connect to this TV right now.")
            setConnecting(tvId = null)
            return
        }

        when (val state = connectionState.value) {
            is ConnectionState.Ready -> {
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.LIFECYCLE,
                    message = "discovery connect ready",
                    metadata = mapOf("tvId" to tvId)
                )
                onConnected()
            }

            is ConnectionState.ConnectedNotReady,
            is ConnectionState.Pairing,
            is ConnectionState.PinRequired -> {
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.LIFECYCLE,
                    message = "discovery connect transitioned to non-ready remote state",
                    metadata = mapOf("tvId" to tvId)
                )
                onConnected()
            }

            is ConnectionState.Error -> {
                diagnosticsTracker.recordError(
                    context = "discovery_connect",
                    errorMessage = state.message,
                    metadata = mapOf("tvId" to tvId)
                )
                showMessage(state.message)
            }

            else -> Unit
        }
        setConnecting(tvId = null)
    }

    private fun showMessage(message: String) {
        uiStateFlow.update { current ->
            current.copy(message = message)
        }
    }

    private fun setConnecting(tvId: String?) {
        uiStateFlow.update { current ->
            current.copy(
                isConnecting = tvId != null,
                connectingTvId = tvId
            )
        }
    }
}

data class DiscoveryUiState(
    val isScanning: Boolean = false,
    val isConnecting: Boolean = false,
    val connectingTvId: String? = null,
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
