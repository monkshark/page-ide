package page.app

import page.runtime.*

import page.editor.Token
import page.editor.TokenKind

internal data class KdocContinuationResult(
    val insertText: String,
    val caretOffsetWithinInsert: Int,
    val consumeAfterCaret: Int = 0,
)

internal object KdocContinuation {

    fun compute(text: String, caret: Int, tokens: List<Token>): KdocContinuationResult? {
        if (caret < 0 || caret > text.length) return null
        val token = tokens.firstOrNull { caret >= it.start && caret < it.endExclusive } ?: return null
        if (token.kind != TokenKind.DOC_COMMENT) return null
        if (caret < token.start + 3) return null
        val end = token.endExclusive
        val hasClose = end >= token.start + 4 &&
            text.getOrNull(end - 2) == '*' && text.getOrNull(end - 1) == '/'
        if (hasClose && caret > end - 2) return null

        var lineStart = token.start
        while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
        val before = text.substring(lineStart, token.start)
        val indent = before.takeWhile { it == ' ' || it == '\t' }
        if (indent.length != before.length) return null

        val continuation = "\n" + indent + " * "
        if (hasClose) {
            val closePos = end - 2
            val between = text.substring(caret, closePos)
            val sameLine = between.none { it == '\n' }
            val allBlank = between.all { it == ' ' || it == '\t' }
            if (sameLine && allBlank) {
                val insert = continuation + "\n" + indent
                return KdocContinuationResult(
                    insertText = insert,
                    caretOffsetWithinInsert = continuation.length,
                    consumeAfterCaret = between.length,
                )
            }
        }
        return KdocContinuationResult(continuation, continuation.length)
    }
}
