package com.example.samsungremotetvandroid.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Base64
import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsCategory
import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsTracker
import com.example.samsungremotetvandroid.data.legacy.LegacyTcpRemoteClient
import com.example.samsungremotetvandroid.data.legacy.LegacyEncryptedSessionCoordinator
import com.example.samsungremotetvandroid.data.legacy.SpcHandshakeClient
import com.example.samsungremotetvandroid.data.legacy.SpcWebSocketClient
import com.example.samsungremotetvandroid.data.storage.SpcCredentials
import com.example.samsungremotetvandroid.data.storage.SpcVariants
import com.example.samsungremotetvandroid.data.storage.SensitivePairingStorage
import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.QuickLaunchShortcut
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.model.SamsungTv
import com.example.samsungremotetvandroid.domain.model.TvCapability
import com.example.samsungremotetvandroid.domain.model.TvProtocol
import com.example.samsungremotetvandroid.domain.repository.TvControlRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetAddress
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
    private val diagnosticsTracker: DiagnosticsTracker,
    private val sensitivePairingStorage: SensitivePairingStorage
) : TvControlRepository {
    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val wsSecureClient = buildTrustingWsClient()

    private val restClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .build()

    private val restProbeClient = restClient.newBuilder()
        .connectTimeout(450, TimeUnit.MILLISECONDS)
        .readTimeout(450, TimeUnit.MILLISECONDS)
        .writeTimeout(450, TimeUnit.MILLISECONDS)
        .build()

    private val restSpcClient = restClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val nsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val connectionMutex = Mutex()
    private val discoveryMutex = Mutex()
    private val legacyCoordinator = LegacyEncryptedSessionCoordinator()
    private val legacyTcpClient = LegacyTcpRemoteClient()
    private val spcHandshakeClient = SpcHandshakeClient(restSpcClient)
    private val spcWebSocketClient = SpcWebSocketClient(
        httpClient = restSpcClient,
        wsClient = wsClient
    )

    private val savedTvsState = MutableStateFlow<List<SamsungTv>>(emptyList())

    private val discoveredTvsState = MutableStateFlow<List<SamsungTv>>(emptyList())
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    private var activeTv: SamsungTv? = null
    private var activeSocket: WebSocket? = null
    private var readySignal: CompletableDeferred<Unit>? = null
    private var activeTransport: ActiveTransport = ActiveTransport.MODERN
    private var activeLegacyCredentialsSource: LegacyEncryptedSessionCoordinator.CredentialSource? = null
    private var awaitingLegacyFirstCommand = false
    private var activeLegacyPairing = false
    private var activeModernCommandMode: ModernCommandMode = ModernCommandMode.REMOTE_CONTROL
    private var activeModernSessionId: String? = null
    private var modernSessionRefreshAttempted: Boolean = false
    private var activeSpcCredentials: SpcCredentials? = null
    private var activeSpcIdentifier: String? = null

    @Volatile
    private var readyForCommands: Boolean = false

    @Volatile
    private var modernUnauthorizedEvent: Boolean = false

    @Volatile
    private var modernLastErrorMessage: String? = null

    private enum class ActiveTransport {
        MODERN,
        LEGACY_ENCRYPTED
    }

    private enum class ModernCommandMode {
        REMOTE_CONTROL,
        EMIT_KEYPRESS,
        EMIT_KEYPRESS_WITH_SESSION,
        EMIT_REMOTE_CONTROL_WITH_SESSION
    }

    private data class ModernConnectAttempt(
        val label: String,
        val url: String,
        val usesToken: Boolean,
        val client: OkHttpClient
    )

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

            val foundByIp = linkedMapOf<String, SamsungTv>()
            var usedFallback = false
            discoverViaNsd().forEach { serviceInfo ->
                val ipAddress = resolveServiceIp(serviceInfo) ?: return@forEach
                if (foundByIp.containsKey(ipAddress)) {
                    return@forEach
                }

                val tv = fetchTvInfoWithFallback(
                    ipAddress = ipAddress,
                    fallbackDisplayName = serviceInfo.serviceName
                ) ?: return@forEach
                foundByIp[ipAddress] = tv
            }

            if (foundByIp.isEmpty()) {
                usedFallback = true
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.LIFECYCLE,
                    message = "nsd discovery empty, running subnet fallback"
                )
                val fallbackByIp = discoverViaSubnetFallback()
                fallbackByIp.forEach { tv ->
                    foundByIp[tv.ipAddress] = tv
                }
            }

            discoveredTvsState.value = foundByIp.values.toList()
            diagnosticsTracker.log(
                category = DiagnosticsCategory.LIFECYCLE,
                message = "discovery scan completed",
                metadata = mapOf(
                    "discoveredCount" to foundByIp.size.toString(),
                    "fallbackUsed" to usedFallback.toString()
                )
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

        val tv = fetchTvInfoWithFallback(normalizedIp)
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
        val tv = when {
            savedTv != null && discoveredTv != null -> discoveredTv.copy(
                id = savedTv.id,
                displayName = savedTv.displayName.takeIf { it.isNotBlank() } ?: discoveredTv.displayName
            )
            discoveredTv != null -> discoveredTv
            else -> savedTv
        }

        if (tv == null) {
            connectionStateFlow.value = ConnectionState.Error("Unable to connect: TV not found")
            diagnosticsTracker.recordError(
                context = "connect_tv_not_found",
                errorMessage = "unable to connect because tv was not found",
                metadata = mapOf("tvId" to tvId)
            )
            return
        }

        var shouldPersistAfterConnect = if (savedTv == null) {
            true
        } else {
            discoveredTv != null && (
                savedTv.protocol != discoveredTv.protocol ||
                    savedTv.modelName != discoveredTv.modelName ||
                    savedTv.macAddress != discoveredTv.macAddress
                )
        }

        val effectiveProtocol = when (tv.protocol) {
            TvProtocol.MODERN -> {
                if (shouldPreferLegacyEncrypted(tv)) {
                    diagnosticsTracker.log(
                        category = DiagnosticsCategory.PROTOCOL,
                        message = "connect routing modern->legacy_encrypted for legacy model/signature",
                        metadata = mapOf("tvId" to tv.id)
                    )
                    TvProtocol.LEGACY_ENCRYPTED
                } else {
                    TvProtocol.MODERN
                }
            }
            else -> tv.protocol
        }
        val effectiveTv = if (tv.protocol != effectiveProtocol) {
            tv.copy(protocol = effectiveProtocol)
        } else {
            tv
        }
        if (savedTv != null && savedTv.protocol != effectiveProtocol) {
            shouldPersistAfterConnect = true
        }

        when (effectiveProtocol) {
            TvProtocol.MODERN -> connectModern(
                tv = effectiveTv,
                shouldPersistAfterConnect = shouldPersistAfterConnect
            )

            TvProtocol.LEGACY_ENCRYPTED -> connectLegacyEncrypted(
                tv = effectiveTv,
                shouldPersistAfterConnect = shouldPersistAfterConnect
            )

            TvProtocol.LEGACY_REMOTE -> {
                val encryptedFallbackCandidate = effectiveTv.copy(protocol = TvProtocol.LEGACY_ENCRYPTED)
                if (shouldPreferLegacyEncrypted(encryptedFallbackCandidate)) {
                    diagnosticsTracker.log(
                        category = DiagnosticsCategory.PROTOCOL,
                        message = "legacy remote protocol selected; routing to encrypted pairing path",
                        metadata = mapOf("tvId" to tv.id)
                    )
                    connectLegacyEncrypted(
                        tv = encryptedFallbackCandidate,
                        shouldPersistAfterConnect = true
                    )
                } else {
                    val message = "This TV appears to require the old legacy TCP remote protocol, which is unsupported in this Android baseline."
                    connectionStateFlow.value = ConnectionState.Error(message)
                    diagnosticsTracker.recordError(
                        context = "connect_legacy_remote_unsupported",
                        errorMessage = message,
                        metadata = mapOf("tvId" to tv.id)
                    )
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
            activeLegacyPairing = false
            awaitingLegacyFirstCommand = false
            activeLegacyCredentialsSource = null
            legacyTcpClient.disconnect()
            shutdownActiveSocket(setDisconnected = true)
        }
    }

    override suspend fun completeEncryptedPairing(tvId: String, pin: String) {
        val sanitizedPin = pin.trim()
        if (sanitizedPin.isEmpty()) {
            connectionStateFlow.value = ConnectionState.Error("Enter the PIN shown on your TV.")
            diagnosticsTracker.recordError(
                context = "complete_encrypted_pairing",
                errorMessage = "pin submission blocked because pin is empty",
                metadata = mapOf("tvId" to tvId)
            )
            return
        }

        var tvToReconnect: SamsungTv? = null
        var pairingIdentifier: String? = null
        connectionMutex.withLock {
            val tv = activeTv
            if (tv == null || tv.id != tvId || tv.protocol != TvProtocol.LEGACY_ENCRYPTED) {
                connectionStateFlow.value = ConnectionState.Error(
                    "Encrypted pairing is not active for this TV."
                )
                diagnosticsTracker.recordError(
                    context = "complete_encrypted_pairing",
                    errorMessage = "pairing completion ignored because active tv does not match",
                    metadata = mapOf("tvId" to tvId)
                )
                return
            }

            val transition = legacyCoordinator.onPairingCompleted(tvId = tvId)
            activeLegacyPairing = false
            awaitingLegacyFirstCommand = false
            activeLegacyCredentialsSource = null
            transition.emittedStates.lastOrNull()?.let { state ->
                connectionStateFlow.value = state
            }
            tvToReconnect = tv
            pairingIdentifier = pairingIdentifierFor(tv)

            diagnosticsTracker.log(
                category = DiagnosticsCategory.PAIRING,
                message = "encrypted pin submitted; running spc handshake",
                metadata = mapOf("tvId" to tvId)
            )
        }

        val tv = tvToReconnect ?: return
        val identifier = pairingIdentifier ?: pairingIdentifierFor(tv)
        val pairingOutcome = runCatching {
            spcHandshakeClient.completePairing(
                ipAddress = tv.ipAddress,
                pin = sanitizedPin,
                preferredVariants = sensitivePairingStorage.loadSpcVariants(identifier)
            )
        }.getOrElse { error ->
            val message = error.message
                ?: "Could not complete encrypted pairing. Retry and re-enter the TV PIN."
            connectionStateFlow.value = ConnectionState.Error(message)
            diagnosticsTracker.recordError(
                context = "complete_encrypted_pairing",
                errorMessage = message,
                metadata = mapOf("tvId" to tv.id)
            )
            return
        }

        sensitivePairingStorage.saveSpcCredentials(
            pairingOutcome.credentials,
            identifier
        )
        sensitivePairingStorage.saveSpcVariants(
            variants = SpcVariants(
                step0 = pairingOutcome.step0Variant,
                step1 = pairingOutcome.step1Variant
            ),
            identifier = identifier
        )

        diagnosticsTracker.log(
            category = DiagnosticsCategory.PAIRING,
            message = "spc credentials saved",
            metadata = mapOf("tvId" to tv.id)
        )

        connectSpcCommandChannel(
            tv = tv,
            credentials = pairingOutcome.credentials,
            shouldPersistAfterConnect = true,
            awaitFirstCommand = true,
            credentialSource = LegacyEncryptedSessionCoordinator.CredentialSource.FRESH_PAIRING
        )
    }

    override suspend fun cancelEncryptedPairing(tvId: String) {
        var ipToCancel: String? = null
        connectionMutex.withLock {
            val tv = activeTv
            if (tv == null || tv.id != tvId || tv.protocol != TvProtocol.LEGACY_ENCRYPTED) {
                return
            }

            val transition = legacyCoordinator.onCancelPairing()
            activeLegacyPairing = false
            awaitingLegacyFirstCommand = transition.awaitingFirstCommand
            activeLegacyCredentialsSource = transition.credentialSource
            connectionStateFlow.value = ConnectionState.Disconnected
            ipToCancel = tv.ipAddress

            diagnosticsTracker.log(
                category = DiagnosticsCategory.PAIRING,
                message = "encrypted pairing cancelled",
                metadata = mapOf("tvId" to tvId)
            )
        }

        ipToCancel?.let { ipAddress ->
            runCatching {
                spcHandshakeClient.cancelPairing(ipAddress)
            }
        }
    }

    override suspend fun sendRemoteKey(key: RemoteKey) {
        when (activeTransport) {
            ActiveTransport.MODERN -> sendModernKey(key)
            ActiveTransport.LEGACY_ENCRYPTED -> sendLegacyEncryptedKey(key)
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
        connectionMutex.withLock {
            val tv = savedTvsState.value.firstOrNull { it.id == tvId }
                ?: discoveredTvsState.value.firstOrNull { it.id == tvId }
                ?: activeTv
            if (tv != null) {
                clearSensitiveArtifactsFor(tv)
                if (activeTv?.id == tvId) {
                    activeLegacyPairing = false
                    awaitingLegacyFirstCommand = false
                    activeLegacyCredentialsSource = null
                }
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.PAIRING,
                    message = "pairing artifacts cleared",
                    metadata = mapOf("tvId" to tvId)
                )
            }

            if (activeTv?.id == tvId) {
                readySignal?.cancel()
                readySignal = null
                shutdownActiveSocket(setDisconnected = true)
            }
        }
    }

    override suspend fun removeDevice(tvId: String) {
        diagnosticsTracker.log(
            category = DiagnosticsCategory.LIFECYCLE,
            message = "remove device requested",
            metadata = mapOf("tvId" to tvId)
        )
        connectionMutex.withLock {
            val removedTv = savedTvsState.value.firstOrNull { it.id == tvId }
            val removedIp = removedTv?.ipAddress

            if (removedTv != null) {
                clearSensitiveArtifactsFor(removedTv)
            }

            savedTvsState.update { tvs -> tvs.filterNot { it.id == tvId } }

            if (removedIp != null) {
                discoveredTvsState.update { tvs -> tvs.filterNot { it.ipAddress == removedIp } }
            }

            if (activeTv?.id == tvId) {
                readySignal?.cancel()
                readySignal = null
                activeLegacyPairing = false
                awaitingLegacyFirstCommand = false
                activeLegacyCredentialsSource = null
                shutdownActiveSocket(setDisconnected = true)
            }
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

    private suspend fun connectModern(
        tv: SamsungTv,
        shouldPersistAfterConnect: Boolean
    ) {
        connectionMutex.withLock {
            shutdownActiveSocket(setDisconnected = false)
            connectionStateFlow.value = ConnectionState.Connecting
            diagnosticsTracker.log(
                category = DiagnosticsCategory.RECONNECT,
                message = "connecting",
                metadata = mapOf("tvId" to tv.id)
            )

            activeTransport = ActiveTransport.MODERN
            activeLegacyPairing = false
            awaitingLegacyFirstCommand = false
            activeLegacyCredentialsSource = null
            activeModernCommandMode = ModernCommandMode.REMOTE_CONTROL
            activeModernSessionId = null
            modernSessionRefreshAttempted = false

            val pairingIdentifier = pairingIdentifierFor(tv)
            var storedToken = sensitivePairingStorage.loadToken(pairingIdentifier)?.takeIf { it.isNotBlank() }
            val attemptedUrls = linkedSetOf<String>()
            var connected = false

            while (!connected) {
                val pendingAttempts = buildModernAttempts(tv.ipAddress, storedToken)
                    .filterNot { attempt -> attemptedUrls.contains(attempt.url) }
                if (pendingAttempts.isEmpty()) {
                    break
                }

                val attempt = pendingAttempts.first()
                attemptedUrls += attempt.url
                modernUnauthorizedEvent = false
                modernLastErrorMessage = null
                readyForCommands = false
                activeModernCommandMode = ModernCommandMode.REMOTE_CONTROL
                activeModernSessionId = null
                modernSessionRefreshAttempted = false

                val signal = CompletableDeferred<Unit>()
                readySignal = signal
                activeTv = tv

                diagnosticsTracker.log(
                    category = DiagnosticsCategory.RECONNECT,
                    message = "modern connect attempt",
                    metadata = mapOf(
                        "tvId" to tv.id,
                        "endpoint" to attempt.label,
                        "tokenUsed" to attempt.usesToken.toString()
                    )
                )

                val request = Request.Builder().url(attempt.url).build()
                activeSocket = attempt.client.newWebSocket(
                    request,
                    modernSocketListener(
                        tvId = tv.id,
                        signal = signal,
                        tokenIdentifier = pairingIdentifier,
                        attemptLabel = attempt.label
                    )
                )

                val attemptSucceeded = try {
                    withTimeout(CONNECTION_READY_TIMEOUT_MS) {
                        signal.await()
                    }
                    connectionStateFlow.value is ConnectionState.Ready ||
                        connectionStateFlow.value is ConnectionState.ConnectedNotReady
                } catch (_: Throwable) {
                    false
                } finally {
                    if (readySignal === signal) {
                        readySignal = null
                    }
                }

                if (attemptSucceeded) {
                    connected = true
                    break
                }

                val attemptError = modernLastErrorMessage
                    ?: (connectionStateFlow.value as? ConnectionState.Error)?.message
                    ?: "modern connection attempt failed"

                diagnosticsTracker.recordError(
                    context = "modern_connect_attempt",
                    errorMessage = attemptError,
                    metadata = mapOf(
                        "tvId" to tv.id,
                        "endpoint" to attempt.label,
                        "tokenUsed" to attempt.usesToken.toString()
                    )
                )

                if (modernUnauthorizedEvent && attempt.usesToken && storedToken != null) {
                    sensitivePairingStorage.deleteToken(pairingIdentifier)
                    storedToken = null
                    diagnosticsTracker.log(
                        category = DiagnosticsCategory.PAIRING,
                        message = "cleared stale modern token after unauthorized response",
                        metadata = mapOf("tvId" to tv.id)
                    )
                }

                shutdownActiveSocket(setDisconnected = false)
                connectionStateFlow.value = ConnectionState.Connecting

                val latestToken = sensitivePairingStorage.loadToken(pairingIdentifier)?.takeIf { it.isNotBlank() }
                if (latestToken != storedToken) {
                    storedToken = latestToken
                    diagnosticsTracker.log(
                        category = DiagnosticsCategory.PAIRING,
                        message = "modern token changed between attempts; refreshing endpoint order",
                        metadata = mapOf(
                            "tvId" to tv.id,
                            "hasToken" to (storedToken != null).toString()
                        )
                    )
                }
            }

            if (connected) {
                if (shouldPersistAfterConnect &&
                    (connectionStateFlow.value is ConnectionState.Ready ||
                        connectionStateFlow.value is ConnectionState.ConnectedNotReady)
                ) {
                    persistToSavedTvs(tv)
                }
                return
            }

            val fallbackMessage = modernLastErrorMessage
                ?: "Could not connect to this TV over the modern path. Keep the TV on the same Wi-Fi and retry."
            connectionStateFlow.value = ConnectionState.Error(fallbackMessage)
            diagnosticsTracker.recordError(
                context = "connect_modern_failed",
                errorMessage = fallbackMessage,
                metadata = mapOf("tvId" to tv.id)
            )
        }
    }

    private suspend fun connectLegacyEncrypted(
        tv: SamsungTv,
        shouldPersistAfterConnect: Boolean
    ) {
        val pairingIdentifier = pairingIdentifierFor(tv)
        val rawStoredCredentials = sensitivePairingStorage.loadSpcCredentials(pairingIdentifier)
        val storedCredentials = rawStoredCredentials?.takeIf { isValidSpcCredentials(it) }
        if (rawStoredCredentials != null && storedCredentials == null) {
            sensitivePairingStorage.deleteSensitiveData(pairingIdentifier)
            diagnosticsTracker.log(
                category = DiagnosticsCategory.PAIRING,
                message = "cleared invalid stored spc credentials",
                metadata = mapOf("tvId" to tv.id)
            )
        }

        if (storedCredentials != null) {
            diagnosticsTracker.log(
                category = DiagnosticsCategory.PAIRING,
                message = "attempting encrypted connect with stored spc credentials",
                metadata = mapOf("tvId" to tv.id)
            )

            val connectedWithStoredCredentials = connectSpcCommandChannel(
                tv = tv,
                credentials = storedCredentials,
                shouldPersistAfterConnect = shouldPersistAfterConnect,
                awaitFirstCommand = true,
                credentialSource = LegacyEncryptedSessionCoordinator.CredentialSource.STORED
            )
            if (connectedWithStoredCredentials) {
                return
            }

            sensitivePairingStorage.deleteSensitiveData(pairingIdentifier)
            diagnosticsTracker.log(
                category = DiagnosticsCategory.PAIRING,
                message = "stored spc credentials failed; cleared and falling back to fresh pairing",
                metadata = mapOf("tvId" to tv.id)
            )
        }

        connectionMutex.withLock {
            shutdownActiveSocket(setDisconnected = false)
            activeTransport = ActiveTransport.LEGACY_ENCRYPTED
            readyForCommands = false
            activeTv = tv
            activeSpcCredentials = null
            activeSpcIdentifier = null

            val transition = legacyCoordinator.onConnect(
                tvId = tv.id,
                hasStoredCredentials = false
            )

            activeLegacyPairing = transition.emittedStates.any { it is ConnectionState.Pairing || it is ConnectionState.PinRequired }
            awaitingLegacyFirstCommand = transition.awaitingFirstCommand
            activeLegacyCredentialsSource = transition.credentialSource

            transition.emittedStates.forEach { state ->
                connectionStateFlow.value = state
            }
        }

        val pairingPreparationError = runCatching {
            spcHandshakeClient.startPairing(tv.ipAddress)
        }.exceptionOrNull()

        if (pairingPreparationError != null) {
            val message = pairingPreparationError.message
                ?: "Could not open the PIN page on TV. Keep the TV awake and retry."
            connectionStateFlow.value = ConnectionState.Error(
                message
            )
            diagnosticsTracker.recordError(
                context = "legacy_prepare_pin_page",
                errorMessage = message,
                metadata = mapOf("tvId" to tv.id)
            )
            return
        }

        diagnosticsTracker.log(
            category = DiagnosticsCategory.PAIRING,
            message = "legacy encrypted connect entering fresh pairing",
            metadata = mapOf("tvId" to tv.id)
        )
    }

    private suspend fun connectSpcCommandChannel(
        tv: SamsungTv,
        credentials: SpcCredentials,
        shouldPersistAfterConnect: Boolean,
        awaitFirstCommand: Boolean,
        credentialSource: LegacyEncryptedSessionCoordinator.CredentialSource?
    ): Boolean {
        var connected = false
        connectionMutex.withLock {
            shutdownActiveSocket(setDisconnected = false)
            activeTransport = ActiveTransport.LEGACY_ENCRYPTED
            activeTv = tv
            readyForCommands = false
            activeSpcCredentials = credentials
            activeSpcIdentifier = pairingIdentifierFor(tv)
            connectionStateFlow.value = ConnectionState.Connecting

            val connectResult = runCatching {
                spcWebSocketClient.connect(tv.ipAddress)
            }

            connectResult.getOrElse { error ->
                val message = error.message ?: "Could not open the encrypted command channel."
                connectionStateFlow.value = ConnectionState.Error(message)
                readyForCommands = false
                activeSpcCredentials = null
                activeSpcIdentifier = null
                diagnosticsTracker.recordError(
                    context = "connect_spc_command_channel",
                    errorMessage = message,
                    metadata = mapOf("tvId" to tv.id)
                )
                return@withLock
            }

            if (awaitFirstCommand) {
                readyForCommands = false
                awaitingLegacyFirstCommand = true
                activeLegacyCredentialsSource = credentialSource
                activeLegacyPairing = false
                connectionStateFlow.value = ConnectionState.ConnectedNotReady(tv.id)
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.LIFECYCLE,
                    message = "encrypted spc command channel connected; awaiting first command confirmation",
                    metadata = mapOf(
                        "tvId" to tv.id,
                        "sessionId" to credentials.sessionId.toString()
                    )
                )
            } else {
                readyForCommands = true
                awaitingLegacyFirstCommand = false
                activeLegacyCredentialsSource = credentialSource
                activeLegacyPairing = false
                connectionStateFlow.value = ConnectionState.Ready(tv.id)
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.LIFECYCLE,
                    message = "encrypted spc command channel is ready",
                    metadata = mapOf(
                        "tvId" to tv.id,
                        "sessionId" to credentials.sessionId.toString()
                    )
                )
            }

            if (shouldPersistAfterConnect) {
                persistToSavedTvs(tv)
            }
            connected = true
        }
        return connected
    }

    private suspend fun sendModernKey(key: RemoteKey) {
        val currentState = connectionStateFlow.value
        val canSendInput = currentState is ConnectionState.Ready
        if (!canSendInput) {
            diagnosticsTracker.recordError(
                context = "send_key_not_ready",
                errorMessage = "remote key blocked because connection is not ready",
                metadata = mapOf("key" to key.name)
            )
            return
        }

        val keyCode = modernKeyCodeFor(key)
        if (keyCode == null) {
            diagnosticsTracker.recordError(
                context = "send_key_unsupported",
                errorMessage = "unsupported key for modern transport",
                metadata = mapOf("key" to key.name)
            )
            return
        }

        if (requiresSession(activeModernCommandMode) && activeModernSessionId.isNullOrBlank()) {
            diagnosticsTracker.log(
                category = DiagnosticsCategory.PROTOCOL,
                message = "session-required modern mode without session; downgrading to non-session mode",
                metadata = mapOf("key" to key.name)
            )
            activeModernCommandMode = ModernCommandMode.EMIT_KEYPRESS
        }

        val payload = buildModernKeyPayload(
            keyCode = keyCode,
            mode = activeModernCommandMode,
            sessionId = activeModernSessionId
        )
        val sent = activeSocket?.send(payload) ?: false
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

    private suspend fun sendLegacyEncryptedKey(key: RemoteKey) {
        val tv = activeTv
        if (tv == null || tv.protocol != TvProtocol.LEGACY_ENCRYPTED) {
            connectionStateFlow.value = ConnectionState.Error("Encrypted session is not active.")
            diagnosticsTracker.recordError(
                context = "legacy_send_key",
                errorMessage = "encrypted key send ignored because encrypted session is inactive",
                metadata = mapOf("key" to key.name)
            )
            return
        }

        if (connectionStateFlow.value is ConnectionState.PinRequired) {
            connectionStateFlow.value = ConnectionState.Error(
                "Enter the TV PIN first, then connect."
            )
            diagnosticsTracker.recordError(
                context = "legacy_send_key_pin_required",
                errorMessage = "encrypted key send blocked because PIN is still required",
                metadata = mapOf("tvId" to tv.id)
            )
            return
        }

        val currentState = connectionStateFlow.value
        val allowLegacyProbe = currentState is ConnectionState.ConnectedNotReady &&
            awaitingLegacyFirstCommand
        if (currentState !is ConnectionState.Ready && !allowLegacyProbe) {
            diagnosticsTracker.recordError(
                context = "legacy_send_key_not_ready",
                errorMessage = "encrypted key send blocked because legacy command channel is not ready",
                metadata = mapOf("tvId" to tv.id)
            )
            return
        }

        val keyCode = legacyKeyCodeFor(key)
        if (keyCode == null) {
            diagnosticsTracker.recordError(
                context = "legacy_send_key_unsupported",
                errorMessage = "unsupported key for legacy encrypted path",
                metadata = mapOf(
                    "tvId" to tv.id,
                    "key" to key.name
                )
            )
            return
        }

        try {
            val credentials = activeSpcCredentials
                ?: activeSpcIdentifier
                    ?.let { identifier ->
                        sensitivePairingStorage.loadSpcCredentials(identifier)
                            ?.takeIf { isValidSpcCredentials(it) }
                    }
                ?: throw IllegalStateException("Encrypted session is missing. Reconnect and pair again.")

            val sendResult = runCatching {
                spcWebSocketClient.sendKey(
                    keyCode = keyCode,
                    ctxUpperHex = credentials.ctxUpperHex,
                    sessionId = credentials.sessionId
                )
            }

            if (sendResult.isFailure) {
                val firstFailureMessage = sendResult.exceptionOrNull()?.message.orEmpty()
                val needsReconnectRetry = firstFailureMessage.contains(
                    "spc socket is not connected",
                    ignoreCase = true
                )

                if (needsReconnectRetry && !awaitingLegacyFirstCommand) {
                    diagnosticsTracker.log(
                        category = DiagnosticsCategory.RECONNECT,
                        message = "spc key send failed with disconnected socket; retrying reconnect once",
                        metadata = mapOf("tvId" to tv.id)
                    )

                    val reconnected = connectSpcCommandChannel(
                        tv = tv,
                        credentials = credentials,
                        shouldPersistAfterConnect = false,
                        awaitFirstCommand = awaitingLegacyFirstCommand,
                        credentialSource = activeLegacyCredentialsSource
                    )
                    if (reconnected) {
                        spcWebSocketClient.sendKey(
                            keyCode = keyCode,
                            ctxUpperHex = credentials.ctxUpperHex,
                            sessionId = credentials.sessionId
                        )
                    } else {
                        throw IllegalStateException(
                            "SPC socket disconnected and reconnect failed. Retry connect."
                        )
                    }
                } else {
                    throw sendResult.exceptionOrNull() ?: IllegalStateException(
                        "Failed to send remote key over encrypted path."
                    )
                }
            }
            if (awaitingLegacyFirstCommand) {
                connectionMutex.withLock {
                    awaitingLegacyFirstCommand = false
                    activeLegacyCredentialsSource = null
                    readyForCommands = true
                    activeLegacyPairing = false
                    connectionStateFlow.value = ConnectionState.Ready(tv.id)
                }
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.LIFECYCLE,
                    message = "encrypted session confirmed by first command",
                    metadata = mapOf("tvId" to tv.id)
                )
            }
        } catch (error: CancellationException) {
            return
        } catch (error: Throwable) {
            val rawMessage = error.message ?: "Failed to send remote key over encrypted path."
            if (rawMessage.contains("cancelled", ignoreCase = true)) {
                return
            }

            if (awaitingLegacyFirstCommand &&
                activeLegacyCredentialsSource == LegacyEncryptedSessionCoordinator.CredentialSource.STORED
            ) {
                recoverStoredSpcSessionToFreshPairing(
                    tv = tv,
                    failureMessage = rawMessage
                )
                return
            }

            val staleMessage = isSpcStaleError(rawMessage)
            val message = if (staleMessage) {
                "Encrypted session expired. Tap Connect and pair again."
            } else {
                rawMessage
            }

            if (staleMessage) {
                val identifier = activeSpcIdentifier ?: pairingIdentifierFor(tv)
                sensitivePairingStorage.deleteSensitiveData(identifier)
                activeSpcCredentials = null
                activeSpcIdentifier = null
                readyForCommands = false
            }

            connectionStateFlow.value = ConnectionState.Error(message)
            diagnosticsTracker.recordError(
                context = "legacy_send_key_failed",
                errorMessage = message,
                metadata = mapOf(
                    "tvId" to tv.id,
                    "key" to key.name
                )
            )
        }
    }

    private suspend fun recoverStoredSpcSessionToFreshPairing(
        tv: SamsungTv,
        failureMessage: String
    ) {
        val transition = legacyCoordinator.onFirstCommand(
            tvId = tv.id,
            source = LegacyEncryptedSessionCoordinator.CredentialSource.STORED
        )
        val identifier = activeSpcIdentifier ?: pairingIdentifierFor(tv)
        connectionMutex.withLock {
            spcWebSocketClient.disconnect()
            sensitivePairingStorage.deleteSensitiveData(identifier)
            activeSpcCredentials = null
            activeSpcIdentifier = null
            readyForCommands = false
            activeLegacyPairing = transition.emittedStates.any {
                it is ConnectionState.Pairing || it is ConnectionState.PinRequired
            }
            awaitingLegacyFirstCommand = transition.awaitingFirstCommand
            activeLegacyCredentialsSource = transition.credentialSource
            transition.emittedStates.forEach { state ->
                connectionStateFlow.value = state
            }
        }

        diagnosticsTracker.log(
            category = DiagnosticsCategory.PAIRING,
            message = "stored spc first command failed; entering fresh pairing",
            metadata = mapOf("tvId" to tv.id)
        )
        diagnosticsTracker.recordError(
            context = "legacy_first_command_stale",
            errorMessage = failureMessage,
            metadata = mapOf("tvId" to tv.id)
        )

        val pairingPreparationError = runCatching {
            spcHandshakeClient.startPairing(tv.ipAddress)
        }.exceptionOrNull()

        if (pairingPreparationError != null) {
            val message = pairingPreparationError.message
                ?: "Could not open the PIN page on TV. Keep the TV awake and retry."
            connectionStateFlow.value = ConnectionState.Error(message)
            diagnosticsTracker.recordError(
                context = "legacy_prepare_pin_page",
                errorMessage = message,
                metadata = mapOf("tvId" to tv.id)
            )
        }
    }

    private fun buildModernAttempts(ipAddress: String, storedToken: String?): List<ModernConnectAttempt> {
        val token = storedToken?.takeIf { it.isNotBlank() }
        val attempts = mutableListOf<ModernConnectAttempt>()

        if (token != null) {
            attempts += ModernConnectAttempt(
                label = "wss:8002(token)",
                url = buildModernSocketUrl(
                    ipAddress = ipAddress,
                    scheme = "wss",
                    port = 8002,
                    token = token
                ),
                usesToken = true,
                client = wsSecureClient
            )
        }
        attempts += ModernConnectAttempt(
            label = "wss:8002",
            url = buildModernSocketUrl(
                ipAddress = ipAddress,
                scheme = "wss",
                port = 8002,
                token = null
            ),
            usesToken = false,
            client = wsSecureClient
        )
        if (token != null) {
            attempts += ModernConnectAttempt(
                label = "ws:8001(token)",
                url = buildModernSocketUrl(
                    ipAddress = ipAddress,
                    scheme = "ws",
                    port = 8001,
                    token = token
                ),
                usesToken = true,
                client = wsClient
            )
        }
        attempts += ModernConnectAttempt(
            label = "ws:8001",
            url = buildModernSocketUrl(
                ipAddress = ipAddress,
                scheme = "ws",
                port = 8001,
                token = null
            ),
            usesToken = false,
            client = wsClient
        )
        return attempts
    }

    private suspend fun discoverViaNsd(): List<NsdServiceInfo> {
        val multicastLock = acquireMulticastLock()
        return try {
            NSD_SERVICE_TYPES
                .flatMap { serviceType -> discoverServices(serviceType) }
                .distinctBy { serviceInfo ->
                    val host = serviceInfo.host?.hostAddress.orEmpty()
                    "${serviceInfo.serviceName}|$host"
                }
        } finally {
            runCatching {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            }
        }
    }

    private suspend fun discoverViaSubnetFallback(): List<SamsungTv> {
        val prefixes = linkedSetOf<String>()
        prefixes += localSubnetPrefixes()
        prefixes += subnetPrefixesFromKnownTvs(savedTvsState.value)
        prefixes += subnetPrefixesFromKnownTvs(discoveredTvsState.value)

        if (prefixes.isEmpty()) {
            diagnosticsTracker.recordError(
                context = "subnet_discovery",
                errorMessage = "subnet fallback skipped because no ipv4 subnet prefix was found"
            )
            return emptyList()
        }

        val localIps = localIpv4Addresses()
        val candidateIps = prefixes
            .flatMap { prefix ->
                (1..254).asSequence()
                    .map { host -> "$prefix.$host" }
                    .filterNot { ip -> ip in localIps }
                    .toList()
            }
            .distinct()

        return coroutineScope {
            val semaphore = Semaphore(SUBNET_DISCOVERY_CONCURRENCY)
            val deferred = candidateIps.map { candidateIp ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        probeIpCandidate(candidateIp)
                    }
                }
            }
            deferred.awaitAll()
                .filterNotNull()
                .distinctBy { tv -> tv.ipAddress }
        }
    }

    private suspend fun probeIpCandidate(ipAddress: String): SamsungTv? {
        val modernPortOpen = isTcpPortOpen(
            ipAddress = ipAddress,
            port = 8001,
            timeoutMs = SUBNET_PROBE_PORT_TIMEOUT_MS
        )
        val legacySpcPortOpen = isTcpPortOpen(
            ipAddress = ipAddress,
            port = LEGACY_SPC_SOCKET_PORT,
            timeoutMs = SUBNET_PROBE_PORT_TIMEOUT_MS
        )
        val legacyPairingPortOpen = isTcpPortOpen(
            ipAddress = ipAddress,
            port = 8080,
            timeoutMs = SUBNET_PROBE_PORT_TIMEOUT_MS
        )

        if (!modernPortOpen && !legacySpcPortOpen && !legacyPairingPortOpen) {
            return null
        }

        return fetchTvInfoWithFallback(
            ipAddress = ipAddress,
            client = restProbeClient,
            knownLegacySpcPortOpen = legacySpcPortOpen,
            knownLegacyPairingPortOpen = legacyPairingPortOpen
        )
    }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        return runCatching {
            wifiManager.createMulticastLock("samsung-remote-discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun localSubnetPrefixes(): Set<String> {
        val addresses = localIpv4Addresses()
        return addresses.mapNotNull { ipAddress -> ipv4Prefix(ipAddress) }.toSet()
    }

    private fun subnetPrefixesFromKnownTvs(tvs: List<SamsungTv>): Set<String> {
        return tvs.mapNotNull { tv -> ipv4Prefix(tv.ipAddress) }.toSet()
    }

    private fun localIpv4Addresses(): Set<String> {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return emptySet()
        return interfaces.toList()
            .filter { networkInterface -> networkInterface.isUp && !networkInterface.isLoopback }
            .flatMap { networkInterface ->
                networkInterface.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { address -> normalizeIpv4(address.hostAddress) }
            }
            .toSet()
    }

    private fun ipv4Prefix(ipAddress: String): String? {
        val segments = ipAddress.split('.')
        if (segments.size != 4) {
            return null
        }
        return "${segments[0]}.${segments[1]}.${segments[2]}"
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

    private suspend fun fetchModernTvInfo(
        ipAddress: String,
        client: OkHttpClient = restClient
    ): SamsungTv? {
        val normalizedIp = normalizeIpv4(ipAddress) ?: return null

        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("http://$normalizedIp:8001/api/v2/")
                .build()

            val response = runCatching {
                client.newCall(request).execute()
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
                val wifiMac = device.optString("wifiMac").orEmpty()
                val displayName = if (deviceName.equals("Samsung TV", ignoreCase = true)) {
                    modelName
                } else {
                    deviceName
                }
                val protocol = if (isLikelyLegacyEncryptedModel(modelName)) {
                    TvProtocol.LEGACY_ENCRYPTED
                } else {
                    TvProtocol.MODERN
                }
                SamsungTv(
                    id = tvIdForIp(normalizedIp),
                    displayName = displayName,
                    ipAddress = normalizedIp,
                    protocol = protocol,
                    capabilities = DEFAULT_CAPABILITIES,
                    modelName = modelName,
                    macAddress = wifiMac
                )
            }
        }
    }

    private suspend fun fetchTvInfoWithFallback(
        ipAddress: String,
        client: OkHttpClient = restClient,
        fallbackDisplayName: String? = null,
        knownLegacySpcPortOpen: Boolean? = null,
        knownLegacyPairingPortOpen: Boolean? = null
    ): SamsungTv? {
        val modern = fetchModernTvInfo(
            ipAddress = ipAddress,
            client = client
        )
        if (modern != null) {
            if (shouldPreferLegacyEncrypted(tv = modern)) {
                return modern.copy(protocol = TvProtocol.LEGACY_ENCRYPTED)
            }
            return modern
        }

        return fetchLegacyTvInfo(
            ipAddress = ipAddress,
            fallbackDisplayName = fallbackDisplayName,
            knownLegacySpcPortOpen = knownLegacySpcPortOpen,
            knownLegacyPairingPortOpen = knownLegacyPairingPortOpen
        )
    }

    private suspend fun fetchLegacyTvInfo(
        ipAddress: String,
        fallbackDisplayName: String?,
        knownLegacySpcPortOpen: Boolean?,
        knownLegacyPairingPortOpen: Boolean?
    ): SamsungTv? {
        val normalizedIp = normalizeIpv4(ipAddress) ?: return null
        val legacySpcPortOpen = knownLegacySpcPortOpen ?: isTcpPortOpen(
            ipAddress = normalizedIp,
            port = LEGACY_SPC_SOCKET_PORT,
            timeoutMs = 700
        )
        val legacyPairingPortOpen = knownLegacyPairingPortOpen ?: isTcpPortOpen(
            ipAddress = normalizedIp,
            port = 8080,
            timeoutMs = 700
        )

        if (!legacySpcPortOpen && !legacyPairingPortOpen) {
            return null
        }

        val protocol = TvProtocol.LEGACY_ENCRYPTED
        val label = fallbackDisplayName?.takeIf { it.isNotBlank() } ?: "Samsung TV"
        val model = "Legacy Encrypted TV"

        return SamsungTv(
            id = tvIdForIp(normalizedIp),
            displayName = label,
            ipAddress = normalizedIp,
            protocol = protocol,
            capabilities = DEFAULT_CAPABILITIES,
            modelName = model,
            macAddress = ""
        )
    }

    private fun shouldPreferLegacyEncrypted(tv: SamsungTv): Boolean {
        if (tv.protocol == TvProtocol.LEGACY_ENCRYPTED) {
            return true
        }

        if (isLikelyLegacyEncryptedModel(tv.modelName)) {
            return true
        }
        return false
    }

    private fun isLikelyLegacyEncryptedModel(modelName: String): Boolean {
        val model = modelName.uppercase()
        if (model.isBlank()) {
            return false
        }

        if (model.contains("LS") || model.contains("QN")) {
            return false
        }

        if (model.contains("JU") || model.contains("JS")) {
            return true
        }

        val hasLegacySeriesMarker = Regex(".*[HJ][0-9]{3,4}.*").matches(model)
        return hasLegacySeriesMarker
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
        signal: CompletableDeferred<Unit>,
        tokenIdentifier: String,
        attemptLabel: String
    ): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (activeTv?.id != tvId) return
                connectionStateFlow.value = ConnectionState.ConnectedNotReady(tvId)
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.LIFECYCLE,
                    message = "modern socket opened",
                    metadata = mapOf(
                        "tvId" to tvId,
                        "endpoint" to attemptLabel
                    )
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (activeTv?.id != tvId) return

                val eventName = extractEventName(text)
                val reason = extractErrorReason(text).orEmpty()
                val sessionId = extractSessionIdentifier(text)
                if (!sessionId.isNullOrBlank()) {
                    activeModernSessionId = sessionId
                    modernSessionRefreshAttempted = false
                }
                when (eventName) {
                    EVENT_CHANNEL_CONNECT,
                    EVENT_CHANNEL_CLIENT_CONNECT,
                    EVENT_CHANNEL_READY -> {
                        val token = extractToken(text)
                        if (!token.isNullOrBlank()) {
                            sensitivePairingStorage.saveToken(token, tokenIdentifier)
                            diagnosticsTracker.log(
                                category = DiagnosticsCategory.PAIRING,
                                message = "modern token updated from tv response",
                                metadata = mapOf("tvId" to tvId)
                            )
                        }
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
                        modernUnauthorizedEvent = true
                        handleSocketFailure(
                            tvId = tvId,
                            signal = signal,
                            context = "socket_unauthorized",
                            message = "TV rejected pairing/authorization on modern path"
                        )
                    }

                    EVENT_CHANNEL_ERROR -> {
                        if (isMissingSessionReason(reason)) {
                            if (activeModernSessionId.isNullOrBlank() && !modernSessionRefreshAttempted) {
                                modernSessionRefreshAttempted = true
                                val refreshSent = sendSessionRefreshRequest(
                                    webSocket = webSocket,
                                    tvId = tvId
                                )
                                if (refreshSent) {
                                    diagnosticsTracker.log(
                                        category = DiagnosticsCategory.PROTOCOL,
                                        message = "modern session refresh requested after missing-session error",
                                        metadata = mapOf("tvId" to tvId)
                                    )
                                    return
                                }
                            }

                            handleSocketFailure(
                                tvId = tvId,
                                signal = signal,
                                context = "socket_session_missing",
                                message = "TV session is not ready yet. Retry connect."
                            )
                            return
                        }

                        if (applyModernModeFallbackIfNeeded(
                                tvId = tvId,
                                reason = reason
                            )
                        ) {
                            readyForCommands = true
                            connectionStateFlow.value = ConnectionState.Ready(tvId)
                            if (!signal.isCompleted) {
                                signal.complete(Unit)
                            }
                            return
                        }

                        if (isAuthorizationFailure(reason)) {
                            modernUnauthorizedEvent = true
                            handleSocketFailure(
                                tvId = tvId,
                                signal = signal,
                                context = "socket_unauthorized",
                                message = "TV rejected pairing/authorization on modern path"
                            )
                        } else {
                            handleSocketFailure(
                                tvId = tvId,
                                signal = signal,
                                context = "socket_error_event",
                                message = reason.ifBlank {
                                    "Modern connection failed: TV reported an unexpected socket error"
                                }
                            )
                        }
                    }

                    else -> {
                        if (!eventName.isNullOrBlank()) {
                            diagnosticsTracker.log(
                                category = DiagnosticsCategory.PROTOCOL,
                                message = "modern socket event received",
                                metadata = mapOf(
                                    "tvId" to tvId,
                                    "event" to eventName
                                )
                            )
                        }
                    }
                }

                if (eventName.isNullOrBlank() && reason.isNotBlank()) {
                    diagnosticsTracker.log(
                        category = DiagnosticsCategory.PROTOCOL,
                        message = "modern socket message without event",
                        metadata = mapOf(
                            "tvId" to tvId,
                            "reason" to reason
                        )
                    )
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (activeTv?.id != tvId) return

                if (!signal.isCompleted && !readyForCommands) {
                    signal.completeExceptionally(
                        IllegalStateException(
                            "TV closed the connection before authorization completed. If the TV shows an Allow prompt, accept it and retry."
                        )
                    )
                    diagnosticsTracker.recordError(
                        context = "socket_closed_before_ready",
                        errorMessage = "modern connection closed before ready/authorization (code=$code reason=$reason)",
                        metadata = mapOf(
                            "tvId" to tvId,
                            "closeCode" to code.toString()
                        )
                    )
                }

                readyForCommands = false
                activeSocket = null
                activeTv = null
                activeModernSessionId = null
                activeModernCommandMode = ModernCommandMode.REMOTE_CONTROL

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
                val rawMessage = t.message ?: "Unknown modern connection failure"
                val message = when {
                    rawMessage.contains("socket closed", ignoreCase = true) -> {
                        "TV closed the connection before authorization completed. If the TV shows an Allow prompt, accept it and retry."
                    }

                    isMissingSessionReason(rawMessage) -> {
                        "TV session is not ready yet. Retry connect."
                    }

                    else -> rawMessage
                }
                if (isAuthorizationFailure(message)) {
                    modernUnauthorizedEvent = true
                }
                handleSocketFailure(
                    tvId = tvId,
                    signal = signal,
                    context = "socket_failure",
                    message = message
                )
            }
        }
    }

    private fun applyModernModeFallbackIfNeeded(
        tvId: String,
        reason: String
    ): Boolean {
        val normalized = reason.lowercase()
        val invalidRemoteControlMethod = normalized.contains("unrecognized method value : ms.remote.control")
        val invalidChannelEmitMethod = normalized.contains("unrecognized method value : ms.channel.emit")

        return when {
            invalidRemoteControlMethod -> {
                activeModernCommandMode = ModernCommandMode.EMIT_KEYPRESS
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.PROTOCOL,
                    message = "modern command mode fallback: remote.control -> ed.keypress",
                    metadata = mapOf("tvId" to tvId)
                )
                true
            }

            activeModernCommandMode == ModernCommandMode.EMIT_KEYPRESS &&
                invalidChannelEmitMethod &&
                !activeModernSessionId.isNullOrBlank() -> {
                activeModernCommandMode = ModernCommandMode.EMIT_KEYPRESS_WITH_SESSION
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.PROTOCOL,
                    message = "modern command mode fallback: ed.keypress -> ed.keypress(session)",
                    metadata = mapOf("tvId" to tvId)
                )
                true
            }

            activeModernCommandMode == ModernCommandMode.EMIT_KEYPRESS_WITH_SESSION &&
                !activeModernSessionId.isNullOrBlank() &&
                isMissingSessionReason(reason) -> {
                activeModernCommandMode = ModernCommandMode.EMIT_REMOTE_CONTROL_WITH_SESSION
                diagnosticsTracker.log(
                    category = DiagnosticsCategory.PROTOCOL,
                    message = "modern command mode fallback: ed.keypress(session) -> ed.remote.control(session)",
                    metadata = mapOf("tvId" to tvId)
                )
                true
            }

            else -> false
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

        modernLastErrorMessage = message
        readyForCommands = false
        activeSocket = null
        activeTv = null
        activeModernSessionId = null
        activeModernCommandMode = ModernCommandMode.REMOTE_CONTROL
        modernSessionRefreshAttempted = false
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
        legacyTcpClient.disconnect()
        spcWebSocketClient.disconnect()
        activeTv = null
        readyForCommands = false
        activeModernCommandMode = ModernCommandMode.REMOTE_CONTROL
        activeModernSessionId = null
        modernSessionRefreshAttempted = false
        activeSpcCredentials = null
        activeSpcIdentifier = null
        activeTransport = ActiveTransport.MODERN
        activeLegacyPairing = false
        awaitingLegacyFirstCommand = false
        activeLegacyCredentialsSource = null

        if (setDisconnected) {
            connectionStateFlow.value = ConnectionState.Disconnected
        }
    }

    private fun buildModernSocketUrl(
        ipAddress: String,
        scheme: String,
        port: Int,
        token: String?
    ): String {
        val encodedName = Base64.encodeToString(
            MODERN_CLIENT_NAME.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )

        val tokenQuery = token
            ?.takeIf { it.isNotBlank() }
            ?.let { value ->
                "&token=" + URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            }
            .orEmpty()
        return "$scheme://$ipAddress:$port/api/v2/channels/samsung.remote.control?name=$encodedName$tokenQuery"
    }

    private fun buildModernKeyPayload(
        keyCode: String,
        mode: ModernCommandMode,
        sessionId: String?
    ): String {
        return when (mode) {
            ModernCommandMode.REMOTE_CONTROL -> buildRemoteControlPayload(keyCode = keyCode)

            ModernCommandMode.EMIT_KEYPRESS -> {
                buildEmitKeypressPayload(
                    keyCode = keyCode,
                    sessionId = null
                )
            }

            ModernCommandMode.EMIT_KEYPRESS_WITH_SESSION -> {
                buildEmitKeypressPayload(
                    keyCode = keyCode,
                    sessionId = sessionId
                )
            }

            ModernCommandMode.EMIT_REMOTE_CONTROL_WITH_SESSION -> {
                buildEmitRemoteControlPayload(
                    keyCode = keyCode,
                    sessionId = sessionId
                )
            }
        }
    }

    private fun buildRemoteControlPayload(keyCode: String): String {
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

    private fun buildEmitKeypressPayload(
        keyCode: String,
        sessionId: String?
    ): String {
        val data = JSONObject().put("key", keyCode)
        val params = JSONObject()
            .put("event", "ed.keypress")
            .put("to", "host")
            .put("data", data)
        if (!sessionId.isNullOrBlank()) {
            params.put("session", sessionId)
        }
        return JSONObject()
            .put("method", "ms.channel.emit")
            .put("params", params)
            .toString()
    }

    private fun buildEmitRemoteControlPayload(
        keyCode: String,
        sessionId: String?
    ): String {
        val data = JSONObject()
            .put("Cmd", "Click")
            .put("DataOfCmd", keyCode)
            .put("Option", "false")
            .put("TypeOfRemote", "SendRemoteKey")
        val params = JSONObject()
            .put("event", "ed.remote.control")
            .put("to", "host")
            .put("data", data)
        if (!sessionId.isNullOrBlank()) {
            params.put("session", sessionId)
        }
        return JSONObject()
            .put("method", "ms.channel.emit")
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

    private fun extractToken(rawMessage: String): String? {
        return runCatching {
            val payload = JSONObject(rawMessage)
            val data = payload.optJSONObject("data") ?: return@runCatching null

            val directToken = data.optString("token").takeIf { it.isNotBlank() }
            if (directToken != null) {
                return@runCatching directToken
            }

            val clients = data.optJSONArray("clients") ?: return@runCatching null
            for (index in 0 until clients.length()) {
                val client = clients.optJSONObject(index) ?: continue
                val token = client.optString("token")
                if (token.isNotBlank()) {
                    return@runCatching token
                }
            }
            null
        }.getOrNull()
    }

    private fun extractSessionIdentifier(rawMessage: String): String? {
        return runCatching {
            val payload = JSONObject(rawMessage)
            val data = payload.optJSONObject("data") ?: return@runCatching null
            data.optString("id").takeIf { it.isNotBlank() }
                ?: run {
                    val clients = data.optJSONArray("clients") ?: return@run null
                    for (index in 0 until clients.length()) {
                        val client = clients.optJSONObject(index) ?: continue
                        val session = client.optString("id")
                        if (session.isNotBlank()) {
                            return@run session
                        }
                    }
                    null
                }
        }.getOrNull()
    }

    private fun extractErrorReason(rawMessage: String): String? {
        return runCatching {
            val payload = JSONObject(rawMessage)
            val data = payload.optJSONObject("data")

            data?.optString("message")?.takeIf { it.isNotBlank() }
                ?: data?.optString("details")?.takeIf { it.isNotBlank() }
                ?: payload.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun isAuthorizationFailure(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("unauthor")
            || normalized.contains("forbidden")
            || normalized.contains("denied")
            || normalized.contains("reject")
    }

    private fun isMissingSessionReason(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("cannot read property 'session' of null")
            || normalized.contains("session is null")
            || normalized.contains("no session")
    }

    private fun isSpcStaleError(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("stale")
            || normalized.contains("session expired")
            || normalized.contains("spc credentials")
            || normalized.contains("encrypted session is missing")
            || normalized.contains("unsupported key size")
    }

    private fun isValidSpcCredentials(credentials: SpcCredentials): Boolean {
        return credentials.sessionId > 0 && isValidSpcContextHex(credentials.ctxUpperHex)
    }

    private fun isValidSpcContextHex(ctxUpperHex: String): Boolean {
        if (ctxUpperHex.length != 32) {
            return false
        }
        return ctxUpperHex.all { character ->
            character in '0'..'9' || character in 'a'..'f' || character in 'A'..'F'
        }
    }

    private fun requiresSession(mode: ModernCommandMode): Boolean {
        return mode == ModernCommandMode.EMIT_KEYPRESS_WITH_SESSION ||
            mode == ModernCommandMode.EMIT_REMOTE_CONTROL_WITH_SESSION
    }

    private fun sendSessionRefreshRequest(
        webSocket: WebSocket,
        tvId: String
    ): Boolean {
        val params = JSONObject().put("name", Base64.encodeToString(
            MODERN_CLIENT_NAME.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        ))
        val payload = JSONObject()
            .put("method", EVENT_CHANNEL_CONNECT)
            .put("params", params)
            .toString()
        val sent = webSocket.send(payload)
        if (!sent) {
            diagnosticsTracker.recordError(
                context = "socket_session_refresh",
                errorMessage = "failed to send modern session refresh request",
                metadata = mapOf("tvId" to tvId)
            )
        }
        return sent
    }

    private fun pairingIdentifierFor(tv: SamsungTv): String {
        if (tv.macAddress.isNotBlank()) {
            return tv.macAddress
        }
        return "ip_${tv.ipAddress}"
    }

    private fun clearSensitiveArtifactsFor(tv: SamsungTv) {
        val identifiers = linkedSetOf(
            pairingIdentifierFor(tv),
            "ip_${tv.ipAddress}"
        )

        if (tv.macAddress.isNotBlank()) {
            identifiers += tv.macAddress
        }

        identifiers.forEach { identifier ->
            if (identifier.isNotBlank()) {
                sensitivePairingStorage.deleteSensitiveData(identifier)
            }
        }
    }

    private suspend fun isTcpPortOpen(
        ipAddress: String,
        port: Int,
        timeoutMs: Int
    ): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(ipAddress, port),
                        timeoutMs
                    )
                    true
                }
            }.getOrDefault(false)
        }
    }

    private fun buildTrustingWsClient(): OkHttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(
                null,
                arrayOf<TrustManager>(trustAll),
                SecureRandom()
            )
        }
        val socketFactory = sslContext.socketFactory
        val hostnameVerifier = HostnameVerifier { _, _ -> true }
        return OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .sslSocketFactory(socketFactory, trustAll)
            .hostnameVerifier(hostnameVerifier)
            .build()
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
        private const val CONNECTION_READY_TIMEOUT_MS = 20_000L
        private const val DISCOVERY_SCAN_TIMEOUT_MS = 4_500L
        private const val RESOLVE_TIMEOUT_MS = 1_500L
        private const val SUBNET_DISCOVERY_CONCURRENCY = 36
        private const val SUBNET_PROBE_PORT_TIMEOUT_MS = 250
        private const val MODERN_CLIENT_NAME = "SamsungTVRemote"
        private const val LEGACY_SPC_SOCKET_PORT = 8000

        private const val EVENT_CHANNEL_CONNECT = "ms.channel.connect"
        private const val EVENT_CHANNEL_CLIENT_CONNECT = "ms.channel.clientConnect"
        private const val EVENT_CHANNEL_READY = "ms.channel.ready"
        private const val EVENT_CHANNEL_UNAUTHORIZED = "ms.channel.unauthorized"
        private const val EVENT_CHANNEL_ERROR = "ms.error"

        private val NSD_SERVICE_TYPES = listOf(
            "_samsungctl._tcp",
            "_samsung-multiscreen._tcp",
            "_samsungmsf._tcp",
            "_mediaremotetv._tcp"
        )

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
        RemoteKey.HOME -> "KEY_HOME"
        RemoteKey.BACK -> "KEY_RETURN"
        RemoteKey.EXIT -> "KEY_EXIT"
        RemoteKey.MENU -> "KEY_MENU"
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

internal fun legacyKeyCodeFor(key: RemoteKey): String? {
    return when (key) {
        RemoteKey.HOME -> "KEY_HOME"
        RemoteKey.BACK -> "KEY_RETURN"
        RemoteKey.EXIT -> "KEY_EXIT"
        RemoteKey.MENU -> "KEY_MENU"
        RemoteKey.D_PAD_UP -> "KEY_UP"
        RemoteKey.D_PAD_DOWN -> "KEY_DOWN"
        RemoteKey.D_PAD_LEFT -> "KEY_LEFT"
        RemoteKey.D_PAD_RIGHT -> "KEY_RIGHT"
        RemoteKey.OK -> "KEY_ENTER"
        RemoteKey.VOLUME_UP -> "KEY_VOLUP"
        RemoteKey.VOLUME_DOWN -> "KEY_VOLDOWN"
        RemoteKey.MUTE -> "KEY_MUTE"
        RemoteKey.MEDIA_PLAY_PAUSE -> "KEY_PLAY"
        RemoteKey.POWER -> "KEY_POWER"
    }
}
