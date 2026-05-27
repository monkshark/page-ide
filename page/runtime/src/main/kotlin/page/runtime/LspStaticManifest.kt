package page.runtime

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

object LspStaticManifest {

    const val DEFAULT_BASE_URL: String = "https://monkshark.github.io/page-ide/lsp"

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    private data class RawRelease(val tag: String?, val publishedAt: String?)
    private data class RawReleaseManifest(
        val updatedAt: String? = null,
        val owner: String? = null,
        val repo: String? = null,
        val releases: List<RawRelease> = emptyList(),
    )

    private data class RawNpmVersion(val version: String?, val publishedAt: String?)
    private data class RawNpmManifest(
        val updatedAt: String? = null,
        val packageName: String? = null,
        val latest: String? = null,
        val versions: List<RawNpmVersion> = emptyList(),
    )

    private data class RawAssetManifest(
        val updatedAt: String? = null,
        val tag: String? = null,
        val assets: List<String?> = emptyList(),
    )

    fun fetchReleaseTags(slug: String, baseUrl: String = DEFAULT_BASE_URL): List<String>? =
        runCatching { fetchBody("$baseUrl/$slug.json") }.getOrNull()?.let { parseReleaseTags(it) }

    fun fetchNpmVersions(slug: String, baseUrl: String = DEFAULT_BASE_URL): List<String>? =
        runCatching { fetchBody("$baseUrl/$slug.json") }.getOrNull()?.let { parseNpmVersions(it) }

    fun fetchAssetNames(slug: String, baseUrl: String = DEFAULT_BASE_URL): List<String>? =
        runCatching { fetchBody("$baseUrl/$slug.json") }.getOrNull()?.let { parseAssetNames(it) }

    internal fun parseReleaseTags(body: String): List<String> {
        val raw = gson.fromJson(body, RawReleaseManifest::class.java) ?: return emptyList()
        return raw.releases.mapNotNull { it.tag?.takeIf { tag -> tag.isNotBlank() } }
    }

    internal fun parseNpmVersions(body: String): List<String> {
        val raw = gson.fromJson(body, RawNpmManifest::class.java) ?: return emptyList()
        return raw.versions.mapNotNull { it.version?.takeIf { v -> v.isNotBlank() } }
    }

    internal fun parseAssetNames(body: String): List<String> {
        val raw = gson.fromJson(body, RawAssetManifest::class.java) ?: return emptyList()
        return raw.assets.mapNotNull { it?.takeIf { name -> name.isNotBlank() } }
    }

    private fun fetchBody(url: String): String {
        val conn = openConnection(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("manifest HTTP $code for $url")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 LspStaticManifest")
        return conn
    }
}
