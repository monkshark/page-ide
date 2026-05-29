package page.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import page.editor.AutoClose
import page.editor.Indent
import page.editor.LineMove
import page.editor.TextEdit
import page.editor.WordBoundary

internal object CodeEditorActions {

    fun applyTab(
        value: TextFieldValue,
        shift: Boolean,
        tabSize: Int = Indent.TAB_UNIT,
        useSpaces: Boolean = true,
    ): TextFieldValue {
        val edit = TextEdit(value.text, value.selection.start, value.selection.end)
        val r = if (shift) Indent.handleShiftTab(edit, tabSize, useSpaces)
        else Indent.handleTab(edit, tabSize, useSpaces)
        return value.copy(text = r.text, selection = TextRange(r.selectionStart, r.selectionEnd))
    }

    fun applyEnter(
        value: TextFieldValue,
        tabSize: Int = Indent.TAB_UNIT,
        useSpaces: Boolean = true,
    ): TextFieldValue {
        val edit = TextEdit(value.text, value.selection.start, value.selection.end)
        val r = Indent.handleEnter(edit, tabSize, useSpaces)
        return value.copy(text = r.text, selection = TextRange(r.selectionStart, r.selectionEnd))
    }

    fun applyBackspace(
        value: TextFieldValue,
        backspaceDeletesPair: Boolean = true,
        tabSize: Int = Indent.TAB_UNIT,
    ): TextFieldValue? {
        val sel = value.selection
        if (!sel.collapsed) {
            return value.copy(
                text = value.text.removeRange(sel.min, sel.max),
                selection = TextRange(sel.min),
            )
        }
        if (backspaceDeletesPair) {
            AutoClose.handleBackspacePair(value.text, sel.end)?.let {
                return value.copy(text = it.text, selection = TextRange(it.caret))
            }
        }
        Indent.handleBackspace(TextEdit(value.text, sel.end), tabSize)?.let {
            return value.copy(text = it.text, selection = TextRange(it.caret))
        }
        if (sel.end == 0) return null
        return value.copy(
            text = value.text.removeRange(sel.end - 1, sel.end),
            selection = TextRange(sel.end - 1),
        )
    }

    fun applyDelete(value: TextFieldValue): TextFieldValue? {
        val sel = value.selection
        if (!sel.collapsed) {
            return value.copy(
                text = value.text.removeRange(sel.min, sel.max),
                selection = TextRange(sel.min),
            )
        }
        if (sel.end == value.text.length) return null
        return value.copy(
            text = value.text.removeRange(sel.end, sel.end + 1),
            selection = TextRange(sel.end),
        )
    }

    fun applyWordLeft(value: TextFieldValue, shift: Boolean): TextFieldValue {
        val target = WordBoundary.prevBoundary(value.text, value.selection.end)
        val sel = if (shift) TextRange(value.selection.start, target) else TextRange(target)
        return value.copy(selection = sel)
    }

    fun applyWordRight(value: TextFieldValue, shift: Boolean): TextFieldValue {
        val target = WordBoundary.nextBoundary(value.text, value.selection.end)
        val sel = if (shift) TextRange(value.selection.start, target) else TextRange(target)
        return value.copy(selection = sel)
    }

    fun applyWordBackspace(value: TextFieldValue): TextFieldValue? {
        if (!value.selection.collapsed) return applyBackspace(value)
        val caret = value.selection.end
        if (caret == 0) return null
        val text = value.text
        val lineStart = text.lastIndexOf('\n', caret - 1) + 1
        if (caret == lineStart) {
            return value.copy(
                text = text.removeRange(caret - 1, caret),
                selection = TextRange(caret - 1),
            )
        }
        val r = WordBoundary.deleteWordBackward(TextEdit(text, caret)) ?: return null
        val clippedCaret = maxOf(r.caret, lineStart)
        if (clippedCaret >= caret) return null
        return value.copy(
            text = text.removeRange(clippedCaret, caret),
            selection = TextRange(clippedCaret),
        )
    }

    fun applyWordDelete(value: TextFieldValue): TextFieldValue? {
        if (!value.selection.collapsed) return applyDelete(value)
        val r = WordBoundary.deleteWordForward(TextEdit(value.text, value.selection.end)) ?: return null
        return value.copy(text = r.text, selection = TextRange(r.caret))
    }

    fun applyLineMove(value: TextFieldValue, down: Boolean, duplicate: Boolean): TextFieldValue? {
        val edit = TextEdit(value.text, value.selection.start, value.selection.end)
        val r = when {
            duplicate && down -> LineMove.duplicateDown(edit)
            duplicate -> LineMove.duplicateUp(edit)
            down -> LineMove.moveDown(edit)
            else -> LineMove.moveUp(edit)
        } ?: return null
        return value.copy(text = r.text, selection = TextRange(r.selectionStart, r.selectionEnd))
    }

    fun applyCharInsert(
        value: TextFieldValue,
        ch: String,
        languageMode: String? = null,
        autoPairs: Boolean = true,
        autoHtmlTags: Boolean = true,
        tabSize: Int = Indent.TAB_UNIT,
    ): TextFieldValue {
        val sel = value.selection
        val oldEdit = TextEdit(value.text, sel.start, sel.end)
        val inserted = value.text.substring(0, sel.min) + ch + value.text.substring(sel.max)
        val caret = sel.min + ch.length
        val newEdit = TextEdit(inserted, caret)
        val withAutoClose = if (autoPairs) AutoClose.apply(oldEdit, newEdit) else newEdit
        val withHtmlTag = if (autoHtmlTags && languageMode == "html" && ch == ">" && sel.collapsed) {
            AutoClose.handleHtmlTagClose(withAutoClose.text, withAutoClose.caret) ?: withAutoClose
        } else withAutoClose
        val withUnindent = Indent.maybeUnindentClosingBrace(oldEdit, withHtmlTag, tabSize)
        return value.copy(
            text = withUnindent.text,
            selection = TextRange(withUnindent.caret),
        )
    }

    fun selectWordAt(value: TextFieldValue, offset: Int): TextFieldValue {
        val r = WordBoundary.wordRangeAt(value.text, offset.coerceIn(0, value.text.length))
        if (r.isEmpty()) return value
        return value.copy(selection = TextRange(r.first, r.last + 1))
    }

    fun selectLineAt(value: TextFieldValue, offset: Int): TextFieldValue {
        val r = WordBoundary.lineRangeAt(value.text, offset.coerceIn(0, value.text.length))
        return value.copy(selection = TextRange(r.first, r.last + 1))
    }

    fun applyDragMove(value: TextFieldValue, dropOffset: Int, copy: Boolean): TextFieldValue? {
        val sel = value.selection
        if (sel.collapsed) return null
        val drop = dropOffset.coerceIn(0, value.text.length)
        if (drop in sel.min..sel.max) return null
        val moved = value.text.substring(sel.min, sel.max)
        val text = value.text
        return if (copy) {
            val newText = text.substring(0, drop) + moved + text.substring(drop)
            value.copy(text = newText, selection = TextRange(drop, drop + moved.length))
        } else if (drop < sel.min) {
            val newText = text.substring(0, drop) + moved + text.substring(drop, sel.min) + text.substring(sel.max)
            value.copy(text = newText, selection = TextRange(drop, drop + moved.length))
        } else {
            val newText = text.substring(0, sel.min) + text.substring(sel.max, drop) + moved + text.substring(drop)
            val insertionStart = sel.min + (drop - sel.max)
            value.copy(text = newText, selection = TextRange(insertionStart, insertionStart + moved.length))
        }
    }
}
