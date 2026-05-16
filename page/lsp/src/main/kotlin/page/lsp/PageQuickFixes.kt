package page.lsp

object PageQuickFixes {

    fun synthesize(
        uri: String,
        text: String,
        diagnostics: List<Diagnostic>,
    ): List<CodeActionEntry> {
        if (diagnostics.isEmpty()) return emptyList()
        val out = mutableListOf<CodeActionEntry>()
        for (d in diagnostics) {
            when (d.code) {
                "UNUSED_VARIABLE" -> {
                    deleteDeclarationLine(uri, text, d)?.let(out::add)
                    prefixIdentifierWithUnderscore(uri, text, d, kindForVarRename, "변수")?.let(out::add)
                }
                "UNUSED_PARAMETER" -> {
                    suppressUnusedParameter(uri, text, d)?.let(out::add)
                    prefixIdentifierWithUnderscore(uri, text, d, kindForParamRename, "파라미터")?.let(out::add)
                }
                "NO_ELSE_IN_WHEN" -> addElseBranch(uri, text, d)?.let(out::add)
                "UNNECESSARY_NOT_NULL_ASSERTION" -> removeNotNullAssertion(uri, text, d)?.let(out::add)
                "UNNECESSARY_SAFE_CALL" -> replaceSafeCallWithDot(uri, text, d)?.let(out::add)
                "WRONG_LONG_SUFFIX" -> capitalizeLongSuffix(uri, text, d)?.let(out::add)
                "USELESS_CAST" -> removeUselessCast(uri, text, d)?.let(out::add)
                "REDUNDANT_ELSE_IN_WHEN" -> removeRedundantElse(uri, text, d)?.let(out::add)
                "USELESS_ELVIS" -> removeUselessElvis(uri, text, d)?.let(out::add)
            }
        }
        return out
    }

    private const val kindForVar = "quickfix.page.unusedVariable"
    private const val kindForVarRename = "quickfix.page.unusedVariable.renameUnderscore"
    private const val kindForParam = "quickfix.page.unusedParameter"
    private const val kindForParamRename = "quickfix.page.unusedParameter.renameUnderscore"
    private const val kindForWhen = "quickfix.page.noElseInWhen"
    private const val kindForNotNullAssertion = "quickfix.page.unnecessaryNotNullAssertion"
    private const val kindForSafeCall = "quickfix.page.unnecessarySafeCall"
    private const val kindForLongSuffix = "quickfix.page.wrongLongSuffix"
    private const val kindForUselessCast = "quickfix.page.uselessCast"
    private const val kindForRedundantElse = "quickfix.page.redundantElseInWhen"
    private const val kindForUselessElvis = "quickfix.page.uselessElvis"

    private fun deleteDeclarationLine(
        uri: String,
        text: String,
        d: Diagnostic,
    ): CodeActionEntry? {
        if (d.start.line != d.end.line) return null
        val name = sliceLineSubstring(text, d.start.line, d.start.character, d.end.line, d.end.character)
            ?: return null
        if (name.isBlank()) return null
        val lines = text.split('\n')
        if (d.start.line < 0 || d.start.line >= lines.size) return null
        val isLastLine = d.start.line == lines.size - 1
        val endLine: Int
        val endChar: Int
        if (isLastLine) {
            endLine = d.start.line
            endChar = lines[d.start.line].length
        } else {
            endLine = d.start.line + 1
            endChar = 0
        }
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(
                        RenameEdit(
                            startLine = d.start.line,
                            startCharacter = 0,
                            endLine = endLine,
                            endCharacter = endChar,
                            newText = "",
                        ),
                    ),
                ),
            ),
        )
        return CodeActionEntry(
            title = "사용하지 않는 변수 '$name' 선언 라인 삭제",
            kind = kindForVar,
            isPreferred = true,
            edit = edit,
            command = null,
        )
    }

    private fun suppressUnusedParameter(
        uri: String,
        text: String,
        d: Diagnostic,
    ): CodeActionEntry? {
        val name = sliceLineSubstring(text, d.start.line, d.start.character, d.end.line, d.end.character)
            ?: return null
        if (name.isBlank()) return null
        val lines = text.split('\n')
        val funLine = findEnclosingFunLine(lines, d.start.line) ?: return null
        if (funLine > 0 && unusedParameterSuppressRegex.containsMatchIn(lines[funLine - 1])) return null
        val indent = lines[funLine].takeWhile { it == ' ' || it == '\t' }
        val annotation = "$indent@Suppress(\"UNUSED_PARAMETER\")\n"
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(
                        RenameEdit(
                            startLine = funLine,
                            startCharacter = 0,
                            endLine = funLine,
                            endCharacter = 0,
                            newText = annotation,
                        ),
                    ),
                ),
            ),
        )
        return CodeActionEntry(
            title = "사용하지 않는 파라미터 '$name' — @Suppress(\"UNUSED_PARAMETER\") 추가",
            kind = kindForParam,
            isPreferred = true,
            edit = edit,
            command = null,
        )
    }

    private val funKeywordRegex = Regex("(^|\\s)fun\\s")

    private val unusedParameterSuppressRegex =
        Regex("""^\s*@Suppress\s*\([^)]*"UNUSED_PARAMETER"[^)]*\)""")

    private fun findEnclosingFunLine(lines: List<String>, fromLine: Int): Int? {
        for (i in fromLine downTo 0) {
            if (funKeywordRegex.containsMatchIn(lines[i])) return i
        }
        return null
    }

    private fun sliceLineSubstring(
        text: String,
        startLine: Int,
        startChar: Int,
        endLine: Int,
        endChar: Int,
    ): String? {
        if (startLine != endLine) return null
        val lines = text.split('\n')
        val line = lines.getOrNull(startLine) ?: return null
        if (startChar < 0 || endChar > line.length || startChar >= endChar) return null
        return line.substring(startChar, endChar)
    }

    private fun prefixIdentifierWithUnderscore(
        uri: String,
        text: String,
        d: Diagnostic,
        kind: String,
        nounKr: String,
    ): CodeActionEntry? {
        val name = sliceLineSubstring(text, d.start.line, d.start.character, d.end.line, d.end.character)
            ?: return null
        if (name.isBlank() || name.startsWith("_")) return null
        val replacement = "_$name"
        return singleEditAction(
            title = "사용하지 않는 $nounKr '$name' 을(를) '$replacement' 로 변경",
            kind = kind,
            preferred = false,
            uri = uri,
            startLine = d.start.line,
            startCharacter = d.start.character,
            endLine = d.end.line,
            endCharacter = d.end.character,
            newText = replacement,
        )
    }

    private fun removeNotNullAssertion(
        uri: String,
        text: String,
        d: Diagnostic,
    ): CodeActionEntry? {
        val from = lineColToOffset(text, d.start.line, d.start.character) ?: return null
        val to = lineColToOffset(text, d.end.line, d.end.character) ?: return null
        if (to <= from || to > text.length) return null
        val rangeText = text.substring(from, to)
        val idx = rangeText.lastIndexOf("!!")
        if (idx < 0) return null
        val absStart = from + idx
        val absEnd = absStart + 2
        val (sl, sc) = offsetToLineCol(text, absStart)
        val (el, ec) = offsetToLineCol(text, absEnd)
        return singleEditAction(
            title = "불필요한 '!!' 단언 삭제",
            kind = kindForNotNullAssertion,
            preferred = true,
            uri = uri,
            startLine = sl,
            startCharacter = sc,
            endLine = el,
            endCharacter = ec,
            newText = "",
        )
    }

    private fun replaceSafeCallWithDot(
        uri: String,
        text: String,
        d: Diagnostic,
    ): CodeActionEntry? {
        val from = lineColToOffset(text, d.start.line, d.start.character) ?: return null
        val to = lineColToOffset(text, d.end.line, d.end.character) ?: return null
        if (to <= from || to > text.length) return null
        val rangeText = text.substring(from, to)
        val idx = rangeText.indexOf("?.")
        if (idx < 0) return null
        val absStart = from + idx
        val absEnd = absStart + 2
        val (sl, sc) = offsetToLineCol(text, absStart)
        val (el, ec) = offsetToLineCol(text, absEnd)
        return singleEditAction(
            title = "불필요한 '?.' 를 '.' 로 변경",
            kind = kindForSafeCall,
            preferred = true,
            uri = uri,
            startLine = sl,
            startCharacter = sc,
            endLine = el,
            endCharacter = ec,
            newText = ".",
        )
    }

    private fun capitalizeLongSuffix(
        uri: String,
        text: String,
        d: Diagnostic,
    ): CodeActionEntry? {
        val from = lineColToOffset(text, d.start.line, d.start.character) ?: return null
        val to = lineColToOffset(text, d.end.line, d.end.character) ?: return null
        if (to <= from || to > text.length) return null
        val lastIdx = to - 1
        if (text[lastIdx] != 'l') return null
        val (sl, sc) = offsetToLineCol(text, lastIdx)
        val (el, ec) = offsetToLineCol(text, lastIdx + 1)
        return singleEditAction(
            title = "Long 리터럴 접미사 'l' 을 'L' 로 변경",
            kind = kindForLongSuffix,
            preferred = true,
            uri = uri,
            startLine = sl,
            startCharacter = sc,
            endLine = el,
            endCharacter = ec,
            newText = "L",
        )
    }

    private fun removeUselessCast(
        uri: String,
        text: String,
        d: Diagnostic,
    ): CodeActionEntry? {
        val from = lineColToOffset(text, d.start.line, d.start.character) ?: return null
        val to = lineColToOffset(text, d.end.line, d.end.character) ?: return null
        if (to <= from || to > text.length) return null
        val asOffset = findAsKeyword(text, from, to) ?: return null
        var leftEdge = asOffset
        while (leftEdge > from && text[leftEdge - 1] == ' ') leftEdge--
        val (sl, sc) = offsetToLineCol(text, leftEdge)
        val (el, ec) = offsetToLineCol(text, to)
        return singleEditAction(
            title = "불필요한 'as' 캐스트 삭제",
            kind = kindForUselessCast,
            preferred = true,
            uri = uri,
            startLine = sl,
            startCharacter = sc,
            endLine = el,
            endCharacter = ec,
            newText = "",
        )
    }

    private fun removeRedundantElse(
        uri: String,
        text: String,
        d: Diagnostic,
    ): CodeActionEntry? {
        val lines = text.split('\n')
        if (d.start.line !in lines.indices) return null
        val entryLine = lines[d.start.line]
        val leadingIndentLen = entryLine.takeWhile { it == ' ' || it == '\t' }.length
        if (d.start.character != leadingIndentLen) return null
        val isLastLine = d.start.line == lines.size - 1
        val endLine: Int
        val endChar: Int
        if (isLastLine) {
            endLine = d.start.line
            endChar = entryLine.length
        } else {
            endLine = d.start.line + 1
            endChar = 0
        }
        return singleEditAction(
            title = "불필요한 'else' 분기 삭제",
            kind = kindForRedundantElse,
            preferred = true,
            uri = uri,
            startLine = d.start.line,
            startCharacter = 0,
            endLine = endLine,
            endCharacter = endChar,
            newText = "",
        )
    }

    private fun removeUselessElvis(
        uri: String,
        text: String,
        d: Diagnostic,
    ): CodeActionEntry? {
        val from = lineColToOffset(text, d.start.line, d.start.character) ?: return null
        val to = lineColToOffset(text, d.end.line, d.end.character) ?: return null
        if (to <= from || to > text.length) return null
        val elvisIdx = findLastElvis(text, from, to) ?: return null
        var start = elvisIdx
        while (start > from && (text[start - 1] == ' ' || text[start - 1] == '\t')) start--
        val (sl, sc) = offsetToLineCol(text, start)
        val (el, ec) = offsetToLineCol(text, to)
        return singleEditAction(
            title = "불필요한 '?:' 절 제거",
            kind = kindForUselessElvis,
            preferred = true,
            uri = uri,
            startLine = sl,
            startCharacter = sc,
            endLine = el,
            endCharacter = ec,
            newText = "",
        )
    }

    private fun findLastElvis(text: String, from: Int, to: Int): Int? {
        var i = from
        var state = ScanState.CODE
        var last = -1
        while (i < to) {
            val ch = text[i]
            when (state) {
                ScanState.CODE -> when {
                    ch == '"' && i + 2 < to && text[i + 1] == '"' && text[i + 2] == '"' -> {
                        state = ScanState.RAW_STRING; i += 3; continue
                    }
                    ch == '"' -> { state = ScanState.STRING; i++; continue }
                    ch == '\'' -> { state = ScanState.CHAR; i++; continue }
                    ch == '/' && i + 1 < to && text[i + 1] == '/' -> {
                        state = ScanState.LINE_COMMENT; i += 2; continue
                    }
                    ch == '/' && i + 1 < to && text[i + 1] == '*' -> {
                        state = ScanState.BLOCK_COMMENT; i += 2; continue
                    }
                    ch == '?' && i + 1 < to && text[i + 1] == ':' -> {
                        last = i; i += 2; continue
                    }
                }
                ScanState.LINE_COMMENT -> if (ch == '\n') state = ScanState.CODE
                ScanState.BLOCK_COMMENT -> if (ch == '*' && i + 1 < to && text[i + 1] == '/') {
                    state = ScanState.CODE; i += 2; continue
                }
                ScanState.STRING -> when {
                    ch == '\\' && i + 1 < to -> { i += 2; continue }
                    ch == '"' -> state = ScanState.CODE
                    ch == '\n' -> state = ScanState.CODE
                }
                ScanState.RAW_STRING -> if (ch == '"' && i + 2 < to && text[i + 1] == '"' && text[i + 2] == '"') {
                    state = ScanState.CODE; i += 3; continue
                }
                ScanState.CHAR -> when {
                    ch == '\\' && i + 1 < to -> { i += 2; continue }
                    ch == '\'' -> state = ScanState.CODE
                }
            }
            i++
        }
        return if (last >= 0) last else null
    }

    private fun findAsKeyword(text: String, from: Int, to: Int): Int? {
        var i = from
        var state = ScanState.CODE
        while (i < to) {
            val ch = text[i]
            when (state) {
                ScanState.CODE -> when {
                    ch == '"' && i + 2 < to && text[i + 1] == '"' && text[i + 2] == '"' -> {
                        state = ScanState.RAW_STRING; i += 3; continue
                    }
                    ch == '"' -> { state = ScanState.STRING; i++; continue }
                    ch == '\'' -> { state = ScanState.CHAR; i++; continue }
                    ch == '/' && i + 1 < to && text[i + 1] == '/' -> {
                        state = ScanState.LINE_COMMENT; i += 2; continue
                    }
                    ch == '/' && i + 1 < to && text[i + 1] == '*' -> {
                        state = ScanState.BLOCK_COMMENT; i += 2; continue
                    }
                    ch == 'a' && i + 1 < to && text[i + 1] == 's' &&
                        (i == 0 || !isIdentPart(text[i - 1])) &&
                        (i + 2 >= text.length || !isIdentPart(text[i + 2])) -> return i
                }
                ScanState.LINE_COMMENT -> if (ch == '\n') state = ScanState.CODE
                ScanState.BLOCK_COMMENT -> if (ch == '*' && i + 1 < to && text[i + 1] == '/') {
                    state = ScanState.CODE; i += 2; continue
                }
                ScanState.STRING -> when {
                    ch == '\\' && i + 1 < to -> { i += 2; continue }
                    ch == '"' -> state = ScanState.CODE
                    ch == '\n' -> state = ScanState.CODE
                }
                ScanState.RAW_STRING -> if (ch == '"' && i + 2 < to && text[i + 1] == '"' && text[i + 2] == '"') {
                    state = ScanState.CODE; i += 3; continue
                }
                ScanState.CHAR -> when {
                    ch == '\\' && i + 1 < to -> { i += 2; continue }
                    ch == '\'' -> state = ScanState.CODE
                }
            }
            i++
        }
        return null
    }

    private fun isIdentPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    private fun singleEditAction(
        title: String,
        kind: String,
        preferred: Boolean,
        uri: String,
        startLine: Int,
        startCharacter: Int,
        endLine: Int,
        endCharacter: Int,
        newText: String,
    ): CodeActionEntry = CodeActionEntry(
        title = title,
        kind = kind,
        isPreferred = preferred,
        edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(
                        RenameEdit(
                            startLine = startLine,
                            startCharacter = startCharacter,
                            endLine = endLine,
                            endCharacter = endCharacter,
                            newText = newText,
                        ),
                    ),
                ),
            ),
        ),
        command = null,
    )

    private fun addElseBranch(
        uri: String,
        text: String,
        d: Diagnostic,
    ): CodeActionEntry? {
        val from = lineColToOffset(text, d.start.line, d.start.character) ?: return null
        val openOffset = findFirstBraceAfter(text, from) ?: return null
        val closeOffset = findMatchingClose(text, openOffset) ?: return null
        val (closeLine, closeCol) = offsetToLineCol(text, closeOffset)
        val (openLine, _) = offsetToLineCol(text, openOffset)
        if (closeLine <= openLine) return null
        val lines = text.split('\n')
        if (closeLine !in lines.indices) return null
        val closeLineText = lines[closeLine]
        val closeIndent = closeLineText.takeWhile { it == ' ' || it == '\t' }
        if (closeIndent.length != closeCol) return null
        val branchIndent = (openLine + 1 until closeLine)
            .asSequence()
            .map { lines[it] }
            .firstOrNull { it.isNotBlank() }
            ?.takeWhile { it == ' ' || it == '\t' }
            ?: (closeIndent + "    ")
        val extraIndent = if (branchIndent.startsWith(closeIndent)) {
            branchIndent.substring(closeIndent.length)
        } else {
            "    "
        }
        val newText = "${extraIndent}else -> TODO()\n$closeIndent"
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(
                        RenameEdit(
                            startLine = closeLine,
                            startCharacter = closeCol,
                            endLine = closeLine,
                            endCharacter = closeCol,
                            newText = newText,
                        ),
                    ),
                ),
            ),
        )
        return CodeActionEntry(
            title = "when 에 'else -> TODO()' 분기 추가",
            kind = kindForWhen,
            isPreferred = true,
            edit = edit,
            command = null,
        )
    }

    private enum class ScanState { CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, RAW_STRING, CHAR }

    private fun findFirstBraceAfter(text: String, fromOffset: Int): Int? {
        var i = fromOffset.coerceAtLeast(0)
        var state = ScanState.CODE
        while (i < text.length) {
            val ch = text[i]
            when (state) {
                ScanState.CODE -> when {
                    ch == '"' && i + 2 < text.length && text[i + 1] == '"' && text[i + 2] == '"' -> {
                        state = ScanState.RAW_STRING; i += 3; continue
                    }
                    ch == '"' -> { state = ScanState.STRING; i++; continue }
                    ch == '\'' -> { state = ScanState.CHAR; i++; continue }
                    ch == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                        state = ScanState.LINE_COMMENT; i += 2; continue
                    }
                    ch == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                        state = ScanState.BLOCK_COMMENT; i += 2; continue
                    }
                    ch == '{' -> return i
                }
                ScanState.LINE_COMMENT -> if (ch == '\n') state = ScanState.CODE
                ScanState.BLOCK_COMMENT -> if (ch == '*' && i + 1 < text.length && text[i + 1] == '/') {
                    state = ScanState.CODE; i += 2; continue
                }
                ScanState.STRING -> when {
                    ch == '\\' && i + 1 < text.length -> { i += 2; continue }
                    ch == '"' -> state = ScanState.CODE
                    ch == '\n' -> state = ScanState.CODE
                }
                ScanState.RAW_STRING -> if (ch == '"' && i + 2 < text.length && text[i + 1] == '"' && text[i + 2] == '"') {
                    state = ScanState.CODE; i += 3; continue
                }
                ScanState.CHAR -> when {
                    ch == '\\' && i + 1 < text.length -> { i += 2; continue }
                    ch == '\'' -> state = ScanState.CODE
                }
            }
            i++
        }
        return null
    }

    private fun findMatchingClose(text: String, openOffset: Int): Int? {
        if (openOffset < 0 || openOffset >= text.length || text[openOffset] != '{') return null
        var i = openOffset + 1
        var depth = 1
        var state = ScanState.CODE
        while (i < text.length) {
            val ch = text[i]
            when (state) {
                ScanState.CODE -> when {
                    ch == '"' && i + 2 < text.length && text[i + 1] == '"' && text[i + 2] == '"' -> {
                        state = ScanState.RAW_STRING; i += 3; continue
                    }
                    ch == '"' -> { state = ScanState.STRING; i++; continue }
                    ch == '\'' -> { state = ScanState.CHAR; i++; continue }
                    ch == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                        state = ScanState.LINE_COMMENT; i += 2; continue
                    }
                    ch == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                        state = ScanState.BLOCK_COMMENT; i += 2; continue
                    }
                    ch == '{' -> depth++
                    ch == '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
                ScanState.LINE_COMMENT -> if (ch == '\n') state = ScanState.CODE
                ScanState.BLOCK_COMMENT -> if (ch == '*' && i + 1 < text.length && text[i + 1] == '/') {
                    state = ScanState.CODE; i += 2; continue
                }
                ScanState.STRING -> when {
                    ch == '\\' && i + 1 < text.length -> { i += 2; continue }
                    ch == '"' -> state = ScanState.CODE
                    ch == '\n' -> state = ScanState.CODE
                }
                ScanState.RAW_STRING -> if (ch == '"' && i + 2 < text.length && text[i + 1] == '"' && text[i + 2] == '"') {
                    state = ScanState.CODE; i += 3; continue
                }
                ScanState.CHAR -> when {
                    ch == '\\' && i + 1 < text.length -> { i += 2; continue }
                    ch == '\'' -> state = ScanState.CODE
                }
            }
            i++
        }
        return null
    }

    private fun lineColToOffset(text: String, line: Int, col: Int): Int? {
        if (line < 0 || col < 0) return null
        var li = 0
        var ci = 0
        for (i in text.indices) {
            if (li == line && ci == col) return i
            if (text[i] == '\n') {
                if (li == line) return i
                li++; ci = 0
            } else {
                ci++
            }
        }
        return if (li == line && ci >= col) text.length else null
    }

    private fun offsetToLineCol(text: String, offset: Int): Pair<Int, Int> {
        val bound = offset.coerceIn(0, text.length)
        var line = 0
        var col = 0
        for (i in 0 until bound) {
            if (text[i] == '\n') { line++; col = 0 } else col++
        }
        return line to col
    }
}
