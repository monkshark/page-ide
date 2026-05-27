package page.runtime

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object LspReleasesCache {

    data class TaggedRelease(val tag: String, val publishedAt: String?)

    data class Cached(
        val fetchedAt: Long,
        val source: String,
        val etagFork: String?,
        val etagUpstream: String?,
        val etagManifest: String?,
        val fork: List<TaggedRelease>,
        val upstream: List<TaggedRelease>,
    )

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    const val DEFAULT_TTL_MS: Long = 60 * 60 * 1000L

    fun userHome(): Path = Paths.get(System.getProperty("user.home") ?: ".")

    fun cacheFile(home: Path = userHome()): Path =
        home.resolve(".page-ide").resolve("cache").resolve("lsp").resolve("kotlin-releases.json")

    fun load(home: Path = userHome()): Cached? {
        val file = cacheFile(home)
        if (!Files.exists(file)) return null
        return runCatching {
            val json = Files.readString(file)
            gson.fromJson(json, Cached::class.java)
        }.getOrNull()
    }

    fun save(cached: Cached, home: Path = userHome()) {
        val file = cacheFile(home)
        Files.createDirectories(file.parent)
        Files.writeString(file, gson.toJson(cached))
    }

    fun isFresh(cached: Cached?, now: Long = System.currentTimeMillis(), ttlMs: Long = DEFAULT_TTL_MS): Boolean {
        if (cached == null) return false
        return (now - cached.fetchedAt) < ttlMs
    }
}
