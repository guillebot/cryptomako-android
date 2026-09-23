package net.gschimmel.cryptomako.store

import java.io.File
import java.nio.file.Files

/**
 * Filesystem-backed [ObjectStore]. Used for golden fixtures and local vault paths.
 * Rejects keys with `..` or absolute paths so listings cannot escape [root].
 */
class LocalFilesystemObjectStore(root: File) : ObjectStore {
    private val root: File = root.canonicalFile

    override fun getObject(key: String): ByteArray {
        val file = resolveFile(key)
        if (!file.isFile) throw ObjectStoreException.NotFound(key)
        return file.readBytes()
    }

    override fun headObject(key: String): ListedObject {
        val file = resolveFile(key)
        if (!file.isFile) throw ObjectStoreException.NotFound(key)
        return ListedObject(key = key, size = file.length(), eTag = "local")
    }

    override fun listImmediate(prefix: String): PrefixListing {
        val dir = resolveFile(prefix)
        if (!dir.isDirectory) {
            return PrefixListing()
        }
        val objects = mutableListOf<ListedObject>()
        val prefixes = mutableListOf<String>()
        val children = dir.listFiles() ?: emptyArray()
        for (child in children) {
            val childKey = if (prefix.endsWith("/") || prefix.isEmpty()) {
                prefix + child.name
            } else {
                "$prefix/${child.name}"
            }
            if (child.isDirectory) {
                prefixes.add(if (childKey.endsWith("/")) childKey else "$childKey/")
            } else if (child.isFile) {
                objects.add(ListedObject(key = childKey, size = child.length(), eTag = "local"))
            }
        }
        return PrefixListing(objects = objects, commonPrefixes = prefixes)
    }

    override fun putObject(key: String, data: ByteArray) {
        val file = resolveFile(key)
        val parent = file.parentFile
            ?: throw ObjectStoreException.Transport("no parent for $key")
        Files.createDirectories(parent.toPath())
        file.writeBytes(data)
    }

    override fun deleteObject(key: String) {
        val file = resolveFile(key)
        if (!file.exists()) throw ObjectStoreException.NotFound(key)
        if (!file.delete()) throw ObjectStoreException.Transport("failed to delete $key")
    }

    private fun resolveFile(key: String): File {
        if (key.contains("..") || key.startsWith("/") || key.contains('\\')) {
            throw ObjectStoreException.UnsafeKey(key)
        }
        val target = if (key.isEmpty()) root else File(root, key)
        val canonical = target.canonicalFile
        val rootPath = root.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
        if (canonical != root && !canonical.path.startsWith(rootPath)) {
            throw ObjectStoreException.UnsafeKey(key)
        }
        return canonical
    }
}
