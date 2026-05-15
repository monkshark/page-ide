package page.lsp

object CodeActionNormalize {

    fun normalize(
        edit: RenameWorkspaceEdit,
        currentUri: String?,
        currentText: String?,
    ): RenameWorkspaceEdit {
        if (edit.isEmpty) return edit
        var anyChanged = false
        val changes = edit.changes.map { change ->
            val text = if (change.uri == currentUri) currentText else null
            if (text == null) change else {
                val normalized = normalizeChange(change, text)
                if (normalized !== change) anyChanged = true
                normalized
            }
        }
        return if (anyChanged) RenameWorkspaceEdit(changes) else edit
    }

    private fun normalizeChange(change: RenameFileChange, text: String): RenameFileChange {
        var anyChanged = false
        val edits = change.edits.map { e ->
            val normalized = normalizeEdit(e, text)
            if (normalized !== e) anyChanged = true
            normalized
        }
        return if (anyChanged) RenameFileChange(change.uri, edits) else change
    }

    private fun normalizeEdit(e: RenameEdit, text: String): RenameEdit {
        val isInsertion = e.startLine == e.endLine && e.startCharacter == e.endCharacter
        if (!isInsertion) return e
        if (!looksLikeImport(e.newText)) return e
        if (e.newText.endsWith("\n")) return e
        val endOffset = offsetAt(text, e.endLine, e.endCharacter)
        if (endOffset < 0) return e
        if (endOffset >= text.length) return e
        val ch = text[endOffset]
        if (ch == '\n' || ch == '\r') return e
        return RenameEdit(
            startLine = e.startLine,
            startCharacter = e.startCharacter,
            endLine = e.endLine,
            endCharacter = e.endCharacter,
            newText = e.newText + "\n",
        )
    }

    private fun looksLikeImport(newText: String): Boolean {
        val trimmed = newText.trimStart('\n', '\r', ' ', '\t')
        return trimmed.startsWith("import ")
    }

    private fun offsetAt(text: String, line: Int, char: Int): Int {
        var l = 0
        var idx = 0
        while (l < line && idx < text.length) {
            if (text[idx] == '\n') l++
            idx++
        }
        if (l != line) return -1
        var lineEnd = idx
        while (lineEnd < text.length && text[lineEnd] != '\n') lineEnd++
        return (idx + char).coerceAtMost(lineEnd)
    }
}
