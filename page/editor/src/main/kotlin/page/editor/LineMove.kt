package page.editor

object LineMove {
    fun moveUp(edit: TextEdit): TextEdit? {
        val text = edit.text
        val a = minOf(edit.selectionStart, edit.selectionEnd)
        val b = maxOf(edit.selectionStart, edit.selectionEnd)
        val firstStart = lineStart(text, a)
        if (firstStart == 0) return null
        val lastEnd = lineEnd(text, b)
        val prevLineStart = lineStart(text, firstStart - 1)

        val before = text.substring(0, prevLineStart)
        val prevLine = text.substring(prevLineStart, firstStart - 1)
        val selected = text.substring(firstStart, lastEnd)
        val rest = text.substring(lastEnd)

        val newText = before + selected + "\n" + prevLine + rest
        val shift = -(firstStart - prevLineStart)
        return TextEdit(
            text = newText,
            selectionStart = edit.selectionStart + shift,
            selectionEnd = edit.selectionEnd + shift,
        )
    }

    fun moveDown(edit: TextEdit): TextEdit? {
        val text = edit.text
        val a = minOf(edit.selectionStart, edit.selectionEnd)
        val b = maxOf(edit.selectionStart, edit.selectionEnd)
        val firstStart = lineStart(text, a)
        val lastEnd = lineEnd(text, b)
        if (lastEnd == text.length) return null
        val nextLineStart = lastEnd + 1
        val nextLineEnd = lineEnd(text, nextLineStart)

        val before = text.substring(0, firstStart)
        val selected = text.substring(firstStart, lastEnd)
        val nextLine = text.substring(nextLineStart, nextLineEnd)
        val rest = text.substring(nextLineEnd)

        val newText = before + nextLine + "\n" + selected + rest
        val shift = nextLineEnd - lastEnd
        return TextEdit(
            text = newText,
            selectionStart = edit.selectionStart + shift,
            selectionEnd = edit.selectionEnd + shift,
        )
    }

    fun duplicateUp(edit: TextEdit): TextEdit {
        val text = edit.text
        val a = minOf(edit.selectionStart, edit.selectionEnd)
        val b = maxOf(edit.selectionStart, edit.selectionEnd)
        val firstStart = lineStart(text, a)
        val lastEnd = lineEnd(text, b)
        val selected = text.substring(firstStart, lastEnd)

        val newText = text.substring(0, firstStart) + selected + "\n" + text.substring(firstStart)
        return TextEdit(
            text = newText,
            selectionStart = edit.selectionStart,
            selectionEnd = edit.selectionEnd,
        )
    }

    fun duplicateDown(edit: TextEdit): TextEdit {
        val text = edit.text
        val a = minOf(edit.selectionStart, edit.selectionEnd)
        val b = maxOf(edit.selectionStart, edit.selectionEnd)
        val firstStart = lineStart(text, a)
        val lastEnd = lineEnd(text, b)
        val selected = text.substring(firstStart, lastEnd)

        val newText = text.substring(0, lastEnd) + "\n" + selected + text.substring(lastEnd)
        val shift = lastEnd - firstStart + 1
        return TextEdit(
            text = newText,
            selectionStart = edit.selectionStart + shift,
            selectionEnd = edit.selectionEnd + shift,
        )
    }

    private fun lineStart(text: String, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private fun lineEnd(text: String, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i < text.length && text[i] != '\n') i++
        return i
    }
}
