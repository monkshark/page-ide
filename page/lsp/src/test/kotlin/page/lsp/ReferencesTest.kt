package page.lsp

import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReferencesTest {

    @Test
    fun `fromLsp returns empty for null input`() {
        assertTrue(ReferenceLocation.fromLsp(null).isEmpty())
    }

    @Test
    fun `fromLsp maps locations preserving range`() {
        val locs = listOf(
            Location("file:///A.kt", Range(Position(1, 2), Position(1, 5))),
            Location("file:///B.kt", Range(Position(10, 0), Position(11, 8))),
        )
        val refs = ReferenceLocation.fromLsp(locs)
        assertEquals(2, refs.size)
        assertEquals("file:///A.kt", refs[0].uri)
        assertEquals(1, refs[0].startLine)
        assertEquals(2, refs[0].startCharacter)
        assertEquals(5, refs[0].endCharacter)
        assertEquals("file:///B.kt", refs[1].uri)
        assertEquals(11, refs[1].endLine)
        assertEquals(8, refs[1].endCharacter)
    }

    @Test
    fun `fromLsp drops null entries and entries missing uri or range`() {
        val withMissingRange = Location().apply { uri = "file:///X.kt" }
        val withMissingUri = Location().apply { range = Range(Position(0, 0), Position(0, 1)) }
        val good = Location("file:///G.kt", Range(Position(0, 0), Position(0, 3)))
        val refs = ReferenceLocation.fromLsp(listOf(null, withMissingRange, withMissingUri, good))
        assertEquals(1, refs.size)
        assertEquals("file:///G.kt", refs[0].uri)
    }
}
