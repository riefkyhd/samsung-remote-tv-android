package com.example.samsungremotetvandroid.data.repository

import android.util.Base64
import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.QuickLaunchShortcut
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.model.SamsungTv
import com.example.samsungremotetvandroid.domain.model.TvCapability
import com.example.samsungremotetvandroid.domain.model.TvProtocol
import com.example.samsungremotetvandroid.domain.repository.TvControlRepository
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

@Singleton
class InMemoryTvControlRepository @Inject constructor() : TvControlRepository {
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val connectionMutex = Mutex()

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

    private var activeTv: SamsungTv? = null
    private var activeSocket: WebSocket? = null
    private var readySignal: CompletableDeferred<Unit>? = null

    @Volatile
    private var readyForCommands: Boolean = false

    override val savedTvs: StateFlow<List<SamsungTv>> = savedTvsState.asStateFlow()
    override val connectionState: StateFlow<ConnectionState> = connectionStateFlow.asStateFlow()

    override suspend fun connect(tvId: String) {
        val tv = savedTvsState.value.firstOrNull { it.id == tvId }
        if (tv == null) {
            connectionStateFlow.value = ConnectionState.Error("Unable to connect: TV not found")
            return
        }

        if (tv.protocol != TvProtocol.MODERN) {
            connectionStateFlow.value = ConnectionState.Error(
                "Only the modern TV path is available in this phase"
            )
            return
        }

        connectionMutex.withLock {
            shutdownActiveSocket(setDisconnected = false)
            connectionStateFlow.value = ConnectionState.Connecting

            val signal = CompletableDeferred<Unit>()
            readySignal = signal
            readyForCommands = false
            activeTv = tv

            val request = Request.Builder()
                .url(buildModernSocketUrl(tv.ipAddress))
                .build()

            activeSocket = okHttpClient.newWebSocket(
                request,
                modernSocketListener(tv.id, signal)
            )

            try {
                withTimeout(CONNECTION_READY_TIMEOUT_MS) {
                    signal.await()
                }
            } catch (_: Throwable) {
                if (connectionStateFlow.value !is ConnectionState.Error) {
                    connectionStateFlow.value = ConnectionState.Error(
                        "Modern connection opened but did not become ready in time"
                    )
                }
                shutdownActiveSocket(setDisconnected = false)
            } finally {
                if (readySignal === signal) {
                    readySignal = null
                }
            }
        }
    }

    override suspend fun disconnect() {
        connectionMutex.withLock {
            readySignal?.cancel()
            readySignal = null
            shutdownActiveSocket(setDisconnected = true)
        }
    }

    override suspend fun sendRemoteKey(key: RemoteKey) {
        if (!readyForCommands || connectionStateFlow.value !is ConnectionState.Ready) {
            connectionStateFlow.value = ConnectionState.Error(
                "Remote input is unavailable until the TV is ready"
            )
            return
        }

        val keyCode = modernKeyCodeFor(key)
        if (keyCode == null) {
            connectionStateFlow.value = ConnectionState.Error(
                "Unsupported key for modern path: $key"
            )
            return
        }

        val sent = activeSocket?.send(buildRemoteKeyPayload(keyCode)) ?: false
        if (!sent) {
            connectionStateFlow.value = ConnectionState.Error(
                "Failed to send remote key on modern path"
            )
        }
    }

    override suspend fun launchQuickLaunchApp(tvId: String, shortcut: QuickLaunchShortcut) {
        connectionStateFlow.value = ConnectionState.Error(
            "Quick Launch transport is not implemented in this modern baseline"
        )
    }

    override suspend fun forgetPairing(tvId: String) {
        val activeTvId = activeTv?.id
        if (activeTvId == tvId) {
            disconnect()
        }
    }

    override suspend fun removeDevice(tvId: String) {
        val activeTvId = activeTv?.id
        savedTvsState.update { tvs -> tvs.filterNot { it.id == tvId } }

        if (activeTvId == tvId) {
            disconnect()
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

    private fun modernSocketListener(
        tvId: String,
        signal: CompletableDeferred<Unit>
    ): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (activeTv?.id != tvId) return
                connectionStateFlow.value = ConnectionState.ConnectedNotReady(tvId)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (activeTv?.id != tvId) return

                when (extractEventName(text)) {
                    EVENT_CHANNEL_CONNECT,
                    EVENT_CHANNEL_READY -> {
                        readyForCommands = true
                        connectionStateFlow.value = ConnectionState.Ready(tvId)
                        if (!signal.isCompleted) {
                            signal.complete(Unit)
                        }
                    }

                    EVENT_CHANNEL_UNAUTHORIZED -> {
                        handleSocketFailure(
                            tvId = tvId,
                            signal = signal,
                            message = "TV rejected pairing/authorization on modern path"
                        )
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (activeTv?.id != tvId) return

                if (!signal.isCompleted && !readyForCommands) {
                    signal.completeExceptionally(
                        IllegalStateException("Modern connection closed before ready")
                    )
                }

                readyForCommands = false
                activeSocket = null
                activeTv = null

                if (connectionStateFlow.value !is ConnectionState.Error) {
                    connectionStateFlow.value = ConnectionState.Disconnected
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleSocketFailure(
                    tvId = tvId,
                    signal = signal,
                    message = t.message ?: "Unknown modern connection failure"
                )
            }
        }
    }

    private fun handleSocketFailure(
        tvId: String,
        signal: CompletableDeferred<Unit>,
        message: String
    ) {
        if (activeTv?.id != tvId) return

        if (!signal.isCompleted) {
            signal.completeExceptionally(IllegalStateException(message))
        }

        readyForCommands = false
        activeSocket = null
        activeTv = null
        connectionStateFlow.value = ConnectionState.Error(message)
    }

    private fun shutdownActiveSocket(setDisconnected: Boolean) {
        activeSocket?.cancel()
        activeSocket = null
        activeTv = null
        readyForCommands = false

        if (setDisconnected) {
            connectionStateFlow.value = ConnectionState.Disconnected
        }
    }

    private fun buildModernSocketUrl(ipAddress: String): String {
        val encodedName = URLEncoder.encode(
            Base64.encodeToString(
                MODERN_CLIENT_NAME.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            ),
            StandardCharsets.UTF_8.name()
        )

        return "ws://$ipAddress:8001/api/v2/channels/samsung.remote.control?name=$encodedName"
    }

    private fun buildRemoteKeyPayload(keyCode: String): String {
        val params = JSONObject()
            .put("Cmd", "Click")
            .put("DataOfCmd", keyCode)
            .put("Option", "false")
            .put("TypeOfRemote", "SendRemoteKey")

        return JSONObject()
            .put("method", "ms.remote.control")
            .put("params", params)
            .toString()
    }

    private fun extractEventName(rawMessage: String): String? {
        return runCatching {
            val payload = JSONObject(rawMessage)
            val event = payload.optString("event")
            if (event.isNotBlank()) {
                event
            } else {
                payload.optString("method").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    companion object {
        private const val CONNECTION_READY_TIMEOUT_MS = 6_000L
        private const val MODERN_CLIENT_NAME = "Samsung Remote TV Android"

        private const val EVENT_CHANNEL_CONNECT = "ms.channel.connect"
        private const val EVENT_CHANNEL_READY = "ms.channel.ready"
        private const val EVENT_CHANNEL_UNAUTHORIZED = "ms.channel.unauthorized"
    }
}

internal fun modernKeyCodeFor(key: RemoteKey): String? {
    return when (key) {
        RemoteKey.D_PAD_UP -> "KEY_UP"
        RemoteKey.D_PAD_DOWN -> "KEY_DOWN"
        RemoteKey.D_PAD_LEFT -> "KEY_LEFT"
        RemoteKey.D_PAD_RIGHT -> "KEY_RIGHT"
        RemoteKey.OK -> "KEY_ENTER"
        RemoteKey.VOLUME_UP -> "KEY_VOLUP"
        RemoteKey.VOLUME_DOWN -> "KEY_VOLDOWN"
        RemoteKey.MUTE -> "KEY_MUTE"
        RemoteKey.MEDIA_PLAY_PAUSE -> "KEY_PLAY_PAUSE"
        RemoteKey.POWER -> "KEY_POWER"
    }
}
