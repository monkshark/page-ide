package page.app.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import page.app.EditorPaneState
import page.app.PaneSide
import page.editor.EditSnapshot
import page.editor.UndoGroupTracker

internal class EditorHistoryController(
    private val focusedPane: () -> PaneSide,
    private val paneOf: (PaneSide) -> EditorPaneState,
    private val mutatePane: (PaneSide, (EditorPaneState) -> EditorPaneState) -> Unit,
    private val undoTracker: (PaneSide) -> UndoGroupTracker,
    private val applyExternalChange: (String, String) -> Unit,
) {
    fun doUndo() {
        val side = focusedPane()
        val pane = paneOf(side)
        val current = EditSnapshot(pane.editorValue.text, pane.editorValue.selection.start)
        val result = pane.book.undoOnActive(current)
        if (result != null) {
            val (newBook, restored) = result
            val caret = restored.caret.coerceIn(0, restored.text.length)
            undoTracker(side).markBreak()
            val groupId = restored.groupId
            val activeBook = if (groupId != null) newBook.undoGroupOnNonActive(groupId) else newBook
            mutatePane(side) {
                it.copy(
                    book = activeBook,
                    editorValue = TextFieldValue(restored.text, TextRange(caret)),
                    search = it.search?.retarget(restored.text),
                )
            }
            if (groupId != null) {
                val otherSide = if (side == PaneSide.PRIMARY) PaneSide.SECONDARY else PaneSide.PRIMARY
                mutatePane(otherSide) { it.copy(book = it.book.undoGroupOnNonActive(groupId)) }
                for (tab in activeBook.tabs) {
                    if (tab.path != pane.book.tabs.getOrNull(pane.book.activeIndex)?.path) {
                        runCatching { applyExternalChange(tab.path.toUri().toString(), tab.text) }
                    }
                }
                for (tab in paneOf(otherSide).book.tabs) {
                    runCatching { applyExternalChange(tab.path.toUri().toString(), tab.text) }
                }
            }
        }
    }

    fun doRedo() {
        val side = focusedPane()
        val pane = paneOf(side)
        val current = EditSnapshot(pane.editorValue.text, pane.editorValue.selection.start)
        val result = pane.book.redoOnActive(current)
        if (result != null) {
            val (newBook, restored) = result
            val caret = restored.caret.coerceIn(0, restored.text.length)
            undoTracker(side).markBreak()
            val groupId = restored.groupId
            val activeBook = if (groupId != null) newBook.redoGroupOnNonActive(groupId) else newBook
            mutatePane(side) {
                it.copy(
                    book = activeBook,
                    editorValue = TextFieldValue(restored.text, TextRange(caret)),
                    search = it.search?.retarget(restored.text),
                )
            }
            if (groupId != null) {
                val otherSide = if (side == PaneSide.PRIMARY) PaneSide.SECONDARY else PaneSide.PRIMARY
                mutatePane(otherSide) { it.copy(book = it.book.redoGroupOnNonActive(groupId)) }
                for (tab in activeBook.tabs) {
                    if (tab.path != pane.book.tabs.getOrNull(pane.book.activeIndex)?.path) {
                        runCatching { applyExternalChange(tab.path.toUri().toString(), tab.text) }
                    }
                }
                for (tab in paneOf(otherSide).book.tabs) {
                    runCatching { applyExternalChange(tab.path.toUri().toString(), tab.text) }
                }
            }
        }
    }
}
