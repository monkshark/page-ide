package page.editor

data class TextEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart,
) {
    constructor(text: String, caret: Int) : this(text, caret, caret)
    val caret: Int get() = selectionStart
}

object AutoClose {
    private val pairs = mapOf(
        '(' to ')',
        '[' to ']',
        '{' to '}',
        '"' to '"',
        '\'' to '\'',
    )
    private val closers = pairs.values.toSet()

    fun apply(old: TextEdit, new: TextEdit): TextEdit {
        if (old.selectionStart != old.selectionEnd) return wrapSelection(old, new)

        if (new.text.length == old.text.length - 1 && new.caret == old.caret - 1) {
            val deleted = old.text.getOrNull(new.caret)
            val expectedCloser = deleted?.let { pairs[it] }
            val next = new.text.getOrNull(new.caret)
            if (expectedCloser != null && next == expectedCloser) {
                val stripped = new.text.substring(0, new.caret) + new.text.substring(new.caret + 1)
                return TextEdit(stripped, new.caret)
            }
        }

        if (new.text.length != old.text.length + 1) return new
        if (new.caret != old.caret + 1) return new
        val cursor = new.caret
        val inserted = new.text.getOrNull(cursor - 1) ?: return new

        if (inserted in closers) {
            val charAtOldCaret = old.text.getOrNull(old.caret)
            if (charAtOldCaret == inserted) {
                return TextEdit(old.text, cursor)
            }
        }

        val closer = pairs[inserted] ?: return new

        val nextChar = new.text.getOrNull(cursor)
        if (nextChar != null && (nextChar.isLetterOrDigit() || nextChar == '_')) return new

        if (inserted == '"' || inserted == '\'') {
            val prevChar = new.text.getOrNull(cursor - 2)
            if (prevChar != null && (prevChar.isLetterOrDigit() || prevChar == '_')) return new
        }

        val withCloser = new.text.substring(0, cursor) + closer + new.text.substring(cursor)
        return TextEdit(withCloser, cursor)
    }

    private fun wrapSelection(old: TextEdit, new: TextEdit): TextEdit {
        val selStart = minOf(old.selectionStart, old.selectionEnd)
        val selEnd = maxOf(old.selectionStart, old.selectionEnd)
        val expectedLen = old.text.length - (selEnd - selStart) + 1
        if (new.text.length != expectedLen) return new
        if (new.selectionStart != new.selectionEnd) return new
        if (new.selectionStart != selStart + 1) return new
        val inserted = new.text.getOrNull(selStart) ?: return new
        val closer = pairs[inserted] ?: return new
        val wrapped = old.text.substring(0, selStart) +
            inserted +
            old.text.substring(selStart, selEnd) +
            closer +
            old.text.substring(selEnd)
        return TextEdit(wrapped, selStart + 1, selEnd + 1)
    }
}
