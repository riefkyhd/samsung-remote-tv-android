package com.example.samsungremotetvandroid.domain.usecase

import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.QuickLaunchShortcut
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.model.SamsungTv
import com.example.samsungremotetvandroid.domain.model.TvCapability
import com.example.samsungremotetvandroid.domain.model.TvProtocol
import com.example.samsungremotetvandroid.domain.repository.TvControlRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryBoundaryUseCaseTest {
    @Test
    fun forgetPairingUseCase_clearsPairingStateWithoutRemovingSavedTv() = runBlocking {
        val repository = FakeTvControlRepository()
        val useCase = ForgetPairingUseCase(repository)

        val tvId = repository.savedTvs.value.first().id
        useCase(tvId)

        assertEquals(1, repository.forgottenTvIds.size)
        assertEquals(tvId, repository.forgottenTvIds.first())
        assertEquals(1, repository.savedTvs.value.size)
    }

    @Test
    fun removeDeviceUseCase_removesSavedTvAndTracksRemoval() = runBlocking {
        val repository = FakeTvControlRepository()
        val useCase = RemoveDeviceUseCase(repository)

        val tvId = repository.savedTvs.value.first().id
        useCase(tvId)

        assertEquals(1, repository.removedTvIds.size)
        assertEquals(tvId, repository.removedTvIds.first())
        assertTrue(repository.savedTvs.value.isEmpty())
    }
}

private class FakeTvControlRepository : TvControlRepository {
    private val testTv = SamsungTv(
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

    private val savedTvsFlow = MutableStateFlow(listOf(testTv))
    private val discoveredTvsFlow = MutableStateFlow(emptyList<SamsungTv>())
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    val forgottenTvIds = mutableListOf<String>()
    val removedTvIds = mutableListOf<String>()

    override val savedTvs: StateFlow<List<SamsungTv>> = savedTvsFlow.asStateFlow()
    override val discoveredTvs: StateFlow<List<SamsungTv>> = discoveredTvsFlow.asStateFlow()
    override val connectionState: StateFlow<ConnectionState> = connectionStateFlow.asStateFlow()

    override suspend fun scanDiscovery() = Unit

    override suspend fun scanManualIp(ipAddress: String): SamsungTv {
        return testTv
    }

    override suspend fun connect(tvId: String) = Unit

    override suspend fun completeEncryptedPairing(tvId: String, pin: String) = Unit

    override suspend fun cancelEncryptedPairing(tvId: String) = Unit

    override suspend fun disconnect() = Unit

    override suspend fun sendRemoteKey(key: RemoteKey) = Unit

    override suspend fun launchQuickLaunchApp(tvId: String, shortcut: QuickLaunchShortcut) = Unit

    override suspend fun forgetPairing(tvId: String) {
        forgottenTvIds += tvId
    }

    override suspend fun removeDevice(tvId: String) {
        removedTvIds += tvId
        savedTvsFlow.update { tvs ->
            tvs.filterNot { it.id == tvId }
        }
    }

    override suspend fun renameTv(tvId: String, newName: String) = Unit
}
