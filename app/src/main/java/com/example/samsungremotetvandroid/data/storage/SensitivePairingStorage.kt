package com.example.samsungremotetvandroid.data.storage

data class SpcCredentials(
    val ctxUpperHex: String,
    val sessionId: Int
)

data class SpcVariants(
    val step0: String?,
    val step1: String?
)

interface SensitivePairingStorage {
    fun saveToken(token: String, identifier: String)
    fun loadToken(identifier: String): String?
    fun deleteToken(identifier: String)

    fun saveSpcCredentials(credentials: SpcCredentials, identifier: String)
    fun loadSpcCredentials(identifier: String): SpcCredentials?
    fun deleteSpcCredentials(identifier: String)

    fun saveSpcVariants(variants: SpcVariants, identifier: String)
    fun loadSpcVariants(identifier: String): SpcVariants?
    fun deleteSpcVariants(identifier: String)

    fun deleteSensitiveData(identifier: String)
}
