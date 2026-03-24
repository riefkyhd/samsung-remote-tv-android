package com.example.samsungremotetvandroid.domain.model

data class SamsungTv(
    val id: String,
    val displayName: String,
    val ipAddress: String,
    val protocol: TvProtocol,
    val capabilities: Set<TvCapability>
)

enum class TvProtocol {
    MODERN,
    LEGACY_ENCRYPTED,
    LEGACY_REMOTE
}

enum class TvCapability {
    D_PAD,
    VOLUME,
    MEDIA,
    POWER,
    QUICK_LAUNCH,
    NUMBER_PAD,
    TRACKPAD
}
