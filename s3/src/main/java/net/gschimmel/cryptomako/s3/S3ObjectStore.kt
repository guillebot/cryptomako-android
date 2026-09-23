package net.gschimmel.cryptomako.s3

import net.gschimmel.cryptomako.store.ListedObject
import net.gschimmel.cryptomako.store.ObjectStore
import net.gschimmel.cryptomako.store.ObjectStoreException
import net.gschimmel.cryptomako.store.PrefixListing
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Sketch of a thin OkHttp + SigV4 HTTPS path-style ObjectStore.
 *
 * MVP: not wired for live S3. Reads/writes throw [ObjectStoreException.Unsupported]
 * or [ObjectStoreException.Transport] — never fake a successful remote write.
 */
class S3ObjectStore(
    private val config: S3Config,
    private val client: OkHttpClient = defaultClient(),
) : ObjectStore {

    override fun getObject(key: String): ByteArray {
        throw ObjectStoreException.Unsupported("S3 getObject (SigV4 path-style not implemented in MVP)")
    }

    override fun headObject(key: String): ListedObject {
        throw ObjectStoreException.Unsupported("S3 headObject (not implemented in MVP)")
    }

    override fun listImmediate(prefix: String): PrefixListing {
        throw ObjectStoreException.Unsupported("S3 listImmediate (not implemented in MVP)")
    }

    override fun putObject(key: String, data: ByteArray) {
        // Fail closed: never report write success without a real 200 from S3.
        throw ObjectStoreException.Unsupported("S3 putObject (writes out of scope for MVP)")
    }

    override fun deleteObject(key: String) {
        throw ObjectStoreException.Unsupported("S3 deleteObject (writes out of scope for MVP)")
    }

    companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .cache(null)
                .build()
    }
}

/**
 * Placeholder for AWS SigV4 signing (path-style URL:
 * `https://{endpoint-host}/{bucket}/{key}`).
 * Implementation intentionally omitted for this MVP.
 */
object SigV4 {
    // Future: canonical request, string-to-sign, HMAC-SHA256 signing key chain.
}
