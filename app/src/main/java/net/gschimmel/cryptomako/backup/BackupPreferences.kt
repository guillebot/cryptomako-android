package net.gschimmel.cryptomako.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android-local backup prefs: persisted SAF tree URI(s) + exclude sets.
 * Exclude JSON property names match macOS BackupSyncExcludes
 * (`directoryNames`, `fileNames`, `fileExtensions`) — not new Platforms keys.
 *
 * Multi-source list lives in [KEY_SOURCES_JSON] (`id` / `safUri` / `displayName`);
 * legacy single [KEY_TREE_URI] migrates on read.
 */
class BackupPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Legacy single-tree accessor: first source URI, or null. */
    var treeUri: Uri?
        get() = loadSources().firstOrNull()?.safUri?.let { Uri.parse(it) }
        set(value) {
            val name = treeDisplayName.ifBlank { "Device" }
            if (value == null) {
                saveSources(emptyList())
            } else {
                val existing = loadSources()
                if (existing.isEmpty()) {
                    saveSources(listOf(BackupSource.create(value.toString(), name)))
                } else {
                    val updated = existing.toMutableList()
                    updated[0] = updated[0].copy(safUri = value.toString(), displayName = name)
                    saveSources(updated)
                }
            }
            prefs.edit().putString(KEY_TREE_URI, value?.toString()).apply()
        }

    var treeDisplayName: String
        get() = loadSources().firstOrNull()?.displayName
            ?: prefs.getString(KEY_TREE_NAME, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_TREE_NAME, value).apply()
            val sources = loadSources().toMutableList()
            if (sources.isNotEmpty()) {
                sources[0] = sources[0].copy(displayName = value)
                saveSources(sources)
            }
        }

    var periodicEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERIODIC, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PERIODIC, value).apply()
        }


    /**
     * Shared family key [BackupTransferMode.PREFS_KEY] (`backup` | `sync`).
     * Default [BackupTransferMode.BACKUP] — no vault orphan deletes until user picks Sync.
     */
    var backupTransferMode: BackupTransferMode
        get() = BackupTransferMode.fromRaw(prefs.getString(BackupTransferMode.PREFS_KEY, null))
        set(value) {
            prefs.edit().putString(BackupTransferMode.PREFS_KEY, value.raw).apply()
        }

    fun loadSources(): List<BackupSource> {
        val raw = prefs.getString(KEY_SOURCES_JSON, null)
        if (!raw.isNullOrBlank()) {
            try {
                val root = JSONObject(raw)
                val arr = root.optJSONArray("sources") ?: JSONArray()
                val out = mutableListOf<BackupSource>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val uri = o.optString("safUri", "").trim()
                    if (uri.isEmpty()) continue
                    out.add(
                        BackupSource(
                            id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                            safUri = uri,
                            displayName = o.optString("displayName").ifBlank { "Device" },
                            addedAtEpochMs = o.optLong("addedAt", System.currentTimeMillis()),
                        ),
                    )
                }
                if (out.isNotEmpty()) return out
            } catch (_: Exception) {
                // fall through to legacy
            }
        }
        // Legacy single-tree migration (read-only until next save).
        val legacyUri = prefs.getString(KEY_TREE_URI, null)?.trim().orEmpty()
        if (legacyUri.isEmpty()) return emptyList()
        val name = prefs.getString(KEY_TREE_NAME, "")?.ifBlank { null } ?: "Device"
        return listOf(BackupSource.create(legacyUri, name))
    }

    fun saveSources(sources: List<BackupSource>) {
        val arr = JSONArray()
        for (s in sources) {
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("safUri", s.safUri)
                    .put("displayName", s.displayName)
                    .put("addedAt", s.addedAtEpochMs),
            )
        }
        val root = JSONObject().put("sources", arr)
        val first = sources.firstOrNull()
        prefs.edit()
            .putString(KEY_SOURCES_JSON, root.toString())
            .putString(KEY_TREE_URI, first?.safUri)
            .putString(KEY_TREE_NAME, first?.displayName ?: "")
            .apply()
    }

    /**
     * Persist a new source. Soft-warns on nested overlap but still adds.
     * @return soft-warn message, or null when disjoint / duplicate skipped message via [alreadyListed]
     */
    fun addSource(safUri: String, displayName: String): AddSourceResult {
        val uri = safUri.trim()
        require(uri.isNotEmpty())
        val existing = loadSources()
        val candidateNorm = try {
            BackupUriOverlap.normalize(uri)
        } catch (_: Exception) {
            null
        }
        if (candidateNorm != null) {
            val dup = existing.firstOrNull {
                try {
                    BackupUriOverlap.normalize(it.safUri) == candidateNorm
                } catch (_: Exception) {
                    it.safUri == uri
                }
            }
            if (dup != null) {
                return AddSourceResult(dup, alreadyListed = true, softWarn = null)
            }
        }
        val warn = BackupUriOverlap.softWarnOnAdd(existing, uri, displayName)
        val src = BackupSource.create(uri, displayName)
        saveSources(existing + src)
        return AddSourceResult(src, alreadyListed = false, softWarn = warn)
    }

    fun removeSource(id: String): Boolean {
        val current = loadSources()
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return false
        saveSources(next)
        return true
    }

    data class AddSourceResult(
        val source: BackupSource,
        val alreadyListed: Boolean,
        val softWarn: String?,
    )

    fun loadExcludes(): BackupSyncExcludes {
        val raw = prefs.getString(KEY_EXCLUDES_JSON, null) ?: return BackupSyncExcludes.DEFAULT
        return try {
            val obj = JSONObject(raw)
            val root = if (obj.has("excludes")) obj.getJSONObject("excludes") else obj
            BackupSyncExcludes(
                directoryNames = stringSet(root, "directoryNames", BackupSyncExcludes.DEFAULT_DIRECTORY_NAMES),
                fileNames = stringSet(root, "fileNames", BackupSyncExcludes.DEFAULT_FILE_NAMES),
                fileExtensions = stringSet(root, "fileExtensions", BackupSyncExcludes.DEFAULT_FILE_EXTENSIONS),
            )
        } catch (_: Exception) {
            BackupSyncExcludes.DEFAULT
        }
    }

    fun saveExcludes(excludes: BackupSyncExcludes) {
        val obj = JSONObject()
        obj.put("directoryNames", JSONArray(excludes.directoryNames.sorted()))
        obj.put("fileNames", JSONArray(excludes.fileNames.sorted()))
        obj.put("fileExtensions", JSONArray(excludes.fileExtensions.sorted()))
        prefs.edit().putString(KEY_EXCLUDES_JSON, obj.toString()).apply()
    }

    private fun stringSet(obj: JSONObject, key: String, default: Set<String>): Set<String> {
        if (!obj.has(key)) return default
        val arr = obj.getJSONArray(key)
        val out = mutableSetOf<String>()
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    companion object {
        private const val PREFS_NAME = "cryptomako_backup"
        private const val KEY_TREE_URI = "backupTreeUri"
        private const val KEY_TREE_NAME = "backupTreeDisplayName"
        private const val KEY_PERIODIC = "backupPeriodicEnabled"
        private const val KEY_EXCLUDES_JSON = "backup_sync_excludes_json"
        private const val KEY_SOURCES_JSON = "backup_sources_json"
    }
}
