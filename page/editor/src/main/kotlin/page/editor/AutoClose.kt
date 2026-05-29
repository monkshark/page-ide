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

    private val htmlVoidTags = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr",
    )

    /**
     * After typing `>` to close an opening HTML tag, insert the matching close tag and
     * leave the caret between them: `<div|` → `<div>|</div>`.
     * Returns null when the `>` does not finish an HTML opening tag (closer, void element,
     * self-closing, comment, doctype, processing instruction).
     */
    fun handleHtmlTagClose(text: String, caret: Int): TextEdit? {
        if (caret <= 0 || caret > text.length) return null
        if (text[caret - 1] != '>') return null
        val before = text.substring(0, caret - 1)
        val after = text.substring(caret)
        val ltIdx = before.lastIndexOf('<')
        if (ltIdx < 0) return null
        if (before.indexOf('>', ltIdx + 1) >= 0) return null
        val inside = before.substring(ltIdx + 1)
        if (inside.isEmpty()) return null
        val firstChar = inside[0]
        if (firstChar == '/' || firstChar == '!' || firstChar == '?') return null
        if (inside.endsWith("/")) return null
        val tagEnd = inside.indexOfFirst { it == ' ' || it == '\t' || it == '\n' }
        val tagName = if (tagEnd < 0) inside else inside.substring(0, tagEnd)
        if (tagName.isEmpty() || !tagName[0].isLetter()) return null
        if (tagName.any { !(it.isLetterOrDigit() || it == '-' || it == ':') }) return null
        if (tagName.lowercase() in htmlVoidTags) return null
        val newText = before + ">" + "</" + tagName + ">" + after
        return TextEdit(newText, caret)
    }

    /**
     * If caret sits between a matched pair like `(|)`, `[|]`, `{|}`, `"|"`, `'|'`,
     * a backspace removes both characters and moves caret left by one.
     * Returns null if no pair to collapse — caller should fall through to normal backspace.
     */
    fun handleBackspacePair(text: String, caret: Int): TextEdit? {
        if (caret <= 0 || caret > text.length) return null
        val prev = text.getOrNull(caret - 1) ?: return null
        val expectedCloser = pairs[prev] ?: return null
        val next = text.getOrNull(caret) ?: return null
        if (next != expectedCloser) return null
        val stripped = text.substring(0, caret - 1) + text.substring(caret + 1)
        return TextEdit(stripped, caret - 1)
    }

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
