package page.editor

internal abstract class JvmLexer(
    private val keywords: Set<String>,
    private val supportTripleQuoted: Boolean,
) : SyntaxLexer {

    override fun tokenize(text: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    val start = i
                    i += 2
                    while (i < n && text[i] != '\n') i++
                    out += Token(TokenKind.COMMENT, start until i)
                }
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    val start = i
                    i += 2
                    while (i + 1 < n && !(text[i] == '*' && text[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(n)
                    out += Token(TokenKind.COMMENT, start until i)
                }
                supportTripleQuoted && c == '"' && i + 2 < n && text[i + 1] == '"' && text[i + 2] == '"' -> {
                    val start = i
                    i += 3
                    while (i + 2 < n && !(text[i] == '"' && text[i + 1] == '"' && text[i + 2] == '"')) i++
                    i = (i + 3).coerceAtMost(n)
                    out += Token(TokenKind.STRING, start until i)
                }
                c == '"' -> {
                    val start = i
                    i = scanQuotedString(text, i, '"')
                    out += Token(TokenKind.STRING, start until i)
                }
                c == '\'' -> {
                    val start = i
                    i = scanQuotedString(text, i, '\'')
                    out += Token(TokenKind.STRING, start until i)
                }
                c == '@' && i + 1 < n && isIdentStart(text[i + 1]) -> {
                    val start = i
                    i++
                    while (i < n && isIdentPart(text[i])) i++
                    out += Token(TokenKind.ANNOTATION, start until i)
                }
                isDigit(c) -> {
                    val start = i
                    i = scanNumber(text, i)
                    out += Token(TokenKind.NUMBER, start until i)
                }
                isIdentStart(c) -> {
                    val start = i
                    while (i < n && isIdentPart(text[i])) i++
                    val word = text.substring(start, i)
                    when {
                        word in keywords -> out += Token(TokenKind.KEYWORD, start until i)
                        startsWithUpperCase(word) -> out += Token(TokenKind.TYPE, start until i)
                    }
                }
                else -> i++
            }
        }
        return out
    }

    private fun scanQuotedString(text: String, from: Int, quote: Char): Int {
        val n = text.length
        var i = from + 1
        while (i < n) {
            val ch = text[i]
            if (ch == '\\' && i + 1 < n) {
                i += 2
                continue
            }
            if (ch == quote) return i + 1
            if (ch == '\n') return i
            i++
        }
        return n
    }

    private fun scanNumber(text: String, from: Int): Int {
        val n = text.length
        var i = from
        if (i + 1 < n && text[i] == '0' && (text[i + 1] == 'x' || text[i + 1] == 'X')) {
            i += 2
            while (i < n && (isHexDigit(text[i]) || text[i] == '_')) i++
        } else {
            while (i < n && (isDigit(text[i]) || text[i] == '_')) i++
            if (i < n && text[i] == '.' && i + 1 < n && isDigit(text[i + 1])) {
                i++
                while (i < n && (isDigit(text[i]) || text[i] == '_')) i++
            }
            if (i < n && (text[i] == 'e' || text[i] == 'E')) {
                i++
                if (i < n && (text[i] == '+' || text[i] == '-')) i++
                while (i < n && isDigit(text[i])) i++
            }
        }
        while (i < n && text[i] in "fFdDLlu") i++
        return i
    }

    private fun isDigit(c: Char) = c in '0'..'9'
    private fun isHexDigit(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    private fun isIdentStart(c: Char) = c == '_' || c.isLetter()
    private fun isIdentPart(c: Char) = c == '_' || c.isLetterOrDigit()
    private fun startsWithUpperCase(s: String) = s.isNotEmpty() && s[0].isUpperCase() && s.length > 1
}
