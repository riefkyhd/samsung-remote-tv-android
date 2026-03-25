package com.example.samsungremotetvandroid.domain.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    data object Connecting : ConnectionState

    data class Pairing(
        val tvId: String,
        val countdownSeconds: Int
    ) : ConnectionState

    data class PinRequired(
        val tvId: String,
        val countdownSeconds: Int
    ) : ConnectionState

    data class ConnectedNotReady(
        val tvId: String
    ) : ConnectionState

    data class Ready(
        val tvId: String
    ) : ConnectionState

    data class Error(
        val message: String
    ) : ConnectionState
}
