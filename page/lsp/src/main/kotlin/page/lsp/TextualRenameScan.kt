package page.lsp

object TextualRenameScan {

    data class Match(val line: Int, val column: Int)

    fun findQualifiedReceiverMatches(text: String, name: String): List<Match> {
        if (name.isEmpty()) return emptyList()
        val matches = mutableListOf<Match>()
        var i = 0
        var line = 0
        var lineStart = 0

        while (i < text.length) {
            val c = text[i]
            when {
                c == '\n' -> {
                    line++
                    i++
                    lineStart = i
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    i += 2
                    while (i < text.length && text[i] != '\n') i++
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2
                    while (i < text.length) {
                        if (text[i] == '\n') {
                            line++
                            i++
                            lineStart = i
                        } else if (text[i] == '*' && i + 1 < text.length && text[i + 1] == '/') {
                            i += 2
                            break
                        } else {
                            i++
                        }
                    }
                }
                c == '"' && i + 2 < text.length && text[i + 1] == '"' && text[i + 2] == '"' -> {
                    i += 3
                    while (i < text.length) {
                        if (text[i] == '\n') {
                            line++
                            i++
                            lineStart = i
                        } else if (
                            text[i] == '"' && i + 2 < text.length &&
                            text[i + 1] == '"' && text[i + 2] == '"'
                        ) {
                            i += 3
                            break
                        } else {
                            i++
                        }
                    }
                }
                c == '"' -> {
                    i++
                    while (i < text.length) {
                        val cc = text[i]
                        if (cc == '\\' && i + 1 < text.length) {
                            if (text[i + 1] == '\n') {
                                line++
                                lineStart = i + 2
                            }
                            i += 2
                            continue
                        }
                        if (cc == '\n') {
                            line++
                            i++
                            lineStart = i
                            break
                        }
                        if (cc == '"') {
                            i++
                            break
                        }
                        i++
                    }
                }
                c == '\'' -> {
                    i++
                    while (i < text.length) {
                        val cc = text[i]
                        if (cc == '\\' && i + 1 < text.length) {
                            i += 2
                            continue
                        }
                        if (cc == '\n') {
                            line++
                            i++
                            lineStart = i
                            break
                        }
                        if (cc == '\'') {
                            i++
                            break
                        }
                        i++
                    }
                }
                c.isJavaIdentifierStart() -> {
                    val prev = if (i > 0) text[i - 1] else ' '
                    val boundary = !prev.isJavaIdentifierPart() && prev != '.'
                    if (boundary && matchesAt(text, i, name)) {
                        val end = i + name.length
                        if (end + 1 < text.length) {
                            val a = text[end]
                            val b = text[end + 1]
                            val qualified = (a == '.' && b.isJavaIdentifierStart()) ||
                                (
                                    a == ':' && b == ':' &&
                                        end + 2 < text.length &&
                                        text[end + 2].isJavaIdentifierStart()
                                    )
                            if (qualified) {
                                matches.add(Match(line, i - lineStart))
                            }
                        }
                    }
                    i++
                    while (i < text.length && text[i].isJavaIdentifierPart()) i++
                }
                else -> i++
            }
        }
        return matches
    }

    fun findAllMatches(text: String, name: String): List<Match> {
        if (name.isEmpty()) return emptyList()
        val matches = mutableListOf<Match>()
        var i = 0
        var line = 0
        var lineStart = 0

        while (i < text.length) {
            val c = text[i]
            when {
                c == '\n' -> {
                    line++
                    i++
                    lineStart = i
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    i += 2
                    while (i < text.length && text[i] != '\n') i++
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2
                    while (i < text.length) {
                        if (text[i] == '\n') {
                            line++
                            i++
                            lineStart = i
                        } else if (text[i] == '*' && i + 1 < text.length && text[i + 1] == '/') {
                            i += 2
                            break
                        } else {
                            i++
                        }
                    }
                }
                c == '"' && i + 2 < text.length && text[i + 1] == '"' && text[i + 2] == '"' -> {
                    i += 3
                    while (i < text.length) {
                        if (text[i] == '\n') {
                            line++
                            i++
                            lineStart = i
                        } else if (
                            text[i] == '"' && i + 2 < text.length &&
                            text[i + 1] == '"' && text[i + 2] == '"'
                        ) {
                            i += 3
                            break
                        } else {
                            i++
                        }
                    }
                }
                c == '"' -> {
                    i++
                    while (i < text.length) {
                        val cc = text[i]
                        if (cc == '\\' && i + 1 < text.length) {
                            if (text[i + 1] == '\n') {
                                line++
                                lineStart = i + 2
                            }
                            i += 2
                            continue
                        }
                        if (cc == '\n') {
                            line++
                            i++
                            lineStart = i
                            break
                        }
                        if (cc == '"') {
                            i++
                            break
                        }
                        i++
                    }
                }
                c == '\'' -> {
                    i++
                    while (i < text.length) {
                        val cc = text[i]
                        if (cc == '\\' && i + 1 < text.length) {
                            i += 2
                            continue
                        }
                        if (cc == '\n') {
                            line++
                            i++
                            lineStart = i
                            break
                        }
                        if (cc == '\'') {
                            i++
                            break
                        }
                        i++
                    }
                }
                c.isJavaIdentifierStart() -> {
                    val prev = if (i > 0) text[i - 1] else ' '
                    val boundary = !prev.isJavaIdentifierPart() && prev != '.'
                    if (boundary && matchesAt(text, i, name)) {
                        matches.add(Match(line, i - lineStart))
                    }
                    i++
                    while (i < text.length && text[i].isJavaIdentifierPart()) i++
                }
                else -> i++
            }
        }
        return matches
    }

    fun findImportMatches(text: String, name: String, expectedPackage: String? = null): List<Match> {
        if (name.isEmpty()) return emptyList()
        val matches = mutableListOf<Match>()
        var i = 0
        var line = 0
        var lineStart = 0

        while (i < text.length) {
            val lineEnd = run {
                var j = i
                while (j < text.length && text[j] != '\n') j++
                j
            }
            val rawLine = text.substring(i, lineEnd)
            val trimmed = rawLine.trimStart()
            val leading = rawLine.length - trimmed.length
            if (trimmed.startsWith("import") &&
                (trimmed.length == 6 || !trimmed[6].isJavaIdentifierPart())
            ) {
                var p = leading + 6
                while (p < rawLine.length && rawLine[p].isWhitespace()) p++
                val pathStart = p
                val pathEnd = run {
                    var q = pathStart
                    while (q < rawLine.length) {
                        val ch = rawLine[q]
                        if (ch.isJavaIdentifierPart() || ch == '.') q++ else break
                    }
                    q
                }
                if (pathEnd > pathStart) {
                    var seg = pathStart
                    while (seg < pathEnd) {
                        val nextDot = rawLine.indexOf('.', seg).let { if (it == -1 || it >= pathEnd) pathEnd else it }
                        val len = nextDot - seg
                        if (len == name.length) {
                            var ok = true
                            for (k in 0 until len) {
                                if (rawLine[seg + k] != name[k]) { ok = false; break }
                            }
                            if (ok) {
                                val packageMatches = expectedPackage == null ||
                                    importSegmentsBefore(rawLine, pathStart, seg) == expectedPackage
                                if (packageMatches) matches.add(Match(line, seg))
                            }
                        }
                        seg = nextDot + 1
                    }
                }
            }
            i = lineEnd
            if (i < text.length && text[i] == '\n') {
                line++
                i++
                lineStart = i
            }
        }
        return matches
    }

    private fun importSegmentsBefore(rawLine: String, pathStart: Int, segStart: Int): String {
        if (segStart <= pathStart) return ""
        val end = if (rawLine[segStart - 1] == '.') segStart - 1 else segStart
        if (end <= pathStart) return ""
        return rawLine.substring(pathStart, end)
    }

    private fun matchesAt(text: String, idx: Int, name: String): Boolean {
        if (idx + name.length > text.length) return false
        for (k in name.indices) {
            if (text[idx + k] != name[k]) return false
        }
        if (idx + name.length < text.length) {
            val next = text[idx + name.length]
            if (next.isJavaIdentifierPart()) return false
        }
        return true
    }
}
