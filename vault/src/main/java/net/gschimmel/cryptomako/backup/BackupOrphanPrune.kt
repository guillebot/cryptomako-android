package net.gschimmel.cryptomako.backup

/**
 * Pure helpers for Sync-mode vault orphan prune scoping.
 *
 * Orphan deletes are always relative to a single source’s vault folder
 * (`Backups/<folder>/…`). They never target SAF/local source paths.
 */
object BackupOrphanPrune {

    /** Cleartext vault prefix for [folderName], always ending with `/`. */
    fun vaultFolderPrefix(folderName: String): String {
        val folder = folderName.trim().trim('/')
        require(folder.isNotEmpty()) { "folderName required" }
        return "Backups/$folder/"
    }

    /**
     * True when [vaultCleartextPath] is exactly `Backups/<folder>` or under
     * `Backups/<folder>/`. Rejects sibling folders and non-Backups paths.
     */
    fun isWithinVaultFolderScope(vaultCleartextPath: String, folderName: String): Boolean {
        val folder = folderName.trim().trim('/')
        require(folder.isNotEmpty()) { "folderName required" }
        val path = vaultCleartextPath.trim().trimStart('/')
        val root = "Backups/$folder"
        return path == root || path.startsWith("$root/")
    }

    /**
     * Whether any eligible local relative path (post-exclude) lives at or under [relDir]
     * (relative to the source vault folder root). Empty [relDir] is the folder root itself
     * and always keeps the directory (Mac parity).
     */
    fun hasLocalUnder(relDir: String, localFiles: Set<String>): Boolean {
        if (relDir.isEmpty()) return true
        val prefix = "$relDir/"
        for (path in localFiles) {
            if (path == relDir || path.startsWith(prefix)) return true
        }
        return false
    }

    /** Vault-relative file is an orphan when missing from the eligible local set. */
    fun isOrphanFile(relPath: String, localFiles: Set<String>): Boolean =
        !localFiles.contains(relPath)

    /**
     * Vault-relative file paths under `Backups/<folder>/` that should be deleted in Sync mode.
     * Input paths must already be relative to that folder (no `Backups/` prefix, no source URIs).
     */
    fun orphanFileRelPaths(
        vaultFileRelPaths: Set<String>,
        localEligibleRelPaths: Set<String>,
    ): Set<String> =
        vaultFileRelPaths.filter { isOrphanFile(it, localEligibleRelPaths) }.toSet()
}
