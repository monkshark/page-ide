package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SvgFormatterTest {

    @Test
    fun `single-line svg expands to indented multi-line`() {
        val input = """<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect x="0" y="0" width="10" height="10"/></svg>"""
        val result = SvgFormatter.prettyPrint(input)
        assertNotNull(result)
        assertTrue(result.contains("\n"), "expected newlines, got: $result")
        assertTrue(result.contains("<svg"))
        assertTrue(result.contains("<rect"))
        assertTrue(
            result.lines().any { it.startsWith("  <rect") },
            "expected child to be indented two spaces, got:\n$result",
        )
    }

    @Test
    fun `malformed xml returns null`() {
        val input = "<svg><rect></svg>"
        assertNull(SvgFormatter.prettyPrint(input))
    }

    @Test
    fun `blank input returns null`() {
        assertNull(SvgFormatter.prettyPrint(""))
        assertNull(SvgFormatter.prettyPrint("   \n  "))
    }

    @Test
    fun `prettyPrint is idempotent`() {
        val input = """<svg xmlns="http://www.w3.org/2000/svg"><g><rect/></g></svg>"""
        val once = SvgFormatter.prettyPrint(input)
        assertNotNull(once)
        val twice = SvgFormatter.prettyPrint(once)
        assertNull(twice, "expected null on second pass (already formatted), got:\n$twice")
    }

    @Test
    fun `text content is preserved`() {
        val input = """<svg xmlns="http://www.w3.org/2000/svg"><text>Hi there</text></svg>"""
        val result = SvgFormatter.prettyPrint(input)
        assertNotNull(result)
        assertTrue(result.contains("Hi there"))
    }

    @Test
    fun `nested groups indent further`() {
        val input = """<svg xmlns="http://www.w3.org/2000/svg"><g><g><rect/></g></g></svg>"""
        val result = SvgFormatter.prettyPrint(input)
        assertNotNull(result)
        val rectLine = result.lines().first { it.contains("<rect") }
        val leading = rectLine.takeWhile { it == ' ' }.length
        assertEquals(6, leading, "expected nested rect indented 6 spaces, got $leading in:\n$result")
    }
}
