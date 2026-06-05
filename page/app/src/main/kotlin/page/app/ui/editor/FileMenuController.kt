package page.app.ui.editor

import page.app.EditorPaneState
import page.app.PaneSide
import page.editor.FileDocument
import page.editor.FileKinds
import page.workspace.FileDialogs
import java.awt.Frame
import java.nio.file.Path

internal class FileMenuController(
    private val openInTab: (Path) -> Unit,
    private val focused: () -> EditorPaneState,
    private val mutateFocused: ((EditorPaneState) -> EditorPaneState) -> Unit,
    private val paneOf: (PaneSide) -> EditorPaneState,
    private val mutatePane: (PaneSide, (EditorPaneState) -> EditorPaneState) -> Unit,
    private val didSave: (Path, String) -> Unit,
    private val openWorkspaceFolder: (Path) -> Unit,
) {
    fun openFile(parent: Frame) {
        FileDialogs.open(parent)?.let { picked -> openInTab(picked) }
    }

    fun saveFile(parent: Frame) {
        val pane = focused()
        val active = pane.book.active
        if (active != null) {
            if (FileKinds.classify(active.path).isEditableAsText) {
                FileDocument.save(active.path, pane.editorValue.text)
                didSave(active.path, pane.editorValue.text)
                mutateFocused {
                    it.copy(
                        book = it.book
                            .updateActive(it.editorValue.text, it.editorValue.selection.start)
                            .markActiveSaved(),
                    )
                }
            }
        } else {
            val target = FileDialogs.saveAs(parent)
            if (target != null) {
                FileDocument.save(target, pane.editorValue.text)
                mutateFocused { it.copy(book = it.book.openOrFocus(target, pane.editorValue.text)) }
            }
        }
    }

    fun saveAllDirty(): Int {
        val pendingByPath = LinkedHashMap<Path, String>()
        for (side in listOf(PaneSide.PRIMARY, PaneSide.SECONDARY)) {
            val pane = paneOf(side)
            val activeIdx = pane.book.activeIndex
            pane.book.tabs.forEachIndexed { idx, tab ->
                if (!FileKinds.classify(tab.path).isEditableAsText) return@forEachIndexed
                val liveText = if (idx == activeIdx) pane.editorValue.text else tab.text
                if (liveText != tab.savedText) {
                    pendingByPath[tab.path] = liveText
                }
            }
        }
        var saved = 0
        for ((path, text) in pendingByPath) {
            try {
                FileDocument.save(path, text)
                didSave(path, text)
                saved += 1
            } catch (_: java.io.IOException) { }
        }
        if (saved > 0) {
            mutatePane(PaneSide.PRIMARY) { state ->
                var book = state.book
                for ((path, text) in pendingByPath) book = book.markPathSaved(path, text)
                state.copy(book = book)
            }
            mutatePane(PaneSide.SECONDARY) { state ->
                var book = state.book
                for ((path, text) in pendingByPath) book = book.markPathSaved(path, text)
                state.copy(book = book)
            }
        }
        return saved
    }

    fun openFolder(parent: Frame) {
        FileDialogs.openDirectory(parent)?.let { picked -> openWorkspaceFolder(picked) }
    }

    fun openFolderPath(picked: Path) {
        openWorkspaceFolder(picked)
    }

    fun newFile(parent: Frame) {
        val target = FileDialogs.saveAs(parent)
        if (target != null) {
            FileDocument.save(target, "")
            openInTab(target)
        }
    }
}
