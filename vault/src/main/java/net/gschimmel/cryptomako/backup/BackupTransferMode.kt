package net.gschimmel.cryptomako.backup

/**
 * Cross-platform Backup Sync transfer mode (shared prefs key [PREFS_KEY]).
 *
 * - [BACKUP]: put/update only. Never deletes the local/SAF source. Never deletes vault extras.
 * - [SYNC]: same puts, then delete vault ciphertext orphans under that source’s
 *   `Backups/<folder>/` only (fail-closed). Never deletes the local/SAF source.
 *
 * Default is [BACKUP] (safer — no vault orphan deletes until the user picks Sync).
 */
enum class BackupTransferMode(val raw: String) {
    BACKUP("backup"),
    SYNC("sync"),
    ;

    companion object {
        /** Shared family prefs key — must match macOS / Platforms exactly. */
        const val PREFS_KEY: String = "backupTransferMode"

        val DEFAULT: BackupTransferMode = BACKUP

        fun fromRaw(value: String?): BackupTransferMode {
            val v = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.raw == v } ?: DEFAULT
        }
    }
}
