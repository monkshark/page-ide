package page.runtime

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

object KlsReleasesManifest {

    const val DEFAULT_URL: String = "https://monkshark.github.io/page-ide/lsp/kotlin.json"

    data class FetchedManifest(
        val etag: String?,
        val notModified: Boolean,
        val fork: List<LspReleasesCache.TaggedRelease>,
        val upstream: List<LspReleasesCache.TaggedRelease>,
    )

    private data class RawEntry(val tag: String?, val publishedAt: String?)
    private data class RawManifest(
        val updatedAt: String? = null,
        val fork: List<RawEntry> = emptyList(),
        val upstream: List<RawEntry> = emptyList(),
    )

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun fetch(url: String = DEFAULT_URL, ifNoneMatch: String? = null): FetchedManifest? {
        return runCatching { fetchOrThrow(url, ifNoneMatch) }.getOrNull()
    }

    private fun fetchOrThrow(url: String, ifNoneMatch: String?): FetchedManifest {
        val conn = openConnection(url)
        if (!ifNoneMatch.isNullOrBlank()) {
            conn.setRequestProperty("If-None-Match", ifNoneMatch)
        }
        try {
            val code = conn.responseCode
            if (code == 304) {
                return FetchedManifest(etag = ifNoneMatch, notModified = true, fork = emptyList(), upstream = emptyList())
            }
            if (code !in 200..299) throw IOException("Manifest HTTP $code for $url")
            val etag = conn.getHeaderField("ETag")
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parseBody(body, etag)
        } finally {
            conn.disconnect()
        }
    }

    internal fun parseBody(body: String, etag: String? = null): FetchedManifest {
        val raw = gson.fromJson(body, RawManifest::class.java) ?: throw IOException("empty manifest")
        return FetchedManifest(
            etag = etag,
            notModified = false,
            fork = raw.fork.mapNotNull { it.tag?.let { tag -> LspReleasesCache.TaggedRelease(tag, it.publishedAt) } },
            upstream = raw.upstream.mapNotNull { it.tag?.let { tag -> LspReleasesCache.TaggedRelease(tag, it.publishedAt) } },
        )
    }

    private fun openConnection(url: String): HttpURLConnection {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 KlsReleasesManifest")
        return conn
    }
}
