package com.example.samsungremotetvandroid.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class EncryptedSensitivePairingStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : SensitivePairingStorage {
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun saveToken(token: String, identifier: String) {
        if (identifier.isBlank()) return
        encryptedPrefs.edit().putString(tokenKey(identifier), token).apply()
    }

    override fun loadToken(identifier: String): String? {
        if (identifier.isBlank()) return null
        return encryptedPrefs.getString(tokenKey(identifier), null)
    }

    override fun deleteToken(identifier: String) {
        if (identifier.isBlank()) return
        encryptedPrefs.edit().remove(tokenKey(identifier)).apply()
    }

    override fun saveSpcCredentials(credentials: SpcCredentials, identifier: String) {
        if (identifier.isBlank()) return
        val serialized = JSONObject()
            .put("ctxUpperHex", credentials.ctxUpperHex)
            .put("sessionId", credentials.sessionId)
            .toString()
        encryptedPrefs.edit().putString(spcKey(identifier), serialized).apply()
    }

    override fun loadSpcCredentials(identifier: String): SpcCredentials? {
        if (identifier.isBlank()) return null
        val serialized = encryptedPrefs.getString(spcKey(identifier), null) ?: return null
        return runCatching {
            val payload = JSONObject(serialized)
            SpcCredentials(
                ctxUpperHex = payload.optString("ctxUpperHex"),
                sessionId = payload.optInt("sessionId")
            )
        }.getOrNull()
    }

    override fun deleteSpcCredentials(identifier: String) {
        if (identifier.isBlank()) return
        encryptedPrefs.edit().remove(spcKey(identifier)).apply()
    }

    override fun saveSpcVariants(variants: SpcVariants, identifier: String) {
        if (identifier.isBlank()) return
        val serialized = JSONObject()
            .put("step0", variants.step0)
            .put("step1", variants.step1)
            .toString()
        encryptedPrefs.edit().putString(spcVariantKey(identifier), serialized).apply()
    }

    override fun loadSpcVariants(identifier: String): SpcVariants? {
        if (identifier.isBlank()) return null
        val serialized = encryptedPrefs.getString(spcVariantKey(identifier), null) ?: return null
        return runCatching {
            val payload = JSONObject(serialized)
            SpcVariants(
                step0 = payload.optString("step0").takeIf { it.isNotBlank() },
                step1 = payload.optString("step1").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    override fun deleteSpcVariants(identifier: String) {
        if (identifier.isBlank()) return
        encryptedPrefs.edit().remove(spcVariantKey(identifier)).apply()
    }

    override fun deleteSensitiveData(identifier: String) {
        if (identifier.isBlank()) return
        encryptedPrefs.edit()
            .remove(tokenKey(identifier))
            .remove(spcKey(identifier))
            .remove(spcVariantKey(identifier))
            .apply()
    }

    private fun tokenKey(identifier: String): String = "token_$identifier"

    private fun spcKey(identifier: String): String = "spc_$identifier"

    private fun spcVariantKey(identifier: String): String = "spc_variant_$identifier"

    private companion object {
        private const val PREF_NAME = "sensitive_pairing_storage"
    }
}
