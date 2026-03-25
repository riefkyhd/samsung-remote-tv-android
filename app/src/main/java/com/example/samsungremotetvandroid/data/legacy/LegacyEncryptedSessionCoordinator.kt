package com.example.samsungremotetvandroid.data.legacy

import com.example.samsungremotetvandroid.domain.model.ConnectionState

class LegacyEncryptedSessionCoordinator {
    enum class CredentialSource {
        STORED,
        FRESH_PAIRING
    }

    data class Transition(
        val emittedStates: List<ConnectionState>,
        val awaitingFirstCommand: Boolean,
        val credentialSource: CredentialSource?
    )

    fun onConnect(tvId: String, hasStoredCredentials: Boolean): Transition {
        val states = mutableListOf<ConnectionState>()
        states += ConnectionState.Connecting

        return if (hasStoredCredentials) {
            states += ConnectionState.ConnectedNotReady(tvId)
            Transition(
                emittedStates = states,
                awaitingFirstCommand = true,
                credentialSource = CredentialSource.STORED
            )
        } else {
            states += ConnectionState.Pairing(tvId = tvId, countdownSeconds = PAIRING_COUNTDOWN_SECONDS)
            states += ConnectionState.PinRequired(tvId = tvId, countdownSeconds = PIN_COUNTDOWN_SECONDS)
            Transition(
                emittedStates = states,
                awaitingFirstCommand = false,
                credentialSource = null
            )
        }
    }

    fun onPairingCompleted(tvId: String): Transition {
        return Transition(
            emittedStates = listOf(ConnectionState.ConnectedNotReady(tvId)),
            awaitingFirstCommand = true,
            credentialSource = CredentialSource.FRESH_PAIRING
        )
    }

    fun onFirstCommand(tvId: String, source: CredentialSource?): Transition {
        return when (source) {
            CredentialSource.STORED -> {
                Transition(
                    emittedStates = listOf(
                        ConnectionState.Error("Encrypted credentials appear stale; starting fresh pairing."),
                        ConnectionState.Pairing(
                            tvId = tvId,
                            countdownSeconds = PAIRING_COUNTDOWN_SECONDS
                        ),
                        ConnectionState.PinRequired(
                            tvId = tvId,
                            countdownSeconds = PIN_COUNTDOWN_SECONDS
                        )
                    ),
                    awaitingFirstCommand = false,
                    credentialSource = null
                )
            }

            CredentialSource.FRESH_PAIRING,
            null -> {
                Transition(
                    emittedStates = listOf(
                        ConnectionState.Error(
                            "Legacy encrypted/JU command transport is not implemented in this spike."
                        )
                    ),
                    awaitingFirstCommand = false,
                    credentialSource = source
                )
            }
        }
    }

    fun onCancelPairing(): Transition {
        return Transition(
            emittedStates = listOf(ConnectionState.Disconnected),
            awaitingFirstCommand = false,
            credentialSource = null
        )
    }

    private companion object {
        private const val PAIRING_COUNTDOWN_SECONDS = 30
        private const val PIN_COUNTDOWN_SECONDS = 60
    }
}
