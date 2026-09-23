# Backup sources (Android)

Android keeps the backup-sources list in **app-local** SharedPreferences JSON
(`cryptomako_backup` / `backup_sources_json`). This is **not** a Platforms shared
settings key.

| Field | Meaning |
|-------|---------|
| `id` | Stable UUID |
| `safUri` | Persisted SAF tree URI (`content://…/tree/…`) |
| `displayName` | Folder name under `Backups/` inside the vault |
| `addedAt` | Epoch millis when added |

Legacy single-tree keys (`backupTreeUri` / `backupTreeDisplayName`) migrate on read.

## Nested / overlapping URIs (Platforms consensus)

Overlap uses the **resolved SAF URI only** (authority + decoded tree document id).
No filesystem path / symlink resolve (SAF has none).

1. **Add (soft-warn):** adding a source whose resolved URI is the same as, or a
   prefix of, another listed source shows a warning and still persists the add.
2. **Sync (hard-fail):** Sync refuses to start if any pair overlaps. Fix the list,
   then retry.
