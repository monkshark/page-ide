package page.app.ui.editor

import page.app.EditorPaneState
import page.app.state.LayoutUiState
import page.app.utils.offsetToLineChar
import page.editor.ProjectFileIndex
import page.language.LspController
import page.lsp.CodeActionEntry
import page.lsp.Diagnostic
import page.lsp.RenameFileChange
import page.lsp.RenameWorkspaceEdit
import java.nio.file.Path

internal class CommandPaletteController(
    private val ui: LayoutUiState,
    private val rootDir: () -> Path?,
    private val focused: () -> EditorPaneState,
    private val controllerFor: (Path) -> LspController?,
    private val allDiagnosticsByUri: () -> Map<String, List<Diagnostic>>,
    private val jumpToProblem: (Path, Int, Int) -> Unit,
    private val applyRename: (RenameWorkspaceEdit) -> Unit,
    private val onCodeActions: (List<CodeActionEntry>, String, String, Int, Boolean) -> Unit,
) {
    fun openQuickOpen() {
        val root = rootDir()
        if (root != null) {
            ui.quickOpenIndex = ProjectFileIndex.walk(root)
            ui.quickOpen = true
        }
    }

    fun jumpProblemRelative(forward: Boolean) {
        val pane = focused()
        val active = pane.book.active
        if (active != null) {
            val activeList = (controllerFor(active.path)?.diagnosticsFor(active.path) ?: emptyList()).sortedWith(
                compareBy({ it.start.line }, { it.start.character }),
            )
            if (activeList.isNotEmpty()) {
                val text = pane.editorValue.text
                val caret = pane.editorValue.selection.start.coerceIn(0, text.length)
                var line = 0
                var lastNl = -1
                var i = 0
                while (i < caret) {
                    if (text[i] == '\n') { line++; lastNl = i }
                    i++
                }
                val char = caret - lastNl - 1
                val target = if (forward) {
                    activeList.firstOrNull { d ->
                        d.start.line > line ||
                            (d.start.line == line && d.start.character > char)
                    } ?: activeList.first()
                } else {
                    activeList.lastOrNull { d ->
                        d.start.line < line ||
                            (d.start.line == line && d.start.character < char)
                    } ?: activeList.last()
                }
                jumpToProblem(active.path, target.start.line, target.start.character)
            } else {
                val anyEntry = allDiagnosticsByUri().entries
                    .firstOrNull { it.value.isNotEmpty() }
                if (anyEntry != null) {
                    val path = runCatching { Path.of(java.net.URI(anyEntry.key)) }.getOrNull()
                    val first = anyEntry.value.firstOrNull()
                    if (path != null && first != null) {
                        jumpToProblem(path, first.start.line, first.start.character)
                    }
                }
            }
        }
    }

    fun openDocumentSymbol() {
        val active = focused().book.active
        val activePath = active?.path
        val ctrl = activePath?.let { controllerFor(it) }
        val status = ctrl?.status?.value
        if (activePath != null
            && ctrl != null
            && status == LspController.Status.READY
        ) {
            val uri = activePath.toUri().toString()
            ctrl.documentSymbols(activePath).whenComplete { syms, err ->
                if (err == null && syms != null) {
                    ui.documentSymbolUri = uri
                    ui.documentSymbolList = syms
                    ui.documentSymbolOpen = true
                }
            }
        }
    }

    fun openWorkspaceSymbol() {
        val wsActivePath = focused().book.active?.path
        val status = wsActivePath?.let { controllerFor(it)?.status?.value }
        if (status == LspController.Status.READY) {
            ui.workspaceSymbolOpen = true
        }
    }

    fun triggerFormat() {
        val active = focused().book.active
        val activePath = active?.path
        val fmtCtrl = activePath?.let { controllerFor(it) }
        if (activePath != null
            && fmtCtrl != null
            && fmtCtrl.status.value == LspController.Status.READY
        ) {
            fmtCtrl.formatting(activePath).whenComplete { edits, err ->
                val list = edits.orEmpty()
                if (err == null && list.isNotEmpty()) {
                    val change = RenameFileChange(activePath.toUri().toString(), list)
                    applyRename(RenameWorkspaceEdit(listOf(change)))
                }
            }
        }
    }

    fun triggerCodeAction() {
        val active = focused().book.active
        val activePath = active?.path
        val caCtrl = activePath?.let { controllerFor(it) }
        if (activePath != null
            && caCtrl != null
            && caCtrl.status.value == LspController.Status.READY
        ) {
            val text = focused().editorValue.text
            val caret = focused().editorValue.selection.start.coerceIn(0, text.length)
            val (line, character) = offsetToLineChar(text, caret)
            val snapshotUri = activePath.toUri().toString()
            val snapshotText = text
            val lineLen = run {
                val lineStart = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
                val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
                lineEnd - lineStart
            }
            caCtrl.codeActions(activePath, line, 0, line, lineLen).whenComplete { actions, err ->
                if (err == null) {
                    val raw = actions.orEmpty()
                    val list = raw.map { entry ->
                        val normalized = page.lsp.CodeActionNormalize.normalize(entry.edit, snapshotUri, snapshotText)
                        if (normalized !== entry.edit) {
                            println("[lsp] codeAction normalize ▶ \"${entry.title}\" — KLS edit 보정됨 (import \\n 보강)")
                            entry.copy(edit = normalized)
                        } else entry
                    }.sortedByDescending { it.isExecutable }
                    val selected = list.indexOfFirst { it.isPreferred }.coerceAtLeast(0)
                    val open = list.isNotEmpty()
                    onCodeActions(list, snapshotUri, snapshotText, selected, open)
                    if (list.isNotEmpty()) {
                        println("[lsp] codeAction debug ▼ $snapshotUri @($line,$character) — ${list.size} action(s)")
                        for ((idx, entry) in list.withIndex()) {
                            println("[lsp] codeAction debug [${idx + 1}/${list.size}]")
                            println(page.lsp.CodeActionDebug.format(entry, snapshotUri, snapshotText))
                        }
                    }
                }
            }
        }
    }
}
