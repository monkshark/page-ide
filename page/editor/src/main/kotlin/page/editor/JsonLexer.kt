package page.editor

object JsonLexer : SyntaxLexer {
    override fun tokenize(text: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '"' -> {
                    val start = i
                    i++
                    while (i < n) {
                        val ch = text[i]
                        if (ch == '\\' && i + 1 < n) { i += 2; continue }
                        if (ch == '"') { i++; break }
                        if (ch == '\n') break
                        i++
                    }
                    out += Token(TokenKind.STRING, start until i)
                }
                c == '-' || c.isDigit() -> {
                    val start = i
                    if (c == '-') i++
                    while (i < n && text[i].isDigit()) i++
                    if (i < n && text[i] == '.') {
                        i++
                        while (i < n && text[i].isDigit()) i++
                    }
                    if (i < n && (text[i] == 'e' || text[i] == 'E')) {
                        i++
                        if (i < n && (text[i] == '+' || text[i] == '-')) i++
                        while (i < n && text[i].isDigit()) i++
                    }
                    out += Token(TokenKind.NUMBER, start until i)
                }
                c.isLetter() -> {
                    val start = i
                    while (i < n && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                    val word = text.substring(start, i)
                    if (word == "true" || word == "false" || word == "null") {
                        out += Token(TokenKind.KEYWORD, start until i)
                    }
                }
                else -> i++
            }
        }
        return out
    }
}
