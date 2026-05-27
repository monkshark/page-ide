package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LspReleasesCacheTest {

    private val tempDirs = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        for (dir in tempDirs) {
            if (Files.exists(dir)) {
                Files.walk(dir).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { p ->
                        runCatching { Files.delete(p) }
                    }
                }
            }
        }
    }

    private fun newHome(): Path {
        val p = Files.createTempDirectory("lsp-cache-home-")
        tempDirs.add(p)
        return p
    }

    @Test
    fun loadReturnsNullWhenCacheFileMissing() {
        val home = newHome()
        assertNull(LspReleasesCache.load(home))
    }

    @Test
    fun saveAndLoadRoundtripsAllFields() {
        val home = newHome()
        val cached = LspReleasesCache.Cached(
            fetchedAt = 1700000000000L,
            source = "manifest",
            etagFork = "fork-etag",
            etagUpstream = "upstream-etag",
            etagManifest = "manifest-etag",
            fork = listOf(LspReleasesCache.TaggedRelease("1.3.13-page-1", "2026-03-04T00:00:00Z")),
            upstream = listOf(LspReleasesCache.TaggedRelease("1.3.12", null)),
        )
        LspReleasesCache.save(cached, home)

        val loaded = LspReleasesCache.load(home)
        assertNotNull(loaded)
        assertEquals(cached.fetchedAt, loaded.fetchedAt)
        assertEquals("manifest", loaded.source)
        assertEquals("fork-etag", loaded.etagFork)
        assertEquals("upstream-etag", loaded.etagUpstream)
        assertEquals("manifest-etag", loaded.etagManifest)
        assertEquals(1, loaded.fork.size)
        assertEquals("1.3.13-page-1", loaded.fork[0].tag)
        assertEquals("2026-03-04T00:00:00Z", loaded.fork[0].publishedAt)
        assertEquals(1, loaded.upstream.size)
        assertEquals("1.3.12", loaded.upstream[0].tag)
        assertNull(loaded.upstream[0].publishedAt)
    }

    @Test
    fun isFreshWithinTtl() {
        val now = 1_000_000L
        val cached = LspReleasesCache.Cached(
            fetchedAt = now - 30_000L,
            source = "manifest",
            etagFork = null, etagUpstream = null, etagManifest = null,
            fork = emptyList(), upstream = emptyList(),
        )
        assertTrue(LspReleasesCache.isFresh(cached, now = now, ttlMs = 60_000L))
    }

    @Test
    fun isStaleAfterTtl() {
        val now = 1_000_000L
        val cached = LspReleasesCache.Cached(
            fetchedAt = now - 120_000L,
            source = "manifest",
            etagFork = null, etagUpstream = null, etagManifest = null,
            fork = emptyList(), upstream = emptyList(),
        )
        assertFalse(LspReleasesCache.isFresh(cached, now = now, ttlMs = 60_000L))
    }

    @Test
    fun isFreshFalseForNullCached() {
        assertFalse(LspReleasesCache.isFresh(null))
    }

    @Test
    fun saveCreatesParentDirectories() {
        val home = newHome()
        val cached = LspReleasesCache.Cached(
            fetchedAt = 0L, source = "test",
            etagFork = null, etagUpstream = null, etagManifest = null,
            fork = emptyList(), upstream = emptyList(),
        )
        LspReleasesCache.save(cached, home)
        assertTrue(Files.exists(LspReleasesCache.cacheFile(home)))
    }
}
