package page.lsp

import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefinitionTest {

    @Test
    fun `null either yields empty list`() {
        assertTrue(DefinitionTarget.fromLsp(null).isEmpty())
    }

    @Test
    fun `Location list maps to targets`() {
        val loc = Location("file:///A.kt", Range(Position(3, 4), Position(3, 9)))
        val either: Either<MutableList<out Location>, MutableList<out LocationLink>> =
            Either.forLeft(mutableListOf(loc))

        val targets = DefinitionTarget.fromLsp(either)
        assertEquals(1, targets.size)
        val t = targets.first()
        assertEquals("file:///A.kt", t.uri)
        assertEquals(3, t.startLine)
        assertEquals(4, t.startCharacter)
        assertEquals(3, t.endLine)
        assertEquals(9, t.endCharacter)
    }

    @Test
    fun `LocationLink prefers targetSelectionRange`() {
        val link = LocationLink().apply {
            targetUri = "file:///B.kt"
            targetRange = Range(Position(10, 0), Position(14, 1))
            targetSelectionRange = Range(Position(10, 4), Position(10, 7))
        }
        val either: Either<MutableList<out Location>, MutableList<out LocationLink>> =
            Either.forRight(mutableListOf(link))

        val targets = DefinitionTarget.fromLsp(either)
        assertEquals(1, targets.size)
        val t = targets.first()
        assertEquals("file:///B.kt", t.uri)
        assertEquals(10, t.startLine)
        assertEquals(4, t.startCharacter)
        assertEquals(10, t.endLine)
        assertEquals(7, t.endCharacter)
    }

    @Test
    fun `LocationLink falls back to targetRange when selection is missing`() {
        val link = LocationLink().apply {
            targetUri = "file:///C.kt"
            targetRange = Range(Position(0, 0), Position(2, 3))
        }
        val either: Either<MutableList<out Location>, MutableList<out LocationLink>> =
            Either.forRight(mutableListOf(link))

        val targets = DefinitionTarget.fromLsp(either)
        assertEquals(1, targets.size)
        val t = targets.first()
        assertEquals("file:///C.kt", t.uri)
        assertEquals(0, t.startLine)
        assertEquals(2, t.endLine)
        assertEquals(3, t.endCharacter)
    }

    @Test
    fun `Location without uri is skipped`() {
        val loc = Location().apply { range = Range(Position(0, 0), Position(0, 1)) }
        val either: Either<MutableList<out Location>, MutableList<out LocationLink>> =
            Either.forLeft(mutableListOf(loc))
        assertTrue(DefinitionTarget.fromLsp(either).isEmpty())
    }
}
