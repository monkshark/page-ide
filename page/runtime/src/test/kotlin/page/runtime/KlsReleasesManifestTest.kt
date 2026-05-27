package page.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KlsReleasesManifestTest {

    @Test
    fun parsesForkAndUpstreamLists() {
        val json = """
        {
          "updatedAt": "2026-05-21T03:00:00Z",
          "fork": [
            { "tag": "1.3.13-page-1", "publishedAt": "2026-03-04T00:00:00Z" },
            { "tag": "1.3.12-page-1", "publishedAt": null }
          ],
          "upstream": [
            { "tag": "1.3.12", "publishedAt": "2025-09-10T00:00:00Z" }
          ]
        }
        """.trimIndent()
        val parsed = KlsReleasesManifest.parseBody(json, etag = "abc")
        assertEquals("abc", parsed.etag)
        assertEquals(2, parsed.fork.size)
        assertEquals("1.3.13-page-1", parsed.fork[0].tag)
        assertEquals("2026-03-04T00:00:00Z", parsed.fork[0].publishedAt)
        assertEquals("1.3.12-page-1", parsed.fork[1].tag)
        assertNull(parsed.fork[1].publishedAt)
        assertEquals(1, parsed.upstream.size)
        assertEquals("1.3.12", parsed.upstream[0].tag)
    }

    @Test
    fun emptyManifestParsesToEmptyLists() {
        val json = """{ "updatedAt": null, "fork": [], "upstream": [] }"""
        val parsed = KlsReleasesManifest.parseBody(json)
        assertTrue(parsed.fork.isEmpty())
        assertTrue(parsed.upstream.isEmpty())
    }

    @Test
    fun entriesWithoutTagAreDropped() {
        val json = """
        {
          "fork": [
            { "tag": "1.3.13-page-1" },
            { "publishedAt": "2026-01-01T00:00:00Z" }
          ],
          "upstream": []
        }
        """.trimIndent()
        val parsed = KlsReleasesManifest.parseBody(json)
        assertEquals(1, parsed.fork.size)
        assertEquals("1.3.13-page-1", parsed.fork[0].tag)
    }
}
