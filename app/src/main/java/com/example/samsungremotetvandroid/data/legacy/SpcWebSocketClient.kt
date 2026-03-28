package com.example.samsungremotetvandroid.data.legacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

internal class SpcWebSocketClient(
    private val httpClient: OkHttpClient,
    private val wsClient: OkHttpClient
) {
    data class SocketIoHandshake(
        val sessionId: String,
        val heartbeatIntervalSeconds: Long?
    )

    private val sendMutex = Mutex()

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var connected: Boolean = false

    @Volatile
    private var receivedFirstResponse: Boolean = false

    @Volatile
    private var firstCommandQueued: Boolean = false

    @Volatile
    private var staleCredentialsDetected: Boolean = false

    @Volatile
    private var lastDisconnectReason: String? = null

    private var heartbeatJob: kotlinx.coroutines.Job? = null

    suspend fun connect(ipAddress: String) {
        disconnect()

        val handshake = fetchSocketIoHandshake(ipAddress)
        val wsUrl = "ws://$ipAddress:8000/socket.io/1/websocket/${handshake.sessionId}"

        val openSignal = kotlinx.coroutines.CompletableDeferred<Unit>()
        val namespaceSignal = kotlinx.coroutines.CompletableDeferred<Unit>()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                webSocket.send("2::")
                webSocket.send("1::/com.samsung.companion")
                if (!openSignal.isCompleted) {
                    openSignal.complete(Unit)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("com.samsung.companion")) {
                    if (!namespaceSignal.isCompleted) {
                        namespaceSignal.complete(Unit)
                    }
                }

                if (text.startsWith("2::")) {
                    webSocket.send("2::")
                }

                if (text.contains("receiveCommon")) {
                    receivedFirstResponse = true
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                socket = null
                lastDisconnectReason = "closed(code=$code reason=$reason)"
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                socket = null
                lastDisconnectReason = t.message
                    ?: "failure(code=${response?.code})"
                if (!openSignal.isCompleted) {
                    openSignal.completeExceptionally(t)
                }
                if (!namespaceSignal.isCompleted) {
                    namespaceSignal.completeExceptionally(t)
                }
            }
        }

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        wsClient.newWebSocket(request, listener)

        try {
            kotlinx.coroutines.withTimeout(5_000) {
                openSignal.await()
            }
        } catch (error: Throwable) {
            disconnect()
            throw IllegalStateException(
                error.message ?: "SPC socket failed to open."
            )
        }

        kotlinx.coroutines.withTimeoutOrNull(2_000) {
            namespaceSignal.await()
        }

        connected = true
        staleCredentialsDetected = false
        receivedFirstResponse = false
        firstCommandQueued = false
        lastDisconnectReason = null
        startHeartbeat(handshake.heartbeatIntervalSeconds)
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null

        connected = false
        receivedFirstResponse = false
        firstCommandQueued = false
        staleCredentialsDetected = false
        lastDisconnectReason = null

        socket?.cancel()
        socket = null
    }

    suspend fun sendKey(
        keyCode: String,
        ctxUpperHex: String,
        sessionId: Int
    ) {
        if (!connected) {
            val reason = lastDisconnectReason?.takeIf { it.isNotBlank() }
            val suffix = if (reason != null) " ($reason)" else ""
            throw IllegalStateException("SPC socket is not connected$suffix")
        }
        if (staleCredentialsDetected) {
            throw IllegalStateException("SPC credentials are stale. Reconnect and pair again.")
        }

        val payload = SpcCrypto.generateCommand(
            ctxUpperHex = ctxUpperHex,
            sessionId = sessionId,
            keyCode = keyCode
        )

        sendMutex.withLock {
            val sent = socket?.send(payload) ?: false
            if (!sent) {
                throw IllegalStateException("Failed to send encrypted key on SPC socket.")
            }

            delay(100)
            firstCommandQueued = true
        }
    }

    private suspend fun fetchSocketIoHandshake(ipAddress: String): SocketIoHandshake {
        val timestamp = System.currentTimeMillis()
        val request = Request.Builder()
            .url("http://$ipAddress:8000/socket.io/1/?t=$timestamp")
            .get()
            .build()

        val body = withContext(Dispatchers.IO) {
            val response = httpClient.newCall(request).execute()
            response.use {
                if (response.code !in 200..299) {
                    throw IllegalStateException("Socket.IO handshake failed (${response.code}).")
                }
                response.body?.string().orEmpty()
            }
        }

        val parsed = parseSocketIoHandshake(body)
        if (parsed == null || parsed.sessionId.isBlank()) {
            throw IllegalStateException("Socket.IO session id is missing.")
        }
        return parsed
    }

    private fun startHeartbeat(heartbeatIntervalSeconds: Long?) {
        heartbeatJob?.cancel()
        val intervalMs = (heartbeatIntervalSeconds?.coerceIn(5, 30) ?: 15L) * 1_000L
        heartbeatJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            while (connected) {
                delay(intervalMs)
                socket?.send("2::")
            }
        }
    }

    internal fun parseSocketIoHandshake(payload: String): SocketIoHandshake? {
        val parts = payload.trim().split(':')
        if (parts.isEmpty()) {
            return null
        }
        val sid = parts.firstOrNull().orEmpty().trim()
        if (sid.isBlank()) {
            return null
        }
        val heartbeat = parts.getOrNull(1)?.trim()?.toLongOrNull()
        return SocketIoHandshake(
            sessionId = sid,
            heartbeatIntervalSeconds = heartbeat
        )
    }
}
