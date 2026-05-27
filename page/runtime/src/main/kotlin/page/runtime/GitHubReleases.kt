package page.runtime

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

object GitHubReleases {

    data class Release(val tagName: String, val publishedAt: String?, val prerelease: Boolean)

    data class ReleasesEtagResponse(
        val notModified: Boolean,
        val etag: String?,
        val releases: List<Release>,
    )

    data class AssetNamesEtagResponse(
        val notModified: Boolean,
        val etag: String?,
        val names: List<String>,
    )

    fun listReleases(owner: String, repo: String, limit: Int = 20): List<Release> {
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=$limit"
        val conn = openConnection(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("GitHub API HTTP $code for $url")
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parseReleases(body)
        } finally {
            conn.disconnect()
        }
    }

    fun listReleasesWithEtag(
        owner: String,
        repo: String,
        ifNoneMatch: String?,
        limit: Int = 20,
    ): ReleasesEtagResponse {
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=$limit"
        val conn = openConnection(url)
        if (!ifNoneMatch.isNullOrBlank()) {
            conn.setRequestProperty("If-None-Match", ifNoneMatch)
        }
        try {
            val code = conn.responseCode
            if (code == 304) {
                return ReleasesEtagResponse(notModified = true, etag = ifNoneMatch, releases = emptyList())
            }
            if (code !in 200..299) throw IOException("GitHub API HTTP $code for $url")
            val etag = conn.getHeaderField("ETag")
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return ReleasesEtagResponse(notModified = false, etag = etag, releases = parseReleases(body))
        } finally {
            conn.disconnect()
        }
    }

    fun latestTag(owner: String, repo: String): String? = runCatching {
        val all = listReleases(owner, repo, limit = 5)
        all.firstOrNull { !it.prerelease }?.tagName ?: all.firstOrNull()?.tagName
    }.getOrNull()

    fun fetchAssetUrl(owner: String, repo: String, tag: String, suffix: String): String? {
        val url = "https://api.github.com/repos/$owner/$repo/releases/tags/$tag"
        val conn = openConnection(url)
        return try {
            val code = conn.responseCode
            if (code !in 200..299) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            findAssetDownloadUrl(body, suffix)
        } catch (_: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }

    fun listAssetNamesWithEtag(
        owner: String,
        repo: String,
        tag: String,
        ifNoneMatch: String?,
    ): AssetNamesEtagResponse {
        val url = "https://api.github.com/repos/$owner/$repo/releases/tags/$tag"
        val conn = openConnection(url)
        if (!ifNoneMatch.isNullOrBlank()) {
            conn.setRequestProperty("If-None-Match", ifNoneMatch)
        }
        try {
            val code = conn.responseCode
            if (code == 304) {
                return AssetNamesEtagResponse(notModified = true, etag = ifNoneMatch, names = emptyList())
            }
            if (code !in 200..299) throw IOException("GitHub API HTTP $code for $url")
            val etag = conn.getHeaderField("ETag")
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return AssetNamesEtagResponse(notModified = false, etag = etag, names = parseAssetNames(body))
        } finally {
            conn.disconnect()
        }
    }

    fun listAssetNames(owner: String, repo: String, tag: String): List<String> {
        val now = System.currentTimeMillis()
        val cached = AssetListCache.load(owner, repo, tag)
        if (AssetListCache.isFresh(cached, now)) {
            return cached!!.assets
        }
        val resp = runCatching {
            listAssetNamesWithEtag(owner, repo, tag, cached?.etag)
        }.getOrNull()
        return when {
            resp == null -> cached?.assets.orEmpty()
            resp.notModified -> {
                if (cached != null) {
                    AssetListCache.save(owner, repo, tag, cached.copy(fetchedAt = now))
                    cached.assets
                } else emptyList()
            }
            else -> {
                AssetListCache.save(
                    owner, repo, tag,
                    AssetListCache.Cached(
                        fetchedAt = now,
                        etag = resp.etag ?: cached?.etag,
                        assets = resp.names,
                    ),
                )
                resp.names
            }
        }
    }

    internal fun parseAssetNames(json: String): List<String> {
        val out = mutableListOf<String>()
        val key = "\"name\""
        var cursor = json.indexOf("\"assets\"")
        if (cursor < 0) return emptyList()
        cursor = json.indexOf('[', cursor)
        if (cursor < 0) return emptyList()
        val end = matchingBracket(json, cursor)
        if (end < 0) return emptyList()
        var i = cursor
        while (true) {
            val keyIdx = json.indexOf(key, i)
            if (keyIdx < 0 || keyIdx >= end) break
            val value = readStringValue(json, keyIdx) ?: break
            out += value
            i = keyIdx + key.length
        }
        return out
    }

    private fun matchingBracket(json: String, openIdx: Int): Int {
        var depth = 0
        var i = openIdx
        var inString = false
        while (i < json.length) {
            val c = json[i]
            when {
                inString && c == '\\' -> { i += 2; continue }
                c == '"' -> inString = !inString
                !inString && c == '[' -> depth++
                !inString && c == ']' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return -1
    }

    internal fun findAssetDownloadUrl(json: String, suffix: String): String? {
        var i = 0
        while (i < json.length) {
            val key = json.indexOf("\"browser_download_url\"", i)
            if (key < 0) return null
            val value = readStringValue(json, key) ?: return null
            if (value.endsWith(suffix, ignoreCase = true)) return value
            i = key + "\"browser_download_url\"".length
        }
        return null
    }

    private fun openConnection(url: String): HttpURLConnection {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 LspInstaller")
        return conn
    }

    internal fun parseReleases(json: String): List<Release> {
        val out = mutableListOf<Release>()
        var i = 0
        while (i < json.length) {
            val tagStart = json.indexOf("\"tag_name\"", i)
            if (tagStart < 0) break
            val tag = readStringValue(json, tagStart) ?: break
            val nameEnd = json.indexOf("\"tag_name\"", tagStart + 1)
            val sliceEnd = if (nameEnd < 0) json.length else nameEnd
            val slice = json.substring(tagStart, sliceEnd)
            val published = readStringFieldIn(slice, "published_at")
            val prerelease = readBoolFieldIn(slice, "prerelease") ?: false
            out += Release(tag, published, prerelease)
            i = sliceEnd
        }
        return out
    }

    private fun readStringValue(json: String, fieldStart: Int): String? {
        val colon = json.indexOf(':', fieldStart)
        if (colon < 0) return null
        var p = colon + 1
        while (p < json.length && json[p].isWhitespace()) p++
        if (p >= json.length || json[p] != '"') return null
        val end = findStringEnd(json, p + 1)
        if (end < 0) return null
        return unescapeJson(json.substring(p + 1, end))
    }

    private fun readStringFieldIn(slice: String, field: String): String? {
        val idx = slice.indexOf("\"$field\"")
        if (idx < 0) return null
        return readStringValue(slice, idx)
    }

    private fun readBoolFieldIn(slice: String, field: String): Boolean? {
        val idx = slice.indexOf("\"$field\"")
        if (idx < 0) return null
        val colon = slice.indexOf(':', idx)
        if (colon < 0) return null
        var p = colon + 1
        while (p < slice.length && slice[p].isWhitespace()) p++
        return when {
            slice.startsWith("true", p) -> true
            slice.startsWith("false", p) -> false
            else -> null
        }
    }

    private fun findStringEnd(json: String, start: Int): Int {
        var p = start
        while (p < json.length) {
            val c = json[p]
            if (c == '\\') {
                p += 2
                continue
            }
            if (c == '"') return p
            p++
        }
        return -1
    }

    private fun unescapeJson(raw: String): String {
        if ('\\' !in raw) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= raw.length) {
                sb.append(c); i++; continue
            }
            when (val next = raw[i + 1]) {
                '"' -> { sb.append('"'); i += 2 }
                '\\' -> { sb.append('\\'); i += 2 }
                '/' -> { sb.append('/'); i += 2 }
                'n' -> { sb.append('\n'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                'u' -> {
                    if (i + 5 < raw.length) {
                        val code = raw.substring(i + 2, i + 6).toIntOrNull(16)
                        if (code != null) { sb.append(code.toChar()); i += 6; continue }
                    }
                    sb.append(next); i += 2
                }
                else -> { sb.append(next); i += 2 }
            }
        }
        return sb.toString()
    }
}
