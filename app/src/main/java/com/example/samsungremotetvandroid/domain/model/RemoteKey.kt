package com.example.samsungremotetvandroid.domain.model

enum class RemoteKey {
    D_PAD_UP,
    D_PAD_DOWN,
    D_PAD_LEFT,
    D_PAD_RIGHT,
    OK,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    MEDIA_PLAY_PAUSE,
    POWER
}

data class QuickLaunchShortcut(
    val id: String,
    val title: String
)
