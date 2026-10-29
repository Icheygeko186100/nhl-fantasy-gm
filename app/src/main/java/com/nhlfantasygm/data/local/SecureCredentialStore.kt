package com.nhlfantasygm.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Hardware-backed Android Keystore credential storage for optional user-supplied external
 * sports-data API keys. No private API keys or secrets are ever embedded in the APK.
 */
class SecureCredentialStore(context: Context) {

    private val prefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "nhl_fantasy_gm_secure_creds",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    fun getOptionalExternalApiKey(): String {
        return prefs?.getString(KEY_CUSTOM_SPORTS_API, "") ?: ""
    }

    fun saveOptionalExternalApiKey(apiKey: String) {
        prefs?.edit()?.putString(KEY_CUSTOM_SPORTS_API, apiKey.trim())?.apply()
    }

    fun clearOptionalExternalApiKey() {
        prefs?.edit()?.remove(KEY_CUSTOM_SPORTS_API)?.apply()
    }

    companion object {
        private const val KEY_CUSTOM_SPORTS_API = "optional_external_sports_api_key"
    }
}
