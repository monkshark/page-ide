package page.editor

enum class TokenKind {
    KEYWORD,
    STRING,
    NUMBER,
    COMMENT,
    ANNOTATION,
    TYPE,
    PUNCT,
}

data class Token(val kind: TokenKind, val range: IntRange) {
    val start: Int get() = range.first
    val endExclusive: Int get() = range.last + 1
}

interface SyntaxLexer {
    fun tokenize(text: String): List<Token>
}
