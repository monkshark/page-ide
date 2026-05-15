package page.editor

internal abstract class JvmLexer(
    private val keywords: Set<String>,
    private val supportTripleQuoted: Boolean,
    private val supportInterpolation: Boolean = false,
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
                    emitTodoTags(text, start + 2, i, out)
                }
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    val start = i
                    val isDocComment = i + 3 < n &&
                        text[i + 2] == '*' &&
                        !(text[i + 3] == '/')
                    i += 2
                    while (i + 1 < n && !(text[i] == '*' && text[i + 1] == '/')) i++
                    val bodyEnd = i
                    i = (i + 2).coerceAtMost(n)
                    val kind = if (isDocComment) TokenKind.DOC_COMMENT else TokenKind.COMMENT
                    out += Token(kind, start until i)
                    val tagsStart = start + if (isDocComment) 3 else 2
                    emitTodoTags(text, tagsStart, bodyEnd, out)
                    if (isDocComment) {
                        emitDocCommentTags(text, bodyStart = start + 3, bodyEnd = bodyEnd, out = out)
                    }
                }
                supportTripleQuoted && c == '"' && i + 2 < n && text[i + 1] == '"' && text[i + 2] == '"' -> {
                    i = emitTripleQuoted(text, i, out)
                }
                c == '"' -> {
                    i = emitQuotedString(text, i, '"', out)
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

    private fun emitQuotedString(text: String, from: Int, quote: Char, out: MutableList<Token>): Int {
        if (!supportInterpolation || quote != '"') {
            val end = scanQuotedString(text, from, quote)
            out += Token(TokenKind.STRING, from until end)
            return end
        }
        return emitInterpolatedString(text, from, isTriple = false, out)
    }

    private fun emitTripleQuoted(text: String, from: Int, out: MutableList<Token>): Int {
        if (!supportInterpolation) {
            val n = text.length
            var i = from + 3
            while (i + 2 < n && !(text[i] == '"' && text[i + 1] == '"' && text[i + 2] == '"')) i++
            val end = (i + 3).coerceAtMost(n)
            out += Token(TokenKind.STRING, from until end)
            return end
        }
        return emitInterpolatedString(text, from, isTriple = true, out)
    }

    private fun emitInterpolatedString(
        text: String,
        from: Int,
        isTriple: Boolean,
        out: MutableList<Token>,
    ): Int {
        val n = text.length
        val openLen = if (isTriple) 3 else 1
        var segStart = from
        var i = from + openLen
        while (i < n) {
            val ch = text[i]
            if (!isTriple && ch == '\\' && i + 1 < n) {
                i += 2
                continue
            }
            if (!isTriple && ch == '\n') {
                if (i > segStart) out += Token(TokenKind.STRING, segStart until i)
                return i
            }
            if (isTriple) {
                if (ch == '"' && i + 2 < n && text[i + 1] == '"' && text[i + 2] == '"') {
                    val end = (i + 3).coerceAtMost(n)
                    if (end > segStart) out += Token(TokenKind.STRING, segStart until end)
                    return end
                }
            } else if (ch == '"') {
                val end = i + 1
                if (end > segStart) out += Token(TokenKind.STRING, segStart until end)
                return end
            }
            if (ch == '$' && i + 1 < n) {
                val next = text[i + 1]
                if (next == '{') {
                    if (i > segStart) out += Token(TokenKind.STRING, segStart until i)
                    val exprStart = i
                    var depth = 1
                    var j = i + 2
                    while (j < n && depth > 0) {
                        val cj = text[j]
                        when (cj) {
                            '{' -> depth++
                            '}' -> depth--
                            '"' -> {
                                val strEnd = scanQuotedString(text, j, '"')
                                j = strEnd
                                continue
                            }
                        }
                        if (depth == 0) {
                            j++
                            break
                        }
                        j++
                    }
                    val exprEnd = j.coerceAtMost(n)
                    out += Token(TokenKind.IDENTIFIER, exprStart until exprEnd)
                    i = exprEnd
                    segStart = i
                    continue
                }
                if (isIdentStart(next)) {
                    if (i > segStart) out += Token(TokenKind.STRING, segStart until i)
                    val identStart = i
                    var j = i + 2
                    while (j < n && isIdentPart(text[j])) j++
                    out += Token(TokenKind.IDENTIFIER, identStart until j)
                    i = j
                    segStart = i
                    continue
                }
            }
            i++
        }
        if (i > segStart) out += Token(TokenKind.STRING, segStart until i)
        return i
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

    private fun emitTodoTags(text: String, from: Int, to: Int, out: MutableList<Token>) {
        if (from >= to) return
        val slice = text.substring(from, to.coerceAtMost(text.length))
        for (m in todoPattern.findAll(slice)) {
            val absStart = from + m.range.first
            val absEnd = from + m.range.last + 1
            out += Token(TokenKind.TODO_TAG, absStart until absEnd)
        }
    }

    private fun emitDocCommentTags(text: String, bodyStart: Int, bodyEnd: Int, out: MutableList<Token>) {
        var i = bodyStart
        while (i < bodyEnd) {
            val c = text[i]
            if (c == '@' && i + 1 < bodyEnd && isIdentStart(text[i + 1]) &&
                isAtDocTagPosition(text, i, bodyStart)
            ) {
                val tagStart = i
                i++
                while (i < bodyEnd && isIdentPart(text[i])) i++
                out += Token(TokenKind.ANNOTATION, tagStart until i)
            } else {
                i++
            }
        }
    }

    private fun isAtDocTagPosition(text: String, at: Int, bodyStart: Int): Boolean {
        var i = at - 1
        while (i >= bodyStart) {
            val c = text[i]
            if (c == '\n') return true
            if (c == ' ' || c == '\t' || c == '*') {
                i--
                continue
            }
            return false
        }
        return true
    }

    private val todoPattern = Regex("""\b(TODO|FIXME|HACK|XXX|NOTE)\b(:[^\n]*)?""")

    private fun isDigit(c: Char) = c in '0'..'9'
    private fun isHexDigit(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    private fun isIdentStart(c: Char) = c == '_' || c.isLetter()
    private fun isIdentPart(c: Char) = c == '_' || c.isLetterOrDigit()
    private fun startsWithUpperCase(s: String) = s.isNotEmpty() && s[0].isUpperCase() && s.length > 1
}
