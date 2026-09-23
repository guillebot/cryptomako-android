package net.gschimmel.cryptomako.s3

import net.gschimmel.cryptomako.store.ListedObject
import net.gschimmel.cryptomako.store.ObjectStoreException
import net.gschimmel.cryptomako.store.PrefixListing
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Parses an S3 ListObjectsV2 XML response (mirrors macOS ListObjectsParser). */
internal object ListObjectsParser {
    data class Result(
        val listing: PrefixListing,
        val nextContinuationToken: String?,
        val isTruncated: Boolean,
    )

    fun parse(data: ByteArray): Result {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
        }
        val doc = try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(data))
        } catch (e: Exception) {
            throw ObjectStoreException.Transport("malformed ListObjectsV2 response", e)
        }
        val root = doc.documentElement
            ?: throw ObjectStoreException.Transport("malformed ListObjectsV2 response")

        val objects = mutableListOf<ListedObject>()
        val commonPrefixes = mutableListOf<String>()
        var nextToken: String? = null
        var isTruncated = false

        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val el = node as Element
            when (el.tagName) {
                "Contents" -> {
                    val key = textChild(el, "Key")
                    if (key.isNotEmpty()) {
                        val size = textChild(el, "Size").toLongOrNull() ?: 0L
                        val eTag = textChild(el, "ETag").replace("\"", "").ifEmpty { null }
                        objects.add(ListedObject(key = key, size = size, eTag = eTag))
                    }
                }
                "CommonPrefixes" -> {
                    val prefix = textChild(el, "Prefix")
                    if (prefix.isNotEmpty()) commonPrefixes.add(prefix)
                }
                "NextContinuationToken" -> {
                    val v = el.textContent?.trim().orEmpty()
                    nextToken = v.ifEmpty { null }
                }
                "IsTruncated" -> {
                    isTruncated = el.textContent?.trim() == "true"
                }
            }
        }

        return Result(
            listing = PrefixListing(objects = objects, commonPrefixes = commonPrefixes),
            nextContinuationToken = nextToken,
            isTruncated = isTruncated,
        )
    }

    private fun textChild(parent: Element, tag: String): String {
        val list = parent.getElementsByTagName(tag)
        if (list.length == 0) return ""
        return list.item(0).textContent?.trim().orEmpty()
    }
}
