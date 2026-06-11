package page.git

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitStatusParserTest {

    private val root = Path.of("ws")

    private fun at(rel: String): Path = root.resolve(rel).toAbsolutePath().normalize()

    private fun joined(vararg entries: String): String =
        entries.joinToString(separator = 0.toChar().toString(), postfix = 0.toChar().toString())

    @Test
    fun classifiesEachStatusKind() {
        val statuses = parseGitStatus(
            joined(
                " M src/modified.kt",
                "M  src/staged.kt",
                "A  src/added.kt",
                " D src/deleted.kt",
                "?? notes.txt",
            ),
            root,
        )
        assertEquals(GitStatusKind.MODIFIED, statuses[at("src/modified.kt")])
        assertEquals(GitStatusKind.MODIFIED, statuses[at("src/staged.kt")])
        assertEquals(GitStatusKind.ADDED, statuses[at("src/added.kt")])
        assertEquals(GitStatusKind.DELETED, statuses[at("src/deleted.kt")])
        assertEquals(GitStatusKind.UNTRACKED, statuses[at("notes.txt")])
    }

    @Test
    fun renameConsumesOldPathToken() {
        val statuses = parseGitStatus(
            joined("R  src/renamed.kt", "src/old.kt", " M src/other.kt"),
            root,
        )
        assertEquals(GitStatusKind.RENAMED, statuses[at("src/renamed.kt")])
        assertFalse(at("src/old.kt") in statuses)
        assertEquals(GitStatusKind.MODIFIED, statuses[at("src/other.kt")])
    }

    @Test
    fun emptyOutputYieldsEmptyMap() {
        assertTrue(parseGitStatus("", root).isEmpty())
    }
}
