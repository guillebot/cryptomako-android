package net.gschimmel.cryptomako.backup

import android.content.Context
import android.net.Uri
import net.gschimmel.cryptomako.vault.NodeKind
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
    /** Vault ciphertext items removed in Sync mode; always 0 in Backup mode. */
    val filesDeleted: Int = 0,
    val error: String? = null,
) {
    enum class Phase { SCANNING, UPLOADING, PRUNING, FINISHED, FAILED }
}

/**
 * Walks a SAF tree and uploads cleartext files into the unlocked vault
 * under `Backups/<folderName>/…` (encrypt + ObjectStore put, fail-closed).
 *
 * Transfer mode ([BackupTransferMode]):
 * - **backup** (default): put/update only. Never deletes vault extras or SAF source.
 * - **sync**: same puts, then delete vault-only ciphertext under this source’s
 *   `Backups/<folder>/` (fail-closed). Never deletes SAF source.
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
        transferMode: BackupTransferMode = BackupTransferMode.DEFAULT,
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

        val localEligible = entries.map { it.relativePath }.toSet()
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

        var filesDeleted = 0
        if (transferMode == BackupTransferMode.SYNC) {
            onProgress(
                BackupProgress(
                    phase = BackupProgress.Phase.PRUNING,
                    filesDone = filesDone,
                    filesTotal = entries.size,
                    bytesDone = bytesDone,
                    bytesTotal = bytesTotal,
                    currentPath = "Comparing vault Backups/$folderName/ to local tree…",
                    filesSkipped = skipped,
                ),
            )
            try {
                // Scope: this source’s vault folder only (never sibling Backups/<other>/).
                val rootDirId = session.ensureDirectoryPath(backupVaultPath(folderName, ""))
                filesDeleted = deleteVaultOrphans(
                    rootDirId = rootDirId,
                    localFiles = localEligible,
                    isCancelled = isCancelled,
                    onPath = { rel ->
                        onProgress(
                            BackupProgress(
                                phase = BackupProgress.Phase.PRUNING,
                                filesDone = filesDone,
                                filesTotal = entries.size,
                                bytesDone = bytesDone,
                                bytesTotal = bytesTotal,
                                currentPath = rel,
                                filesSkipped = skipped,
                                filesDeleted = filesDeleted,
                            ),
                        )
                    },
                )
            } catch (e: VaultException) {
                val failed = BackupProgress(
                    phase = BackupProgress.Phase.FAILED,
                    filesDone = filesDone,
                    filesTotal = entries.size,
                    bytesDone = bytesDone,
                    bytesTotal = bytesTotal,
                    currentPath = "",
                    filesSkipped = skipped,
                    filesDeleted = filesDeleted,
                    error = e.message ?: "Vault orphan prune failed",
                )
                onProgress(failed)
                return failed
            } catch (e: Exception) {
                val msg = when {
                    e.message == "Cancelled" || isCancelled() -> "Cancelled"
                    else -> e.message ?: "Sync prune failed"
                }
                val failed = BackupProgress(
                    phase = BackupProgress.Phase.FAILED,
                    filesDone = filesDone,
                    filesTotal = entries.size,
                    bytesDone = bytesDone,
                    bytesTotal = bytesTotal,
                    currentPath = "",
                    filesSkipped = skipped,
                    filesDeleted = filesDeleted,
                    error = msg,
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
            filesDeleted = filesDeleted,
        )
        onProgress(done)
        return done
    }

    /**
     * Sync mode only: delete remote ciphertext under this source’s vault folder that
     * has no matching eligible SAF file. Never touches the SAF/local source tree.
     * Fail-closed: ObjectStore / [VaultSession] delete errors abort the run.
     */
    private fun deleteVaultOrphans(
        rootDirId: String,
        localFiles: Set<String>,
        isCancelled: () -> Boolean,
        onPath: (String) -> Unit,
    ): Int {
        fun prune(dirId: String, relPrefix: String): Int {
            if (isCancelled()) throw IOException("Cancelled")
            val children = session.list(dirId)
            var deleted = 0
            for (child in children) {
                if (isCancelled()) throw IOException("Cancelled")
                val childRel = if (relPrefix.isEmpty()) {
                    child.cleartextName
                } else {
                    "$relPrefix/${child.cleartextName}"
                }
                onPath(childRel)
                when (child.kind) {
                    NodeKind.FILE, NodeKind.SYMLINK -> {
                        if (BackupOrphanPrune.isOrphanFile(childRel, localFiles)) {
                            // Remote ObjectStore ciphertext delete only — never SAF source.
                            session.deleteFile(child)
                            deleted += 1
                        }
                    }
                    NodeKind.DIRECTORY -> {
                        val childDirId = child.dirId ?: continue
                        if (!BackupOrphanPrune.hasLocalUnder(childRel, localFiles)) {
                            session.deleteDirectory(child, recursive = true)
                            deleted += 1
                        } else {
                            deleted += prune(childDirId, childRel)
                        }
                    }
                }
            }
            return deleted
        }
        return prune(rootDirId, "")
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
