package net.gschimmel.cryptomako.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPathMappingAppTest {
    @Test
    fun vaultPathAndExcludesMatchMacDefaults() {
        assertEquals(
            "Backups/Photos/DCIM/a.jpg",
            backupVaultPath("Photos", "DCIM/a.jpg"),
        )
        val ex = BackupSyncExcludes.DEFAULT
        assertTrue(ex.shouldSkipRelativePath("x/node_modules/y"))
        assertFalse(ex.shouldSkipRelativePath("Photos/DCIM/a.jpg"))
    }
}
