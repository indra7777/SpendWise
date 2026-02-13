package com.rupeelog.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DPDP & Google Play Compliance: Encrypted storage for sensitive data
 *
 * Uses Android's EncryptedSharedPreferences with AES-256 encryption
 * for storing API keys and other sensitive configuration.
 */
@Singleton
class EncryptedPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // API Keys
    fun getGeminiApiKey(): String? = encryptedPrefs.getString(KEY_GEMINI_API, null)

    fun setGeminiApiKey(key: String?) {
        encryptedPrefs.edit().apply {
            if (key != null) putString(KEY_GEMINI_API, key)
            else remove(KEY_GEMINI_API)
        }.apply()
    }

    // User consent tracking (DPDP requirement)
    fun hasUserConsented(): Boolean = encryptedPrefs.getBoolean(KEY_USER_CONSENT, false)

    fun setUserConsent(consented: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_USER_CONSENT, consented).apply()
    }

    fun getConsentTimestamp(): Long = encryptedPrefs.getLong(KEY_CONSENT_TIMESTAMP, 0)

    fun setConsentTimestamp(timestamp: Long) {
        encryptedPrefs.edit().putLong(KEY_CONSENT_TIMESTAMP, timestamp).apply()
    }

    // MCP Server token (for secure API access)
    fun getMcpServerToken(): String? = encryptedPrefs.getString(KEY_MCP_TOKEN, null)

    fun setMcpServerToken(token: String?) {
        encryptedPrefs.edit().apply {
            if (token != null) putString(KEY_MCP_TOKEN, token)
            else remove(KEY_MCP_TOKEN)
        }.apply()
    }

    fun generateMcpServerToken(): String {
        val token = java.util.UUID.randomUUID().toString()
        setMcpServerToken(token)
        return token
    }

    // Clear all encrypted data (for "Delete My Data" feature)
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    companion object {
        private const val ENCRYPTED_PREFS_NAME = "rupeelog_secure_prefs"
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_USER_CONSENT = "user_consent_given"
        private const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"
        private const val KEY_MCP_TOKEN = "mcp_server_token"
    }
}
