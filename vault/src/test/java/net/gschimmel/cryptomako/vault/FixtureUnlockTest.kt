package net.gschimmel.cryptomako.vault

import net.gschimmel.cryptomako.store.LocalFilesystemObjectStore
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Golden unlock against the macOS CryptoMako fixtures when present on disk.
 * Skips gracefully if the fixtures path (or PASSWORD) is missing — e.g. CI without the sibling repo.
 *
 * Never prints or asserts on the passphrase value.
 */
class FixtureUnlockTest {

    @Test
    fun unlockAndListMatchesExpectedLs() {
        val fixtures = File(FIXTURES_DIR)
        assumeTrue("fixtures dir missing at $FIXTURES_DIR", fixtures.isDirectory)
        val vaultDir = File(fixtures, "vault")
        assumeTrue("fixtures/vault missing", vaultDir.isDirectory)
        val passwordFile = File(fixtures, "PASSWORD")
        assumeTrue("fixtures/PASSWORD missing", passwordFile.isFile)
        val expectedFile = File(fixtures, "expected-ls.txt")
        assumeTrue("fixtures/expected-ls.txt missing", expectedFile.isFile)

        val passphrase = passwordFile.readText().trimEnd('\n')
        assumeTrue("empty passphrase file", passphrase.isNotEmpty())

        val store = LocalFilesystemObjectStore(vaultDir)
        VaultSession.unlock(store, passphrase).use { session ->
            assertTrue("expected format 8", session.config.format == 8)
            val actual = session.listRecursive()
            val expected = expectedFile.readLines()
                .map { it.trimEnd() }
                .filter { it.isNotEmpty() }
                .sorted()
            val actualSorted = actual.sorted()

            val missing = expected.filterNot { it in actualSorted }
            val extra = actualSorted.filterNot { it in expected }
            assertTrue(
                "listing mismatch.\nmissing=$missing\nextra=$extra\nactual=$actualSorted",
                missing.isEmpty() && extra.isEmpty(),
            )

            // Spot-check names called out in the brief
            val rootNames = session.list().map { it.cleartextName }.toSet()
            assertTrue(rootNames.contains("hello.txt"))
            assertTrue(rootNames.contains("notes"))
        }
    }

    companion object {
        // Absolute path on Guillermo's Mac; tests skip if absent.
        private const val FIXTURES_DIR = "/Users/guille/dev/cryptomako/fixtures"
    }
}
