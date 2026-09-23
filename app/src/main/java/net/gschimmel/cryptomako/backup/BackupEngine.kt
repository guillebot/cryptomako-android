package net.gschimmel.cryptomako.backup

import android.content.Context
import android.net.Uri
import net.gschimmel.cryptomako.vault.VaultException
import net.gschimmel.cryptomako.vault.VaultSession
import java.io.IOException

data class BackupProgress(
    val phase: Phase,
    val filesDone: Int,
    val filesTotal: Int,
    val bytesDone: Long,
    val bytesTotal: Long,
    val currentPath: String,
    val filesSkipped: Int = 0,
    val error: String? = null,
) {
    enum class Phase { SCANNING, UPLOADING, FINISHED, FAILED }
}

/**
 * Walks a SAF tree and uploads cleartext files into the unlocked vault
 * under `Backups/<folderName>/…` (encrypt + ObjectStore put, fail-closed).
 *
 * Limitations (MVP): loads each file into memory (no multipart / streaming);
 * overwrites same cleartext names; skips excluded paths; requires unlocked session.
 */
class BackupEngine(
    private val context: Context,
    private val session: VaultSession,
    private val excludes: BackupSyncExcludes = BackupSyncExcludes.DEFAULT,
) {
    fun run(
        treeUri: Uri,
        folderName: String,
        onProgress: (BackupProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): BackupProgress {
        onProgress(
            BackupProgress(
                phase = BackupProgress.Phase.SCANNING,
                filesDone = 0,
                filesTotal = 0,
                bytesDone = 0,
                bytesTotal = 0,
                currentPath = "Scanning…",
            ),
        )
        val entries = try {
            SafTreeWalker(context, excludes).listFiles(treeUri)
        } catch (e: Exception) {
            val failed = BackupProgress(
                phase = BackupProgress.Phase.FAILED,
                filesDone = 0,
                filesTotal = 0,
                bytesDone = 0,
                bytesTotal = 0,
                currentPath = "",
                error = e.message ?: "SAF scan failed",
            )
            onProgress(failed)
            return failed
        }

        val bytesTotal = entries.sumOf { it.sizeHint }
        var filesDone = 0
        var bytesDone = 0L
        var skipped = 0

        for (entry in entries) {
            if (isCancelled()) {
                val failed = BackupProgress(
                    phase = BackupProgress.Phase.FAILED,
                    filesDone = filesDone,
                    filesTotal = entries.size,
                    bytesDone = bytesDone,
                    bytesTotal = bytesTotal,
                    currentPath = entry.relativePath,
                    filesSkipped = skipped,
                    error = "Cancelled",
                )
                onProgress(failed)
                return failed
            }
            onProgress(
                BackupProgress(
                    phase = BackupProgress.Phase.UPLOADING,
                    filesDone = filesDone,
                    filesTotal = entries.size,
                    bytesDone = bytesDone,
                    bytesTotal = bytesTotal,
                    currentPath = entry.relativePath,
                    filesSkipped = skipped,
                ),
            )
            try {
                val vaultPath = backupVaultPath(folderName, entry.relativePath)
                val (parentPath, name) = splitParentAndName(vaultPath)
                val parentDirId = session.ensureDirectoryPath(parentPath)
                val bytes = readAll(entry)
                if (bytes == null) {
                    skipped++
                    filesDone++
                } else {
                    session.createOrOverwriteFile(parentDirId, name, bytes)
                    bytesDone += bytes.size.toLong()
                    filesDone++
                }
            } catch (e: VaultException) {
                val failed = BackupProgress(
                    phase = BackupProgress.Phase.FAILED,
                    filesDone = filesDone,
                    filesTotal = entries.size,
                    bytesDone = bytesDone,
                    bytesTotal = bytesTotal,
                    currentPath = entry.relativePath,
                    filesSkipped = skipped,
                    error = e.message ?: "Vault upload failed",
                )
                onProgress(failed)
                return failed
            } catch (e: Exception) {
                val failed = BackupProgress(
                    phase = BackupProgress.Phase.FAILED,
                    filesDone = filesDone,
                    filesTotal = entries.size,
                    bytesDone = bytesDone,
                    bytesTotal = bytesTotal,
                    currentPath = entry.relativePath,
                    filesSkipped = skipped,
                    error = e.message ?: "Backup failed",
                )
                onProgress(failed)
                return failed
            }
        }

        val done = BackupProgress(
            phase = BackupProgress.Phase.FINISHED,
            filesDone = filesDone,
            filesTotal = entries.size,
            bytesDone = bytesDone,
            bytesTotal = bytesTotal,
            currentPath = "",
            filesSkipped = skipped,
        )
        onProgress(done)
        return done
    }

    private fun readAll(entry: SafFileEntry): ByteArray? {
        val uri = entry.document.uri
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("null stream for ${entry.relativePath}")
        } catch (e: OutOfMemoryError) {
            throw IOException(
                "File too large to load into memory: ${entry.relativePath} " +
                    "(multipart uploads not yet supported)",
                e,
            )
        }
    }
}
