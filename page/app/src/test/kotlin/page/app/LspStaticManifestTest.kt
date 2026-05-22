package page.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LspStaticManifestTest {

    @Test
    fun parseReleaseTagsExtractsOrderedTags() {
        val body = """
            {
              "updatedAt": "2026-05-22T03:30:00Z",
              "owner": "rust-lang",
              "repo": "rust-analyzer",
              "releases": [
                {"tag": "2026-05-19", "publishedAt": "2026-05-19T00:00:00Z"},
                {"tag": "2026-05-12", "publishedAt": "2026-05-12T00:00:00Z"}
              ]
            }
        """.trimIndent()
        assertEquals(listOf("2026-05-19", "2026-05-12"), LspStaticManifest.parseReleaseTags(body))
    }

    @Test
    fun parseReleaseTagsSkipsBlankOrMissingTags() {
        val body = """
            {
              "releases": [
                {"tag": "v1.0.0"},
                {"tag": ""},
                {"publishedAt": "2026-05-12T00:00:00Z"}
              ]
            }
        """.trimIndent()
        assertEquals(listOf("v1.0.0"), LspStaticManifest.parseReleaseTags(body))
    }

    @Test
    fun parseReleaseTagsHandlesEmptyList() {
        assertTrue(LspStaticManifest.parseReleaseTags("""{"releases": []}""").isEmpty())
    }

    @Test
    fun parseNpmVersionsExtractsOrderedVersions() {
        val body = """
            {
              "updatedAt": "2026-05-22T04:00:00Z",
              "packageName": "typescript-language-server",
              "latest": "4.4.0",
              "versions": [
                {"version": "4.4.0", "publishedAt": "2026-04-01T00:00:00Z"},
                {"version": "4.3.4", "publishedAt": "2026-03-15T00:00:00Z"}
              ]
            }
        """.trimIndent()
        assertEquals(listOf("4.4.0", "4.3.4"), LspStaticManifest.parseNpmVersions(body))
    }

    @Test
    fun parseNpmVersionsSkipsBlankOrMissing() {
        val body = """
            {
              "versions": [
                {"version": "1.0.0"},
                {"version": null},
                {"publishedAt": "2026-04-01T00:00:00Z"}
              ]
            }
        """.trimIndent()
        assertEquals(listOf("1.0.0"), LspStaticManifest.parseNpmVersions(body))
    }

    @Test
    fun parseHandlesMissingFields() {
        assertTrue(LspStaticManifest.parseReleaseTags("{}").isEmpty())
        assertTrue(LspStaticManifest.parseNpmVersions("{}").isEmpty())
    }
}
