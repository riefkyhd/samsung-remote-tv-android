package com.example.samsungremotetvandroid.presentation.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.samsungremotetvandroid.domain.usecase.ConnectToTvUseCase
import com.example.samsungremotetvandroid.domain.usecase.ObserveSavedTvsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val observeSavedTvsUseCase: ObserveSavedTvsUseCase,
    private val connectToTvUseCase: ConnectToTvUseCase
) : ViewModel() {
    val savedTvs = observeSavedTvsUseCase()

    fun connect(tvId: String) {
        viewModelScope.launch {
            connectToTvUseCase(tvId)
        }
    }
}
