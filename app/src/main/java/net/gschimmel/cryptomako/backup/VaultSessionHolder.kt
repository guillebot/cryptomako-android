package net.gschimmel.cryptomako.backup

import net.gschimmel.cryptomako.vault.VaultSession

/**
 * Process-scoped holder for the unlocked [VaultSession].
 * Backup WorkManager / foreground work requires an unlocked vault; no passphrase is persisted.
 *
 * Owns session lifecycle: [set] / [clear] destroy cryptor + masterkey of any previous session
 * so key material is not left reachable after lock or re-unlock.
 */
object VaultSessionHolder {
    @Volatile
    var session: VaultSession? = null
        private set

    @Synchronized
    fun set(session: VaultSession?) {
        val previous = this.session
        if (previous === session) return
        this.session = session
        if (previous != null) {
            runCatching { previous.close() }
        }
    }

    fun clear() {
        set(null)
    }
}
