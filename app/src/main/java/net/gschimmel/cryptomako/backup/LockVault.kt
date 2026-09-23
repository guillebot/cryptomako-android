package net.gschimmel.cryptomako.backup

import android.content.Context

/**
 * Lock High parity: cancel SAF Backup Sync WorkManager, then clear the in-process
 * [VaultSession]. EncryptedSharedPreferences / Keystore credentials are untouched.
 */
object LockVault {
    fun perform(context: Context) {
        BackupWorker.cancelAll(context)
        VaultSessionHolder.clear()
    }
}
