package page.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoDlManifestTest {

    @Test
    fun parseVersionsStripsGoPrefixAndPreservesOrder() {
        val body = """
            [
              {"version": "go1.22.5", "stable": true},
              {"version": "go1.22.4", "stable": true},
              {"version": "go1.21.13", "stable": true}
            ]
        """.trimIndent()
        assertEquals(listOf("1.22.5", "1.22.4", "1.21.13"), GoDlManifest.parseVersions(body, stableOnly = true))
    }

    @Test
    fun parseVersionsFiltersUnstableWhenRequested() {
        val body = """
            [
              {"version": "go1.23rc1", "stable": false},
              {"version": "go1.22.5", "stable": true}
            ]
        """.trimIndent()
        assertEquals(listOf("1.22.5"), GoDlManifest.parseVersions(body, stableOnly = true))
    }

    @Test
    fun parseVersionsIncludesUnstableWhenAllowed() {
        val body = """
            [
              {"version": "go1.23rc1", "stable": false},
              {"version": "go1.22.5", "stable": true}
            ]
        """.trimIndent()
        assertEquals(listOf("1.23rc1", "1.22.5"), GoDlManifest.parseVersions(body, stableOnly = false))
    }

    @Test
    fun parseVersionsHandlesEmptyArray() {
        assertTrue(GoDlManifest.parseVersions("[]", stableOnly = true).isEmpty())
    }

    @Test
    fun parseVersionsSkipsBlankOrMissing() {
        val body = """
            [
              {"version": "", "stable": true},
              {"stable": true},
              {"version": "go1.22.0", "stable": true}
            ]
        """.trimIndent()
        assertEquals(listOf("1.22.0"), GoDlManifest.parseVersions(body, stableOnly = true))
    }
}
