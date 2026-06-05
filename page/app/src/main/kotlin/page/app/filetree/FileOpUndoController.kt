package page.app.filetree

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import page.app.EditorPaneState
import page.app.PaneSide
import page.workspace.FileOpHistory
import java.nio.file.Files
import java.nio.file.Path

internal class FileOpUndoController(
    private val fileOpHistory: FileOpHistory.Stack,
    private val paneOf: (PaneSide) -> EditorPaneState,
    private val setPane: (PaneSide, EditorPaneState) -> Unit,
    private val applyExternalChange: (String, String) -> Unit,
    private val remapTabsAfterRename: (Path, Path) -> Unit,
    private val remapTreeStateAfterRename: (Path, Path) -> Unit,
    private val onFileOpHistoryChanged: () -> Unit,
    private val onTreeRevision: () -> Unit,
) {
    fun readFileTextWithTabs(p: Path): String? {
        return paneOf(PaneSide.PRIMARY).book.tabs.firstOrNull { it.path == p }?.text
            ?: paneOf(PaneSide.SECONDARY).book.tabs.firstOrNull { it.path == p }?.text
            ?: runCatching { Files.readString(p) }.getOrNull()
    }

    fun applyTextReplace(path: Path, newText: String) {
        var inTab = false
        for (side in listOf(PaneSide.PRIMARY, PaneSide.SECONDARY)) {
            val p = paneOf(side)
            val idx = p.book.tabs.indexOfFirst { it.path == path }
            if (idx < 0) continue
            inTab = true
            val tab = p.book.tabs[idx]
            if (tab.text == newText) continue
            val isActive = idx == p.book.activeIndex
            val updatedTabs = p.book.tabs.toMutableList().also { it[idx] = tab.copy(text = newText) }
            val newEditorValue = if (isActive) {
                val caret = p.editorValue.selection.start.coerceAtMost(newText.length)
                TextFieldValue(newText, TextRange(caret))
            } else p.editorValue
            setPane(side, p.copy(book = p.book.copy(tabs = updatedTabs), editorValue = newEditorValue))
        }
        if (!inTab) {
            runCatching { Files.writeString(path, newText) }
        }
        runCatching { applyExternalChange(path.toUri().toString(), newText) }
    }

    private fun postUndoRemapForOp(op: FileOpHistory.Op) {
        when (op) {
            is FileOpHistory.PasteCutOp -> op.moves.forEach { (origin, current) ->
                remapTabsAfterRename(current, origin)
                remapTreeStateAfterRename(current, origin)
            }
            is FileOpHistory.RenameOp -> {
                remapTabsAfterRename(op.to, op.from)
                remapTreeStateAfterRename(op.to, op.from)
            }
            is FileOpHistory.ReferenceRewriteOp -> op.rewrites.forEach { entry ->
                applyTextReplace(entry.path, entry.original)
            }
            is FileOpHistory.CompositeOp -> op.parts.asReversed().forEach { postUndoRemapForOp(it) }
            else -> Unit
        }
    }

    fun onUndoFileOp(): Boolean {
        val op = fileOpHistory.peek()
        return if (op == null) false
        else when (val result = op.undo()) {
            is FileOpHistory.UndoResult.Ok -> {
                fileOpHistory.popForUndo()
                onFileOpHistoryChanged()
                postUndoRemapForOp(op)
                onTreeRevision()
                true
            }
            is FileOpHistory.UndoResult.Err -> {
                println("[filetree] undo failed: ${result.message}")
                false
            }
        }
    }

    private fun postRedoRemapForOp(op: FileOpHistory.Op) {
        when (op) {
            is FileOpHistory.PasteCutOp -> op.moves.forEach { (origin, current) ->
                remapTabsAfterRename(origin, current)
                remapTreeStateAfterRename(origin, current)
            }
            is FileOpHistory.RenameOp -> {
                remapTabsAfterRename(op.from, op.to)
                remapTreeStateAfterRename(op.from, op.to)
            }
            is FileOpHistory.ReferenceRewriteOp -> op.rewrites.forEach { entry ->
                applyTextReplace(entry.path, entry.rewritten)
            }
            is FileOpHistory.CompositeOp -> op.parts.forEach { postRedoRemapForOp(it) }
            else -> Unit
        }
    }

    fun onRedoFileOp(): Boolean {
        val op = fileOpHistory.peekRedo()
        return if (op == null) false
        else when (val result = op.redo()) {
            is FileOpHistory.UndoResult.Ok -> {
                fileOpHistory.popForRedo()
                onFileOpHistoryChanged()
                postRedoRemapForOp(op)
                onTreeRevision()
                true
            }
            is FileOpHistory.UndoResult.Err -> {
                println("[filetree] redo failed: ${result.message}")
                false
            }
        }
    }
}
