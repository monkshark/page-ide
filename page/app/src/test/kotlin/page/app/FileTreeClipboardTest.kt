package page.app

import page.runtime.*

import java.awt.GraphicsEnvironment
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileTreeClipboardTest {

    private val headless: Boolean get() = GraphicsEnvironment.isHeadless()

    private fun newDir(): Path {
        val dir = Files.createTempDirectory("page-ide-clip-test-")
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        })
        return dir
    }

    @Test
    fun `writeCopy then read returns Copy mode and paths`() {
        if (headless) return
        val root = newDir()
        val a = Files.createFile(root.resolve("a.kt"))
        val b = Files.createFile(root.resolve("b.kt"))
        FileTreeClipboard.writeCopy(listOf(a, b))

        val content = FileTreeClipboard.read()
        assertNotNull(content)
        assertEquals(FileTreeClipboard.Mode.Copy, content.mode)
        assertEquals(listOf(a, b), content.paths)
        assertTrue(FileTreeClipboard.markedCutPaths.isEmpty())
        assertFalse(FileTreeClipboard.isCut(a))
    }

    @Test
    fun `writeCut marks paths as cut and read returns Cut mode`() {
        if (headless) return
        val root = newDir()
        val a = Files.createFile(root.resolve("a.kt"))
        FileTreeClipboard.writeCut(listOf(a))

        assertTrue(FileTreeClipboard.isCut(a))
        assertEquals(listOf(a), FileTreeClipboard.markedCutPaths)

        val content = FileTreeClipboard.read()
        assertNotNull(content)
        assertEquals(FileTreeClipboard.Mode.Cut, content.mode)
        assertEquals(listOf(a), content.paths)
    }

    @Test
    fun `writeCopy clears prior cut marking`() {
        if (headless) return
        val root = newDir()
        val a = Files.createFile(root.resolve("a.kt"))
        val b = Files.createFile(root.resolve("b.kt"))
        FileTreeClipboard.writeCut(listOf(a))
        assertTrue(FileTreeClipboard.isCut(a))

        FileTreeClipboard.writeCopy(listOf(b))
        assertFalse(FileTreeClipboard.isCut(a))
        assertFalse(FileTreeClipboard.isCut(b))
        assertTrue(FileTreeClipboard.markedCutPaths.isEmpty())
    }

    @Test
    fun `clearCutMarking resets cut state without touching copy state`() {
        if (headless) return
        val root = newDir()
        val a = Files.createFile(root.resolve("a.kt"))
        FileTreeClipboard.writeCut(listOf(a))
        FileTreeClipboard.clearCutMarking()
        assertFalse(FileTreeClipboard.isCut(a))
        assertTrue(FileTreeClipboard.markedCutPaths.isEmpty())

        FileTreeClipboard.writeCopy(listOf(a))
        FileTreeClipboard.clearCutMarking()
        val content = FileTreeClipboard.read()
        assertNotNull(content)
        assertEquals(FileTreeClipboard.Mode.Copy, content.mode)
    }
}
