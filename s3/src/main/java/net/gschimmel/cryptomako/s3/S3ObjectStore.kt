package net.gschimmel.cryptomako.s3

import net.gschimmel.cryptomako.store.ListedObject
import net.gschimmel.cryptomako.store.ObjectStore
import net.gschimmel.cryptomako.store.ObjectStoreException
import net.gschimmel.cryptomako.store.PrefixListing
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * HTTPS path-style S3 client: OkHttp + hand-rolled SigV4.
 *
 * Fail-closed: [putObject] / [deleteObject] return only after HTTP 2xx from S3.
 * Never treats local buffering as durable success.
 */
class S3ObjectStore(
    private val config: S3Config,
    private val client: OkHttpClient = defaultClient(),
) : ObjectStore {

    private val endpointUrl: HttpUrl = config.endpoint.trimEnd('/').toHttpUrl()
    private val credentials = SigV4.Credentials(
        accessKey = config.accessKey,
        secretKey = String(config.secretKey),
        region = config.region,
    )

    override fun getObject(key: String): ByteArray {
        val request = signedRequest("GET", key, emptyList())
        val (code, body) = execute(request, key)
        checkResponse(code, key, body)
        return body
    }

    override fun headObject(key: String): ListedObject {
        val request = signedRequest("HEAD", key, emptyList())
        return try {
            client.newCall(request).execute().use { response ->
                val errBody = if (!response.isSuccessful) {
                    response.body?.bytes() ?: ByteArray(0)
                } else {
                    ByteArray(0)
                }
                checkResponse(response.code, key, errBody)
                val length = response.header("Content-Length")?.toLongOrNull() ?: 0L
                val eTag = response.header("ETag")?.replace("\"", "")
                ListedObject(key = key, size = length, eTag = eTag)
            }
        } catch (e: ObjectStoreException) {
            throw e
        } catch (e: IOException) {
            throw ObjectStoreException.Transport("transport error for $key: ${e.message}", e)
        }
    }

    override fun listImmediate(prefix: String): PrefixListing {
        val objects = mutableListOf<ListedObject>()
        val prefixes = mutableListOf<String>()
        var token: String? = null
        do {
            val query = mutableListOf(
                "list-type" to "2",
                "delimiter" to "/",
                "max-keys" to "1000",
                "prefix" to prefix,
            )
            if (token != null) {
                query.add("continuation-token" to token!!)
            }
            val request = signedRequest("GET", key = "", query = query)
            val (code, body) = execute(request, prefix)
            checkResponse(code, prefix, body)
            val parsed = ListObjectsParser.parse(body)
            objects.addAll(parsed.listing.objects)
            prefixes.addAll(parsed.listing.commonPrefixes)
            token = if (parsed.isTruncated) parsed.nextContinuationToken else null
        } while (token != null)
        return PrefixListing(objects = objects, commonPrefixes = prefixes)
    }

    override fun putObject(key: String, data: ByteArray) {
        val payloadHash = SigV4.sha256Hex(data)
        val request = signedRequest(
            method = "PUT",
            key = key,
            query = emptyList(),
            payloadHash = payloadHash,
            body = data,
        )
        val (code, body) = execute(request, key)
        checkResponse(code, key, body)
    }

    override fun deleteObject(key: String) {
        val request = signedRequest("DELETE", key, emptyList())
        val (code, body) = execute(request, key)
        checkResponse(code, key, body)
    }

    private fun signedRequest(
        method: String,
        key: String,
        query: List<Pair<String, String>>,
        payloadHash: String = SigV4.EMPTY_PAYLOAD_SHA256,
        body: ByteArray? = null,
    ): Request {
        val url = buildUrl(key, query)
        val uri = URI.create(url.toString())
        val signedHeaders = SigV4.sign(
            method = method,
            uri = uri,
            credentials = credentials,
            payloadHash = payloadHash,
        )
        val builder = Request.Builder().url(url)
        if (body != null) {
            builder.method(method, body.toRequestBody("application/octet-stream".toMediaType()))
            builder.header("Content-Length", body.size.toString())
        } else {
            builder.method(method, null)
        }
        for ((name, value) in signedHeaders) {
            // OkHttp is case-insensitive; Authorization must be set exactly once.
            builder.header(name, value)
        }
        return builder.build()
    }

    private fun buildUrl(key: String, query: List<Pair<String, String>>): HttpUrl {
        val rawPath = if (config.pathStyle) {
            "/" + config.bucket + if (key.isEmpty()) "/" else "/$key"
        } else {
            if (key.isEmpty()) "/" else "/$key"
        }
        val encodedPath = SigV4.uriEncode(rawPath, encodeSlash = false)
        val encodedQuery = if (query.isEmpty()) null else SigV4.canonicalQueryString(query)

        val builder = endpointUrl.newBuilder()
        if (!config.pathStyle) {
            builder.host("${config.bucket}.${endpointUrl.host}")
        }
        builder.encodedPath(encodedPath)
        builder.encodedQuery(encodedQuery)
        return builder.build()
    }

    private fun execute(request: Request, key: String): Pair<Int, ByteArray> {
        return try {
            client.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                response.code to bytes
            }
        } catch (e: IOException) {
            throw ObjectStoreException.Transport("transport error for $key: ${e.message}", e)
        }
    }

    private fun checkResponse(code: Int, key: String, body: ByteArray) {
        when (code) {
            in 200..299 -> return
            404 -> throw ObjectStoreException.NotFound(key)
            403 -> {
                val detail = body.toString(Charsets.UTF_8).take(512)
                throw ObjectStoreException.Transport("access denied for $key (HTTP 403) $detail")
            }
            else -> {
                val detail = body.toString(Charsets.UTF_8).take(512)
                throw ObjectStoreException.Transport("HTTP $code for $key $detail")
            }
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(120, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .cache(null)
                .build()
    }
}
