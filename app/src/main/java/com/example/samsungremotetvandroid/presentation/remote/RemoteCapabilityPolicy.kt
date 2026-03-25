package com.example.samsungremotetvandroid.presentation.remote

import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.model.TvCapability

internal const val QUICK_LAUNCH_UNAVAILABLE_MESSAGE =
    "Quick Launch shortcuts are not available in this baseline yet. Installed-app browsing is unsupported."

internal fun capabilityForKey(key: RemoteKey): TvCapability? {
    return when (key) {
        RemoteKey.D_PAD_UP,
        RemoteKey.D_PAD_DOWN,
        RemoteKey.D_PAD_LEFT,
        RemoteKey.D_PAD_RIGHT,
        RemoteKey.OK -> TvCapability.D_PAD

        RemoteKey.VOLUME_UP,
        RemoteKey.VOLUME_DOWN,
        RemoteKey.MUTE -> TvCapability.VOLUME

        RemoteKey.MEDIA_PLAY_PAUSE -> TvCapability.MEDIA
        RemoteKey.POWER -> TvCapability.POWER
    }
}

internal fun activeTvId(state: ConnectionState): String? {
    return when (state) {
        is ConnectionState.ConnectedNotReady -> state.tvId
        is ConnectionState.Ready -> state.tvId
        is ConnectionState.Pairing -> state.tvId
        is ConnectionState.PinRequired -> state.tvId
        ConnectionState.Disconnected,
        ConnectionState.Connecting,
        is ConnectionState.Error -> null
    }
}

internal fun unsupportedMessageForCapability(capability: TvCapability): String {
    return when (capability) {
        TvCapability.D_PAD -> "D-pad actions are not supported on this TV."
        TvCapability.VOLUME -> "Volume actions are not supported on this TV."
        TvCapability.MEDIA -> "Media actions are not supported on this TV."
        TvCapability.POWER -> "Power action is not supported on this TV."
        TvCapability.QUICK_LAUNCH -> "Quick Launch is not supported on this TV."
        TvCapability.NUMBER_PAD -> "Number pad actions are not supported on this TV."
        TvCapability.TRACKPAD -> "Trackpad actions are not supported on this TV."
    }
}
