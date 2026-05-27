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

class AssetListCacheTest {

    private lateinit var tempHome: Path

    private fun useTempHome(): Path {
        tempHome = Files.createTempDirectory("page-asset-cache-test-")
        return tempHome
    }

    @AfterTest
    fun cleanup() {
        if (::tempHome.isInitialized) {
            runCatching {
                Files.walk(tempHome).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test
    fun loadReturnsNullWhenFileMissing() {
        val home = useTempHome()
        assertNull(AssetListCache.load("monkshark", "page-ide-assets", "metals-bundle", home))
    }

    @Test
    fun saveAndLoadRoundtripsAssetsAndEtag() {
        val home = useTempHome()
        val cached = AssetListCache.Cached(
            fetchedAt = 123L,
            etag = "\"abc\"",
            assets = listOf("page-scala-metals-linux-x86_64-1.6.7.tar.gz"),
        )
        AssetListCache.save("monkshark", "page-ide-assets", "metals-bundle", cached, home)
        val loaded = AssetListCache.load("monkshark", "page-ide-assets", "metals-bundle", home)
        assertNotNull(loaded)
        assertEquals(123L, loaded.fetchedAt)
        assertEquals("\"abc\"", loaded.etag)
        assertEquals(listOf("page-scala-metals-linux-x86_64-1.6.7.tar.gz"), loaded.assets)
    }

    @Test
    fun cacheFilePathIsKeyedByOwnerRepoTag() {
        val home = useTempHome()
        val a = AssetListCache.cacheFile("monkshark", "page-ide-assets", "metals-bundle", home)
        val b = AssetListCache.cacheFile("monkshark", "page-ide-assets", "ruby-bundle", home)
        val c = AssetListCache.cacheFile("monkshark", "other-repo", "metals-bundle", home)
        assertTrue(a != b, "tag should affect cache file path")
        assertTrue(a != c, "repo should affect cache file path")
    }

    @Test
    fun isFreshIsTrueWithinTtl() {
        val now = 1_000_000L
        val cached = AssetListCache.Cached(fetchedAt = now - 60_000L, etag = null, assets = emptyList())
        assertTrue(AssetListCache.isFresh(cached, now, ttlMs = 3 * 60_000L))
    }

    @Test
    fun isFreshIsFalseOutsideTtl() {
        val now = 1_000_000L
        val cached = AssetListCache.Cached(fetchedAt = now - 5 * 60_000L, etag = null, assets = emptyList())
        assertFalse(AssetListCache.isFresh(cached, now, ttlMs = 3 * 60_000L))
    }

    @Test
    fun isFreshIsFalseWhenCachedNull() {
        assertFalse(AssetListCache.isFresh(null, 0L, ttlMs = 1L))
    }
}
