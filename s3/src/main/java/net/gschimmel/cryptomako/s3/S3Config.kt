package net.gschimmel.cryptomako.s3

/**
 * DRAFT connection settings for a future HTTPS path-style S3 client.
 * Keys are proposed only — not yet shared with the macOS app JSON.
 */
data class S3Config(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val prefix: String = "",
    val accessKey: String,
    /** Never log. Prefer Keystore / EncryptedSharedPreferences in a ship build. */
    val secretKey: CharArray,
) {
    init {
        require(endpoint.startsWith("https://")) {
            "S3 endpoint must be HTTPS (fail closed on cleartext)"
        }
    }
}
