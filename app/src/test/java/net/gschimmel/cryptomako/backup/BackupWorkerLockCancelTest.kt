package net.gschimmel.cryptomako.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupWorkerLockCancelTest {
    @Test
    fun lockCancelsOneShotAndPeriodicUniqueWorks() {
        val names = BackupWorker.lockCancelWorkNames()
        assertEquals(
            listOf(BackupWorker.UNIQUE_ONE_SHOT, BackupWorker.UNIQUE_PERIODIC),
            names,
        )
        assertTrue(names.contains("cryptomako_backup_now"))
        assertTrue(names.contains("cryptomako_backup_periodic"))
    }
}
