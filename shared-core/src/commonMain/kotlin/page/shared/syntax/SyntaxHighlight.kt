package page.shared.syntax

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

fun colorizeCode(code: String, lexer: SyntaxLexer, palette: SyntaxPalette): AnnotatedString {
    val tokens = lexer.tokenize(code)
    return buildAnnotatedString {
        var cursor = 0
        for (t in tokens) {
            val start = t.start.coerceIn(0, code.length)
            val end = t.endExclusive.coerceIn(0, code.length)
            if (start < cursor || end <= start) continue
            if (start > cursor) append(code.substring(cursor, start))
            val color = colorFor(t.kind, palette)
            if (color != null) withStyle(SpanStyle(color = color)) { append(code.substring(start, end)) }
            else append(code.substring(start, end))
            cursor = end
        }
        if (cursor < code.length) append(code.substring(cursor))
    }
}

private fun colorFor(kind: TokenKind, palette: SyntaxPalette): Color? = when (kind) {
    TokenKind.KEYWORD -> palette.keyword
    TokenKind.STRING -> palette.string
    TokenKind.NUMBER -> palette.number
    TokenKind.COMMENT -> palette.comment
    TokenKind.DOC_COMMENT -> palette.docComment
    TokenKind.TODO_TAG -> palette.todoTag
    TokenKind.ANNOTATION -> palette.annotation
    TokenKind.TYPE -> palette.type
    TokenKind.IDENTIFIER -> palette.identifier
    TokenKind.PUNCT -> null
}
