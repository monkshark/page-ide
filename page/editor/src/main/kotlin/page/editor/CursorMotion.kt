package page.editor

object CursorMotion {
    fun moveLeft(content: EditorContent, extend: Boolean): EditorContent {
        val target = (content.selection.caret - 1).coerceAtLeast(0)
        return moveTo(content, target, extend)
    }

    fun moveRight(content: EditorContent, extend: Boolean): EditorContent {
        val target = (content.selection.caret + 1).coerceAtMost(content.length)
        return moveTo(content, target, extend)
    }

    fun moveWordLeft(content: EditorContent, extend: Boolean): EditorContent {
        val target = WordBoundary.prevBoundary(content.text, content.selection.caret)
        return moveTo(content, target, extend)
    }

    fun moveWordRight(content: EditorContent, extend: Boolean): EditorContent {
        val target = WordBoundary.nextBoundary(content.text, content.selection.caret)
        return moveTo(content, target, extend)
    }

    fun moveLineHome(content: EditorContent, extend: Boolean): EditorContent {
        val text = content.text
        var i = content.selection.caret
        while (i > 0 && text[i - 1] != '\n') i--
        return moveTo(content, i, extend)
    }

    fun moveLineEnd(content: EditorContent, extend: Boolean): EditorContent {
        val text = content.text
        var i = content.selection.caret
        while (i < text.length && text[i] != '\n') i++
        return moveTo(content, i, extend)
    }

    fun moveDocStart(content: EditorContent, extend: Boolean): EditorContent =
        moveTo(content, 0, extend)

    fun moveDocEnd(content: EditorContent, extend: Boolean): EditorContent =
        moveTo(content, content.length, extend)

    fun selectAll(content: EditorContent): EditorContent =
        content.withSelection(Selection(0, content.length))

    fun deleteBackward(content: EditorContent): EditorContent {
        if (!content.selection.isCollapsed) return content.replaceSelection("")
        val caret = content.selection.caret
        if (caret == 0) return content
        return content.replace(caret - 1, caret, "")
    }

    fun deleteForward(content: EditorContent): EditorContent {
        if (!content.selection.isCollapsed) return content.replaceSelection("")
        val caret = content.selection.caret
        if (caret >= content.length) return content
        return content.replace(caret, caret + 1, "")
    }

    fun deleteWordBackward(content: EditorContent): EditorContent {
        if (!content.selection.isCollapsed) return content.replaceSelection("")
        val caret = content.selection.caret
        val target = WordBoundary.prevBoundary(content.text, caret)
        if (target == caret) return content
        return content.replace(target, caret, "")
    }

    fun deleteWordForward(content: EditorContent): EditorContent {
        if (!content.selection.isCollapsed) return content.replaceSelection("")
        val caret = content.selection.caret
        val target = WordBoundary.nextBoundary(content.text, caret)
        if (target == caret) return content
        return content.replace(caret, target, "")
    }

    fun insert(content: EditorContent, text: String): EditorContent =
        content.replaceSelection(text)

    private fun moveTo(content: EditorContent, target: Int, extend: Boolean): EditorContent {
        val safe = target.coerceIn(0, content.length)
        val newSel = if (extend) Selection(content.selection.anchor, safe)
        else Selection.at(safe)
        return content.withSelection(newSel)
    }
}
