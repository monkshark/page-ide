package page.editor

object Indent {
    const val TAB_UNIT = 4
    private const val TAB_SPACES = "    "
    private val indentTriggers = setOf('{', '(', '[', ':')
    private val matchingClosers = mapOf('{' to '}', '(' to ')', '[' to ']')
    private val unindentChars = setOf('}', ']', ')')

    private fun indentUnit(tabSize: Int, useSpaces: Boolean): String =
        if (useSpaces) " ".repeat(tabSize.coerceAtLeast(1)) else "\t"

    fun handleTab(edit: TextEdit, tabSize: Int = TAB_UNIT, useSpaces: Boolean = true): TextEdit {
        val unit = indentUnit(tabSize, useSpaces)
        val width = tabSize.coerceAtLeast(1)
        val selStart = minOf(edit.selectionStart, edit.selectionEnd)
        val selEnd = maxOf(edit.selectionStart, edit.selectionEnd)
        val text = edit.text
        if (selStart == selEnd) {
            val lineStart = lineStartOf(text, selStart)
            val col = visualColAt(text, lineStart, selStart, width)
            val pad = if (useSpaces) width - (col % width) else 1
            val insert = if (useSpaces) " ".repeat(pad) else "\t"
            val newText = text.substring(0, selStart) + insert + text.substring(selEnd)
            return TextEdit(newText, selStart + insert.length)
        }
        val isMultiLine = text.substring(selStart, selEnd).contains('\n')
        if (isMultiLine) return indentLines(edit, +1, unit, width)
        val lineStart = lineStartOf(text, selStart)
        val newText = text.substring(0, lineStart) + unit + text.substring(lineStart)
        return TextEdit(
            newText,
            edit.selectionStart + unit.length,
            edit.selectionEnd + unit.length,
        )
    }

    fun handleShiftTab(edit: TextEdit, tabSize: Int = TAB_UNIT, useSpaces: Boolean = true): TextEdit =
        indentLines(edit, -1, indentUnit(tabSize, useSpaces), tabSize.coerceAtLeast(1))

    fun handleLiteralTab(edit: TextEdit): TextEdit {
        val selStart = minOf(edit.selectionStart, edit.selectionEnd)
        val selEnd = maxOf(edit.selectionStart, edit.selectionEnd)
        val text = edit.text
        if (selStart == selEnd) {
            val newText = text.substring(0, selStart) + "\t" + text.substring(selEnd)
            return TextEdit(newText, selStart + 1)
        }
        val isMultiLine = text.substring(selStart, selEnd).contains('\n')
        if (isMultiLine) return indentLines(edit, +1, "\t", TAB_UNIT)
        val lineStart = lineStartOf(text, selStart)
        val newText = text.substring(0, lineStart) + "\t" + text.substring(lineStart)
        return TextEdit(newText, edit.selectionStart + 1, edit.selectionEnd + 1)
    }

    fun handleBackspace(edit: TextEdit, tabSize: Int = TAB_UNIT): TextEdit? {
        if (edit.selectionStart != edit.selectionEnd) return null
        val width = tabSize.coerceAtLeast(1)
        val caret = edit.caret
        if (caret == 0) return null
        val text = edit.text
        val lineStart = lineStartOf(text, caret)
        if (caret == lineStart) return null
        for (i in lineStart until caret) {
            if (text[i] != ' ') return null
        }
        val col = caret - lineStart
        val toRemove = ((col - 1) % width) + 1
        if (toRemove <= 1) return null
        val newText = text.substring(0, caret - toRemove) + text.substring(caret)
        return TextEdit(newText, caret - toRemove)
    }

    fun maybeApplyEnter(
        old: TextEdit,
        new: TextEdit,
        tabSize: Int = TAB_UNIT,
        useSpaces: Boolean = true,
    ): TextEdit {
        val oldSelStart = minOf(old.selectionStart, old.selectionEnd)
        val oldSelEnd = maxOf(old.selectionStart, old.selectionEnd)
        val expectedNewLen = old.text.length - (oldSelEnd - oldSelStart) + 1
        if (new.text.length != expectedNewLen) return new
        if (new.caret != oldSelStart + 1) return new
        if (new.text.getOrNull(new.caret - 1) != '\n') return new
        return handleEnter(old, tabSize, useSpaces)
    }

    fun handleEnter(
        edit: TextEdit,
        tabSize: Int = TAB_UNIT,
        useSpaces: Boolean = true,
    ): TextEdit {
        val unit = indentUnit(tabSize, useSpaces)
        val selStart = minOf(edit.selectionStart, edit.selectionEnd)
        val selEnd = maxOf(edit.selectionStart, edit.selectionEnd)
        val text = edit.text
        val lineStart = lineStartOf(text, selStart)
        val leading = leadingWhitespace(text, lineStart)
        val charBeforeCaret = if (selStart > 0) text[selStart - 1] else null
        val charAtSelEnd = if (selEnd < text.length) text[selEnd] else null
        val needsExtraIndent = charBeforeCaret in indentTriggers
        val expectedCloser = charBeforeCaret?.let { matchingClosers[it] }
        val isPairSplit = expectedCloser != null && expectedCloser == charAtSelEnd

        if (isPairSplit) {
            val insert = "\n" + leading + unit + "\n" + leading
            val newText = text.substring(0, selStart) + insert + text.substring(selEnd)
            val caret = selStart + 1 + leading.length + unit.length
            return TextEdit(newText, caret)
        }
        val indent = if (needsExtraIndent) leading + unit else leading
        val insert = "\n" + indent
        val newText = text.substring(0, selStart) + insert + text.substring(selEnd)
        return TextEdit(newText, selStart + insert.length)
    }

    fun maybeUnindentClosingBrace(
        old: TextEdit,
        new: TextEdit,
        tabSize: Int = TAB_UNIT,
    ): TextEdit {
        val width = tabSize.coerceAtLeast(1)
        if (new.text.length != old.text.length + 1) return new
        if (new.caret != old.caret + 1) return new
        val cursor = new.caret
        val typed = new.text.getOrNull(cursor - 1) ?: return new
        if (typed !in unindentChars) return new
        val lineStart = lineStartOf(new.text, cursor - 1)
        for (i in lineStart until cursor - 1) {
            if (!new.text[i].isWhitespace()) return new
        }
        var col = 0
        for (i in lineStart until cursor - 1) {
            col += if (new.text[i] == '\t') width - (col % width) else 1
        }
        if (col == 0) return new
        val targetCol = ((col - 1) / width) * width
        val newIndent = " ".repeat(targetCol)
        val newText = new.text.substring(0, lineStart) +
            newIndent + typed + new.text.substring(cursor)
        val newCaret = lineStart + newIndent.length + 1
        return TextEdit(newText, newCaret)
    }

    private fun indentLines(edit: TextEdit, direction: Int, addUnit: String, width: Int): TextEdit {
        val selStart = minOf(edit.selectionStart, edit.selectionEnd)
        val selEnd = maxOf(edit.selectionStart, edit.selectionEnd)
        val text = edit.text
        val firstLineStart = lineStartOf(text, selStart)
        val lastLineStart =
            if (selEnd > selStart) lineStartOf(text, selEnd - 1) else firstLineStart

        data class ModifiedLine(
            val lineStart: Int,
            val lineEnd: Int,
            val newLine: String,
            val delta: Int,
            val removed: Int,
        )

        val modified = mutableListOf<ModifiedLine>()
        var i = firstLineStart
        while (true) {
            val le = lineEndOf(text, i)
            val r = applyIndentChange(text.substring(i, le), direction, addUnit, width)
            modified.add(ModifiedLine(i, le, r.line, r.delta, r.removed))
            if (i >= lastLineStart) break
            i = le + 1
        }

        val sb = StringBuilder()
        var prev = 0
        for (ml in modified) {
            sb.append(text, prev, ml.lineStart)
            sb.append(ml.newLine)
            prev = ml.lineEnd
        }
        sb.append(text, prev, text.length)
        val newText = sb.toString()

        fun adjust(o: Int): Int {
            var sum = 0
            for (ml in modified) {
                if (ml.lineStart > o) break
                if (ml.lineEnd < o) {
                    sum += ml.delta
                } else {
                    if (direction > 0) {
                        if (o > ml.lineStart) sum += ml.delta
                    } else {
                        val maxRemovable = minOf(ml.removed, o - ml.lineStart)
                        sum -= maxRemovable
                    }
                }
            }
            return o + sum
        }

        val newSelStart = adjust(selStart)
        val newSelEnd = adjust(selEnd)
        return if (edit.selectionStart <= edit.selectionEnd) {
            TextEdit(newText, newSelStart, newSelEnd)
        } else {
            TextEdit(newText, newSelEnd, newSelStart)
        }
    }

    private data class IndentResult(val line: String, val delta: Int, val removed: Int)

    private fun applyIndentChange(line: String, direction: Int, addUnit: String, width: Int): IndentResult {
        return if (direction > 0) {
            IndentResult(addUnit + line, addUnit.length, 0)
        } else {
            var i = 0
            var removed = 0
            while (i < line.length && removed < width) {
                val c = line[i]
                if (c == ' ') {
                    removed++; i++
                } else if (c == '\t') {
                    removed = width; i++; break
                } else break
            }
            IndentResult(line.substring(i), -i, i)
        }
    }

    private fun lineStartOf(text: String, offset: Int): Int {
        var i = offset
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private fun lineEndOf(text: String, offset: Int): Int {
        var i = offset
        while (i < text.length && text[i] != '\n') i++
        return i
    }

    private fun leadingWhitespace(text: String, lineStart: Int): String {
        var i = lineStart
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return text.substring(lineStart, i)
    }

    private fun visualColAt(text: String, lineStart: Int, caret: Int, width: Int): Int {
        var col = 0
        for (i in lineStart until caret) {
            col += if (text[i] == '\t') width - (col % width) else 1
        }
        return col
    }
}
