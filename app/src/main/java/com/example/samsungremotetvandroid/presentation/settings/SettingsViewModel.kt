package com.example.samsungremotetvandroid.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.samsungremotetvandroid.domain.usecase.ForgetPairingUseCase
import com.example.samsungremotetvandroid.domain.usecase.ObserveSavedTvsUseCase
import com.example.samsungremotetvandroid.domain.usecase.RemoveDeviceUseCase
import com.example.samsungremotetvandroid.domain.usecase.RenameTvUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeSavedTvsUseCase: ObserveSavedTvsUseCase,
    private val forgetPairingUseCase: ForgetPairingUseCase,
    private val removeDeviceUseCase: RemoveDeviceUseCase,
    private val renameTvUseCase: RenameTvUseCase
) : ViewModel() {
    val savedTvs = observeSavedTvsUseCase()

    fun forgetPairingForFirstTv() {
        val tvId = savedTvs.value.firstOrNull()?.id ?: return
        viewModelScope.launch {
            forgetPairingUseCase(tvId)
        }
    }

    fun renameFirstTv() {
        val tv = savedTvs.value.firstOrNull() ?: return
        viewModelScope.launch {
            renameTvUseCase(tv.id, "${tv.displayName} (Renamed)")
        }
    }

    fun removeFirstTv() {
        val tvId = savedTvs.value.firstOrNull()?.id ?: return
        viewModelScope.launch {
            removeDeviceUseCase(tvId)
        }
    }
}
