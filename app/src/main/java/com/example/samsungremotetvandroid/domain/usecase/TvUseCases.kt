package com.example.samsungremotetvandroid.domain.usecase

import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.QuickLaunchShortcut
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.model.SamsungTv
import com.example.samsungremotetvandroid.domain.repository.TvControlRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveSavedTvsUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    operator fun invoke(): StateFlow<List<SamsungTv>> = repository.savedTvs
}

class ObserveDiscoveredTvsUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    operator fun invoke(): StateFlow<List<SamsungTv>> = repository.discoveredTvs
}

class ObserveConnectionStateUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    operator fun invoke(): StateFlow<ConnectionState> = repository.connectionState
}

class ScanDiscoveryUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    suspend operator fun invoke() = repository.scanDiscovery()
}

class ScanManualIpUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    suspend operator fun invoke(ipAddress: String): SamsungTv = repository.scanManualIp(ipAddress)
}

class ConnectToTvUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    suspend operator fun invoke(tvId: String) = repository.connect(tvId)
}

class CompleteEncryptedPairingUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    suspend operator fun invoke(tvId: String, pin: String) {
        repository.completeEncryptedPairing(tvId = tvId, pin = pin)
    }
}

class CancelEncryptedPairingUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    suspend operator fun invoke(tvId: String) = repository.cancelEncryptedPairing(tvId)
}

class DisconnectFromTvUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    suspend operator fun invoke() = repository.disconnect()
}

class SendRemoteKeyUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    suspend operator fun invoke(key: RemoteKey) = repository.sendRemoteKey(key)
}

class LaunchQuickLaunchShortcutUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    suspend operator fun invoke(tvId: String, shortcut: QuickLaunchShortcut) {
        repository.launchQuickLaunchApp(tvId, shortcut)
    }
}

class ForgetPairingUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    suspend operator fun invoke(tvId: String) = repository.forgetPairing(tvId)
}

class RemoveDeviceUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    suspend operator fun invoke(tvId: String) = repository.removeDevice(tvId)
}

class RenameTvUseCase @Inject constructor(
    private val repository: TvControlRepository
) {
    suspend operator fun invoke(tvId: String, newName: String) = repository.renameTv(tvId, newName)
}
