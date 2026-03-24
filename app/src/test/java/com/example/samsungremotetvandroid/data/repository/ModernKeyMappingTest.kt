package com.example.samsungremotetvandroid.data.repository

import com.example.samsungremotetvandroid.domain.model.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ModernKeyMappingTest {
    @Test
    fun modernKeyCodeFor_mapsExpectedRemoteKeys() {
        assertEquals("KEY_UP", modernKeyCodeFor(RemoteKey.D_PAD_UP))
        assertEquals("KEY_DOWN", modernKeyCodeFor(RemoteKey.D_PAD_DOWN))
        assertEquals("KEY_LEFT", modernKeyCodeFor(RemoteKey.D_PAD_LEFT))
        assertEquals("KEY_RIGHT", modernKeyCodeFor(RemoteKey.D_PAD_RIGHT))
        assertEquals("KEY_ENTER", modernKeyCodeFor(RemoteKey.OK))
        assertEquals("KEY_VOLUP", modernKeyCodeFor(RemoteKey.VOLUME_UP))
        assertEquals("KEY_VOLDOWN", modernKeyCodeFor(RemoteKey.VOLUME_DOWN))
        assertEquals("KEY_MUTE", modernKeyCodeFor(RemoteKey.MUTE))
        assertEquals("KEY_PLAY_PAUSE", modernKeyCodeFor(RemoteKey.MEDIA_PLAY_PAUSE))
        assertEquals("KEY_POWER", modernKeyCodeFor(RemoteKey.POWER))
    }
}
