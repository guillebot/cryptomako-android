package net.gschimmel.cryptomako.backup

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Nested / overlapping Backup Sync sources (Platforms consensus):
 * soft-warn on add; hard-fail on Sync when one resolved URI prefixes another.
 *
 * Android compares **resolved SAF URI identity only** (authority + tree document id),
 * not display names. No filesystem symlink resolve (SAF has no Path.GetFullPath).
 */
object BackupUriOverlap {
    data class OverlapPair(
        val a: BackupSource,
        val b: BackupSource,
        val resolvedA: String,
        val resolvedB: String,
    )

    /**
     * Normalize a SAF tree/document URI (or opaque path) to a comparable key:
     * `authority/tree/<decodedDocId>` when `/tree/` is present, else authority+decoded path.
     */
    fun normalize(uriOrPath: String): String {
        val raw = uriOrPath.trim()
        require(raw.isNotEmpty()) { "uri required" }

        val schemeSep = raw.indexOf("://")
        val authStart = if (schemeSep >= 0) schemeSep + 3 else 0
        val pathStart = raw.indexOf('/', authStart).let { if (it < 0) raw.length else it }
        val authority = raw.substring(authStart, pathStart).lowercase()
        val pathAndMore = if (pathStart < raw.length) raw.substring(pathStart) else ""
        val path = pathAndMore.substringBefore('?').substringBefore('#')

        val treeMarker = "/tree/"
        val treeIdx = path.indexOf(treeMarker)
        if (treeIdx >= 0) {
            var rest = path.substring(treeIdx + treeMarker.length)
            val docMarker = "/document/"
            val docIdx = rest.indexOf(docMarker)
            if (docIdx >= 0) rest = rest.substring(0, docIdx)
            val docId = trimSep(urlDecode(rest))
            return "$authority/tree/$docId"
        }

        return trimSep("$authority${urlDecode(path)}")
    }

    /** True when [ancestor] equals [descendant] or is a strict path prefix (boundary-safe). */
    fun isSameOrPrefix(ancestor: String, descendant: String): Boolean {
        val a = trimSep(ancestor)
        val b = trimSep(descendant)
        if (a == b) return true
        if (a.isEmpty()) return false
        return b.startsWith("$a/")
    }

    fun findOverlaps(sources: Iterable<BackupSource>): List<OverlapPair> {
        val resolved = mutableListOf<Pair<BackupSource, String>>()
        for (s in sources) {
            if (s.safUri.isBlank()) continue
            try {
                resolved.add(s to normalize(s.safUri))
            } catch (_: Exception) {
                // Skip unresolvable for soft paths; Sync hard-fail uses the same list.
            }
        }

        val pairs = mutableListOf<OverlapPair>()
        for (i in resolved.indices) {
            for (j in i + 1 until resolved.size) {
                val (sa, pa) = resolved[i]
                val (sb, pb) = resolved[j]
                if (isSameOrPrefix(pa, pb) || isSameOrPrefix(pb, pa)) {
                    pairs.add(OverlapPair(sa, sb, pa, pb))
                }
            }
        }
        return pairs
    }

    /** Soft-warn when adding [candidateSafUri] beside [existing]; null if disjoint. */
    fun softWarnOnAdd(
        existing: Iterable<BackupSource>,
        candidateSafUri: String,
        candidateDisplayName: String? = null,
    ): String? {
        val candidate = try {
            BackupSource.create(candidateSafUri, candidateDisplayName)
        } catch (_: Exception) {
            return null
        }
        val overlaps = findOverlaps(existing.asSequence().plus(candidate).asIterable())
        if (overlaps.isEmpty()) return null
        val o = overlaps.first()
        return "Warning: backup source overlaps another (nested paths). " +
            "'${o.resolvedA}' ↔ '${o.resolvedB}'. Sync will refuse to start until resolved."
    }

    /** Error message for Sync hard-fail, or null when no overlap. */
    fun overlapErrorOrNull(sources: Iterable<BackupSource>): String? {
        val overlaps = findOverlaps(sources)
        if (overlaps.isEmpty()) return null
        val o = overlaps.first()
        return "Backup Sync refused: nested/overlapping sources. " +
            "'${o.resolvedA}' overlaps '${o.resolvedB}'. Remove or change one source before syncing."
    }

    /** Hard-fail before Sync when any pair overlaps. */
    fun throwIfOverlapping(sources: Iterable<BackupSource>) {
        val err = overlapErrorOrNull(sources) ?: return
        throw IllegalStateException(err)
    }

    private fun urlDecode(s: String): String {
        var cur = s
        // SAF tree ids are often double-friendly; decode until stable (cap iterations).
        repeat(3) {
            val next = try {
                URLDecoder.decode(cur, StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                return cur
            }
            if (next == cur) return cur
            cur = next
        }
        return cur
    }

    private fun trimSep(p: String): String = p.trimEnd('/')
}
