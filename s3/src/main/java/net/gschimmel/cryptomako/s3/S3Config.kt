package net.gschimmel.cryptomako.s3

/**
 * Runtime S3 connection settings (HTTPS path-style by default).
 * Aligns with macOS [VaultSettings] non-secret fields; [secretKey] is never JSON-persisted.
 */
data class S3Config(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val prefix: String = "",
    val accessKey: String,
    /** Never log. Prefer EncryptedSharedPreferences / Keystore in the app. */
    val secretKey: CharArray,
    val pathStyle: Boolean = true,
) {
    init {
        val okHttps = endpoint.startsWith("https://")
        val okLocalHttp = endpoint.startsWith("http://127.0.0.1") ||
            endpoint.startsWith("http://localhost") ||
            endpoint.startsWith("http://[::1]")
        require(okHttps || okLocalHttp) {
            "S3 endpoint must be HTTPS (fail closed on cleartext; localhost HTTP allowed for tests only)"
        }
        require(bucket.isNotBlank()) { "bucket required" }
        require(accessKey.isNotBlank()) { "accessKey required" }
        require(secretKey.isNotEmpty()) { "secretKey required" }
    }

    val normalizedPrefix: String
        get() {
            val trimmed = prefix.trim()
            if (trimmed.isEmpty()) return ""
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
}
