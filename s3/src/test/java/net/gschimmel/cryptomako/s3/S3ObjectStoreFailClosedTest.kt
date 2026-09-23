package net.gschimmel.cryptomako.s3

import net.gschimmel.cryptomako.store.ObjectStoreException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** MockWebServer: get/put/delete succeed only on HTTP 2xx (fail-closed). */
class S3ObjectStoreFailClosedTest {

    private lateinit var server: MockWebServer
    private lateinit var store: S3ObjectStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val endpoint = server.url("/").toString().trimEnd('/')
        // localhost HTTP is permitted only for tests (see S3Config).
        store = S3ObjectStore(
            S3Config(
                endpoint = endpoint,
                region = "us-east-1",
                bucket = "test-bucket",
                accessKey = "AKIA_TEST",
                secretKey = "secret_test_key_value_123456".toCharArray(),
                pathStyle = true,
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getObjectReturnsBodyOn200() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello-bytes"))
        val data = store.getObject("folder/file.txt")
        assertArrayEquals("hello-bytes".toByteArray(), data)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("/test-bucket/folder/file.txt"))
        assertTrue(recorded.getHeader("Authorization")!!.startsWith("AWS4-HMAC-SHA256"))
    }

    @Test
    fun getObject404ThrowsNotFound() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))
        assertThrows(ObjectStoreException.NotFound::class.java) {
            store.getObject("missing.txt")
        }
    }

    @Test
    fun putObjectRequires2xx_failClosedOn500() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertThrows(ObjectStoreException.Transport::class.java) {
            store.putObject("new.txt", "payload".toByteArray())
        }
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertArrayEquals("payload".toByteArray(), recorded.body.readByteArray())
    }

    @Test
    fun putObjectSucceedsOn200() {
        server.enqueue(MockResponse().setResponseCode(200))
        store.putObject("new.txt", "payload".toByteArray())
        assertEquals("PUT", server.takeRequest().method)
    }

    @Test
    fun deleteObjectRequires2xx_failClosedOn403() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("denied"))
        assertThrows(ObjectStoreException.Transport::class.java) {
            store.deleteObject("secret.txt")
        }
    }

    @Test
    fun listImmediateParsesContentsAndPrefixes() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ListBucketResult>
              <IsTruncated>false</IsTruncated>
              <Contents>
                <Key>vault/d/ab/cd/file.c9r</Key>
                <Size>12</Size>
                <ETag>"abc"</ETag>
              </Contents>
              <CommonPrefixes>
                <Prefix>vault/d/ab/cd/dir.c9r/</Prefix>
              </CommonPrefixes>
            </ListBucketResult>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(xml))
        val listing = store.listImmediate("vault/d/ab/cd/")
        assertEquals(1, listing.objects.size)
        assertEquals("vault/d/ab/cd/file.c9r", listing.objects[0].key)
        assertEquals(12L, listing.objects[0].size)
        assertEquals(listOf("vault/d/ab/cd/dir.c9r/"), listing.commonPrefixes)
    }
}
