package page.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class GitHubReleaseInstallerTest {

    private fun descriptor(): GitHubReleaseDescriptor = GitHubReleaseDescriptor(
        languageId = "rust",
        displayName = "rust-analyzer",
        owner = "rust-lang",
        repo = "rust-analyzer",
        perOs = mapOf(
            "linux" to OsAsset(
                url = "https://example.com/{tag}/rust-analyzer.gz",
                executableRelative = "rust-analyzer",
                archiveType = ArchiveType.GZ_BINARY,
            ),
        ),
    )

    @Test
    fun availableVersionsPrefersManifestOverApi() {
        val installer = GitHubReleaseInstaller(
            descriptor(),
            manifestFetcher = { slug ->
                assertEquals("rust", slug)
                listOf("2026-05-19", "2026-05-12", "2026-05-05")
            },
            apiFetcher = { _, _ -> fail("API should not be called when manifest hits") },
        )
        assertEquals(
            listOf("2026-05-19", "2026-05-12", "2026-05-05"),
            installer.availableVersions(),
        )
    }

    @Test
    fun availableVersionsFallsBackToApiWhenManifestNull() {
        val apiCalls = mutableListOf<Pair<String, String>>()
        val installer = GitHubReleaseInstaller(
            descriptor(),
            manifestFetcher = { null },
            apiFetcher = { owner, repo ->
                apiCalls += owner to repo
                listOf("2026-04-01")
            },
        )
        assertEquals(listOf("2026-04-01"), installer.availableVersions())
        assertEquals(listOf("rust-lang" to "rust-analyzer"), apiCalls)
    }

    @Test
    fun availableVersionsFallsBackToApiWhenManifestEmpty() {
        val installer = GitHubReleaseInstaller(
            descriptor(),
            manifestFetcher = { emptyList() },
            apiFetcher = { _, _ -> listOf("2026-04-01") },
        )
        assertEquals(listOf("2026-04-01"), installer.availableVersions())
    }

    @Test
    fun availableVersionsReturnsEmptyWhenBothFail() {
        val installer = GitHubReleaseInstaller(
            descriptor(),
            manifestFetcher = { null },
            apiFetcher = { _, _ -> throw RuntimeException("HTTP 403 rate limit") },
        )
        assertTrue(installer.availableVersions().isEmpty())
    }
}
