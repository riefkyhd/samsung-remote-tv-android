package com.example.samsungremotetvandroid.core.diagnostics

enum class DiagnosticsCategory(val wireName: String) {
    PROTOCOL("protocol"),
    PAIRING("pairing"),
    RECONNECT("reconnect"),
    CAPABILITIES("capabilities"),
    ERROR("error"),
    LIFECYCLE("lifecycle")
}
