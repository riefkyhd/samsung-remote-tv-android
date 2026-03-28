package com.example.samsungremotetvandroid.data.legacy

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpcWebSocketClientTest {
    private val client = SpcWebSocketClient(
        httpClient = OkHttpClient(),
        wsClient = OkHttpClient()
    )

    @Test
    fun parseSocketIoHandshake_parsesSessionAndHeartbeat() {
        val parsed = client.parseSocketIoHandshake("abc123:10:60:websocket")

        assertNotNull(parsed)
        assertEquals("abc123", parsed?.sessionId)
        assertEquals(10L, parsed?.heartbeatIntervalSeconds)
    }

    @Test
    fun parseSocketIoHandshake_acceptsMissingHeartbeat() {
        val parsed = client.parseSocketIoHandshake("abc123::60:websocket")

        assertNotNull(parsed)
        assertEquals("abc123", parsed?.sessionId)
        assertNull(parsed?.heartbeatIntervalSeconds)
    }

    @Test
    fun parseSocketIoHandshake_returnsNullForInvalidPayload() {
        assertNull(client.parseSocketIoHandshake(""))
        assertNull(client.parseSocketIoHandshake(":10:60:websocket"))
    }
}
