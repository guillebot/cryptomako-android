package net.gschimmel.cryptomako.settings

/**
 * Non-secret connection settings. Keys align with macOS `VaultSettings`:
 * storageMode, endpoint, region, bucket, prefix, accessKey, localVaultPath, autoReconnect, pathStyle?
 *
 * Secret key + passphrase are NOT stored in this JSON — app Keystore /
 * EncryptedSharedPreferences (or test env) hold those.
 */
data class VaultSettings(
    val storageMode: StorageMode = StorageMode.LOCAL,
    val endpoint: String = "",
    val region: String = "us-east-1",
    val bucket: String = "",
    val prefix: String = "",
    val accessKey: String = "",
    val localVaultPath: String = "",
    val autoReconnect: Boolean = false,
    val pathStyle: Boolean = true,
) {
    enum class StorageMode { LOCAL, S3 }

    val isLocal: Boolean get() = storageMode == StorageMode.LOCAL

    val normalizedPrefix: String
        get() {
            val trimmed = prefix.trim()
            if (trimmed.isEmpty()) return ""
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }

    val isComplete: Boolean
        get() = if (isLocal) {
            localVaultPath.isNotBlank()
        } else {
            endpoint.isNotBlank() && bucket.isNotBlank() && accessKey.isNotBlank() &&
                endpoint.startsWith("https://")
        }

    fun toJsonMap(): Map<String, Any> = buildMap {
        put("storageMode", storageMode.name.lowercase())
        put("endpoint", endpoint)
        put("region", region)
        put("bucket", bucket)
        put("prefix", normalizedPrefix)
        put("accessKey", accessKey)
        if (localVaultPath.isNotEmpty()) put("localVaultPath", localVaultPath)
        put("autoReconnect", autoReconnect)
        put("pathStyle", pathStyle)
    }

    companion object {
        fun fromJsonMap(map: Map<String, Any?>): VaultSettings {
            val modeRaw = (map["storageMode"] as? String)?.lowercase()
            val localPath = (map["localVaultPath"] as? String)
                ?: (map["localPath"] as? String)
                ?: ""
            val mode = when (modeRaw) {
                "local" -> StorageMode.LOCAL
                "s3" -> StorageMode.S3
                null -> if (localPath.isEmpty()) StorageMode.S3 else StorageMode.LOCAL
                else -> StorageMode.LOCAL
            }
            return VaultSettings(
                storageMode = mode,
                endpoint = map["endpoint"] as? String ?: "",
                region = map["region"] as? String ?: "us-east-1",
                bucket = map["bucket"] as? String ?: "",
                prefix = map["prefix"] as? String ?: "",
                accessKey = map["accessKey"] as? String ?: "",
                localVaultPath = localPath,
                autoReconnect = map["autoReconnect"] as? Boolean ?: false,
                pathStyle = map["pathStyle"] as? Boolean ?: true,
            )
        }
    }
}
