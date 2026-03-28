package com.example.samsungremotetvandroid.data.legacy

import com.example.samsungremotetvandroid.data.storage.SpcCredentials
import com.example.samsungremotetvandroid.data.storage.SpcVariants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class SpcHandshakeClient(
    private val httpClient: OkHttpClient
) {
    data class PairingOutcome(
        val credentials: SpcCredentials,
        val step0Variant: String,
        val step1Variant: String
    )

    private val pendingByIp = linkedSetOf<String>()
    private val pendingMutex = Mutex()

    suspend fun startPairing(ipAddress: String) {
        val alreadyPending = pendingMutex.withLock {
            pendingByIp.contains(ipAddress)
        }

        if (alreadyPending) {
            val running = waitForPinPageRunning(
                ipAddress = ipAddress,
                timeoutMs = 2_000
            )
            if (running) {
                return
            }

            pendingMutex.withLock {
                pendingByIp.remove(ipAddress)
            }
        }

        requestDeletePinPage(ipAddress)
        requestShowPinPage(ipAddress)

        pendingMutex.withLock {
            pendingByIp.add(ipAddress)
        }
    }

    suspend fun completePairing(
        ipAddress: String,
        pin: String,
        preferredVariants: SpcVariants?
    ): PairingOutcome {
        val normalizedPin = pin.trim()
        if (normalizedPin.isBlank()) {
            throw IllegalStateException("Enter the PIN shown on your TV.")
        }

        val deviceId = FIXED_DEVICE_ID
        requestStep0(
            ipAddress = ipAddress,
            deviceId = deviceId
        )

        val localHello = SpcCrypto.generateServerHello(
            userId = deviceId,
            pin = normalizedPin
        )

        val step1Response = requestStep1(
            ipAddress = ipAddress,
            deviceId = deviceId,
            serverHelloHex = localHello.serverHelloHex
        )

        val clientHelloHex = extractGeneratorClientHello(step1Response)
            ?: throw IllegalStateException("TV returned empty SPC step1 response.")

        val parsedClientHello = SpcCrypto.parseClientHello(
            clientHelloHex = clientHelloHex,
            aesKey = localHello.aesKey,
            userId = deviceId
        ) ?: throw IllegalStateException("The TV rejected the PIN. Enter the current TV PIN and retry.")

        val requestId = extractRequestId(step1Response) ?: "0"
        val serverAck = SpcCrypto.generateServerAcknowledge(parsedClientHello.skPrime)

        val step2Response = requestStep2(
            ipAddress = ipAddress,
            deviceId = deviceId,
            requestId = requestId,
            serverAckMsg = serverAck
        )

        if (isAuthDataEmpty(step2Response)) {
            throw IllegalStateException("TV closed the pairing session before completion. Retry pairing.")
        }

        val parsedStep2 = parseStep2(step2Response)

        pendingMutex.withLock {
            pendingByIp.remove(ipAddress)
        }

        return PairingOutcome(
            credentials = SpcCredentials(
                ctxUpperHex = parsedClientHello.ctxUpperHex,
                sessionId = parsedStep2.sessionId
            ),
            step0Variant = preferredVariants?.step0 ?: "CONFIRMED",
            step1Variant = preferredVariants?.step1 ?: "CONFIRMED"
        )
    }

    suspend fun cancelPairing(ipAddress: String) {
        pendingMutex.withLock {
            pendingByIp.remove(ipAddress)
        }
    }

    private suspend fun requestDeletePinPage(ipAddress: String) {
        val request = Request.Builder()
            .url("http://$ipAddress:8080/ws/apps/CloudPINPage/run")
            .delete()
            .build()
        execute(request)
    }

    private suspend fun requestShowPinPage(ipAddress: String) {
        val getRequest = Request.Builder()
            .url("http://$ipAddress:8080/ws/apps/CloudPINPage")
            .get()
            .build()
        execute(getRequest)

        val postRequest = Request.Builder()
            .url("http://$ipAddress:8080/ws/apps/CloudPINPage")
            .post("pin4".toRequestBody(TEXT_PLAIN))
            .build()
        val postResponse = execute(postRequest)

        if (postResponse.statusCode !in 200..299) {
            throw IllegalStateException("Could not open the PIN page on TV. Keep the TV awake and retry.")
        }

        val running = waitForPinPageRunning(
            ipAddress = ipAddress,
            timeoutMs = 3_000
        )
        if (!running) {
            throw IllegalStateException("TV did not show a PIN page. Retry connect.")
        }
    }

    private suspend fun waitForPinPageRunning(
        ipAddress: String,
        timeoutMs: Long
    ): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val stateRequest = Request.Builder()
                .url("http://$ipAddress:8080/ws/apps/CloudPINPage")
                .get()
                .build()
            val response = execute(stateRequest)
            if (response.statusCode in 200..299 && response.body.contains("<state>running</state>", ignoreCase = true)) {
                return true
            }
            delay(250)
        }
        return false
    }

    private suspend fun requestStep0(
        ipAddress: String,
        deviceId: String
    ): String {
        val request = Request.Builder()
            .url(pairingUrl(ipAddress, step = 0, deviceId = deviceId, includeType = true))
            .get()
            .build()
        return execute(request).body
    }

    private suspend fun requestStep1(
        ipAddress: String,
        deviceId: String,
        serverHelloHex: String
    ): String {
        val body = JSONObject()
            .put(
                "auth_Data",
                JSONObject()
                    .put("auth_type", "SPC")
                    .put("GeneratorServerHello", serverHelloHex)
            )
            .toString()

        val request = Request.Builder()
            .url(pairingUrl(ipAddress, step = 1, deviceId = deviceId, includeType = false))
            .post(body.toRequestBody(APPLICATION_JSON))
            .build()

        val response = execute(request)
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("TV rejected SPC step1 (${response.statusCode}).")
        }
        return response.body
    }

    private suspend fun requestStep2(
        ipAddress: String,
        deviceId: String,
        requestId: String,
        serverAckMsg: String
    ): String {
        val body = JSONObject()
            .put(
                "auth_Data",
                JSONObject()
                    .put("auth_type", "SPC")
                    .put("request_id", requestId)
                    .put("ServerAckMsg", serverAckMsg)
            )
            .toString()

        val request = Request.Builder()
            .url(pairingUrl(ipAddress, step = 2, deviceId = deviceId, includeType = false))
            .post(body.toRequestBody(APPLICATION_JSON))
            .build()

        val response = execute(request)
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("TV rejected SPC step2 (${response.statusCode}).")
        }
        return response.body
    }

    private fun extractGeneratorClientHello(step1Raw: String): String? {
        val root = parseJson(step1Raw) ?: return null

        val directAuth = root.optJSONObject("auth_Data") ?: root.optJSONObject("auth_data")
        val direct = directAuth?.optString("GeneratorClientHello")
        if (!direct.isNullOrBlank()) {
            return direct
        }

        val authText = root.optString("auth_Data").ifBlank {
            root.optString("auth_data")
        }
        if (authText.isNotBlank()) {
            val authJson = parseJson(authText)
            val nested = authJson?.optString("GeneratorClientHello")
            if (!nested.isNullOrBlank()) {
                return nested
            }
        }

        return null
    }

    private fun extractRequestId(step1Raw: String): String? {
        val root = parseJson(step1Raw) ?: return null

        val directAuth = root.optJSONObject("auth_Data") ?: root.optJSONObject("auth_data")
        directAuth?.optString("request_id")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val authText = root.optString("auth_Data").ifBlank {
            root.optString("auth_data")
        }
        if (authText.isNotBlank()) {
            val authJson = parseJson(authText)
            authJson?.optString("request_id")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        return null
    }

    private fun parseStep2(raw: String): ParsedStep2 {
        val root = parseJson(raw)
            ?: throw IllegalStateException("Invalid SPC step2 response.")

        val authDataObject = root.optJSONObject("auth_data")
            ?: root.optJSONObject("auth_Data")

        val authData = if (authDataObject != null) {
            authDataObject
        } else {
            val authText = root.optString("auth_data").ifBlank {
                root.optString("auth_Data")
            }
            parseJson(authText)
                ?: throw IllegalStateException("Invalid SPC step2 auth_data payload.")
        }

        val sessionId = authData.optInt("session_id").takeIf { it > 0 }
            ?: authData.optString("session_id")
                .toIntOrNull()
                ?.takeIf { it > 0 }
            ?: throw IllegalStateException("SPC step2 session_id is missing.")

        return ParsedStep2(
            sessionId = sessionId
        )
    }

    private fun isAuthDataEmpty(raw: String): Boolean {
        val normalized = raw
            .replace(" ", "")
            .replace("\n", "")
            .lowercase()
        return normalized == "{\"auth_data\":\"\"}"
            || normalized == "{\"auth_data\":null}"
            || normalized.contains("\"auth_data\":\"\"")
    }

    private fun parseJson(raw: String): JSONObject? {
        if (raw.isBlank()) {
            return null
        }
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun pairingUrl(
        ipAddress: String,
        step: Int,
        deviceId: String,
        includeType: Boolean
    ): String {
        val base = "http://$ipAddress:8080/ws/pairing?step=$step&app_id=$APP_ID&device_id=$deviceId"
        return if (includeType) {
            "$base&type=1"
        } else {
            base
        }
    }

    private suspend fun execute(request: Request): HttpResponse {
        return withContext(Dispatchers.IO) {
            val call = httpClient.newCall(request)
            val response = call.execute()
            response.use {
                HttpResponse(
                    statusCode = response.code,
                    body = response.body?.string().orEmpty()
                )
            }
        }
    }

    private data class HttpResponse(
        val statusCode: Int,
        val body: String
    )

    private data class ParsedStep2(
        val sessionId: Int
    )

    private companion object {
        private val APPLICATION_JSON = "application/json; charset=utf-8".toMediaType()
        private val TEXT_PLAIN = "text/plain; charset=utf-8".toMediaType()

        private const val APP_ID = "12345"
        private const val FIXED_DEVICE_ID = "654321"
    }
}
