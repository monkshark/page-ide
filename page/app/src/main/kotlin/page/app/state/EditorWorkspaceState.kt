package page.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import page.app.EditorPaneState
import page.app.EditorScrollSnapshot
import page.app.PaneSide
import page.app.mvi.IdeStore
import page.editor.EditSnapshot
import page.editor.SplitOrientation
import page.editor.SplitPaneState
import page.editor.UndoGroupTracker
import java.nio.file.Path

internal class EditorWorkspaceState(
    private val undoTracker: (PaneSide) -> UndoGroupTracker,
    private val store: IdeStore = IdeStore(),
) {
    var primaryPane by mutableStateOf(EditorPaneState())
    var secondaryPane by mutableStateOf(EditorPaneState())
    var focusedPane: PaneSide
        get() = store.editorLayout.focusedPane
        set(value) = store.updateEditorLayout { it.copy(focusedPane = value) }

    var splitEnabled: Boolean
        get() = store.editorLayout.splitEnabled
        set(value) = store.updateEditorLayout { it.copy(splitEnabled = value) }
    var splitOrientation: SplitOrientation
        get() = store.editorLayout.splitOrientation
        set(value) = store.updateEditorLayout { it.copy(splitOrientation = value) }
    var splitState: SplitPaneState
        get() = store.editorLayout.splitState
        set(value) = store.updateEditorLayout { it.copy(splitState = value) }

    var editorScrollByPath: Map<Path, EditorScrollSnapshot>
        get() = store.editorScroll.scrollByPath
        set(value) = store.updateEditorScroll { it.copy(scrollByPath = value) }
    var foldByPath: Map<String, Set<Int>>
        get() = store.editorLayout.foldByPath
        set(value) = store.updateEditorLayout { it.copy(foldByPath = value) }

    fun paneOf(side: PaneSide): EditorPaneState = when (side) {
        PaneSide.PRIMARY -> primaryPane
        PaneSide.SECONDARY -> secondaryPane
    }

    fun setPane(side: PaneSide, value: EditorPaneState) {
        when (side) {
            PaneSide.PRIMARY -> primaryPane = value
            PaneSide.SECONDARY -> secondaryPane = value
        }
    }

    fun mutatePane(side: PaneSide, transform: (EditorPaneState) -> EditorPaneState) {
        setPane(side, transform(paneOf(side)))
    }

    fun mutateFocused(transform: (EditorPaneState) -> EditorPaneState) {
        mutatePane(focusedPane, transform)
    }

    fun focused(): EditorPaneState = paneOf(focusedPane)

    fun activateAdjacentTab(delta: Int) {
        val book = paneOf(focusedPane).book
        val n = book.tabs.size
        if (n <= 1) return
        val next = ((book.activeIndex + delta) % n + n) % n
        if (next != book.activeIndex) {
            mutatePane(focusedPane) { it.copy(book = it.book.activate(next)) }
        }
    }

    fun handleEditorChange(side: PaneSide, value: TextFieldValue) {
        mutatePane(side) {
            val priorText = it.editorValue.text
            val priorSelection = it.editorValue.selection
            val textChanged = value.text != priorText
            val tracker = undoTracker(side)
            val nextBook = if (textChanged) {
                val priorCaret = priorSelection.start
                val shouldPush = tracker.onTextChange(priorText, value.text)
                val withPush = if (shouldPush) {
                    it.book.pushHistoryOnActive(EditSnapshot(priorText, priorCaret))
                } else it.book
                withPush.updateActive(value.text, value.selection.start)
            } else {
                if (value.selection != priorSelection) tracker.markBreak()
                it.book.updateActive(value.text, value.selection.start)
            }
            val nextSearch = if (textChanged) it.search?.retarget(value.text) else it.search
            it.copy(editorValue = value, book = nextBook, search = nextSearch)
        }
    }

    fun activateTab(side: PaneSide, index: Int) {
        mutatePane(side) { it.copy(book = it.book.activate(index)) }
    }

    fun moveTab(side: PaneSide, from: Int, to: Int) {
        mutatePane(side) { it.copy(book = it.book.move(from, to)) }
    }

    fun moveTabAcross(source: PaneSide, index: Int) {
        if (!splitEnabled) return
        val tab = paneOf(source).book.tabs.getOrNull(index) ?: return
        val target = if (source == PaneSide.PRIMARY) PaneSide.SECONDARY else PaneSide.PRIMARY
        mutatePane(source) { it.copy(book = it.book.close(index)) }
        mutatePane(target) { it.copy(book = it.book.appendTab(tab)) }
        focusedPane = target
    }
}
