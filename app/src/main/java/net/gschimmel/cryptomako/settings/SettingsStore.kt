package net.gschimmel.cryptomako.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * Persists non-secret [VaultSettings] in plain SharedPreferences JSON,
 * and the S3 secret key in EncryptedSharedPreferences (Keystore-backed).
 * Passphrase is never persisted.
 */
class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val secretPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            SECRET_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): VaultSettings {
        val json = prefs.getString(KEY_SETTINGS_JSON, null) ?: return VaultSettings()
        return try {
            val obj = JSONObject(json)
            val map = obj.keys().asSequence().associateWith { key ->
                when (val v = obj.get(key)) {
                    JSONObject.NULL -> null
                    else -> v
                }
            }
            VaultSettings.fromJsonMap(map)
        } catch (_: Exception) {
            VaultSettings()
        }
    }

    fun save(settings: VaultSettings) {
        val map = settings.toJsonMap()
        val obj = JSONObject()
        for ((k, v) in map) obj.put(k, v)
        prefs.edit().putString(KEY_SETTINGS_JSON, obj.toString()).apply()
    }

    fun loadSecretKey(): CharArray {
        val s = secretPrefs.getString(KEY_SECRET, "") ?: ""
        return s.toCharArray()
    }

    fun saveSecretKey(secret: CharArray) {
        secretPrefs.edit().putString(KEY_SECRET, String(secret)).apply()
    }

    fun clearSecretKey() {
        secretPrefs.edit().remove(KEY_SECRET).apply()
    }

    companion object {
        private const val PREFS_NAME = "cryptomako_settings"
        private const val SECRET_PREFS_NAME = "cryptomako_secrets"
        private const val KEY_SETTINGS_JSON = "vault_settings_json"
        private const val KEY_SECRET = "s3_secret_key"
    }
}
