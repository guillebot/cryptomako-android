package net.gschimmel.cryptomako.vault

import org.cryptomator.cryptolib.api.FileNameCryptor
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal object DirLayout {
    const val DATA_DIR = "d"
    const val FILE_SUFFIX = ".c9r"
    const val SHORT_SUFFIX = ".c9s"
    const val DIR_FILE = "dir.c9r"
    const val CONTENTS_FILE = "contents.c9r"
    const val NAME_FILE = "name.c9s"
    const val SYMLINK_FILE = "symlink.c9r"
    const val DIRID_FILE = "dirid.c9r"

    fun ciphertextDirectoryPrefix(prefix: String, fileNameCryptor: FileNameCryptor, dirId: String): String {
        val hash = fileNameCryptor.hashDirectoryId(dirId)
        require(hash.length >= 3) { "directory hash too short" }
        val head = hash.substring(0, 2)
        val tail = hash.substring(2)
        val p = if (prefix.isEmpty() || prefix.endsWith("/")) prefix else "$prefix/"
        return "${p}$DATA_DIR/$head/$tail/"
    }

    fun shortenedName(ciphertextFileName: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(ciphertextFileName.toByteArray(StandardCharsets.UTF_8))
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return b64 + SHORT_SUFFIX
    }

    fun associatedData(dirId: String): ByteArray = dirId.toByteArray(StandardCharsets.UTF_8)
}
