package page.app.ui.editor

import page.app.EditorPaneState
import page.app.PaneSide
import page.app.PendingClose
import page.editor.OpenTab
import java.nio.file.Path

internal class EditorTabController(
    private val paneOf: (PaneSide) -> EditorPaneState,
    private val mutatePane: (PaneSide, (EditorPaneState) -> EditorPaneState) -> Unit,
    private val isOpenAnywhere: (Path) -> Boolean,
    private val forgetScroll: (Path) -> Unit,
    private val didClose: (Path) -> Unit,
    private val isUnsavedText: (OpenTab) -> Boolean,
    private val setPendingClose: (PendingClose) -> Unit,
    private val autoSaveOnClose: () -> Boolean,
    private val saveTabAt: (PaneSide, Int) -> Unit,
    private val mergeSplitIfEmptyPane: () -> Unit = {},
) {
    private fun disposeClosed(paths: List<Path>) {
        paths.forEach { p ->
            if (!isOpenAnywhere(p)) {
                forgetScroll(p)
                didClose(p)
            }
        }
    }

    fun closeTabAt(side: PaneSide, idx: Int) {
        val tab = paneOf(side).book.tabs.getOrNull(idx)
        mutatePane(side) { it.copy(book = it.book.close(idx)) }
        if (tab != null) disposeClosed(listOf(tab.path))
        mergeSplitIfEmptyPane()
    }

    fun closeTabsUnderPath(path: Path) {
        listOf(PaneSide.PRIMARY, PaneSide.SECONDARY).forEach { side ->
            val pane = paneOf(side)
            val victims = pane.book.tabs.withIndex()
                .filter { (_, tab) -> tab.path == path || tab.path.startsWith(path) }
                .map { it.index }
            val closedPaths = victims.map { pane.book.tabs[it].path }
            if (victims.isNotEmpty()) {
                val newBook = victims.sortedDescending().fold(pane.book) { acc, idx -> acc.close(idx) }
                mutatePane(side) { it.copy(book = newBook) }
            }
            disposeClosed(closedPaths)
        }
        mergeSplitIfEmptyPane()
    }

    fun requestCloseTab(side: PaneSide, idx: Int) {
        val tab = paneOf(side).book.tabs.getOrNull(idx)
        if (tab != null && isUnsavedText(tab)) {
            if (autoSaveOnClose()) {
                saveTabAt(side, idx)
                closeTabAt(side, idx)
            } else {
                setPendingClose(PendingClose.Tab(side, idx))
            }
        } else {
            closeTabAt(side, idx)
        }
    }

    fun closeManyOnPane(side: PaneSide, indices: List<Int>) {
        if (indices.isEmpty()) return
        val pane = paneOf(side)
        val closedPaths = indices.mapNotNull { i -> pane.book.tabs.getOrNull(i)?.path }
        mutatePane(side) { it.copy(book = it.book.closeMany(indices)) }
        disposeClosed(closedPaths)
        mergeSplitIfEmptyPane()
    }

    fun requestBatchClose(side: PaneSide, indices: List<Int>) {
        val pane = paneOf(side)
        val valid = indices.filter { it in pane.book.tabs.indices }
        val dirtyPairs = valid.mapNotNull { i ->
            val t = pane.book.tabs[i]
            if (isUnsavedText(t)) (side to t.path) else null
        }
        if (dirtyPairs.isEmpty()) {
            closeManyOnPane(side, valid)
        } else if (autoSaveOnClose()) {
            valid.forEach { i -> if (isUnsavedText(pane.book.tabs[i])) saveTabAt(side, i) }
            closeManyOnPane(side, valid)
        } else {
            val cleanIndices = valid.filter { i -> !isUnsavedText(pane.book.tabs[i]) }
            val allPaths = valid.map { side to pane.book.tabs[it].path }
            closeManyOnPane(side, cleanIndices)
            setPendingClose(
                PendingClose.Batch(
                    allPaths.filter { (s, p) ->
                        val tab = paneOf(s).book.tabs.firstOrNull { it.path == p }
                        tab != null && isUnsavedText(tab)
                    },
                ),
            )
        }
    }
}
