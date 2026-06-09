package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BracketMatchTest {
    @Test
    fun `caret right after opener matches forward`() {
        assertEquals(0 to 1, BracketMatch.find("()", 1))
    }

    @Test
    fun `caret right before opener matches forward`() {
        assertEquals(0 to 1, BracketMatch.find("()", 0))
    }

    @Test
    fun `caret right after closer matches backward`() {
        assertEquals(0 to 1, BracketMatch.find("()", 2))
    }

    @Test
    fun `nested parens — outer pair from caret after outer opener`() {
        assertEquals(0 to 3, BracketMatch.find("(())", 1))
    }

    @Test
    fun `nested parens — inner pair from caret after inner opener`() {
        assertEquals(1 to 2, BracketMatch.find("(())", 2))
    }

    @Test
    fun `nested parens — inner pair from caret after inner closer`() {
        assertEquals(1 to 2, BracketMatch.find("(())", 3))
    }

    @Test
    fun `square brackets`() {
        assertEquals(0 to 1, BracketMatch.find("[]", 1))
    }

    @Test
    fun `curly braces`() {
        assertEquals(0 to 1, BracketMatch.find("{}", 1))
    }

    @Test
    fun `mixed brackets — outer square contains parens`() {
        assertEquals(0 to 3, BracketMatch.find("[()]", 1))
    }

    @Test
    fun `unmatched opener returns null`() {
        assertNull(BracketMatch.find("(abc", 1))
    }

    @Test
    fun `unmatched closer returns null`() {
        assertNull(BracketMatch.find("abc)", 4))
    }

    @Test
    fun `caret not adjacent to bracket returns null`() {
        assertNull(BracketMatch.find("foo bar", 4))
    }

    @Test
    fun `caret at zero of empty text returns null`() {
        assertNull(BracketMatch.find("", 0))
    }

    @Test
    fun `caret at end past last char returns null when no bracket`() {
        assertNull(BracketMatch.find("abc", 3))
    }

    @Test
    fun `bracket left of caret takes priority over bracket at caret`() {
        assertEquals(0 to 1, BracketMatch.find("()", 1))
    }

    @Test
    fun `caret between two brackets prioritizes left`() {
        assertEquals(0 to 1, BracketMatch.find("(){}", 2))
    }

    @Test
    fun `deep nesting`() {
        assertEquals(0 to 5, BracketMatch.find("((()))", 1))
    }

    @Test
    fun `caret after deeply nested closer matches its opener`() {
        assertEquals(2 to 3, BracketMatch.find("((()))", 4))
    }

    @Test
    fun `mismatched types do not pair`() {
        assertNull(BracketMatch.find("(]", 1))
    }
}
