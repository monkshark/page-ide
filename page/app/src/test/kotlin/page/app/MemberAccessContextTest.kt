package page.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemberAccessContextTest {

    @Test
    fun `prefix right after a dot is member access`() {
        val text = "        list.stream().f"
        assertTrue(isMemberAccessContext(text, 0, text.length, 1))
    }

    @Test
    fun `empty prefix right after a dot is member access`() {
        val text = "        list.stream()."
        assertTrue(isMemberAccessContext(text, 0, text.length, 0))
    }

    @Test
    fun `word at start of statement is not member access`() {
        val text = "        pub"
        assertFalse(isMemberAccessContext(text, 0, text.length, 3))
    }

    @Test
    fun `word preceded by whitespace is not member access`() {
        val text = "foo bar"
        assertFalse(isMemberAccessContext(text, 0, text.length, 3))
    }

    @Test
    fun `word at column zero is not member access`() {
        val text = "abc"
        assertFalse(isMemberAccessContext(text, 0, text.length, 3))
    }

    @Test
    fun `member access is detected on the active line of a multiline buffer`() {
        val text = "package demo\nobj.m"
        assertTrue(isMemberAccessContext(text, 1, 5, 1))
    }

    @Test
    fun `out of range line is not member access`() {
        assertFalse(isMemberAccessContext("a.b", 5, 3, 1))
    }
}
