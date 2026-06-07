package page.app.ui.editor

import page.app.EditorPaneState
import page.app.PaneSide
import page.editor.OpenTab
import page.editor.TabBook
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabContextControllerTest {

    private fun paneWithTab(name: String): EditorPaneState {
        val tab = OpenTab(path = Path.of(name), text = "x")
        return EditorPaneState(book = TabBook(tabs = listOf(tab), activeIndex = 0))
    }

    private fun controller(
        primary: EditorPaneState,
        secondary: EditorPaneState,
        panes: MutableMap<PaneSide, EditorPaneState>,
        splitState: BooleanArray,
        focus: Array<PaneSide>,
    ): TabContextController {
        panes[PaneSide.PRIMARY] = primary
        panes[PaneSide.SECONDARY] = secondary
        return TabContextController(
            paneOf = { side -> panes.getValue(side) },
            mutatePane = { side, transform -> panes[side] = transform(panes.getValue(side)) },
            focusedPane = { focus[0] },
            splitEnabled = { splitState[0] },
            setSplitEnabled = { splitState[0] = it },
            copyToClipboard = {},
            relativeTo = { it.toString() },
            onRevealInFiles = {},
            requestRename = {},
            requestCloseTab = { _, _ -> },
            requestBatchClose = { _, _ -> },
            closeManyOnPane = { _, _ -> },
            moveTabAcross = { _, _ -> },
        )
    }

    @Test
    fun `split only enables split and leaves opposite pane empty`() {
        val panes = mutableMapOf<PaneSide, EditorPaneState>()
        val split = booleanArrayOf(false)
        val focus = arrayOf(PaneSide.PRIMARY)
        val primary = paneWithTab("a.kt")
        val secondary = EditorPaneState()
        val c = controller(primary, secondary, panes, split, focus)

        c.actionsFor(PaneSide.PRIMARY).onSplit?.invoke(0)

        assertTrue(split[0])
        assertEquals(PaneSide.PRIMARY, focus[0])
        assertTrue(panes.getValue(PaneSide.SECONDARY).book.tabs.isEmpty())
        assertEquals(primary, panes.getValue(PaneSide.PRIMARY))
    }

    @Test
    fun `split is idempotent when already enabled`() {
        val panes = mutableMapOf<PaneSide, EditorPaneState>()
        val split = booleanArrayOf(true)
        val focus = arrayOf(PaneSide.SECONDARY)
        val primary = paneWithTab("a.kt")
        val secondary = paneWithTab("b.kt")
        val c = controller(primary, secondary, panes, split, focus)

        c.actionsFor(PaneSide.PRIMARY).onSplit?.invoke(0)

        assertTrue(split[0])
        assertEquals(PaneSide.SECONDARY, focus[0])
        assertEquals(secondary, panes.getValue(PaneSide.SECONDARY))
        assertEquals(primary, panes.getValue(PaneSide.PRIMARY))
    }

    @Test
    fun `move to other pane is offered only when split enabled`() {
        val panes = mutableMapOf<PaneSide, EditorPaneState>()
        val focus = arrayOf(PaneSide.PRIMARY)
        val primary = paneWithTab("a.kt")
        val secondary = EditorPaneState()

        val off = controller(primary, secondary, panes, booleanArrayOf(false), focus)
        assertFalse(off.actionsFor(PaneSide.PRIMARY).onMoveToOtherPane != null)

        val on = controller(primary, secondary, panes, booleanArrayOf(true), focus)
        assertTrue(on.actionsFor(PaneSide.PRIMARY).onMoveToOtherPane != null)
    }
}
