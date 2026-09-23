package net.gschimmel.cryptomako.backup

import java.util.UUID

/**
 * One SAF tree folder synced into `Backups/{displayName}/`.
 * Android-local list (SharedPreferences JSON) — mirrors Mac/Windows mental model
 * (`id` / path-or-uri / folder name); [safUri] replaces filesystem `path`.
 * Not a Platforms shared settings key.
 */
data class BackupSource(
    val id: String = UUID.randomUUID().toString(),
    val safUri: String,
    /** Cleartext folder name under `Backups/` in the vault. */
    val displayName: String,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
) {
    companion object {
        fun create(safUri: String, displayName: String? = null): BackupSource {
            val uri = safUri.trim()
            require(uri.isNotEmpty()) { "safUri required" }
            val name = displayName?.trim().orEmpty().ifEmpty { "Device" }
            return BackupSource(
                id = UUID.randomUUID().toString(),
                safUri = uri,
                displayName = name,
                addedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }
}
