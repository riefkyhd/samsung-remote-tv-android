package com.example.samsungremotetvandroid.core.diagnostics

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class InMemoryDiagnosticsTracker @Inject constructor() : DiagnosticsTracker {
    private val recentEventsFlow = MutableStateFlow<List<String>>(emptyList())
    private val lastErrorSummaryFlow = MutableStateFlow<String?>(null)

    override val recentEvents: StateFlow<List<String>> = recentEventsFlow.asStateFlow()
    override val lastErrorSummary: StateFlow<String?> = lastErrorSummaryFlow.asStateFlow()

    override fun log(
        category: DiagnosticsCategory,
        message: String,
        metadata: Map<String, String>
    ) {
        val sanitizedMessage = sanitizeMessage(message)
        val sanitizedMetadata = sanitizeMetadata(metadata)
        val event = DiagnosticsEvent(
            category = category,
            message = sanitizedMessage,
            metadata = sanitizedMetadata
        )
        val displayEvent = event.toDisplayString()
        emitToLogcat("[TVDBG]$displayEvent")
        appendEvent(displayEvent)
    }

    override fun recordError(
        context: String,
        errorMessage: String,
        metadata: Map<String, String>
    ) {
        val sanitizedContext = sanitizeMessage(context)
        val sanitizedError = sanitizeMessage(errorMessage)
        lastErrorSummaryFlow.value = "$sanitizedContext: $sanitizedError"

        log(
            category = DiagnosticsCategory.ERROR,
            message = "ui error",
            metadata = metadata + mapOf(
                "context" to sanitizedContext,
                "message" to sanitizedError
            )
        )
    }

    private fun appendEvent(event: String) {
        recentEventsFlow.update { current ->
            val updated = current + event
            if (updated.size <= MAX_RECENT_EVENTS) {
                updated
            } else {
                updated.takeLast(MAX_RECENT_EVENTS)
            }
        }
    }

    private fun emitToLogcat(message: String) {
        // Local JVM unit tests do not provide android.util.Log implementations.
        runCatching { Log.d(LOG_TAG, message) }
    }

    private fun sanitizeMessage(raw: String): String {
        return raw.trim().replace(Regex("\\s+"), " ")
    }

    private fun sanitizeMetadata(metadata: Map<String, String>): Map<String, String> {
        return metadata.mapValues { (key, value) ->
            sanitizeMetadataValue(key = key, value = value)
        }
    }

    private fun sanitizeMetadataValue(key: String, value: String): String {
        val normalizedKey = key.lowercase()
        val trimmedValue = value.trim()

        return when {
            SENSITIVE_KEY_TOKENS.any { token -> normalizedKey.contains(token) } -> REDACTED
            IDENTIFIER_KEY_TOKENS.any { token -> normalizedKey.contains(token) } ->
                redactIdentifier(trimmedValue)
            else -> sanitizeMessage(trimmedValue)
        }
    }

    private fun redactIdentifier(value: String): String {
        if (value.isBlank()) {
            return value
        }

        return if (value.length <= 4) {
            "***"
        } else {
            "${value.take(2)}...${value.takeLast(2)}"
        }
    }

    companion object {
        private const val LOG_TAG = "TVDiagnostics"
        private const val REDACTED = "[REDACTED]"
        private const val MAX_RECENT_EVENTS = 20

        private val SENSITIVE_KEY_TOKENS = listOf(
            "pin",
            "token",
            "secret",
            "credential",
            "password",
            "auth",
            "spc"
        )
        private val IDENTIFIER_KEY_TOKENS = listOf(
            "ip",
            "mac",
            "device",
            "tv",
            "id"
        )
    }
}
