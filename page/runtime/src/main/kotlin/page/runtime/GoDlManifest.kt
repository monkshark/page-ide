package page.runtime

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

object GoDlManifest {

    const val DEFAULT_URL: String = "https://go.dev/dl/?mode=json&include=all"

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    private data class RawEntry(val version: String?, val stable: Boolean = false)

    fun fetchVersions(url: String = DEFAULT_URL, stableOnly: Boolean = true): List<String> = runCatching {
        parseVersions(fetchBody(url), stableOnly)
    }.getOrDefault(emptyList())

    internal fun parseVersions(body: String, stableOnly: Boolean): List<String> {
        val raw = gson.fromJson(body, Array<RawEntry>::class.java) ?: return emptyList()
        return raw.asSequence()
            .filter { it.version?.isNotBlank() == true }
            .filter { !stableOnly || it.stable }
            .mapNotNull { it.version?.removePrefix("go") }
            .toList()
    }

    private fun fetchBody(url: String): String {
        val conn = openConnection(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("go.dev/dl HTTP $code")
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
        conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 GoDlManifest")
        return conn
    }
}
