package page.language

import page.editor.Token
import page.editor.TokenKind
import page.lsp.DefinitionTarget
import page.lsp.ReferenceLocation

internal data class ClampedInlayRange(
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int,
)

internal fun clampInlayHintRange(
    syncedText: String,
    startLine: Int,
    startCharacter: Int,
    endLine: Int,
    endCharacter: Int,
): ClampedInlayRange {
    val lines = syncedText.split('\n')
    val maxLine = (lines.size - 1).coerceAtLeast(0)
    val endL = endLine.coerceIn(0, maxLine)
    val endC = if (endL == endLine) endCharacter else lines[endL].length
    val startL = startLine.coerceIn(0, endL)
    val startC = if (startL == startLine) startCharacter else 0
    return ClampedInlayRange(startL, startC, endL, endC)
}

internal fun isMemberAccessContext(text: String, line: Int, character: Int, prefixLength: Int): Boolean {
    val lines = text.split('\n')
    if (line < 0 || line >= lines.size) return false
    val raw = lines[line]
    val ln = if (raw.endsWith('\r')) raw.dropLast(1) else raw
    val wordStart = (character - prefixLength).coerceIn(0, ln.length)
    if (wordStart <= 0) return false
    return ln[wordStart - 1] == '.'
}

internal fun computePrefix(text: String, line: Int, character: Int): String {
    val lines = text.split('\n')
    if (line < 0 || line >= lines.size) return ""
    val raw = lines[line]
    val ln = if (raw.endsWith('\r')) raw.dropLast(1) else raw
    val col = character.coerceIn(0, ln.length)
    var start = col
    while (start > 0) {
        val c = ln[start - 1]
        if (!c.isLetterOrDigit() && c != '_') break
        start--
    }
    return ln.substring(start, col)
}

internal fun rangeOverlaps(
    aSL: Int, aSC: Int, aEL: Int, aEC: Int,
    bSL: Int, bSC: Int, bEL: Int, bEC: Int,
): Boolean {
    val aAfterB = aSL > bEL || (aSL == bEL && aSC > bEC)
    val bAfterA = bSL > aEL || (bSL == aEL && bSC > aEC)
    return !aAfterB && !bAfterA
}

internal fun isNamedArgumentPosition(text: String, idx: Int, endExclusive: Int): Boolean {
    var p = idx - 1
    while (p >= 0 && (text[p] == ' ' || text[p] == '\t')) p--
    if (p < 0) return false
    if (text[p] != '(' && text[p] != ',') return false
    var q = endExclusive
    while (q < text.length && (text[q] == ' ' || text[q] == '\t')) q++
    if (q >= text.length || text[q] != '=') return false
    if (q + 1 < text.length && text[q + 1] == '=') return false
    return true
}

internal fun stringCommentRanges(tokens: List<Token>): List<Pair<Int, Int>> = tokens
    .filter { it.kind == TokenKind.STRING || it.kind == TokenKind.COMMENT || it.kind == TokenKind.DOC_COMMENT }
    .map { it.range.first to (it.range.last + 1) }
    .sortedBy { it.first }

internal fun lineColToOffset(text: String, line: Int, character: Int): Int? {
    if (line < 0) return null
    var idx = 0
    var l = 0
    while (idx < text.length && l < line) {
        if (text[idx] == '\n') l++
        idx++
    }
    if (l != line) return null
    var end = idx
    while (end < text.length && text[end] != '\n') end++
    val col = character.coerceIn(0, end - idx)
    return idx + col
}

internal fun findEnclosingFunctionRange(
    text: String,
    tokens: List<Token>,
    caret: Int,
    excluded: List<Pair<Int, Int>>,
): IntRange? {
    val funPositions = tokens.asSequence()
        .filter { it.kind == TokenKind.KEYWORD }
        .filter {
            val r = it.range
            val len = r.last + 1 - r.first
            len == 3 && text.regionMatches(r.first, "fun", 0, 3)
        }
        .map { it.range.first }
        .toList()
    val scopes = mutableListOf<IntRange>()
    for (funStart in funPositions) {
        var i = funStart + 3
        while (i < text.length && (text[i] != '{' || isInsideRange(i, excluded))) i++
        if (i >= text.length) continue
        var depth = 1
        i++
        while (i < text.length && depth > 0) {
            if (!isInsideRange(i, excluded)) {
                when (text[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            scopes += funStart..i
                            break
                        }
                    }
                }
            }
            i++
        }
    }
    return scopes.filter { caret in it }.minByOrNull { it.last - it.first }
}

internal fun hasLocalDeclaration(
    scopeText: String,
    name: String,
    excluded: List<Pair<Int, Int>>,
    offset: Int,
): Boolean {
    val escaped = Regex.escape(name)
    val patterns = listOf(
        Regex("\\b(val|var)\\s+$escaped\\b"),
        Regex("[\\s(,]$escaped\\s*:"),
    )
    for (re in patterns) {
        for (m in re.findAll(scopeText)) {
            val absOffset = offset + m.range.first
            if (!isInsideRange(absOffset, excluded)) return true
        }
    }
    return false
}

internal fun isInsideRange(offset: Int, sortedRanges: List<Pair<Int, Int>>): Boolean {
    if (sortedRanges.isEmpty()) return false
    var lo = 0
    var hi = sortedRanges.size - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val (s, e) = sortedRanges[mid]
        if (offset < s) hi = mid - 1
        else if (offset >= e) lo = mid + 1
        else return true
    }
    return false
}

internal fun computeLineStarts(text: String): IntArray {
    val starts = ArrayList<Int>(64)
    starts.add(0)
    for (i in text.indices) {
        if (text[i] == '\n') starts.add(i + 1)
    }
    return starts.toIntArray()
}

internal fun offsetToLineCol(offset: Int, lineStarts: IntArray): Pair<Int, Int> {
    var lo = 0
    var hi = lineStarts.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) ushr 1
        if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
    }
    return lo to (offset - lineStarts[lo])
}

internal fun lineAtIndex(text: String, line: Int): String? {
    if (line < 0) return null
    var idx = 0
    var l = 0
    while (idx < text.length && l < line) {
        if (text[idx] == '\n') l++
        idx++
    }
    if (l != line) return null
    var end = idx
    while (end < text.length && text[end] != '\n') end++
    val raw = text.substring(idx, end)
    return if (raw.endsWith('\r')) raw.dropLast(1) else raw
}

internal fun findWordBoundaryMatch(line: String, word: String, prefer: Int): Pair<Int, Int>? {
    if (word.isEmpty() || word.length > line.length) return null
    val matches = mutableListOf<Pair<Int, Int>>()
    var i = 0
    while (i <= line.length - word.length) {
        if (line.regionMatches(i, word, 0, word.length)) {
            val before = if (i == 0) ' ' else line[i - 1]
            val after = if (i + word.length >= line.length) ' ' else line[i + word.length]
            val boundedBefore = !before.isLetterOrDigit() && before != '_'
            val boundedAfter = !after.isLetterOrDigit() && after != '_'
            if (boundedBefore && boundedAfter) {
                matches += i to (i + word.length)
            }
        }
        i++
    }
    if (matches.isEmpty()) return null
    return matches.minByOrNull { kotlin.math.abs(it.first - prefer) }
}

internal fun mergeDeclarationIntoRefs(
    defs: List<DefinitionTarget>,
    refs: List<ReferenceLocation>,
): List<ReferenceLocation> {
    if (defs.isEmpty()) return refs
    val seen = refs.mapTo(HashSet()) { Triple(it.uri, it.startLine, it.startCharacter) }
    val extra = defs.mapNotNull { d ->
        val key = Triple(d.uri, d.startLine, d.startCharacter)
        if (key in seen) null else ReferenceLocation(
            uri = d.uri,
            startLine = d.startLine,
            startCharacter = d.startCharacter,
            endLine = d.endLine,
            endCharacter = d.endCharacter,
        )
    }
    return if (extra.isEmpty()) refs else extra + refs
}

internal fun nearestIdentifierStart(text: String, line: Int, character: Int): Int {
    var idx = 0
    var l = 0
    while (idx < text.length && l < line) {
        if (text[idx] == '\n') l++
        idx++
    }
    if (l != line) return character
    var end = idx
    while (end < text.length && text[end] != '\n') end++
    val lineText = text.substring(idx, end)
    val isIdent = { c: Char -> c.isLetterOrDigit() || c == '_' }
    val pos = character.coerceIn(0, lineText.length)
    if (pos < lineText.length && isIdent(lineText[pos])) {
        var start = pos
        while (start > 0 && isIdent(lineText[start - 1])) start--
        return start
    }
    var p = pos - 1
    while (p >= 0 && !isIdent(lineText[p])) p--
    if (p < 0) {
        var q = pos
        while (q < lineText.length && !isIdent(lineText[q])) q++
        if (q >= lineText.length) return character
        return q
    }
    var start = p
    while (start > 0 && isIdent(lineText[start - 1])) start--
    return start
}

internal fun lineContextAt(text: String, line: Int, character: Int): Pair<String, String> {
    var idx = 0
    var l = 0
    while (idx < text.length && l < line) {
        if (text[idx] == '\n') l++
        idx++
    }
    if (l != line) return "" to ""
    var end = idx
    while (end < text.length && text[end] != '\n') end++
    val lineText = text.substring(idx, end).take(120)
    val marker = " ".repeat(character.coerceAtLeast(0).coerceAtMost(lineText.length)) + "^"
    return lineText to marker
}
