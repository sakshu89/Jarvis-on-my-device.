package com.jarvis.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted key/value storage (Android Keystore-backed AES-256).
 * Holds API keys, settings, memories and reminders. Nothing here is ever logged.
 */
class SecureStore(private val ctx: Context) {
    private val prefs: SharedPreferences = create()

    private fun create(): SharedPreferences {
        fun build(): SharedPreferences {
            val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                ctx,
                FILE,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return try {
            build()
        } catch (e: Exception) {
            // Keystore corruption (restore, OEM bug): start clean rather than crash.
            ctx.deleteSharedPreferences(FILE)
            build()
        }
    }

    fun str(key: String, def: String = ""): String = prefs.getString(key, def) ?: def
    fun putStr(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun bool(key: String, def: Boolean = false): Boolean = prefs.getBoolean(key, def)
    fun putBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun remove(key: String) = prefs.edit().remove(key).apply()

    private companion object {
        const val FILE = "jarvis_secure"
    }
}
