package com.example.samsungremotetvandroid.presentation.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsCategory
import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsTracker
import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.usecase.ConnectToTvUseCase
import com.example.samsungremotetvandroid.domain.usecase.DisconnectFromTvUseCase
import com.example.samsungremotetvandroid.domain.usecase.ObserveConnectionStateUseCase
import com.example.samsungremotetvandroid.domain.usecase.ObserveSavedTvsUseCase
import com.example.samsungremotetvandroid.domain.usecase.SendRemoteKeyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val observeConnectionStateUseCase: ObserveConnectionStateUseCase,
    private val observeSavedTvsUseCase: ObserveSavedTvsUseCase,
    private val connectToTvUseCase: ConnectToTvUseCase,
    private val disconnectFromTvUseCase: DisconnectFromTvUseCase,
    private val sendRemoteKeyUseCase: SendRemoteKeyUseCase,
    private val diagnosticsTracker: DiagnosticsTracker
) : ViewModel() {
    val connectionState = observeConnectionStateUseCase()
    val savedTvs = observeSavedTvsUseCase()
    private val connectAttemptsFlow = MutableStateFlow(0)

    val diagnosticsEvents = diagnosticsTracker.recentEvents
    val lastErrorSummary = diagnosticsTracker.lastErrorSummary
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

    fun connectFirstSavedTv() {
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
    }

    private fun connectionStateLabel(state: ConnectionState): String {
        return when (state) {
            ConnectionState.Disconnected -> "Disconnected"
            ConnectionState.Connecting -> "Connecting"
            is ConnectionState.ConnectedNotReady -> "Connected (Not Ready)"
            is ConnectionState.Ready -> "Ready"
            is ConnectionState.Error -> "Error"
        }
    }
}
