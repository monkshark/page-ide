package page.app.ui.editor

import page.app.EditorPaneState
import page.app.PaneSide
import page.app.TabContextActions
import page.editor.OpenTab
import java.nio.file.Path

internal class TabContextController(
    private val paneOf: (PaneSide) -> EditorPaneState,
    private val mutatePane: (PaneSide, (EditorPaneState) -> EditorPaneState) -> Unit,
    private val focusedPane: () -> PaneSide,
    private val setFocusedPane: (PaneSide) -> Unit,
    private val splitEnabled: () -> Boolean,
    private val setSplitEnabled: (Boolean) -> Unit,
    private val copyToClipboard: (String) -> Unit,
    private val relativeTo: (Path) -> String,
    private val onRevealInFiles: (Path) -> Unit,
    private val requestRename: (Path) -> Unit,
    private val requestCloseTab: (PaneSide, Int) -> Unit,
    private val requestBatchClose: (PaneSide, List<Int>) -> Unit,
    private val closeManyOnPane: (PaneSide, List<Int>) -> Unit,
    private val moveTabAcross: (PaneSide, Int) -> Unit,
) {
    private fun togglePin(side: PaneSide, idx: Int) {
        mutatePane(side) { it.copy(book = it.book.togglePinned(idx)) }
    }

    private fun copyAbsolutePathOfTab(side: PaneSide, idx: Int) {
        paneOf(side).book.tabs.getOrNull(idx)?.let { copyToClipboard(it.path.toAbsolutePath().toString()) }
    }

    private fun copyRelativePathOfTab(side: PaneSide, idx: Int) {
        paneOf(side).book.tabs.getOrNull(idx)?.let { copyToClipboard(relativeTo(it.path)) }
    }

    private fun showInExplorerOfTab(side: PaneSide, idx: Int) {
        paneOf(side).book.tabs.getOrNull(idx)?.let { onRevealInFiles(it.path) }
    }

    private fun renameTabFile(side: PaneSide, idx: Int) {
        paneOf(side).book.tabs.getOrNull(idx)?.let { requestRename(it.path) }
    }

    private fun splitWithTab(side: PaneSide, idx: Int) {
        val tab = paneOf(side).book.tabs.getOrNull(idx) ?: return
        if (!splitEnabled()) setSplitEnabled(true)
        val target = if (side == PaneSide.PRIMARY) PaneSide.SECONDARY else PaneSide.PRIMARY
        mutatePane(target) {
            it.copy(
                book = it.book.appendTab(
                    OpenTab(path = tab.path, text = tab.text, savedText = tab.savedText, caret = tab.caret),
                ),
            )
        }
        setFocusedPane(target)
    }

    fun closeActiveTab() {
        val side = focusedPane()
        val idx = paneOf(side).book.activeIndex
        if (idx in paneOf(side).book.tabs.indices) requestCloseTab(side, idx)
    }

    fun actionsFor(side: PaneSide): TabContextActions {
        return TabContextActions(
            onClose = { idx -> requestCloseTab(side, idx) },
            onCloseOthers = { idx ->
                val pane = paneOf(side)
                val toClose = pane.book.tabs.indices.filter { i -> i != idx && !pane.book.tabs[i].isPinned }
                requestBatchClose(side, toClose)
            },
            onCloseToLeft = { idx ->
                val pane = paneOf(side)
                val toClose = (0 until idx).filter { i -> !pane.book.tabs[i].isPinned }
                requestBatchClose(side, toClose)
            },
            onCloseToRight = { idx ->
                val pane = paneOf(side)
                val toClose = ((idx + 1) until pane.book.tabs.size).filter { i -> !pane.book.tabs[i].isPinned }
                requestBatchClose(side, toClose)
            },
            onCloseAll = {
                val pane = paneOf(side)
                val toClose = pane.book.tabs.indices.filter { i -> !pane.book.tabs[i].isPinned }
                requestBatchClose(side, toClose)
            },
            onCloseUnmodified = {
                val pane = paneOf(side)
                val toClose = pane.book.tabs.indices.filter { i -> !pane.book.tabs[i].dirty && !pane.book.tabs[i].isPinned }
                closeManyOnPane(side, toClose)
            },
            onCopyAbsolutePath = { idx -> copyAbsolutePathOfTab(side, idx) },
            onCopyRelativePath = { idx -> copyRelativePathOfTab(side, idx) },
            onShowInExplorer = { idx -> showInExplorerOfTab(side, idx) },
            onTogglePin = { idx -> togglePin(side, idx) },
            onMoveToOtherPane = if (splitEnabled()) {
                { idx -> moveTabAcross(side, idx) }
            } else {
                null
            },
            onSplit = { idx -> splitWithTab(side, idx) },
            onRename = { idx -> renameTabFile(side, idx) },
        )
    }
}
