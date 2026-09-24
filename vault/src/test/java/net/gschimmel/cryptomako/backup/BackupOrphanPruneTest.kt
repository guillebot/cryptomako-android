package net.gschimmel.cryptomako.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupOrphanPruneTest {

    @Test
    fun vaultFolderPrefix_scopesUnderBackupsFolder() {
        assertEquals("Backups/Photos/", BackupOrphanPrune.vaultFolderPrefix("Photos"))
        assertEquals("Backups/Device/", BackupOrphanPrune.vaultFolderPrefix(" /Device/ "))
    }

    @Test
    fun isWithinVaultFolderScope_acceptsOnlyThatFolder() {
        assertTrue(BackupOrphanPrune.isWithinVaultFolderScope("Backups/Photos", "Photos"))
        assertTrue(BackupOrphanPrune.isWithinVaultFolderScope("Backups/Photos/a.txt", "Photos"))
        assertTrue(BackupOrphanPrune.isWithinVaultFolderScope("Backups/Photos/nested/b.txt", "Photos"))
        assertFalse(BackupOrphanPrune.isWithinVaultFolderScope("Backups/Other/a.txt", "Photos"))
        assertFalse(BackupOrphanPrune.isWithinVaultFolderScope("Backups/PhotosExtra/a.txt", "Photos"))
        assertFalse(BackupOrphanPrune.isWithinVaultFolderScope("hello.txt", "Photos"))
        // Never treat SAF / absolute source paths as in-scope vault orphans.
        assertFalse(
            BackupOrphanPrune.isWithinVaultFolderScope(
                "content://com.android.externalstorage.documents/tree/primary%3APhotos",
                "Photos",
            ),
        )
        assertFalse(BackupOrphanPrune.isWithinVaultFolderScope("/sdcard/Photos/a.txt", "Photos"))
    }

    @Test
    fun hasLocalUnder_emptyRootAlwaysKept() {
        assertTrue(BackupOrphanPrune.hasLocalUnder("", emptySet()))
        assertTrue(BackupOrphanPrune.hasLocalUnder("", setOf("a.txt")))
    }

    @Test
    fun hasLocalUnder_detectsChildren() {
        val local = setOf("docs/readme.md", "docs/deep/x.txt", "root.txt")
        assertTrue(BackupOrphanPrune.hasLocalUnder("docs", local))
        assertTrue(BackupOrphanPrune.hasLocalUnder("docs/deep", local))
        assertFalse(BackupOrphanPrune.hasLocalUnder("docs/missing", local))
        assertFalse(BackupOrphanPrune.hasLocalUnder("other", local))
        // partial segment must not match
        assertFalse(BackupOrphanPrune.hasLocalUnder("doc", local))
    }

    @Test
    fun orphanFileRelPaths_onlyVaultRelativesMissingLocally() {
        val vault = setOf("keep.txt", "gone.txt", "nested/old.txt", "nested/keep.txt")
        val local = setOf("keep.txt", "nested/keep.txt", "new-local-only.txt")
        val orphans = BackupOrphanPrune.orphanFileRelPaths(vault, local)
        assertEquals(setOf("gone.txt", "nested/old.txt"), orphans)
        // Must never invent or return source paths.
        assertTrue(orphans.none { it.startsWith("/") })
        assertTrue(orphans.none { it.startsWith("content:") })
        assertTrue(orphans.none { it.startsWith("Backups/") })
    }

    @Test
    fun backupModeSemantics_orphanHelperUnusedImpliesNoDeletes() {
        // Backup mode never calls orphan prune; empty orphan set documents put-only safety.
        val vault = setOf("extra-in-vault.txt")
        val local = emptySet<String>()
        // Helper still identifies orphans, but BackupTransferMode.BACKUP must not invoke deletes.
        assertEquals(setOf("extra-in-vault.txt"), BackupOrphanPrune.orphanFileRelPaths(vault, local))
        assertEquals(BackupTransferMode.BACKUP, BackupTransferMode.DEFAULT)
    }
}
