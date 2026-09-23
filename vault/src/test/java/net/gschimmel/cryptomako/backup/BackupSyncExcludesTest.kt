package net.gschimmel.cryptomako.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSyncExcludesTest {
    private val excludes = BackupSyncExcludes.DEFAULT

    @Test
    fun skipsDefaultDirectories() {
        assertTrue(excludes.shouldSkipDirectory("node_modules"))
        assertTrue(excludes.shouldSkipDirectory(".git"))
        assertFalse(excludes.shouldSkipDirectory("src"))
    }

    @Test
    fun skipsDefaultFileNames() {
        assertTrue(excludes.shouldSkipFile(".DS_Store"))
        assertTrue(excludes.shouldSkipFile("Thumbs.db"))
        assertFalse(excludes.shouldSkipFile("readme.md"))
    }

    @Test
    fun skipsDefaultExtensions() {
        assertTrue(excludes.shouldSkipFile("module.pyc"))
        assertTrue(excludes.shouldSkipFile("x.PYC"))
        assertFalse(excludes.shouldSkipFile("module.py"))
        assertFalse(excludes.shouldSkipFile("pyc")) // no extension
    }

    @Test
    fun skipsRelativePathWithExcludedSegment() {
        assertTrue(excludes.shouldSkipRelativePath("proj/node_modules/pkg/index.js"))
        assertTrue(excludes.shouldSkipRelativePath("a/.git/config"))
        assertTrue(excludes.shouldSkipRelativePath("docs/.DS_Store"))
        assertTrue(excludes.shouldSkipRelativePath("build/foo.pyc"))
        assertFalse(excludes.shouldSkipRelativePath("src/main/App.kt"))
    }

    @Test
    fun pathMappingVaultRoot() {
        val mapped = backupVaultPath(folderName = "Camera", relativePath = "DCIM/img.jpg")
        assertTrue(mapped == "Backups/Camera/DCIM/img.jpg")
        val rootOnly = backupVaultPath(folderName = "Docs", relativePath = "")
        assertTrue(rootOnly == "Backups/Docs")
    }

    @Test
    fun splitParentAndName() {
        assertTrue(splitParentAndName("Backups/Camera/a.txt") == ("Backups/Camera" to "a.txt"))
        assertTrue(splitParentAndName("readme.md") == ("" to "readme.md"))
    }
}
