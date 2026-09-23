package net.gschimmel.cryptomako.s3

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4 signer for S3 (path-style HTTPS).
 * Semantics mirror macOS `SigV4.swift`.
 */
object SigV4 {
    const val EMPTY_PAYLOAD_SHA256 =
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    data class Credentials(
        val accessKey: String,
        val secretKey: String,
        val region: String,
        val service: String = "s3",
    )

    data class SignedHeaders(
        val headers: Map<String, String>,
    )

    fun sign(
        method: String,
        uri: URI,
        credentials: Credentials,
        payloadHash: String = EMPTY_PAYLOAD_SHA256,
        now: Instant = Instant.now(),
        extraHeaders: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val host = uri.host ?: throw IllegalArgumentException("URI missing host")
        val amzDate = AMZ_DATE.format(now.atZone(ZoneOffset.UTC))
        val dateStamp = amzDate.substring(0, 8)

        var hostHeader = host
        val port = if (uri.port >= 0) uri.port else -1
        if (port > 0 && !isDefaultPort(port, uri.scheme)) {
            hostHeader = "$host:$port"
        }

        val headers = linkedMapOf(
            "host" to hostHeader,
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to payloadHash,
        )
        for ((k, v) in extraHeaders) {
            headers[k.lowercase()] = v
        }

        val canonicalURI = canonicalPath(uri)
        val canonicalQuery = canonicalQueryString(uri)
        val sortedKeys = headers.keys.sorted()
        val canonicalHeaders = sortedKeys.joinToString("") { key ->
            "$key:${headers[key]!!.trim()}\n"
        }
        val signedHeaders = sortedKeys.joinToString(";")

        val canonicalRequest = listOf(
            method.uppercase(),
            canonicalURI,
            canonicalQuery,
            canonicalHeaders,
            signedHeaders,
            payloadHash,
        ).joinToString("\n")

        val scope = "$dateStamp/${credentials.region}/${credentials.service}/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            scope,
            sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8)),
        ).joinToString("\n")

        val signingKey = derivedKey(
            secret = credentials.secretKey,
            dateStamp = dateStamp,
            region = credentials.region,
            service = credentials.service,
        )
        val signature = hex(hmac(signingKey, stringToSign.toByteArray(StandardCharsets.UTF_8)))

        headers["authorization"] = "AWS4-HMAC-SHA256 " +
            "Credential=${credentials.accessKey}/$scope, " +
            "SignedHeaders=$signedHeaders, " +
            "Signature=$signature"
        return headers
    }

    /** S3 signs the path as-sent; segments encoded but `/` preserved. */
    fun canonicalPath(uri: URI): String {
        val raw = uri.rawPath ?: uri.path ?: "/"
        val decoded = try {
            java.net.URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            raw
        }
        val path = if (decoded.isEmpty()) "/" else decoded
        return uriEncode(path, encodeSlash = false)
    }

    fun canonicalQueryString(uri: URI): String {
        val raw = uri.rawQuery ?: return ""
        if (raw.isEmpty()) return ""
        val items = raw.split("&").map { part ->
            val eq = part.indexOf('=')
            if (eq < 0) {
                decodeQueryComponent(part) to ""
            } else {
                decodeQueryComponent(part.substring(0, eq)) to decodeQueryComponent(part.substring(eq + 1))
            }
        }
        return canonicalQueryString(items)
    }

    fun canonicalQueryString(items: List<Pair<String, String>>): String {
        val pairs = items.map { (name, value) ->
            uriEncode(name, encodeSlash = true) to uriEncode(value, encodeSlash = true)
        }.sortedWith(compareBy({ it.first }, { it.second }))
        return pairs.joinToString("&") { "${it.first}=${it.second}" }
    }

    /** RFC 3986 unreserved; everything else percent-encoded uppercase. */
    fun uriEncode(string: String, encodeSlash: Boolean): String {
        val bytes = string.toByteArray(StandardCharsets.UTF_8)
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val ub = b.toInt() and 0xff
            when {
                ub in 0x41..0x5A || ub in 0x61..0x7A || ub in 0x30..0x39 ||
                    ub == 0x2D || ub == 0x2E || ub == 0x5F || ub == 0x7E -> {
                    out.append(ub.toChar())
                }
                ub == 0x2F -> out.append(if (encodeSlash) "%2F" else "/")
                else -> out.append(String.format("%%%02X", ub))
            }
        }
        return out.toString()
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return hex(digest)
    }

    fun derivedKey(secret: String, dateStamp: String, region: String, service: String): ByteArray {
        var key = "AWS4$secret".toByteArray(StandardCharsets.UTF_8)
        for (step in listOf(dateStamp, region, service, "aws4_request")) {
            key = hmac(key, step.toByteArray(StandardCharsets.UTF_8))
        }
        return key
    }

    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b.toInt() and 0xff))
        return sb.toString()
    }

    private fun isDefaultPort(port: Int, scheme: String?): Boolean =
        (scheme == "https" && port == 443) || (scheme == "http" && port == 80)

    private fun decodeQueryComponent(raw: String): String {
        // Decode %XX but treat '+' as literal plus (query was already encoded).
        return try {
            java.net.URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            raw
        }
    }

    private val AMZ_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
}
