package com.example.samsungremotetvandroid.core.diagnostics

import kotlinx.coroutines.flow.StateFlow

interface DiagnosticsTracker {
    val recentEvents: StateFlow<List<String>>
    val lastErrorSummary: StateFlow<String?>

    fun log(
        category: DiagnosticsCategory,
        message: String,
        metadata: Map<String, String> = emptyMap()
    )

    fun recordError(
        context: String,
        errorMessage: String,
        metadata: Map<String, String> = emptyMap()
    )
}
