package page.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiagnosticUnderlineRangeTest {

    @Test
    fun `non-empty range is preserved`() {
        assertEquals(2 to 5, diagnosticUnderlineRange("let x = 1;", 2, 5))
    }

    @Test
    fun `zero-width range widens one char to the right`() {
        assertEquals(3 to 4, diagnosticUnderlineRange("abc def", 3, 3))
    }

    @Test
    fun `zero-width range at line end widens one char to the left`() {
        val text = "abc\ndef"
        assertEquals(2 to 3, diagnosticUnderlineRange(text, 3, 3))
    }

    @Test
    fun `zero-width range on empty line cannot widen`() {
        assertNull(diagnosticUnderlineRange("\n", 0, 0))
    }

    @Test
    fun `negative offset is rejected`() {
        assertNull(diagnosticUnderlineRange("abc", -1, 2))
    }
}
