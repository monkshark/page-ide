package page.app.ui.editor

import page.app.EditorPaneState
import page.app.PaneSide
import page.app.PendingClose
import page.editor.OpenTab
import page.editor.TabBook
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class EditorTabControllerTest {

    private fun tab(path: String, dirty: Boolean = false): OpenTab {
        val p = Paths.get(path)
        return OpenTab(path = p, text = "x", savedText = if (dirty) "y" else "x")
    }

    private fun book(vararg tabs: OpenTab): TabBook =
        TabBook(tabs = tabs.toList(), activeIndex = if (tabs.isEmpty()) -1 else 0)

    private class Harness {
        val panes = mutableMapOf(
            PaneSide.PRIMARY to EditorPaneState(),
            PaneSide.SECONDARY to EditorPaneState(),
        )
        val didClose = mutableListOf<Path>()
        val forgotten = mutableListOf<Path>()
        var pending: PendingClose? = null

        val controller = EditorTabController(
            paneOf = { panes.getValue(it) },
            mutatePane = { side, transform -> panes[side] = transform(panes.getValue(side)) },
            isOpenAnywhere = { p -> panes.values.any { pane -> pane.book.tabs.any { it.path == p } } },
            forgetScroll = { forgotten.add(it) },
            didClose = { didClose.add(it) },
            isUnsavedText = { it.dirty },
            setPendingClose = { pending = it },
        )

        fun setPane(side: PaneSide, b: TabBook) {
            panes[side] = panes.getValue(side).copy(book = b)
        }

        fun paths(side: PaneSide): List<Path> = panes.getValue(side).book.tabs.map { it.path }
    }

    @Test
    fun `closeTabAt removes tab and disposes when not open elsewhere`() {
        val h = Harness()
        val a = tab("/p/A.kt")
        val b = tab("/p/B.kt")
        h.setPane(PaneSide.PRIMARY, book(a, b))

        h.controller.closeTabAt(PaneSide.PRIMARY, 0)

        assertEquals(listOf(b.path), h.paths(PaneSide.PRIMARY))
        assertEquals(listOf(a.path), h.didClose)
        assertEquals(listOf(a.path), h.forgotten)
    }

    @Test
    fun `closeTabAt does not dispose when same path stays open in other pane`() {
        val h = Harness()
        val a = tab("/p/A.kt")
        h.setPane(PaneSide.PRIMARY, book(a))
        h.setPane(PaneSide.SECONDARY, book(tab("/p/A.kt")))

        h.controller.closeTabAt(PaneSide.PRIMARY, 0)

        assertTrue(h.paths(PaneSide.PRIMARY).isEmpty())
        assertTrue(h.didClose.isEmpty(), "still open in SECONDARY, must not didClose")
    }

    @Test
    fun `requestCloseTab on dirty tab defers to pendingClose without closing`() {
        val h = Harness()
        h.setPane(PaneSide.PRIMARY, book(tab("/p/A.kt", dirty = true)))

        h.controller.requestCloseTab(PaneSide.PRIMARY, 0)

        assertEquals(1, h.paths(PaneSide.PRIMARY).size)
        val p = h.pending
        assertIs<PendingClose.Tab>(p)
        assertEquals(PaneSide.PRIMARY, p.side)
        assertEquals(0, p.index)
    }

    @Test
    fun `requestCloseTab on clean tab closes immediately`() {
        val h = Harness()
        h.setPane(PaneSide.PRIMARY, book(tab("/p/A.kt")))

        h.controller.requestCloseTab(PaneSide.PRIMARY, 0)

        assertTrue(h.paths(PaneSide.PRIMARY).isEmpty())
        assertNull(h.pending)
    }

    @Test
    fun `requestBatchClose closes clean tabs and defers dirty ones`() {
        val h = Harness()
        val clean = tab("/p/A.kt")
        val dirty = tab("/p/B.kt", dirty = true)
        h.setPane(PaneSide.PRIMARY, book(clean, dirty))

        h.controller.requestBatchClose(PaneSide.PRIMARY, listOf(0, 1))

        assertEquals(listOf(dirty.path), h.paths(PaneSide.PRIMARY), "dirty tab survives")
        assertEquals(listOf(clean.path), h.didClose)
        val p = h.pending
        assertIs<PendingClose.Batch>(p)
        assertEquals(listOf(PaneSide.PRIMARY to dirty.path), p.targets)
    }

    @Test
    fun `requestBatchClose with all clean closes everything and leaves no pending`() {
        val h = Harness()
        h.setPane(PaneSide.PRIMARY, book(tab("/p/A.kt"), tab("/p/B.kt")))

        h.controller.requestBatchClose(PaneSide.PRIMARY, listOf(0, 1))

        assertTrue(h.paths(PaneSide.PRIMARY).isEmpty())
        assertNull(h.pending)
    }
}
