package com.example.samsungremotetvandroid.presentation.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsCategory
import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsTracker
import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.model.TvCapability
import com.example.samsungremotetvandroid.domain.usecase.ObserveDiscoveredTvsUseCase
import com.example.samsungremotetvandroid.domain.usecase.CancelEncryptedPairingUseCase
import com.example.samsungremotetvandroid.domain.usecase.CompleteEncryptedPairingUseCase
import com.example.samsungremotetvandroid.domain.usecase.ConnectToTvUseCase
import com.example.samsungremotetvandroid.domain.usecase.DisconnectFromTvUseCase
import com.example.samsungremotetvandroid.domain.usecase.ObserveConnectionStateUseCase
import com.example.samsungremotetvandroid.domain.usecase.ObserveSavedTvsUseCase
import com.example.samsungremotetvandroid.domain.usecase.SendRemoteKeyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val observeConnectionStateUseCase: ObserveConnectionStateUseCase,
    private val observeSavedTvsUseCase: ObserveSavedTvsUseCase,
    private val observeDiscoveredTvsUseCase: ObserveDiscoveredTvsUseCase,
    private val connectToTvUseCase: ConnectToTvUseCase,
    private val completeEncryptedPairingUseCase: CompleteEncryptedPairingUseCase,
    private val cancelEncryptedPairingUseCase: CancelEncryptedPairingUseCase,
    private val disconnectFromTvUseCase: DisconnectFromTvUseCase,
    private val sendRemoteKeyUseCase: SendRemoteKeyUseCase,
    private val diagnosticsTracker: DiagnosticsTracker
) : ViewModel() {
    val connectionState = observeConnectionStateUseCase()
    val savedTvs = observeSavedTvsUseCase()
    val discoveredTvs = observeDiscoveredTvsUseCase()
    private val connectAttemptsFlow = MutableStateFlow(0)
    private val pendingPinFlow = MutableStateFlow("")
    private val userMessageFlow = MutableStateFlow<String?>(null)
    private val holdRepeater = HoldRepeatController(
        scope = viewModelScope,
        initialDelayMs = HOLD_REPEAT_INITIAL_DELAY_MS,
        repeatIntervalMs = HOLD_REPEAT_INTERVAL_MS,
        onSend = { key -> sendRemoteKeyInternal(key) },
        shouldContinue = { state -> state is ConnectionState.Ready }
    )

    val diagnosticsEvents = diagnosticsTracker.recentEvents
    val lastErrorSummary = diagnosticsTracker.lastErrorSummary
    val pendingPin: StateFlow<String> = pendingPinFlow.asStateFlow()
    val userMessage: StateFlow<String?> = userMessageFlow.asStateFlow()
    val diagnosticsSummary = combine(
        connectionState,
        savedTvs,
        connectAttemptsFlow
    ) { state, tvs, attempts ->
        "State: ${connectionStateLabel(state)} | Saved TVs: ${tvs.size} | Attempts: $attempts"
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = "State: Disconnected | Saved TVs: 0 | Attempts: 0"
    )

    init {
        viewModelScope.launch {
            connectionState.collectLatest { state ->
                if (state !is ConnectionState.Ready) {
                    holdRepeater.stopAll()
                }
            }
        }
    }

    fun connectFirstSavedTv() {
        holdRepeater.stopAll()
        val tvId = savedTvs.value.firstOrNull()?.id ?: run {
            diagnosticsTracker.recordError(
                context = "remote_connect",
                errorMessage = "no saved tv available to connect"
            )
            return
        }
        connectAttemptsFlow.update { attempts -> attempts + 1 }
        diagnosticsTracker.log(
            category = DiagnosticsCategory.RECONNECT,
            message = "remote connect requested",
            metadata = mapOf("tvId" to tvId)
        )
        viewModelScope.launch {
            runCatching {
                connectToTvUseCase(tvId)
            }.onFailure { error ->
                diagnosticsTracker.recordError(
                    context = "remote_connect",
                    errorMessage = error.message ?: "failed to connect requested tv",
                    metadata = mapOf("tvId" to tvId)
                )
            }
        }
    }

    fun disconnect() {
        holdRepeater.stopAll()
        diagnosticsTracker.log(
            category = DiagnosticsCategory.LIFECYCLE,
            message = "remote disconnect requested"
        )
        viewModelScope.launch {
            runCatching {
                disconnectFromTvUseCase()
            }.onFailure { error ->
                diagnosticsTracker.recordError(
                    context = "remote_disconnect",
                    errorMessage = error.message ?: "failed to disconnect"
                )
            }
        }
    }

    fun sendRemoteKey(key: RemoteKey) {
        viewModelScope.launch {
            sendRemoteKeyInternal(key)
        }
    }

    fun startKeyHold(key: RemoteKey) {
        if (key !in HOLD_REPEAT_KEYS) {
            sendRemoteKey(key)
            return
        }
        if (!isRemoteKeySupported(key)) {
            holdRepeater.stop(key)
            return
        }
        holdRepeater.start(
            key = key,
            connectionState = connectionState
        )
    }

    fun stopKeyHold(key: RemoteKey) {
        holdRepeater.stop(key)
    }

    fun releaseAllHeldKeys() {
        holdRepeater.stopAll()
    }

    fun dismissUserMessage() {
        userMessageFlow.value = null
    }

    fun showQuickLaunchUnavailable() {
        showUserMessage(QUICK_LAUNCH_UNAVAILABLE_MESSAGE)
        diagnosticsTracker.log(
            category = DiagnosticsCategory.CAPABILITIES,
            message = "quick launch blocked truthfully",
            metadata = mapOf("reason" to "transport_unavailable_baseline")
        )
    }

    fun updatePendingPin(value: String) {
        pendingPinFlow.value = value
    }

    fun submitEncryptedPin() {
        val state = connectionState.value as? ConnectionState.PinRequired ?: run {
            diagnosticsTracker.recordError(
                context = "submit_encrypted_pin",
                errorMessage = "pin submission ignored because pin-required state is not active"
            )
            return
        }

        val pin = pendingPinFlow.value.trim()
        viewModelScope.launch {
            runCatching {
                completeEncryptedPairingUseCase(
                    tvId = state.tvId,
                    pin = pin
                )
                pendingPinFlow.value = ""
            }.onFailure { error ->
                diagnosticsTracker.recordError(
                    context = "submit_encrypted_pin",
                    errorMessage = error.message ?: "failed to complete encrypted pairing",
                    metadata = mapOf("tvId" to state.tvId)
                )
            }
        }
    }

    fun cancelEncryptedPairing() {
        val state = connectionState.value as? ConnectionState.PinRequired ?: return
        viewModelScope.launch {
            runCatching {
                cancelEncryptedPairingUseCase(state.tvId)
                pendingPinFlow.value = ""
            }.onFailure { error ->
                diagnosticsTracker.recordError(
                    context = "cancel_encrypted_pairing",
                    errorMessage = error.message ?: "failed to cancel encrypted pairing",
                    metadata = mapOf("tvId" to state.tvId)
                )
            }
        }
    }

    override fun onCleared() {
        holdRepeater.stopAll()
        super.onCleared()
    }

    private suspend fun sendRemoteKeyInternal(key: RemoteKey) {
        if (!isRemoteKeySupported(key)) {
            return
        }
        runCatching {
            sendRemoteKeyUseCase(key)
        }.onFailure { error ->
            diagnosticsTracker.recordError(
                context = "remote_send_key",
                errorMessage = error.message ?: "failed to send remote key",
                metadata = mapOf("key" to key.name)
            )
        }
    }

    private fun isRemoteKeySupported(key: RemoteKey): Boolean {
        val requiredCapability = capabilityForKey(key) ?: return true
        val tvCapabilities = activeTvCapabilities() ?: return true
        if (requiredCapability in tvCapabilities) {
            return true
        }

        val message = unsupportedMessageForCapability(requiredCapability)
        showUserMessage(message)
        diagnosticsTracker.recordError(
            context = "remote_capability_blocked",
            errorMessage = message,
            metadata = mapOf(
                "key" to key.name,
                "capability" to requiredCapability.name
            )
        )
        return false
    }

    private fun activeTvCapabilities(): Set<TvCapability>? {
        val tvId = activeTvId(connectionState.value) ?: return null
        return savedTvs.value.firstOrNull { it.id == tvId }?.capabilities
            ?: discoveredTvs.value.firstOrNull { it.id == tvId }?.capabilities
    }

    private fun showUserMessage(message: String) {
        userMessageFlow.value = message
    }

    private fun connectionStateLabel(state: ConnectionState): String {
        return when (state) {
            ConnectionState.Disconnected -> "Disconnected"
            ConnectionState.Connecting -> "Connecting"
            is ConnectionState.Pairing -> "Pairing"
            is ConnectionState.PinRequired -> "Pin Required"
            is ConnectionState.ConnectedNotReady -> "Connected (Not Ready)"
            is ConnectionState.Ready -> "Ready"
            is ConnectionState.Error -> "Error"
        }
    }

    private companion object {
        private const val HOLD_REPEAT_INITIAL_DELAY_MS = 300L
        private const val HOLD_REPEAT_INTERVAL_MS = 90L
        private val HOLD_REPEAT_KEYS = setOf(
            RemoteKey.D_PAD_UP,
            RemoteKey.D_PAD_DOWN,
            RemoteKey.D_PAD_LEFT,
            RemoteKey.D_PAD_RIGHT,
            RemoteKey.VOLUME_UP,
            RemoteKey.VOLUME_DOWN
        )
    }
}
