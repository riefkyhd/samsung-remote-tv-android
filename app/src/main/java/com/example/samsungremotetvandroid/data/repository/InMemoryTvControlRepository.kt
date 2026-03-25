package com.example.samsungremotetvandroid.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsCategory
import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsTracker
import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.QuickLaunchShortcut
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.model.SamsungTv
import com.example.samsungremotetvandroid.domain.model.TvCapability
import com.example.samsungremotetvandroid.domain.model.TvProtocol
import com.example.samsungremotetvandroid.domain.repository.TvControlRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetAddress
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

@Singleton
class InMemoryTvControlRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticsTracker: DiagnosticsTracker
) : TvControlRepository {
    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val restClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .build()

    private val nsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val connectionMutex = Mutex()
    private val discoveryMutex = Mutex()

    private val savedTvsState = MutableStateFlow(
        listOf(
            SamsungTv(
                id = "living-room-tv",
                displayName = "Living Room TV",
                ipAddress = "192.168.1.20",
                protocol = TvProtocol.MODERN,
                capabilities = DEFAULT_CAPABILITIES
            )
        )
    )

    private val discoveredTvsState = MutableStateFlow<List<SamsungTv>>(emptyList())
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    private var activeTv: SamsungTv? = null
    private var activeSocket: WebSocket? = null
    private var readySignal: CompletableDeferred<Unit>? = null

    @Volatile
    private var readyForCommands: Boolean = false

    override val savedTvs: StateFlow<List<SamsungTv>> = savedTvsState.asStateFlow()
    override val discoveredTvs: StateFlow<List<SamsungTv>> = discoveredTvsState.asStateFlow()
    override val connectionState: StateFlow<ConnectionState> = connectionStateFlow.asStateFlow()

    override suspend fun scanDiscovery() {
        discoveryMutex.withLock {
            diagnosticsTracker.log(
                category = DiagnosticsCategory.LIFECYCLE,
                message = "discovery scan requested"
            )

            if (!isOnWifi()) {
                discoveredTvsState.value = emptyList()
                diagnosticsTracker.recordError(
                    context = "scan_discovery",
                    errorMessage = "discovery skipped: not on wifi"
                )
                return
            }

            val serviceTypes = listOf(
                "_samsungctl._tcp.",
                "_samsung-multiscreen._tcp."
            )

            val services = mutableListOf<NsdServiceInfo>()
            serviceTypes.forEach { serviceType ->
                services += discoverServices(serviceType)
            }

            val foundByIp = linkedMapOf<String, SamsungTv>()
            services.forEach { serviceInfo ->
                val ipAddress = resolveServiceIp(serviceInfo) ?: return@forEach
                if (foundByIp.containsKey(ipAddress)) {
                    return@forEach
                }

                val tv = fetchModernTvInfo(ipAddress) ?: return@forEach
                foundByIp[ipAddress] = tv
            }

            discoveredTvsState.value = foundByIp.values.toList()
            diagnosticsTracker.log(
                category = DiagnosticsCategory.LIFECYCLE,
                message = "discovery scan completed",
                metadata = mapOf("discoveredCount" to foundByIp.size.toString())
            )
        }
    }

    override suspend fun scanManualIp(ipAddress: String): SamsungTv {
        val normalizedIp = ipAddress.trim()
        diagnosticsTracker.log(
            category = DiagnosticsCategory.LIFECYCLE,
            message = "manual ip scan requested",
            metadata = mapOf("ipAddress" to normalizedIp)
        )

        if (!isOnWifi()) {
            diagnosticsTracker.recordError(
                context = "scan_manual_ip",
                errorMessage = "manual ip scan blocked: not on wifi",
                metadata = mapOf("ipAddress" to normalizedIp)
            )
            throw IllegalStateException("Connect to Wi-Fi, then try again.")
        }

        val tv = fetchModernTvInfo(normalizedIp)
            ?: run {
                diagnosticsTracker.recordError(
                    context = "scan_manual_ip",
                    errorMessage = "manual ip scan failed to match compatible tv",
                    metadata = mapOf("ipAddress" to normalizedIp)
                )
                throw IllegalStateException("Could not reach a compatible Samsung TV at that IP.")
            }

        discoveredTvsState.update { existing ->
            val withoutSameIp = existing.filterNot { it.ipAddress == tv.ipAddress }
            listOf(tv) + withoutSameIp
        }

        diagnosticsTracker.log(
            category = DiagnosticsCategory.LIFECYCLE,
            message = "manual ip scan matched tv",
            metadata = mapOf(
                "tvId" to tv.id,
                "ipAddress" to tv.ipAddress
            )
        )

        return tv
    }

    override suspend fun connect(tvId: String) {
        diagnosticsTracker.log(
            category = DiagnosticsCategory.RECONNECT,
            message = "connect requested",
            metadata = mapOf("tvId" to tvId)
        )

        val savedTv = savedTvsState.value.firstOrNull { it.id == tvId }
        val discoveredTv = discoveredTvsState.value.firstOrNull { it.id == tvId }
        val tv = savedTv ?: discoveredTv

        if (tv == null) {
            connectionStateFlow.value = ConnectionState.Error("Unable to connect: TV not found")
            diagnosticsTracker.recordError(
                context = "connect_tv_not_found",
                errorMessage = "unable to connect because tv was not found",
                metadata = mapOf("tvId" to tvId)
            )
            return
        }

        if (tv.protocol != TvProtocol.MODERN) {
            connectionStateFlow.value = ConnectionState.Error(
                "Only the modern TV path is available in this phase"
            )
            diagnosticsTracker.recordError(
                context = "connect_unsupported_protocol",
                errorMessage = "connect blocked because protocol is unsupported in this phase",
                metadata = mapOf(
                    "tvId" to tv.id,
                    "protocol" to tv.protocol.name
                )
            )
            return
        }

        val shouldPersistAfterConnect = savedTv == null

        connectionMutex.withLock {
            shutdownActiveSocket(setDisconnected = false)
            connectionStateFlow.value = ConnectionState.Connecting
            diagnosticsTracker.log(
                category = DiagnosticsCategory.RECONNECT,
                message = "connecting",
                metadata = mapOf("tvId" to tv.id)
            )

            val signal = CompletableDeferred<Unit>()
            readySignal = signal
            readyForCommands = false
            activeTv = tv

            val request = Request.Builder()
                .url(buildModernSocketUrl(tv.ipAddress))
                .build()

            activeSocket = wsClient.newWebSocket(
                request,
                modernSocketListener(tv.id, signal)
            )

            try {
                withTimeout(CONNECTION_READY_TIMEOUT_MS) {
                    signal.await()
                }

                if (shouldPersistAfterConnect && connectionStateFlow.value is ConnectionState.Ready) {
                    persistToSavedTvs(tv)
                }
            } catch (_: Throwable) {
                if (connectionStateFlow.value !is ConnectionState.Error) {
                    connectionStateFlow.value = ConnectionState.Error(
                        "Modern connection opened but did not become ready in time"
                    )
                    diagnosticsTracker.recordError(
                        context = "connect_ready_timeout",
                        errorMessage = "modern connection did not become ready in time",
                        metadata = mapOf("tvId" to tv.id)
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
        diagnosticsTracker.log(
            category = DiagnosticsCategory.LIFECYCLE,
            message = "disconnect requested"
        )
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
            diagnosticsTracker.recordError(
                context = "send_key_not_ready",
                errorMessage = "remote key blocked because connection is not ready",
                metadata = mapOf("key" to key.name)
            )
            return
        }

        val keyCode = modernKeyCodeFor(key)
        if (keyCode == null) {
            connectionStateFlow.value = ConnectionState.Error(
                "Unsupported key for modern path: $key"
            )
            diagnosticsTracker.recordError(
                context = "send_key_unsupported",
                errorMessage = "unsupported key for modern transport",
                metadata = mapOf("key" to key.name)
            )
            return
        }

        val sent = activeSocket?.send(buildRemoteKeyPayload(keyCode)) ?: false
        if (!sent) {
            connectionStateFlow.value = ConnectionState.Error(
                "Failed to send remote key on modern path"
            )
            diagnosticsTracker.recordError(
                context = "send_key_failed",
                errorMessage = "failed to send key over modern socket",
                metadata = mapOf("keyCode" to keyCode)
            )
        }
    }

    override suspend fun launchQuickLaunchApp(tvId: String, shortcut: QuickLaunchShortcut) {
        connectionStateFlow.value = ConnectionState.Error(
            "Quick Launch transport is not implemented in this modern baseline"
        )
        diagnosticsTracker.log(
            category = DiagnosticsCategory.CAPABILITIES,
            message = "quick launch transport not implemented",
            metadata = mapOf(
                "tvId" to tvId,
                "shortcut" to shortcut.title
            )
        )
    }

    override suspend fun forgetPairing(tvId: String) {
        diagnosticsTracker.log(
            category = DiagnosticsCategory.PAIRING,
            message = "forget pairing requested",
            metadata = mapOf("tvId" to tvId)
        )
        val activeTvId = activeTv?.id
        if (activeTvId == tvId) {
            disconnect()
        }
    }

    override suspend fun removeDevice(tvId: String) {
        diagnosticsTracker.log(
            category = DiagnosticsCategory.LIFECYCLE,
            message = "remove device requested",
            metadata = mapOf("tvId" to tvId)
        )
        val activeTvId = activeTv?.id
        val removedIp = savedTvsState.value.firstOrNull { it.id == tvId }?.ipAddress

        savedTvsState.update { tvs -> tvs.filterNot { it.id == tvId } }

        if (removedIp != null) {
            discoveredTvsState.update { tvs -> tvs.filterNot { it.ipAddress == removedIp } }
        }

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
        diagnosticsTracker.log(
            category = DiagnosticsCategory.LIFECYCLE,
            message = "rename tv requested",
            metadata = mapOf("tvId" to tvId)
        )
    }

    private suspend fun discoverServices(serviceType: String): List<NsdServiceInfo> {
        return withContext(Dispatchers.Main) {
            val found = mutableListOf<NsdServiceInfo>()
            val completed = CompletableDeferred<List<NsdServiceInfo>>()

            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) = Unit

                override fun onServiceFound(service: NsdServiceInfo) {
                    found += service
                }

                override fun onServiceLost(service: NsdServiceInfo) = Unit

                override fun onDiscoveryStopped(serviceType: String) {
                    if (!completed.isCompleted) {
                        completed.complete(found.toList())
                    }
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    runCatching { nsdManager.stopServiceDiscovery(this) }
                    if (!completed.isCompleted) {
                        completed.complete(emptyList())
                    }
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    runCatching { nsdManager.stopServiceDiscovery(this) }
                    if (!completed.isCompleted) {
                        completed.complete(found.toList())
                    }
                }
            }

            val started = runCatching {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }.isSuccess

            if (!started) {
                return@withContext emptyList()
            }

            val timeoutJob = launch {
                delay(DISCOVERY_SCAN_TIMEOUT_MS)
                runCatching { nsdManager.stopServiceDiscovery(listener) }
                if (!completed.isCompleted) {
                    completed.complete(found.toList())
                }
            }

            val result = completed.await()
            timeoutJob.cancel()
            result
        }
    }

    private suspend fun resolveServiceIp(serviceInfo: NsdServiceInfo): String? {
        return withContext(Dispatchers.Main) {
            val completed = CompletableDeferred<String?>()

            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (!completed.isCompleted) {
                        completed.complete(null)
                    }
                }

                override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                    val normalizedIp = normalizeIpv4(resolvedServiceInfo.host?.hostAddress)
                    if (!completed.isCompleted) {
                        completed.complete(normalizedIp)
                    }
                }
            }

            val started = runCatching {
                nsdManager.resolveService(serviceInfo, listener)
            }.isSuccess

            if (!started) {
                return@withContext null
            }

            try {
                withTimeout(RESOLVE_TIMEOUT_MS) {
                    completed.await()
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    private suspend fun fetchModernTvInfo(ipAddress: String): SamsungTv? {
        val normalizedIp = normalizeIpv4(ipAddress) ?: return null

        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("http://$normalizedIp:8001/api/v2/")
                .build()

            val response = runCatching {
                restClient.newCall(request).execute()
            }.getOrNull() ?: return@withContext null

            response.use {
                if (!response.isSuccessful) {
                    return@withContext null
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext null
                }

                val payload = runCatching { JSONObject(body) }.getOrNull()
                    ?: return@withContext null
                val device = payload.optJSONObject("device")
                    ?: return@withContext null

                val deviceName = device.optString("name").ifBlank { "Samsung TV" }
                val modelName = device.optString("modelName").ifBlank { "Samsung TV" }
                val displayName = if (deviceName.equals("Samsung TV", ignoreCase = true)) {
                    modelName
                } else {
                    deviceName
                }

                SamsungTv(
                    id = tvIdForIp(normalizedIp),
                    displayName = displayName,
                    ipAddress = normalizedIp,
                    protocol = TvProtocol.MODERN,
                    capabilities = DEFAULT_CAPABILITIES
                )
            }
        }
    }

    private fun persistToSavedTvs(tv: SamsungTv) {
        savedTvsState.update { current ->
            val existingIndex = current.indexOfFirst {
                it.id == tv.id || it.ipAddress == tv.ipAddress
            }

            if (existingIndex >= 0) {
                current.toMutableList().apply {
                    val existing = this[existingIndex]
                    this[existingIndex] = tv.copy(id = existing.id)
                }
            } else {
                current + tv
            }
        }

        discoveredTvsState.update { current ->
            current.filterNot { it.ipAddress == tv.ipAddress }
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
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.LIFECYCLE,
                    message = "modern socket opened",
                    metadata = mapOf("tvId" to tvId)
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (activeTv?.id != tvId) return

                when (extractEventName(text)) {
                    EVENT_CHANNEL_CONNECT,
                    EVENT_CHANNEL_READY -> {
                        readyForCommands = true
                        connectionStateFlow.value = ConnectionState.Ready(tvId)
                        diagnosticsTracker.log(
                            category = DiagnosticsCategory.LIFECYCLE,
                            message = "modern channel is ready",
                            metadata = mapOf("tvId" to tvId)
                        )
                        if (!signal.isCompleted) {
                            signal.complete(Unit)
                        }
                    }

                    EVENT_CHANNEL_UNAUTHORIZED -> {
                        handleSocketFailure(
                            tvId = tvId,
                            signal = signal,
                            context = "socket_unauthorized",
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
                    diagnosticsTracker.recordError(
                        context = "socket_closed_before_ready",
                        errorMessage = "modern connection closed before ready",
                        metadata = mapOf("tvId" to tvId)
                    )
                }

                readyForCommands = false
                activeSocket = null
                activeTv = null

                if (connectionStateFlow.value !is ConnectionState.Error) {
                    connectionStateFlow.value = ConnectionState.Disconnected
                    diagnosticsTracker.log(
                        category = DiagnosticsCategory.LIFECYCLE,
                        message = "modern socket closed",
                        metadata = mapOf("tvId" to tvId)
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleSocketFailure(
                    tvId = tvId,
                    signal = signal,
                    context = "socket_failure",
                    message = t.message ?: "Unknown modern connection failure"
                )
            }
        }
    }

    private fun handleSocketFailure(
        tvId: String,
        signal: CompletableDeferred<Unit>,
        context: String,
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
        diagnosticsTracker.recordError(
            context = context,
            errorMessage = message,
            metadata = mapOf("tvId" to tvId)
        )
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

    private fun isOnWifi(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun normalizeIpv4(rawAddress: String?): String? {
        val candidate = rawAddress?.substringBefore('%')?.trim().orEmpty()
        if (candidate.isBlank()) {
            return null
        }

        val parsed = runCatching { InetAddress.getByName(candidate) }.getOrNull() ?: return null
        val normalized = parsed.hostAddress?.substringBefore('%') ?: return null

        return if (normalized.contains(':')) {
            null
        } else {
            normalized
        }
    }

    private fun tvIdForIp(ipAddress: String): String {
        return "tv-" + ipAddress.replace('.', '-')
    }

    companion object {
        private const val CONNECTION_READY_TIMEOUT_MS = 6_000L
        private const val DISCOVERY_SCAN_TIMEOUT_MS = 4_500L
        private const val RESOLVE_TIMEOUT_MS = 1_500L
        private const val MODERN_CLIENT_NAME = "Samsung Remote TV Android"

        private const val EVENT_CHANNEL_CONNECT = "ms.channel.connect"
        private const val EVENT_CHANNEL_READY = "ms.channel.ready"
        private const val EVENT_CHANNEL_UNAUTHORIZED = "ms.channel.unauthorized"

        private val DEFAULT_CAPABILITIES = setOf(
            TvCapability.D_PAD,
            TvCapability.VOLUME,
            TvCapability.MEDIA,
            TvCapability.POWER,
            TvCapability.QUICK_LAUNCH
        )
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
