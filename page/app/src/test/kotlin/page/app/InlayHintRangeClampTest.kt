package page.app

import kotlin.test.Test
import kotlin.test.assertEquals

class InlayHintRangeClampTest {

    private val threeLines = "fn main() {\n    let x = 1;\n}\n"

    @Test
    fun `range within document is unchanged`() {
        val r = clampInlayHintRange(threeLines, 0, 0, 2, 1)
        assertEquals(ClampedInlayRange(0, 0, 2, 1), r)
    }

    @Test
    fun `end line beyond document is clamped to last line`() {
        val r = clampInlayHintRange(threeLines, 0, 0, 35, 0)
        assertEquals(0, r.startLine)
        assertEquals(3, r.endLine)
        assertEquals(threeLines.split('\n')[3].length, r.endCharacter)
    }

    @Test
    fun `start line is clamped to not exceed end line`() {
        val r = clampInlayHintRange("a\nb\n", 99, 4, 99, 4)
        assertEquals(2, r.startLine)
        assertEquals(2, r.endLine)
    }

    @Test
    fun `single line document clamps everything to line zero`() {
        val r = clampInlayHintRange("only one line no newline", 5, 2, 9, 7)
        assertEquals(0, r.startLine)
        assertEquals(0, r.endLine)
        assertEquals("only one line no newline".length, r.endCharacter)
    }

    @Test
    fun `empty document clamps to zero range`() {
        val r = clampInlayHintRange("", 3, 0, 8, 0)
        assertEquals(ClampedInlayRange(0, 0, 0, 0), r)
    }
}
