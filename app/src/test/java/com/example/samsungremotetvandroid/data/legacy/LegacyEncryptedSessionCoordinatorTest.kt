package com.example.samsungremotetvandroid.data.legacy

import com.example.samsungremotetvandroid.domain.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyEncryptedSessionCoordinatorTest {
    private val coordinator = LegacyEncryptedSessionCoordinator()

    @Test
    fun connectWithStoredCredentials_emitsConnectedNotReadyAndAwaitsFirstCommand() {
        val transition = coordinator.onConnect(
            tvId = "legacy-tv-1",
            hasStoredCredentials = true
        )

        assertEquals(ConnectionState.Connecting, transition.emittedStates[0])
        assertEquals(
            ConnectionState.ConnectedNotReady("legacy-tv-1"),
            transition.emittedStates[1]
        )
        assertTrue(transition.awaitingFirstCommand)
        assertEquals(
            LegacyEncryptedSessionCoordinator.CredentialSource.STORED,
            transition.credentialSource
        )
    }

    @Test
    fun firstCommandWithStoredCredentials_fallsBackToPairingInSameFlow() {
        val transition = coordinator.onFirstCommand(
            tvId = "legacy-tv-1",
            source = LegacyEncryptedSessionCoordinator.CredentialSource.STORED
        )

        assertTrue(transition.emittedStates[0] is ConnectionState.Error)
        assertEquals(
            ConnectionState.Pairing(tvId = "legacy-tv-1", countdownSeconds = 30),
            transition.emittedStates[1]
        )
        assertEquals(
            ConnectionState.PinRequired(tvId = "legacy-tv-1", countdownSeconds = 60),
            transition.emittedStates[2]
        )
        assertFalse(transition.awaitingFirstCommand)
        assertEquals(null, transition.credentialSource)
    }

    @Test
    fun completePairing_emitsConnectedNotReadyWithFreshPairingSource() {
        val transition = coordinator.onPairingCompleted(tvId = "legacy-tv-1")

        assertEquals(
            listOf(ConnectionState.ConnectedNotReady("legacy-tv-1")),
            transition.emittedStates
        )
        assertTrue(transition.awaitingFirstCommand)
        assertEquals(
            LegacyEncryptedSessionCoordinator.CredentialSource.FRESH_PAIRING,
            transition.credentialSource
        )
    }
}
