package net.gschimmel.cryptomako.backup

/**
 * Path filters for Backup Sync. Property names match macOS [BackupSyncExcludes]:
 * `directoryNames`, `fileNames`, `fileExtensions`. Defaults match the macOS app.
 */
data class BackupSyncExcludes(
    val directoryNames: Set<String> = DEFAULT_DIRECTORY_NAMES,
    val fileNames: Set<String> = DEFAULT_FILE_NAMES,
    val fileExtensions: Set<String> = DEFAULT_FILE_EXTENSIONS,
) {
    /** True when this directory basename should not be descended into. */
    fun shouldSkipDirectory(named: String): Boolean = named in directoryNames

    /** True when this regular file basename should not be uploaded. */
    fun shouldSkipFile(named: String): Boolean {
        if (named in fileNames) return true
        val dot = named.lastIndexOf('.')
        if (dot <= 0 || dot == named.lastIndex) return false
        val ext = named.substring(dot + 1).lowercase()
        return ext in fileExtensions
    }

    /** True when any path component of [relativePath] is excluded. */
    fun shouldSkipRelativePath(relativePath: String): Boolean {
        for (part in relativePath.split('/')) {
            if (part.isEmpty() || part == ".") continue
            if (part in directoryNames) return true
            if (shouldSkipFile(part)) return true
        }
        return false
    }

    companion object {
        val DEFAULT_DIRECTORY_NAMES: Set<String> = setOf(
            "node_modules",
            ".git",
            "__pycache__",
            ".svn",
            ".hg",
            ".tox",
            ".venv",
            "venv",
            ".idea",
            ".next",
            "Pods",
        )

        val DEFAULT_FILE_NAMES: Set<String> = setOf(
            ".DS_Store",
            "Thumbs.db",
            "desktop.ini",
        )

        /** Extensions without leading dot, lowercase. */
        val DEFAULT_FILE_EXTENSIONS: Set<String> = setOf(
            "pyc",
            "pyo",
        )

        val DEFAULT: BackupSyncExcludes = BackupSyncExcludes()
    }
}
