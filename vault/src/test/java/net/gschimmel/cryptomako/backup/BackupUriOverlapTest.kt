package net.gschimmel.cryptomako.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupUriOverlapTest {

    private val auth = "com.android.externalstorage.documents"

    private fun treeUri(docIdEncoded: String): String =
        "content://$auth/tree/$docIdEncoded"

    @Test
    fun normalize_extracts_decoded_tree_doc_id() {
        val uri = treeUri("primary%3ADocuments")
        assertEquals(
            "$auth/tree/primary:Documents",
            BackupUriOverlap.normalize(uri),
        )
    }

    @Test
    fun normalize_nested_doc_id_with_slash() {
        val uri = treeUri("primary%3ADocuments%2FPhotos")
        assertEquals(
            "$auth/tree/primary:Documents/Photos",
            BackupUriOverlap.normalize(uri),
        )
    }

    @Test
    fun isSameOrPrefix_parent_and_child() {
        val parent = "$auth/tree/primary:Documents"
        val child = "$auth/tree/primary:Documents/Photos"
        assertTrue(BackupUriOverlap.isSameOrPrefix(parent, child))
        assertFalse(BackupUriOverlap.isSameOrPrefix(child, parent))
        assertTrue(BackupUriOverlap.isSameOrPrefix(parent, parent))
    }

    @Test
    fun isSameOrPrefix_rejects_partial_segment() {
        // primary:Doc must not prefix primary:Documents
        assertFalse(
            BackupUriOverlap.isSameOrPrefix(
                "$auth/tree/primary:Doc",
                "$auth/tree/primary:Documents",
            ),
        )
    }

    @Test
    fun softWarnOnAdd_when_nested_under_existing() {
        val existing = listOf(
            BackupSource.create(treeUri("primary%3ADocuments"), "Documents"),
        )
        val warn = BackupUriOverlap.softWarnOnAdd(
            existing,
            treeUri("primary%3ADocuments%2FPhotos"),
            "Photos",
        )
        assertNotNull(warn)
        assertTrue(warn!!.contains("overlap", ignoreCase = true))
    }

    @Test
    fun softWarnOnAdd_null_when_disjoint() {
        val existing = listOf(
            BackupSource.create(treeUri("primary%3ADocuments"), "Documents"),
        )
        assertNull(
            BackupUriOverlap.softWarnOnAdd(
                existing,
                treeUri("primary%3ADownload"),
                "Download",
            ),
        )
    }

    @Test
    fun throwIfOverlapping_when_parent_and_child() {
        val sources = listOf(
            BackupSource.create(treeUri("primary%3ADocuments"), "Documents"),
            BackupSource.create(treeUri("primary%3ADocuments%2FWork"), "Work"),
        )
        try {
            BackupUriOverlap.throwIfOverlapping(sources)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("refused", ignoreCase = true))
        }
    }

    @Test
    fun throwIfOverlapping_allows_siblings() {
        val sources = listOf(
            BackupSource.create(treeUri("primary%3ADocuments%2Fa"), "A"),
            BackupSource.create(treeUri("primary%3ADocuments%2Fb"), "B"),
        )
        BackupUriOverlap.throwIfOverlapping(sources)
    }

    @Test
    fun findOverlaps_same_uri() {
        val uri = treeUri("primary%3ADownload")
        val sources = listOf(
            BackupSource.create(uri, "One"),
            BackupSource.create(uri, "Two"),
        )
        assertEquals(1, BackupUriOverlap.findOverlaps(sources).size)
    }

    @Test
    fun overlapErrorOrNull_null_for_single_source() {
        val sources = listOf(
            BackupSource.create(treeUri("primary%3ADownload"), "Download"),
        )
        assertNull(BackupUriOverlap.overlapErrorOrNull(sources))
    }
}
