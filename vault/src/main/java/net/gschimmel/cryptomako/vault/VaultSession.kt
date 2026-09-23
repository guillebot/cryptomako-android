package net.gschimmel.cryptomako.vault

import com.google.common.io.BaseEncoding
import net.gschimmel.cryptomako.store.ObjectStore
import net.gschimmel.cryptomako.store.ObjectStoreException
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.cryptolib.api.CryptorProvider
import org.cryptomator.cryptolib.api.Masterkey
import org.cryptomator.cryptolib.common.DecryptingReadableByteChannel
import org.cryptomator.cryptolib.common.EncryptingWritableByteChannel
import org.cryptomator.cryptolib.common.MasterkeyFileAccess
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

/**
 * Unlocked format-8 Cryptomator vault backed by an [ObjectStore].
 * Passphrase and raw masterkey must never be logged.
 */
class VaultSession private constructor(
    val config: VaultConfig,
    private val store: ObjectStore,
    private val cryptor: Cryptor,
    private val masterkey: Masterkey,
    private val prefix: String,
) : AutoCloseable {

    val rootCipherPrefix: String =
        DirLayout.ciphertextDirectoryPrefix(prefix, cryptor.fileNameCryptor(), ROOT_DIR_ID)

    fun list(dirId: String = ROOT_DIR_ID): List<VaultNode> {
        val dirPrefix = DirLayout.ciphertextDirectoryPrefix(prefix, cryptor.fileNameCryptor(), dirId)
        val listing = store.listImmediate(dirPrefix)
        val nodes = mutableListOf<VaultNode>()

        for (obj in listing.objects) {
            val name = relativeName(obj.key, dirPrefix)
            if (name.contains('/') || name.isEmpty() || name == DirLayout.DIRID_FILE) continue
            if (name.endsWith(DirLayout.FILE_SUFFIX)) {
                nodeForFileObject(name, dirId, obj.key, obj.size)?.let { nodes.add(it) }
            }
        }

        for (common in listing.commonPrefixes) {
            val name = relativeName(common, dirPrefix).trimEnd('/')
            if (name.contains('/') || name.isEmpty()) continue
            when {
                name.endsWith(DirLayout.FILE_SUFFIX) ->
                    nodeForDirectoryLike(name, dirId, common)?.let { nodes.add(it) }
                name.endsWith(DirLayout.SHORT_SUFFIX) ->
                    nodeForShortened(name, dirId, common)?.let { nodes.add(it) }
            }
        }
        return nodes.sortedBy { it.cleartextName.lowercase() }
    }

    /** Recursive cleartext paths like `/hello.txt`, `/notes/`, `/notes/todo.md`. */
    fun listRecursive(dirId: String = ROOT_DIR_ID, pathPrefix: String = ""): List<String> {
        val out = mutableListOf<String>()
        val nodes = list(dirId)
        for (node in nodes) {
            val path = "$pathPrefix/${node.cleartextName}"
            when (node.kind) {
                NodeKind.DIRECTORY -> {
                    out.add("$path/")
                    val childId = node.dirId ?: continue
                    out.addAll(listRecursive(childId, path))
                }
                NodeKind.FILE, NodeKind.SYMLINK -> out.add(path)
            }
        }
        return out.sorted()
    }

    private fun nodeForFileObject(name: String, parentDirId: String, key: String, size: Long): VaultNode? {
        val bare = name.removeSuffix(DirLayout.FILE_SUFFIX)
        val clear = decryptName(bare, parentDirId) ?: return null
        return VaultNode(
            cleartextName = clear,
            kind = NodeKind.FILE,
            cipherName = name,
            parentDirId = parentDirId,
            ciphertextKey = key,
            size = size,
        )
    }

    private fun nodeForDirectoryLike(name: String, parentDirId: String, folderPrefixRaw: String): VaultNode? {
        val folderPrefix = if (folderPrefixRaw.endsWith("/")) folderPrefixRaw else "$folderPrefixRaw/"
        val bare = name.removeSuffix(DirLayout.FILE_SUFFIX)
        val clear = decryptName(bare, parentDirId) ?: return null
        return classifyFolder(clear, name, parentDirId, folderPrefix)
    }

    private fun nodeForShortened(name: String, parentDirId: String, folderPrefixRaw: String): VaultNode? {
        val folderPrefix = if (folderPrefixRaw.endsWith("/")) folderPrefixRaw else "$folderPrefixRaw/"
        val nameBytes = try {
            store.getObject(folderPrefix + DirLayout.NAME_FILE)
        } catch (_: ObjectStoreException.NotFound) {
            return null
        }
        var longName = String(nameBytes, StandardCharsets.UTF_8).trim()
        if (longName.endsWith(DirLayout.FILE_SUFFIX)) {
            longName = longName.removeSuffix(DirLayout.FILE_SUFFIX)
        }
        val clear = decryptName(longName, parentDirId) ?: return null
        return classifyFolder(clear, name, parentDirId, folderPrefix)
    }

    private fun classifyFolder(
        clear: String,
        cipherName: String,
        parentDirId: String,
        folderPrefix: String,
    ): VaultNode? {
        try {
            val dirBytes = store.getObject(folderPrefix + DirLayout.DIR_FILE)
            val childId = String(dirBytes, StandardCharsets.UTF_8).trim()
            if (childId.isNotEmpty()) {
                return VaultNode(
                    cleartextName = clear,
                    kind = NodeKind.DIRECTORY,
                    cipherName = cipherName,
                    parentDirId = parentDirId,
                    dirId = childId,
                    ciphertextKey = folderPrefix + DirLayout.DIR_FILE,
                )
            }
        } catch (_: ObjectStoreException.NotFound) {
            // not a directory
        }
        try {
            val meta = store.headObject(folderPrefix + DirLayout.CONTENTS_FILE)
            return VaultNode(
                cleartextName = clear,
                kind = NodeKind.FILE,
                cipherName = cipherName,
                parentDirId = parentDirId,
                ciphertextKey = folderPrefix + DirLayout.CONTENTS_FILE,
                size = meta.size,
            )
        } catch (_: ObjectStoreException.NotFound) {
            // not a shortened file
        }
        try {
            store.headObject(folderPrefix + DirLayout.SYMLINK_FILE)
            return VaultNode(
                cleartextName = clear,
                kind = NodeKind.SYMLINK,
                cipherName = cipherName,
                parentDirId = parentDirId,
                ciphertextKey = folderPrefix + DirLayout.SYMLINK_FILE,
            )
        } catch (_: ObjectStoreException.NotFound) {
            return null
        }
    }

    private fun decryptName(cipherBare: String, dirId: String): String? {
        return try {
            cryptor.fileNameCryptor().decryptFilename(
                BaseEncoding.base64Url(),
                cipherBare,
                DirLayout.associatedData(dirId),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun relativeName(key: String, prefix: String): String {
        return if (key.startsWith(prefix)) key.substring(prefix.length) else key
    }


    /**
     * Download ciphertext for [node] and decrypt to cleartext bytes.
     * Fail-closed: transport / auth errors propagate; never returns partial garbage as success.
     */
    fun downloadCleartext(node: VaultNode): ByteArray {
        if (node.kind != NodeKind.FILE) {
            throw VaultException.NotAFile(node.cleartextName)
        }
        if (node.ciphertextKey.isEmpty()) {
            throw VaultException.Io("missing ciphertext key for ${node.cleartextName}")
        }
        val ciphertext = try {
            store.getObject(node.ciphertextKey)
        } catch (e: ObjectStoreException) {
            throw VaultException.Io("download failed for ${node.cleartextName}", e)
        }
        return try {
            decryptContent(ciphertext)
        } catch (e: Exception) {
            throw VaultException.Io("decrypt failed for ${node.cleartextName}", e)
        }
    }

    /**
     * Encrypt a small cleartext file and PUT ciphertext via [ObjectStore].
     * Success only after [ObjectStore.putObject] completes (fail-closed).
     * Format-8 layout: `name.c9r`, or shortened `.c9s/` + `name.c9s` + `contents.c9r`.
     */
    fun uploadCleartextFile(
        parentDirId: String,
        cleartextName: String,
        contents: ByteArray,
    ): VaultNode {
        val name = cleartextName.trim()
        if (name.isEmpty() || name.contains('/')) {
            throw VaultException.InvalidPath(cleartextName)
        }
        if (list(parentDirId).any { it.cleartextName == name }) {
            throw VaultException.AlreadyExists(name)
        }

        val encName = try {
            cryptor.fileNameCryptor().encryptFilename(
                BaseEncoding.base64Url(),
                name,
                DirLayout.associatedData(parentDirId),
            ) + DirLayout.FILE_SUFFIX
        } catch (e: Exception) {
            throw VaultException.Io("encrypt filename failed", e)
        }

        val parentPrefix = DirLayout.ciphertextDirectoryPrefix(prefix, cryptor.fileNameCryptor(), parentDirId)
        val cipherBytes = try {
            encryptContent(contents)
        } catch (e: Exception) {
            throw VaultException.Io("encrypt content failed", e)
        }

        val ciphertextKey: String
        val displayCipherName: String
        try {
            if (encName.length > config.shorteningThreshold) {
                val short = DirLayout.shortenedName(encName)
                val folder = "$parentPrefix$short/"
                ciphertextKey = folder + DirLayout.CONTENTS_FILE
                displayCipherName = short
                store.putObject(folder + DirLayout.NAME_FILE, encName.toByteArray(StandardCharsets.UTF_8))
                store.putObject(ciphertextKey, cipherBytes)
            } else {
                ciphertextKey = parentPrefix + encName
                displayCipherName = encName
                store.putObject(ciphertextKey, cipherBytes)
            }
        } catch (e: ObjectStoreException) {
            throw VaultException.Io("upload putObject failed for $name", e)
        }

        return VaultNode(
            cleartextName = name,
            kind = NodeKind.FILE,
            cipherName = displayCipherName,
            parentDirId = parentDirId,
            ciphertextKey = ciphertextKey,
            size = cipherBytes.size.toLong(),
        )
    }

    private fun encryptContent(cleartext: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        Channels.newChannel(out).use { writable ->
            EncryptingWritableByteChannel(writable, cryptor).use { enc ->
                val buf = ByteBuffer.wrap(cleartext)
                while (buf.hasRemaining()) {
                    enc.write(buf)
                }
            }
        }
        return out.toByteArray()
    }

    private fun decryptContent(ciphertext: ByteArray): ByteArray {
        ByteArrayInputStream(ciphertext).use { input ->
            Channels.newChannel(input).use { readable ->
                DecryptingReadableByteChannel(readable, cryptor, true).use { dec ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteBuffer.allocate(64 * 1024)
                    while (true) {
                        buf.clear()
                        val n = dec.read(buf)
                        if (n < 0) break
                        buf.flip()
                        out.write(buf.array(), buf.position(), buf.remaining())
                    }
                    return out.toByteArray()
                }
            }
        }
    }


    override fun close() {
        try {
            cryptor.destroy()
        } finally {
            masterkey.destroy()
        }
    }

    companion object {
        const val ROOT_DIR_ID: String = ""
        const val REQUIRED_FORMAT: Int = 8
        private const val VAULT_CONFIG_NAME = "vault.cryptomator"
        private const val MASTERKEY_NAME = "masterkey.cryptomator"

        /**
         * Unlock a format-8 vault. Rejects other formats. Wrong passphrase and corrupt vault
         * both surface as [VaultException.UnlockFailed] (fail closed; do not distinguish in logs).
         */
        @JvmStatic
        fun unlock(
            store: ObjectStore,
            passphrase: CharSequence,
            prefix: String = "",
        ): VaultSession {
            val normalizedPrefix = when {
                prefix.isEmpty() -> ""
                prefix.endsWith("/") -> prefix
                else -> "$prefix/"
            }

            val jwtData = try {
                store.getObject(normalizedPrefix + VAULT_CONFIG_NAME)
            } catch (_: ObjectStoreException.NotFound) {
                throw VaultException.MissingVaultConfig()
            }
            val jwt = String(jwtData, StandardCharsets.UTF_8).trim()
            if (jwt.isEmpty()) throw VaultException.MissingVaultConfig()

            val unverified = try {
                VaultJwt.decodeUnverified(jwt).second
            } catch (e: VaultException) {
                throw VaultException.UnlockFailed(e)
            }
            if (unverified.format != REQUIRED_FORMAT) {
                throw VaultException.UnsupportedFormat(unverified.format)
            }

            val masterData = try {
                store.getObject(normalizedPrefix + MASTERKEY_NAME)
            } catch (_: ObjectStoreException.NotFound) {
                throw VaultException.MissingMasterkey()
            }

            val masterkeyFileAccess = MasterkeyFileAccess(ByteArray(0), SecureRandom())
            val masterkey: Masterkey = try {
                masterkeyFileAccess.load(ByteArrayInputStream(masterData), passphrase)
            } catch (e: Exception) {
                throw VaultException.UnlockFailed(e)
            }

            try {
                val headerKid = VaultJwt.decodeUnverified(jwt).first.kid
                val payload = try {
                    VaultJwt.verify(jwt, masterkey.encoded)
                } catch (e: Exception) {
                    masterkey.destroy()
                    throw VaultException.UnlockFailed(e)
                }
                if (payload.format != REQUIRED_FORMAT) {
                    masterkey.destroy()
                    throw VaultException.UnsupportedFormat(payload.format)
                }

                val scheme = try {
                    CryptorProvider.Scheme.valueOf(payload.cipherCombo)
                } catch (_: IllegalArgumentException) {
                    masterkey.destroy()
                    throw VaultException.UnsupportedCipherCombo(payload.cipherCombo)
                }

                val cryptor = try {
                    CryptorProvider.forScheme(scheme).provide(masterkey, SecureRandom())
                } catch (e: Exception) {
                    masterkey.destroy()
                    throw VaultException.UnlockFailed(e)
                }

                val config = VaultConfig(
                    format = payload.format,
                    shorteningThreshold = payload.shorteningThreshold ?: 220,
                    cipherCombo = payload.cipherCombo,
                    jti = payload.jti,
                    kid = headerKid,
                )
                return VaultSession(config, store, cryptor, masterkey, normalizedPrefix)
            } catch (e: VaultException) {
                throw e
            } catch (e: Exception) {
                masterkey.destroy()
                throw VaultException.UnlockFailed(e)
            }
        }
    }
}
