package com.example.samsungremotetvandroid.presentation.remote

import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.model.TvCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCapabilityPolicyTest {
    @Test
    fun capabilityForKey_mapsRemoteKeysToExpectedCapabilities() {
        assertEquals(TvCapability.D_PAD, capabilityForKey(RemoteKey.D_PAD_UP))
        assertEquals(TvCapability.VOLUME, capabilityForKey(RemoteKey.MUTE))
        assertEquals(TvCapability.MEDIA, capabilityForKey(RemoteKey.MEDIA_PLAY_PAUSE))
        assertEquals(TvCapability.POWER, capabilityForKey(RemoteKey.POWER))
    }

    @Test
    fun activeTvId_returnsNullForNonTvBoundStates() {
        assertNull(activeTvId(ConnectionState.Disconnected))
        assertNull(activeTvId(ConnectionState.Connecting))
        assertNull(activeTvId(ConnectionState.Error("error")))
    }

    @Test
    fun activeTvId_returnsTvIdForConnectionBoundStates() {
        assertEquals("tv-ready", activeTvId(ConnectionState.Ready("tv-ready")))
        assertEquals("tv-connected", activeTvId(ConnectionState.ConnectedNotReady("tv-connected")))
        assertEquals("tv-pin", activeTvId(ConnectionState.PinRequired("tv-pin", 60)))
        assertEquals("tv-pairing", activeTvId(ConnectionState.Pairing("tv-pairing", 30)))
    }

    @Test
    fun unsupportedCapabilityMessage_isTruthful() {
        val message = unsupportedMessageForCapability(TvCapability.QUICK_LAUNCH)
        assertTrue(message.contains("not supported"))
    }
}
