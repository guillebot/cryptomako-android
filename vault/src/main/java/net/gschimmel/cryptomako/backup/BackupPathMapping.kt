package net.gschimmel.cryptomako.backup

/**
 * Cleartext vault path for a file under a named backup source folder.
 * Matches macOS Backup Sync layout: `Backups/<sourceFolder>/<relativePath>`.
 */
fun backupVaultPath(folderName: String, relativePath: String): String {
    val folder = folderName.trim().trim('/')
    require(folder.isNotEmpty()) { "folderName required" }
    val rel = relativePath.trim().trimStart('/')
    return if (rel.isEmpty()) "Backups/$folder" else "Backups/$folder/$rel"
}

/** Parent directory cleartext path and leaf filename for [vaultRelativeFilePath]. */
fun splitParentAndName(vaultRelativeFilePath: String): Pair<String, String> {
    val parts = vaultRelativeFilePath.split('/').filter { it.isNotEmpty() && it != "." }
    require(parts.isNotEmpty()) { "empty path" }
    if (parts.size == 1) return "" to parts[0]
    val name = parts.last()
    val parent = parts.dropLast(1).joinToString("/")
    return parent to name
}
