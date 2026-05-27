package page.runtime

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object AssetListCache {

    data class Cached(
        val fetchedAt: Long,
        val etag: String?,
        val assets: List<String>,
    )

    const val DEFAULT_TTL_MS: Long = 60 * 60 * 1000L

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun userHome(): Path = Paths.get(System.getProperty("user.home") ?: ".")

    fun cacheFile(owner: String, repo: String, tag: String, home: Path = userHome()): Path {
        val key = sanitize("${owner}_${repo}_${tag}")
        return home.resolve(".page-ide").resolve("cache").resolve("lsp").resolve("assets").resolve("$key.json")
    }

    fun load(owner: String, repo: String, tag: String, home: Path = userHome()): Cached? {
        val file = cacheFile(owner, repo, tag, home)
        if (!Files.exists(file)) return null
        return runCatching {
            val json = Files.readString(file)
            gson.fromJson(json, Cached::class.java)
        }.getOrNull()
    }

    fun save(owner: String, repo: String, tag: String, cached: Cached, home: Path = userHome()) {
        val file = cacheFile(owner, repo, tag, home)
        Files.createDirectories(file.parent)
        Files.writeString(file, gson.toJson(cached))
    }

    fun isFresh(cached: Cached?, now: Long = System.currentTimeMillis(), ttlMs: Long = DEFAULT_TTL_MS): Boolean {
        if (cached == null) return false
        return (now - cached.fetchedAt) < ttlMs
    }

    private fun sanitize(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}
