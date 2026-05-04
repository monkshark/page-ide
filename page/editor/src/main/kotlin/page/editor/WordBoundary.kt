package page.editor

object WordBoundary {

    fun nextBoundary(text: String, offset: Int): Int {
        if (offset >= text.length) return text.length
        var i = offset
        while (i < text.length && isHSpace(text[i])) i++
        if (i == text.length) return i
        if (text[i] == '\n') {
            return if (i == offset) i + 1 else i
        }
        val cls = classify(text[i])
        while (i < text.length && classify(text[i]) == cls) i++
        return i
    }

    fun prevBoundary(text: String, offset: Int): Int {
        if (offset <= 0) return 0
        var i = offset
        while (i > 0 && isHSpace(text[i - 1])) i--
        if (i == 0) return 0
        if (text[i - 1] == '\n') {
            return if (i == offset) i - 1 else i
        }
        val cls = classify(text[i - 1])
        while (i > 0 && classify(text[i - 1]) == cls) i--
        return i
    }

    fun deleteWordBackward(edit: TextEdit): TextEdit? {
        if (edit.selectionStart != edit.selectionEnd) return null
        val caret = edit.caret
        if (caret == 0) return null
        val target = prevBoundary(edit.text, caret)
        if (target == caret) return null
        val newText = edit.text.substring(0, target) + edit.text.substring(caret)
        return TextEdit(newText, target)
    }

    fun deleteWordForward(edit: TextEdit): TextEdit? {
        if (edit.selectionStart != edit.selectionEnd) return null
        val caret = edit.caret
        if (caret == edit.text.length) return null
        val target = nextBoundary(edit.text, caret)
        if (target == caret) return null
        val newText = edit.text.substring(0, caret) + edit.text.substring(target)
        return TextEdit(newText, caret)
    }

    private enum class CharClass { WORD, PUNCT }

    private fun isHSpace(c: Char): Boolean = c == ' ' || c == '\t'

    private fun classify(c: Char): CharClass =
        if (c.isLetterOrDigit() || c == '_') CharClass.WORD else CharClass.PUNCT
}
