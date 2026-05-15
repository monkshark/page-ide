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
                "UNUSED_VARIABLE" -> deleteDeclarationLine(uri, text, d)?.let(out::add)
                "UNUSED_PARAMETER" -> suppressUnusedParameter(uri, text, d)?.let(out::add)
                "NO_ELSE_IN_WHEN" -> addElseBranch(uri, text, d)?.let(out::add)
            }
        }
        return out
    }

    private const val kindForVar = "quickfix.page.unusedVariable"
    private const val kindForParam = "quickfix.page.unusedParameter"
    private const val kindForWhen = "quickfix.page.noElseInWhen"

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
