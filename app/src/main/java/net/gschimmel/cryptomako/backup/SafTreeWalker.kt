package net.gschimmel.cryptomako.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class SafFileEntry(
    val document: DocumentFile,
    /** Path relative to the picked tree root, using `/` separators (no leading slash). */
    val relativePath: String,
    val sizeHint: Long,
)

/**
 * Walks a persisted SAF tree URI, applying [BackupSyncExcludes].
 * Does not follow virtual "shortcut" docs beyond DocumentFile children.
 */
class SafTreeWalker(
    private val context: Context,
    private val excludes: BackupSyncExcludes = BackupSyncExcludes.DEFAULT,
) {
    fun listFiles(treeUri: Uri): List<SafFileEntry> {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("Unable to open tree URI")
        if (!root.isDirectory) {
            throw IllegalArgumentException("Tree URI is not a directory")
        }
        val out = mutableListOf<SafFileEntry>()
        walk(root, relativePrefix = "", out = out)
        return out
    }

    private fun walk(dir: DocumentFile, relativePrefix: String, out: MutableList<SafFileEntry>) {
        val children = dir.listFiles()
        for (child in children) {
            val name = child.name ?: continue
            if (name == "." || name == "..") continue
            val rel = if (relativePrefix.isEmpty()) name else "$relativePrefix/$name"
            if (child.isDirectory) {
                if (excludes.shouldSkipDirectory(name)) continue
                if (excludes.shouldSkipRelativePath(rel)) continue
                walk(child, rel, out)
            } else if (child.isFile) {
                if (excludes.shouldSkipFile(name)) continue
                if (excludes.shouldSkipRelativePath(rel)) continue
                out.add(
                    SafFileEntry(
                        document = child,
                        relativePath = rel,
                        sizeHint = child.length().coerceAtLeast(0L),
                    ),
                )
            }
        }
    }
}
