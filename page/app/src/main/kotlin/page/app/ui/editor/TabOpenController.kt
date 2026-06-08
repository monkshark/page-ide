package page.app.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import page.app.EditorPaneState
import page.editor.FileDocument
import page.editor.FileKinds
import java.nio.file.Path

internal class TabOpenController(
    private val focused: () -> EditorPaneState,
    private val mutateFocused: ((EditorPaneState) -> EditorPaneState) -> Unit,
    private val addRecentFile: (Path) -> Unit,
    private val scope: CoroutineScope,
) {
    fun openInTab(picked: Path) {
        val kind = FileKinds.classify(picked)
        if (!kind.isEditableAsText) {
            mutateFocused { it.copy(book = it.book.openOrFocus(picked, "")) }
            addRecentFile(picked)
            return
        }
        if (focused().book.tabs.any { it.path == picked }) {
            mutateFocused { it.copy(book = it.book.openOrFocus(picked, "")) }
            addRecentFile(picked)
            return
        }
        scope.launch {
            val text = withContext(Dispatchers.IO) { FileDocument.loadOrNull(picked) } ?: return@launch
            mutateFocused { it.copy(book = it.book.openOrFocus(picked, text)) }
            addRecentFile(picked)
        }
    }

    fun openInTabAt(picked: Path, offset: Int) {
        if (!FileKinds.classify(picked).isEditableAsText) return
        val pane = focused()
        val existing = pane.book.tabs.indexOfFirst { it.path == picked }
        if (existing >= 0) {
            val tab = pane.book.tabs[existing]
            val caret = offset.coerceIn(0, tab.text.length)
            mutateFocused {
                it.copy(
                    book = it.book.activate(existing).updateActive(tab.text, caret),
                    editorValue = TextFieldValue(tab.text, TextRange(caret)),
                )
            }
            addRecentFile(picked)
            return
        }
        scope.launch {
            val text = withContext(Dispatchers.IO) { FileDocument.loadOrNull(picked) } ?: return@launch
            val caret = offset.coerceIn(0, text.length)
            mutateFocused {
                val opened = it.book.openOrFocus(picked, text)
                it.copy(
                    book = opened.updateActive(text, caret),
                    editorValue = TextFieldValue(text, TextRange(caret)),
                )
            }
            addRecentFile(picked)
        }
    }
}
