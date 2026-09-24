package net.gschimmel.cryptomako.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BackupTransferModeTest {

    @Test
    fun prefsKey_matchesFamilySharedName() {
        assertEquals("backupTransferMode", BackupTransferMode.PREFS_KEY)
    }

    @Test
    fun default_isBackup_saferNoVaultDeletes() {
        assertSame(BackupTransferMode.BACKUP, BackupTransferMode.DEFAULT)
        assertEquals("backup", BackupTransferMode.DEFAULT.raw)
    }

    @Test
    fun fromRaw_parsesBackupAndSync() {
        assertEquals(BackupTransferMode.BACKUP, BackupTransferMode.fromRaw("backup"))
        assertEquals(BackupTransferMode.BACKUP, BackupTransferMode.fromRaw("BACKUP"))
        assertEquals(BackupTransferMode.SYNC, BackupTransferMode.fromRaw("sync"))
        assertEquals(BackupTransferMode.SYNC, BackupTransferMode.fromRaw(" Sync "))
    }

    @Test
    fun fromRaw_unknownOrNull_defaultsToBackup() {
        assertEquals(BackupTransferMode.BACKUP, BackupTransferMode.fromRaw(null))
        assertEquals(BackupTransferMode.BACKUP, BackupTransferMode.fromRaw(""))
        assertEquals(BackupTransferMode.BACKUP, BackupTransferMode.fromRaw("mirror"))
        assertEquals(BackupTransferMode.BACKUP, BackupTransferMode.fromRaw("delete"))
    }
}
