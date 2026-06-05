package page.app.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import page.app.EditorPaneState
import page.app.PaneSide
import page.editor.EditSnapshot
import page.editor.FileKinds
import page.editor.Replace
import page.editor.SearchState
import page.editor.UndoGroupTracker

internal class EditorSearchController(
    private val paneOf: (PaneSide) -> EditorPaneState,
    private val mutatePane: (PaneSide, (EditorPaneState) -> EditorPaneState) -> Unit,
    private val mutateFocused: ((EditorPaneState) -> EditorPaneState) -> Unit,
    private val focused: () -> EditorPaneState,
    private val undoTracker: (PaneSide) -> UndoGroupTracker,
    private val addSearchQuery: (String) -> Unit,
    private val addReplaceText: (String) -> Unit,
) {
    private fun moveCaretToActiveMatch(side: PaneSide, s: SearchState) {
        val range = s.active
        if (range != null) {
            mutatePane(side) {
                val text = it.editorValue.text
                val start = range.first.coerceIn(0, text.length)
                val end = (range.last + 1).coerceIn(start, text.length)
                it.copy(editorValue = it.editorValue.copy(selection = TextRange(start, end)))
            }
        }
    }

    fun openSearch() {
        val pane = focused()
        val active = pane.book.active
        if (active != null && FileKinds.classify(active.path).isEditableAsText) {
            mutateFocused {
                val current = it.search
                it.copy(
                    search = current?.withReplaceVisible(false)
                        ?: SearchState().withQuery(it.editorValue.text, ""),
                )
            }
        }
    }

    fun openReplace() {
        val pane = focused()
        val active = pane.book.active
        if (active != null && FileKinds.classify(active.path).isEditableAsText) {
            mutateFocused {
                val current = it.search
                it.copy(
                    search = current?.withReplaceVisible(true)
                        ?: SearchState()
                            .withQuery(it.editorValue.text, "")
                            .withReplaceVisible(true),
                )
            }
        }
    }

    fun closeSearch(side: PaneSide) {
        mutatePane(side) { it.copy(search = null) }
    }

    fun onQueryChange(side: PaneSide, q: String) {
        val pane = paneOf(side)
        val updated = (pane.search ?: SearchState()).withQuery(pane.editorValue.text, q)
        mutatePane(side) { it.copy(search = updated) }
        moveCaretToActiveMatch(side, updated)
    }

    fun onToggleCase(side: PaneSide) {
        val pane = paneOf(side)
        val s = pane.search
        if (s != null) {
            val updated = s.withCaseSensitive(pane.editorValue.text, !s.caseSensitive)
            mutatePane(side) { it.copy(search = updated) }
            moveCaretToActiveMatch(side, updated)
        }
    }

    fun onSearchNext(side: PaneSide) {
        val s = paneOf(side).search
        if (s != null) {
            val updated = s.next()
            mutatePane(side) { it.copy(search = updated) }
            moveCaretToActiveMatch(side, updated)
            addSearchQuery(updated.query)
        }
    }

    fun onSearchPrev(side: PaneSide) {
        val s = paneOf(side).search
        if (s != null) {
            val updated = s.prev()
            mutatePane(side) { it.copy(search = updated) }
            moveCaretToActiveMatch(side, updated)
        }
    }

    fun onReplaceChange(side: PaneSide, value: String) {
        mutatePane(side) { it.copy(search = it.search?.withReplace(value)) }
    }

    fun onReplace(side: PaneSide) {
        val pane = paneOf(side)
        val s = pane.search
        val range = s?.active
        if (s != null && range != null) {
            val text = pane.editorValue.text
            val caret = pane.editorValue.selection.start
            val r = Replace.applyCurrent(text, range, s.replace)
            val retargeted = s.retarget(r.text)
            val nextIdx = retargeted.matches.indexOfFirst { it.first >= r.caret }
            val updatedSearch = retargeted.copy(
                activeMatchIndex = if (nextIdx >= 0) nextIdx
                else if (retargeted.matches.isNotEmpty()) 0 else -1,
            )
            undoTracker(side).markBreak()
            mutatePane(side) {
                it.copy(
                    book = it.book
                        .pushHistoryOnActive(EditSnapshot(text, caret))
                        .updateActive(r.text, r.caret),
                    editorValue = TextFieldValue(r.text, TextRange(r.caret)),
                    search = updatedSearch,
                )
            }
            moveCaretToActiveMatch(side, updatedSearch)
            addSearchQuery(s.query)
            addReplaceText(s.replace)
        }
    }

    fun onReplaceAll(side: PaneSide) {
        val pane = paneOf(side)
        val s = pane.search
        if (s != null && s.matches.isNotEmpty()) {
            val text = pane.editorValue.text
            val caret = pane.editorValue.selection.start
            val r = Replace.applyAll(text, s.matches, s.replace)
            undoTracker(side).markBreak()
            mutatePane(side) {
                it.copy(
                    book = it.book
                        .pushHistoryOnActive(EditSnapshot(text, caret))
                        .updateActive(r.text, r.caret),
                    editorValue = TextFieldValue(r.text, TextRange(r.caret)),
                    search = s.retarget(r.text),
                )
            }
            addSearchQuery(s.query)
            addReplaceText(s.replace)
        }
    }
}
