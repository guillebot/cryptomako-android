package net.gschimmel.cryptomako.backup

import net.gschimmel.cryptomako.vault.VaultSession

/**
 * Process-scoped holder for the unlocked [VaultSession].
 * Backup WorkManager / foreground work requires an unlocked vault; no passphrase is persisted.
 */
object VaultSessionHolder {
    @Volatile
    var session: VaultSession? = null
        private set

    fun set(session: VaultSession?) {
        this.session = session
    }

    fun clear() {
        session = null
    }
}
