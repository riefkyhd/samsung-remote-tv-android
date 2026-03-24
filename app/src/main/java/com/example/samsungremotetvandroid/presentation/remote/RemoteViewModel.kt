package com.example.samsungremotetvandroid.presentation.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.usecase.ConnectToTvUseCase
import com.example.samsungremotetvandroid.domain.usecase.DisconnectFromTvUseCase
import com.example.samsungremotetvandroid.domain.usecase.ObserveConnectionStateUseCase
import com.example.samsungremotetvandroid.domain.usecase.ObserveSavedTvsUseCase
import com.example.samsungremotetvandroid.domain.usecase.SendRemoteKeyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val observeConnectionStateUseCase: ObserveConnectionStateUseCase,
    private val observeSavedTvsUseCase: ObserveSavedTvsUseCase,
    private val connectToTvUseCase: ConnectToTvUseCase,
    private val disconnectFromTvUseCase: DisconnectFromTvUseCase,
    private val sendRemoteKeyUseCase: SendRemoteKeyUseCase
) : ViewModel() {
    val connectionState = observeConnectionStateUseCase()
    val savedTvs = observeSavedTvsUseCase()

    fun connectFirstSavedTv() {
        val tvId = savedTvs.value.firstOrNull()?.id ?: return
        viewModelScope.launch {
            connectToTvUseCase(tvId)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            disconnectFromTvUseCase()
        }
    }

    fun sendRemoteKey(key: RemoteKey) {
        viewModelScope.launch {
            sendRemoteKeyUseCase(key)
        }
    }
}
