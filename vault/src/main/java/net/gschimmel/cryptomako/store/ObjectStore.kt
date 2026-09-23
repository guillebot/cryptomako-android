package net.gschimmel.cryptomako.store

/**
 * Thin object storage abstraction so local filesystem and (later) S3 share one vault path.
 * Writes must never report success unless the backend truly persisted the bytes (fail-closed).
 */
interface ObjectStore {
    @Throws(ObjectStoreException::class)
    fun getObject(key: String): ByteArray

    @Throws(ObjectStoreException::class)
    fun headObject(key: String): ListedObject

    @Throws(ObjectStoreException::class)
    fun listImmediate(prefix: String): PrefixListing

    /** Optional; MVP may throw [ObjectStoreException.Unsupported]. */
    @Throws(ObjectStoreException::class)
    fun putObject(key: String, data: ByteArray)

    /** Optional; MVP may throw [ObjectStoreException.Unsupported]. */
    @Throws(ObjectStoreException::class)
    fun deleteObject(key: String)
}

data class ListedObject(
    val key: String,
    val size: Long,
    val eTag: String? = null,
)

data class PrefixListing(
    val objects: List<ListedObject> = emptyList(),
    val commonPrefixes: List<String> = emptyList(),
)

sealed class ObjectStoreException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotFound(val key: String) : ObjectStoreException("object not found: $key")
    class Transport(message: String, cause: Throwable? = null) : ObjectStoreException(message, cause)
    class UnsafeKey(val key: String) : ObjectStoreException("unsafe object key: $key")
    class Unsupported(op: String) : ObjectStoreException("unsupported operation: $op")
}
