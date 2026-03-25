package com.example.samsungremotetvandroid.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.samsungremotetvandroid.R
import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsCategory
import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsTracker
import com.example.samsungremotetvandroid.domain.usecase.ForgetPairingUseCase
import com.example.samsungremotetvandroid.domain.usecase.ObserveSavedTvsUseCase
import com.example.samsungremotetvandroid.domain.usecase.RemoveDeviceUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val observeSavedTvsUseCase: ObserveSavedTvsUseCase,
    private val forgetPairingUseCase: ForgetPairingUseCase,
    private val removeDeviceUseCase: RemoveDeviceUseCase,
    private val diagnosticsTracker: DiagnosticsTracker
) : ViewModel() {
    val savedTvs = observeSavedTvsUseCase()
    private val uiStateFlow = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = uiStateFlow.asStateFlow()

    init {
        viewModelScope.launch {
            savedTvs.collectLatest { currentSavedTvs ->
                val visibleIds = currentSavedTvs.map { it.id }.toSet()
                uiStateFlow.update { current ->
                    current.copy(
                        pairingClearedTvIds = current.pairingClearedTvIds.intersect(visibleIds)
                    )
                }
            }
        }
    }

    fun forgetPairing(tvId: String) {
        if (savedTvs.value.none { it.id == tvId }) {
            return
        }

        viewModelScope.launch {
            runCatching {
                forgetPairingUseCase(tvId)
            }.onSuccess {
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.PAIRING,
                    message = "forget pairing completed",
                    metadata = mapOf("tvId" to tvId)
                )
                uiStateFlow.update { current ->
                    current.copy(
                        pairingClearedTvIds = current.pairingClearedTvIds + tvId,
                        alertTitle = appContext.getString(R.string.settings_pairing_reset_title),
                        alertMessage = appContext.getString(R.string.settings_pairing_reset_success)
                    )
                }
            }.onFailure { error ->
                diagnosticsTracker.recordError(
                    context = "settings_forget_pairing",
                    errorMessage = error.message ?: appContext.getString(R.string.settings_action_failed),
                    metadata = mapOf("tvId" to tvId)
                )
                uiStateFlow.update { current ->
                    current.copy(
                        alertTitle = appContext.getString(R.string.common_error),
                        alertMessage = error.message ?: appContext.getString(R.string.settings_action_failed)
                    )
                }
            }
        }
    }

    fun removeDevice(tvId: String) {
        if (savedTvs.value.none { it.id == tvId }) {
            return
        }

        viewModelScope.launch {
            runCatching {
                removeDeviceUseCase(tvId)
            }.onSuccess {
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.LIFECYCLE,
                    message = "remove device completed",
                    metadata = mapOf("tvId" to tvId)
                )
                uiStateFlow.update { current ->
                    current.copy(
                        pairingClearedTvIds = current.pairingClearedTvIds - tvId,
                        alertTitle = appContext.getString(R.string.settings_device_removed_title),
                        alertMessage = appContext.getString(R.string.settings_device_removed_success)
                    )
                }
            }.onFailure { error ->
                diagnosticsTracker.recordError(
                    context = "settings_remove_device",
                    errorMessage = error.message ?: appContext.getString(R.string.settings_action_failed),
                    metadata = mapOf("tvId" to tvId)
                )
                uiStateFlow.update { current ->
                    current.copy(
                        alertTitle = appContext.getString(R.string.common_error),
                        alertMessage = error.message ?: appContext.getString(R.string.settings_action_failed)
                    )
                }
            }
        }
    }

    fun dismissAlert() {
        uiStateFlow.update { current ->
            current.copy(
                alertTitle = null,
                alertMessage = null
            )
        }
    }

    fun forgetPairingButtonLabel(tvId: String): String {
        return if (isPairingCleared(tvId)) {
            appContext.getString(R.string.settings_pairing_cleared)
        } else {
            appContext.getString(R.string.settings_forget_pairing)
        }
    }

    fun isPairingCleared(tvId: String): Boolean {
        return uiStateFlow.value.pairingClearedTvIds.contains(tvId)
    }
}

data class SettingsUiState(
    val pairingClearedTvIds: Set<String> = emptySet(),
    val alertTitle: String? = null,
    val alertMessage: String? = null
)
