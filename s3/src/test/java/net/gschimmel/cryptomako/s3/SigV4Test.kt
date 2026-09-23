package net.gschimmel.cryptomako.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.time.Instant

/** Offline SigV4 vectors — no live AWS required. */
class SigV4Test {

    @Test
    fun emptyPayloadSha256MatchesAws() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            SigV4.EMPTY_PAYLOAD_SHA256,
        )
        assertEquals(SigV4.EMPTY_PAYLOAD_SHA256, SigV4.sha256Hex(ByteArray(0)))
    }

    @Test
    fun uriEncodePreservesSlashUnlessAsked() {
        assertEquals("/bucket/a%20b/c", SigV4.uriEncode("/bucket/a b/c", encodeSlash = false))
        assertEquals("%2Fbucket%2Fa%20b%2Fc", SigV4.uriEncode("/bucket/a b/c", encodeSlash = true))
        assertEquals("foo-_.~bar", SigV4.uriEncode("foo-_.~bar", encodeSlash = true))
    }

    @Test
    fun canonicalQuerySortsAndEncodesSlashInValues() {
        val q = SigV4.canonicalQueryString(
            listOf(
                "prefix" to "photos/2024/",
                "list-type" to "2",
                "delimiter" to "/",
            ),
        )
        assertEquals("delimiter=%2F&list-type=2&prefix=photos%2F2024%2F", q)
    }

    @Test
    fun canonicalPathKeepsTrailingSlash() {
        val uri = URI.create("https://s3.example.com/mybucket/")
        assertEquals("/mybucket/", SigV4.canonicalPath(uri))
    }

    /**
     * AWS docs signing-key example:
     * secret = wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY
     * date = 20120215, region = us-east-1, service = iam
     */
    @Test
    fun derivedSigningKeyMatchesAwsExample() {
        val key = SigV4.derivedKey(
            secret = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
            dateStamp = "20120215",
            region = "us-east-1",
            service = "iam",
        )
        assertEquals(
            "f4780e2d9f65fa895f9c67b32ce1baf0b0d8a43505a000a1a9e090d414db404d",
            SigV4.hex(key),
        )
    }

    @Test
    fun signProducesAuthorizationWithExpectedShape() {
        val uri = URI.create("https://s3.example.com/bucket/key.txt")
        val headers = SigV4.sign(
            method = "GET",
            uri = uri,
            credentials = SigV4.Credentials(
                accessKey = "AKIAIOSFODNN7EXAMPLE",
                secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                region = "us-east-1",
            ),
            payloadHash = SigV4.EMPTY_PAYLOAD_SHA256,
            now = Instant.parse("2013-05-24T00:00:00Z"),
        )
        val auth = headers.entries.first { it.key.equals("authorization", ignoreCase = true) }.value
        assertTrue(auth.startsWith("AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request"))
        assertTrue(auth.contains("SignedHeaders="))
        assertTrue(auth.contains("Signature="))
        assertEquals("20130524T000000Z", headers["x-amz-date"])
        assertEquals(SigV4.EMPTY_PAYLOAD_SHA256, headers["x-amz-content-sha256"])
    }
}
