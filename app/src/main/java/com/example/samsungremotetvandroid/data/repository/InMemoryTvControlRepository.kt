package com.example.samsungremotetvandroid.data.repository

import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.QuickLaunchShortcut
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.model.SamsungTv
import com.example.samsungremotetvandroid.domain.model.TvCapability
import com.example.samsungremotetvandroid.domain.model.TvProtocol
import com.example.samsungremotetvandroid.domain.repository.TvControlRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryTvControlRepository @Inject constructor() : TvControlRepository {
    private val savedTvsState = MutableStateFlow(
        listOf(
            SamsungTv(
                id = "living-room-tv",
                displayName = "Living Room TV",
                ipAddress = "192.168.1.20",
                protocol = TvProtocol.MODERN,
                capabilities = setOf(
                    TvCapability.D_PAD,
                    TvCapability.VOLUME,
                    TvCapability.MEDIA,
                    TvCapability.POWER,
                    TvCapability.QUICK_LAUNCH
                )
            )
        )
    )

    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    override val savedTvs: StateFlow<List<SamsungTv>> = savedTvsState.asStateFlow()
    override val connectionState: StateFlow<ConnectionState> = connectionStateFlow.asStateFlow()

    override suspend fun connect(tvId: String) {
        val tv = savedTvsState.value.firstOrNull { it.id == tvId }
        if (tv == null) {
            connectionStateFlow.value = ConnectionState.Error("Unable to connect: TV not found")
            return
        }

        connectionStateFlow.value = ConnectionState.Connecting
        delay(150)
        connectionStateFlow.value = ConnectionState.ConnectedNotReady(tv.id)
        delay(150)
        connectionStateFlow.value = ConnectionState.Ready(tv.id)
    }

    override suspend fun disconnect() {
        connectionStateFlow.value = ConnectionState.Disconnected
    }

    override suspend fun sendRemoteKey(key: RemoteKey) {
        if (connectionStateFlow.value !is ConnectionState.Ready) {
            connectionStateFlow.value = ConnectionState.Error(
                "Remote input is unavailable until the TV is ready"
            )
        }
    }

    override suspend fun launchQuickLaunchApp(tvId: String, shortcut: QuickLaunchShortcut) {
        if (connectionStateFlow.value !is ConnectionState.Ready) {
            connectionStateFlow.value = ConnectionState.Error(
                "Quick Launch is unavailable until the TV is ready"
            )
        }
    }

    override suspend fun forgetPairing(tvId: String) {
        if (connectionStateFlow.value is ConnectionState.Ready ||
            connectionStateFlow.value is ConnectionState.ConnectedNotReady
        ) {
            connectionStateFlow.value = ConnectionState.Disconnected
        }
    }

    override suspend fun removeDevice(tvId: String) {
        savedTvsState.update { tvs -> tvs.filterNot { it.id == tvId } }

        val activeTvId = when (val currentState = connectionStateFlow.value) {
            is ConnectionState.ConnectedNotReady -> currentState.tvId
            is ConnectionState.Ready -> currentState.tvId
            else -> null
        }

        if (activeTvId == tvId) {
            connectionStateFlow.value = ConnectionState.Disconnected
        }
    }

    override suspend fun renameTv(tvId: String, newName: String) {
        val normalizedName = newName.trim()
        if (normalizedName.isEmpty()) {
            return
        }

        savedTvsState.update { tvs ->
            tvs.map { tv ->
                if (tv.id == tvId) tv.copy(displayName = normalizedName) else tv
            }
        }
    }
}
