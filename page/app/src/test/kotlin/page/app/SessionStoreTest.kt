package page.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStoreTest {

    private fun newWorkspace(): Path {
        val dir = Files.createTempDirectory("page-ide-session-test-")
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        })
        return dir
    }

    @Test
    fun `load on fresh workspace returns null`() {
        val ws = newWorkspace()
        assertNull(SessionStore.load(ws))
    }

    @Test
    fun `save then load round-trips`() {
        val ws = newWorkspace()
        val file = SessionFile(
            primary = SessionPane(
                tabs = listOf(SessionTabState("a.kt", 4), SessionTabState("b.kt", 10)),
                activeIndex = 1,
            ),
            secondary = SessionPane(),
            focusedPane = "PRIMARY",
            splitEnabled = true,
            splitOrientation = "VERTICAL",
            splitRatio = 0.4f,
            sidebarWidth = 300f,
            problemsOpen = true,
            problemsHeight = 250f,
            todoOpen = false,
            todoHeight = 220f,
        )
        SessionStore.save(ws, file)
        val loaded = SessionStore.load(ws)
        assertNotNull(loaded)
        assertEquals(2, loaded.primary.tabs.size)
        assertEquals(1, loaded.primary.activeIndex)
        assertEquals("b.kt", loaded.primary.tabs[1].path)
        assertEquals(10, loaded.primary.tabs[1].caret)
        assertEquals("VERTICAL", loaded.splitOrientation)
        assertEquals(0.4f, loaded.splitRatio)
        assertEquals(300f, loaded.sidebarWidth)
        assertEquals(true, loaded.problemsOpen)
    }

    @Test
    fun `save creates page-ide dir and session file`() {
        val ws = newWorkspace()
        SessionStore.save(ws, SessionFile())
        assertTrue(Files.exists(ws.resolve(PageIdeStore.DIR_NAME)))
        assertTrue(Files.exists(ws.resolve(PageIdeStore.DIR_NAME).resolve(SessionStore.FILE_NAME)))
    }

    @Test
    fun `restoreTabBook drops nonexistent paths`() {
        val pane = SessionPane(
            tabs = listOf(
                SessionTabState("Z:/no-such-path-xyz/missing.kt", 0),
            ),
            activeIndex = 0,
        )
        val book = restoreTabBook(pane)
        assertTrue(book.tabs.isEmpty())
        assertEquals(-1, book.activeIndex)
    }

    @Test
    fun `restoreTabBook loads existing files with carets coerced`() {
        val ws = newWorkspace()
        val a = ws.resolve("a.txt")
        val b = ws.resolve("b.txt")
        Files.writeString(a, "hello")
        Files.writeString(b, "world wide")
        val pane = SessionPane(
            tabs = listOf(
                SessionTabState(a.toString(), 3),
                SessionTabState(b.toString(), 999),
            ),
            activeIndex = 1,
        )
        val book = restoreTabBook(pane)
        assertEquals(2, book.tabs.size)
        assertEquals(3, book.tabs[0].caret)
        assertEquals(10, book.tabs[1].caret)
        assertEquals(1, book.activeIndex)
    }

    @Test
    fun `paneSnapshot serializes paths as strings`() {
        val ws = newWorkspace()
        val a = ws.resolve("a.txt")
        Files.writeString(a, "x")
        val book = restoreTabBook(
            SessionPane(tabs = listOf(SessionTabState(a.toString(), 0)), activeIndex = 0),
        )
        val pane = EditorPaneState(book = book)
        val snap = paneSnapshot(pane)
        assertEquals(1, snap.tabs.size)
        assertEquals(a.toString(), snap.tabs[0].path)
        assertEquals(0, snap.activeIndex)
    }

    @Test
    fun `empty pane snapshot has activeIndex -1`() {
        val snap = paneSnapshot(EditorPaneState())
        assertTrue(snap.tabs.isEmpty())
        assertEquals(-1, snap.activeIndex)
    }

    @Test
    fun `foldedStartLinesByPath round-trips`() {
        val ws = newWorkspace()
        val file = SessionFile(
            foldedStartLinesByPath = mapOf(
                "X:/a.kt" to listOf(3, 7, 12),
                "X:/b.kt" to listOf(0),
            ),
        )
        SessionStore.save(ws, file)
        val loaded = SessionStore.load(ws)
        assertNotNull(loaded)
        assertEquals(listOf(3, 7, 12), loaded.foldedStartLinesByPath["X:/a.kt"])
        assertEquals(listOf(0), loaded.foldedStartLinesByPath["X:/b.kt"])
    }

    @Test
    fun `expandedDirs round-trips`() {
        val ws = newWorkspace()
        val file = SessionFile(expandedDirs = listOf("X:/proj", "X:/proj/src"))
        SessionStore.save(ws, file)
        val loaded = SessionStore.load(ws)
        assertNotNull(loaded)
        assertEquals(listOf("X:/proj", "X:/proj/src"), loaded.expandedDirs)
    }

    @Test
    fun `restoreExpandedDirs keeps existing directories`() {
        val ws = newWorkspace()
        val sub = Files.createDirectory(ws.resolve("sub"))
        val result = restoreExpandedDirs(listOf(ws.toString(), sub.toString()))
        assertTrue(ws in result)
        assertTrue(sub in result)
    }

    @Test
    fun `restoreExpandedDirs drops non-directories and missing paths`() {
        val ws = newWorkspace()
        val file = Files.createFile(ws.resolve("a.txt"))
        val missing = ws.resolve("ghost")
        val result = restoreExpandedDirs(listOf(file.toString(), missing.toString()))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `restoreExpandedDirs returns empty for empty snapshot`() {
        assertTrue(restoreExpandedDirs(emptyList()).isEmpty())
    }

    @Test
    fun `problems and todo persistence fields round-trip`() {
        val ws = newWorkspace()
        val file = SessionFile(
            problemsCollapsed = listOf("X:/a.kt", "X:/b.kt"),
            problemsFileOrder = listOf("X:/b.kt", "X:/a.kt"),
            todoCollapsed = listOf("X:/c.kt"),
            todoFileOrder = listOf("X:/c.kt", "X:/a.kt"),
        )
        SessionStore.save(ws, file)
        val loaded = SessionStore.load(ws)
        assertNotNull(loaded)
        assertEquals(listOf("X:/a.kt", "X:/b.kt"), loaded.problemsCollapsed)
        assertEquals(listOf("X:/b.kt", "X:/a.kt"), loaded.problemsFileOrder)
        assertEquals(listOf("X:/c.kt"), loaded.todoCollapsed)
        assertEquals(listOf("X:/c.kt", "X:/a.kt"), loaded.todoFileOrder)
    }

    @Test
    fun `legacy session without new fields loads with empty defaults`() {
        val ws = newWorkspace()
        SessionStore.save(ws, SessionFile())
        val loaded = SessionStore.load(ws)
        assertNotNull(loaded)
        assertTrue(loaded.problemsCollapsed.isEmpty())
        assertTrue(loaded.problemsFileOrder.isEmpty())
        assertTrue(loaded.todoCollapsed.isEmpty())
        assertTrue(loaded.todoFileOrder.isEmpty())
    }
}
