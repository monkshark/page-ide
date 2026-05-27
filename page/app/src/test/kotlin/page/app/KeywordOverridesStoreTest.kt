package page.app

import page.runtime.*

import androidx.compose.ui.graphics.Color
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeywordOverridesStoreTest {

    private fun newWorkspace(): Path {
        val dir = Files.createTempDirectory("page-ide-test-")
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        })
        return dir
    }

    @Test
    fun `load on fresh workspace returns empty file`() {
        val ws = newWorkspace()
        val file = KeywordOverridesStore.load(ws)
        assertEquals(1, file.version)
        assertTrue(file.colors.isEmpty())
    }

    @Test
    fun `setColor then load round-trips`() {
        val ws = newWorkspace()
        KeywordOverridesStore.setColor(ws, "TODO", "#ABCDEF")
        val loaded = KeywordOverridesStore.load(ws)
        assertEquals("#ABCDEF", loaded.colors["TODO"])
    }

    @Test
    fun `setColor uppercases the keyword key`() {
        val ws = newWorkspace()
        KeywordOverridesStore.setColor(ws, "todo", "#123456")
        val loaded = KeywordOverridesStore.load(ws)
        assertEquals("#123456", loaded.colors["TODO"])
    }

    @Test
    fun `setColor creates page-ide dir and keywords json`() {
        val ws = newWorkspace()
        KeywordOverridesStore.setColor(ws, "FIXME", "#FF0000")
        assertTrue(Files.exists(ws.resolve(PageIdeStore.DIR_NAME)))
        assertTrue(Files.exists(ws.resolve(PageIdeStore.DIR_NAME).resolve(KeywordOverridesStore.FILE_NAME)))
    }

    @Test
    fun `removeColor deletes the entry`() {
        val ws = newWorkspace()
        KeywordOverridesStore.setColor(ws, "TODO", "#FF0000")
        KeywordOverridesStore.setColor(ws, "FIXME", "#00FF00")
        KeywordOverridesStore.removeColor(ws, "TODO")
        val loaded = KeywordOverridesStore.load(ws)
        assertTrue("TODO" !in loaded.colors)
        assertEquals("#00FF00", loaded.colors["FIXME"])
    }

    @Test
    fun `setColor overwrites existing color`() {
        val ws = newWorkspace()
        KeywordOverridesStore.setColor(ws, "TODO", "#FF0000")
        KeywordOverridesStore.setColor(ws, "TODO", "#00FF00")
        val loaded = KeywordOverridesStore.load(ws)
        assertEquals("#00FF00", loaded.colors["TODO"])
    }

    @Test
    fun `parseHexColor accepts 6-digit hex`() {
        val c = parseHexColor("#FF8800")
        assertNotNull(c)
        assertEquals(Color(0xFFFF8800.toInt()), c)
    }

    @Test
    fun `parseHexColor accepts 8-digit hex with alpha`() {
        val c = parseHexColor("80FF8800")
        assertNotNull(c)
        assertEquals(Color(0x80FF8800.toInt()), c)
    }

    @Test
    fun `parseHexColor accepts hex without leading hash`() {
        val c = parseHexColor("FF8800")
        assertNotNull(c)
        assertEquals(Color(0xFFFF8800.toInt()), c)
    }

    @Test
    fun `parseHexColor rejects invalid length`() {
        assertNull(parseHexColor("#FF88"))
        assertNull(parseHexColor("#FF888"))
    }

    @Test
    fun `parseHexColor rejects non-hex characters`() {
        assertNull(parseHexColor("#GGHHII"))
    }
}
