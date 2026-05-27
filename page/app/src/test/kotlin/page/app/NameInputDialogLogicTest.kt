package page.app

import page.runtime.*

import kotlin.test.Test
import kotlin.test.assertEquals

class NameInputDialogLogicTest {

    @Test
    fun `selection excludes extension for typical Kotlin file`() {
        val v = initialNameFieldValue("Main.kt")
        assertEquals("Main.kt", v.text)
        assertEquals(0, v.selection.start)
        assertEquals(4, v.selection.end)
    }

    @Test
    fun `selection covers full name when there is no extension`() {
        val v = initialNameFieldValue("README")
        assertEquals(0, v.selection.start)
        assertEquals(6, v.selection.end)
    }

    @Test
    fun `selection covers full name for dotfiles like gitignore`() {
        val v = initialNameFieldValue(".gitignore")
        assertEquals(0, v.selection.start)
        assertEquals(10, v.selection.end)
    }

    @Test
    fun `selection uses last dot for multi-extension archives`() {
        val v = initialNameFieldValue("archive.tar.gz")
        assertEquals(0, v.selection.start)
        assertEquals(11, v.selection.end)
        assertEquals("archive.tar", v.text.substring(v.selection.start, v.selection.end))
    }

    @Test
    fun `selection is empty for empty initial name`() {
        val v = initialNameFieldValue("")
        assertEquals("", v.text)
        assertEquals(0, v.selection.start)
        assertEquals(0, v.selection.end)
    }

    @Test
    fun `selection picks last dot for a-b-c pattern`() {
        val v = initialNameFieldValue("a.b.c")
        assertEquals(0, v.selection.start)
        assertEquals(3, v.selection.end)
        assertEquals("a.b", v.text.substring(v.selection.start, v.selection.end))
    }

    @Test
    fun `selection covers full name when dot is at start only`() {
        val v = initialNameFieldValue(".env")
        assertEquals(0, v.selection.start)
        assertEquals(4, v.selection.end)
    }

    @Test
    fun `selection stops before extension for single-char stem`() {
        val v = initialNameFieldValue("a.kt")
        assertEquals(0, v.selection.start)
        assertEquals(1, v.selection.end)
    }
}
