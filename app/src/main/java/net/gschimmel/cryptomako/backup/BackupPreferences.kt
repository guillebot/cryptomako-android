package net.gschimmel.cryptomako.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android-local backup prefs: persisted SAF tree URI + exclude sets.
 * Exclude JSON property names match macOS BackupSyncExcludes
 * (`directoryNames`, `fileNames`, `fileExtensions`) — not new Platforms keys.
 */
class BackupPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var treeUri: Uri?
        get() = prefs.getString(KEY_TREE_URI, null)?.let { Uri.parse(it) }
        set(value) {
            prefs.edit().putString(KEY_TREE_URI, value?.toString()).apply()
        }

    var treeDisplayName: String
        get() = prefs.getString(KEY_TREE_NAME, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_TREE_NAME, value).apply()
        }

    var periodicEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERIODIC, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PERIODIC, value).apply()
        }

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
    }
}
