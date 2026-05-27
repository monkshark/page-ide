package page.app

import page.runtime.*

import page.lsp.CompletionItem
import page.lsp.CompletionItemKind
import page.lsp.CompletionList

internal data class CommentKeywordContext(
    val anchor: Int,
    val needsLeadingSpace: Boolean,
)

internal object CommentKeywordCompletion {

    val DISMISS_ITEM: CompletionItem = CompletionItem(
        label = "",
        kind = CompletionItemKind.KEYWORD,
        detail = null,
        documentation = null,
        insertText = "",
        isSnippet = false,
        edit = null,
        additionalEdits = emptyList(),
        filterText = "",
        sortText = "00",
    )

    val items: List<CompletionItem> =
        listOf(DISMISS_ITEM) + TodoMultiKeyword.STANDARD_KEYWORDS.mapIndexed { idx, kw ->
            CompletionItem(
                label = kw,
                kind = CompletionItemKind.KEYWORD,
                detail = "comment tag",
                documentation = null,
                insertText = kw,
                isSnippet = false,
                edit = null,
                additionalEdits = emptyList(),
                filterText = kw,
                sortText = "%02d".format(idx + 1),
            )
        }

    val list: CompletionList = CompletionList(isIncomplete = false, items = items)

    fun detect(text: String, caret: Int): CommentKeywordContext? {
        if (caret < 0 || caret > text.length) return null
        var keywordStart = caret
        while (keywordStart > 0) {
            val c = text[keywordStart - 1]
            if (c.isLetterOrDigit() || c == '_') keywordStart-- else break
        }
        var wsStart = keywordStart
        while (wsStart > 0) {
            val c = text[wsStart - 1]
            if (c == ' ' || c == '\t') wsStart-- else break
        }
        val matched: Boolean = when {
            wsStart >= 3 &&
                text[wsStart - 3] == '/' && text[wsStart - 2] == '*' && text[wsStart - 1] == '*' -> true
            wsStart >= 2 &&
                text[wsStart - 2] == '/' && text[wsStart - 1] == '/' -> true
            wsStart >= 2 &&
                text[wsStart - 2] == '/' && text[wsStart - 1] == '*' -> true
            else -> false
        }
        if (!matched) return null
        for (i in wsStart until keywordStart) if (text[i] == '\n') return null
        var keywordEnd = caret
        while (keywordEnd < text.length) {
            val c = text[keywordEnd]
            if (c.isLetterOrDigit() || c == '_') keywordEnd++ else break
        }
        val fullWord = text.substring(keywordStart, keywordEnd)
        if (fullWord in TodoMultiKeyword.STANDARD_KEYWORDS) return null
        return CommentKeywordContext(
            anchor = keywordStart,
            needsLeadingSpace = (wsStart == keywordStart),
        )
    }
}
