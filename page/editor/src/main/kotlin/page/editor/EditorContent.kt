package page.editor

data class Selection(val anchor: Int, val caret: Int) {
    val start: Int get() = if (anchor <= caret) anchor else caret
    val end: Int get() = if (anchor <= caret) caret else anchor
    val isCollapsed: Boolean get() = anchor == caret
    val length: Int get() = end - start

    fun collapsed(at: Int = caret): Selection = Selection(at, at)

    companion object {
        fun at(offset: Int): Selection = Selection(offset, offset)
        fun range(start: Int, end: Int): Selection = Selection(start, end)
    }
}

data class EditorContent(val text: String, val selection: Selection) {
    val length: Int get() = text.length

    fun replaceSelection(replacement: String): EditorContent {
        val s = selection.start
        val e = selection.end
        val newText = text.substring(0, s) + replacement + text.substring(e)
        val caret = s + replacement.length
        return EditorContent(newText, Selection.at(caret))
    }

    fun replace(start: Int, end: Int, replacement: String): EditorContent {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val newText = text.substring(0, s) + replacement + text.substring(e)
        val caret = s + replacement.length
        return EditorContent(newText, Selection.at(caret))
    }

    fun withSelection(newSelection: Selection): EditorContent {
        val safe = Selection(
            newSelection.anchor.coerceIn(0, text.length),
            newSelection.caret.coerceIn(0, text.length),
        )
        return copy(selection = safe)
    }

    fun withCaret(caret: Int): EditorContent =
        withSelection(Selection.at(caret))

    companion object {
        val EMPTY = EditorContent("", Selection.at(0))

        fun of(text: String, caret: Int = 0): EditorContent =
            EditorContent(text, Selection.at(caret.coerceIn(0, text.length)))
    }
}
