package com.example.iris

import android.content.Context

// Using a deprecated library here because the modern alternative (Google Tink + JetPack Datastore) is overkill for our needs
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureConfig(context: Context) {
    private val prefs = run {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "iris_secure_prefs",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var bridgeUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_URL, v.trimEnd('/')).apply()

    var bearerToken: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TOKEN, v.trim()).apply()

    val isConfigured: Boolean
        get() = bridgeUrl.isNotBlank() && bearerToken.isNotBlank()

    private companion object {
        const val KEY_URL = "bridge_url"
        const val KEY_TOKEN = "bearer_token"
    }
}
