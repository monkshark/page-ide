package page.lsp

import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    @Test
    fun `pickSingleOtherReference returns null when result count differs from two`() {
        val a = ReferenceLocation("file:///A.kt", 0, 0, 0, 3)
        val b = ReferenceLocation("file:///A.kt", 5, 0, 5, 3)
        val c = ReferenceLocation("file:///A.kt", 10, 0, 10, 3)
        assertNull(pickSingleOtherReference(emptyList(), "file:///A.kt", 0, 1))
        assertNull(pickSingleOtherReference(listOf(a), "file:///A.kt", 0, 1))
        assertNull(pickSingleOtherReference(listOf(a, b, c), "file:///A.kt", 0, 1))
    }

    @Test
    fun `pickSingleOtherReference returns the other when caret hits the declaration`() {
        val decl = ReferenceLocation("file:///A.kt", 4, 4, 4, 7)
        val usage = ReferenceLocation("file:///A.kt", 10, 8, 10, 11)
        val picked = pickSingleOtherReference(listOf(decl, usage), "file:///A.kt", 4, 5)
        assertEquals(usage, picked)
    }

    @Test
    fun `pickSingleOtherReference returns the other when caret hits the usage`() {
        val decl = ReferenceLocation("file:///A.kt", 4, 4, 4, 7)
        val usage = ReferenceLocation("file:///A.kt", 10, 8, 10, 11)
        val picked = pickSingleOtherReference(listOf(decl, usage), "file:///A.kt", 10, 10)
        assertEquals(decl, picked)
    }

    @Test
    fun `pickSingleOtherReference matches caret at exact range bounds`() {
        val a = ReferenceLocation("file:///A.kt", 4, 4, 4, 7)
        val b = ReferenceLocation("file:///A.kt", 10, 8, 10, 11)
        assertEquals(b, pickSingleOtherReference(listOf(a, b), "file:///A.kt", 4, 4))
        assertEquals(b, pickSingleOtherReference(listOf(a, b), "file:///A.kt", 4, 7))
    }

    @Test
    fun `pickSingleOtherReference ignores results in other files when matching caret`() {
        val sameFile = ReferenceLocation("file:///A.kt", 4, 4, 4, 7)
        val otherFile = ReferenceLocation("file:///B.kt", 4, 4, 4, 7)
        val picked = pickSingleOtherReference(listOf(sameFile, otherFile), "file:///A.kt", 4, 5)
        assertEquals(otherFile, picked)
    }

    @Test
    fun `pickSingleOtherReference returns null when caret is in neither range`() {
        val a = ReferenceLocation("file:///A.kt", 4, 4, 4, 7)
        val b = ReferenceLocation("file:///A.kt", 10, 8, 10, 11)
        assertNull(pickSingleOtherReference(listOf(a, b), "file:///A.kt", 99, 0))
    }

    @Test
    fun `contains handles multi-line ranges`() {
        val multi = ReferenceLocation("file:///A.kt", 4, 4, 6, 2)
        assertTrue(multi.contains("file:///A.kt", 4, 4))
        assertTrue(multi.contains("file:///A.kt", 5, 100))
        assertTrue(multi.contains("file:///A.kt", 6, 2))
        assertEquals(false, multi.contains("file:///A.kt", 4, 3))
        assertEquals(false, multi.contains("file:///A.kt", 6, 3))
        assertEquals(false, multi.contains("file:///B.kt", 5, 0))
    }
}
