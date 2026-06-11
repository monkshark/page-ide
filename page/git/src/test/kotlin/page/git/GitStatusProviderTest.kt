package page.git

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitStatusProviderTest {

    private val root = Path.of("ws")

    @Test
    fun cachesWithinTtlAndRefetchesAfter() {
        var calls = 0
        var now = 0L
        val provider = GitStatusProvider(
            root = root,
            ttlMs = 30_000,
            clock = { now },
            runGit = {
                calls++
                "?? a.txt" + 0.toChar()
            },
        )
        provider.statuses()
        provider.statuses()
        assertEquals(1, calls)
        now = 31_000
        provider.statuses()
        assertEquals(2, calls)
    }

    @Test
    fun refreshForcesRefetch() {
        var calls = 0
        val provider = GitStatusProvider(
            root = root,
            ttlMs = 30_000,
            clock = { 0L },
            runGit = {
                calls++
                "?? a.txt" + 0.toChar()
            },
        )
        provider.statuses()
        provider.refresh()
        provider.statuses()
        assertEquals(2, calls)
    }

    @Test
    fun commandFailureYieldsEmptyStatuses() {
        val provider = GitStatusProvider(
            root = root,
            ttlMs = 30_000,
            clock = { 0L },
            runGit = { null },
        )
        assertTrue(provider.statuses().isEmpty())
    }

    @Test
    fun statusesParseIntoAbsolutePaths() {
        val provider = GitStatusProvider(
            root = root,
            ttlMs = 30_000,
            clock = { 0L },
            runGit = { " M src/a.kt" + 0.toChar() },
        )
        val expected = root.resolve("src/a.kt").toAbsolutePath().normalize()
        assertEquals(GitStatusKind.MODIFIED, provider.statuses()[expected])
    }
}
