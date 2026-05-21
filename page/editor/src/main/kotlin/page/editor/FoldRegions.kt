package page.editor

object FoldRegions {
    data class Region(
        val startLine: Int,
        val endLine: Int,
        val placeholderPrefix: String = "",
    )

    data class Segment(
        val origStart: Int,
        val origEnd: Int,
        val replacement: String,
        val closerOrigStart: Int = origStart,
        val closerInRepStart: Int = 0,
        val closerLength: Int = 0,
    )

    fun detect(text: String): List<Region> {
        val regions = mutableListOf<Region>()
        val stack = ArrayDeque<Int>()
        var line = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\n' -> { line++; i++ }
                c == '"' || c == '\'' -> {
                    val quote = c
                    i++
                    while (i < text.length) {
                        val cc = text[i]
                        if (cc == '\\' && i + 1 < text.length) {
                            if (text[i + 1] == '\n') line++
                            i += 2
                            continue
                        }
                        if (cc == '\n') { line++; i++; break }
                        if (cc == quote) { i++; break }
                        i++
                    }
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    i += 2
                    while (i < text.length && text[i] != '\n') i++
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2
                    while (i < text.length) {
                        if (text[i] == '\n') { line++; i++ }
                        else if (text[i] == '*' && i + 1 < text.length && text[i + 1] == '/') { i += 2; break }
                        else i++
                    }
                }
                c == '{' -> { stack.addLast(line); i++ }
                c == '}' -> {
                    val openLine = stack.removeLastOrNull()
                    if (openLine != null && openLine < line && !hasTrailingContent(text, i + 1)) {
                        regions.add(Region(openLine, line))
                    }
                    i++
                }
                else -> i++
            }
        }
        return regions.sortedBy { it.startLine }
    }

    private fun hasTrailingContent(text: String, afterCloser: Int): Boolean {
        var k = afterCloser
        while (k < text.length && text[k] != '\n') {
            if (!text[k].isWhitespace()) return true
            k++
        }
        return false
    }

    fun segmentsFor(text: String, foldedRegions: Collection<Region>): List<Segment> {
        if (foldedRegions.isEmpty()) return emptyList()
        val lineStarts = mutableListOf(0)
        for (i in text.indices) if (text[i] == '\n') lineStarts.add(i + 1)
        val totalLines = lineStarts.size

        fun lineEndIndex(lineIdx: Int): Int =
            if (lineIdx + 1 < totalLines) lineStarts[lineIdx + 1] - 1 else text.length

        val sorted = foldedRegions.distinct().sortedBy { it.startLine }
        val out = mutableListOf<Segment>()
        var lastConsumedEnd = -1
        for (r in sorted) {
            if (r.startLine < 0 || r.endLine >= totalLines || r.endLine <= r.startLine) continue
            val hasPrefix = r.placeholderPrefix.isNotEmpty()
            val origStart = if (hasPrefix) lineStarts[r.startLine] else lineEndIndex(r.startLine)
            val hasTrailingNewline = r.endLine + 1 < totalLines
            val origEnd = if (hasTrailingNewline) lineStarts[r.endLine + 1] else text.length
            val closer = if (hasPrefix) "" else computeCloser(text, lineStarts[r.endLine], lineEndIndex(r.endLine))
            val core = when {
                hasPrefix -> "${r.placeholderPrefix} ..."
                closer.isEmpty() -> " ..."
                else -> " ... $closer"
            }
            val replacement = if (hasTrailingNewline) "$core\n" else core
            val closerOrigStart = if (closer.isEmpty()) origStart else {
                var end = lineEndIndex(r.endLine)
                while (end > lineStarts[r.endLine] && text[end - 1].isWhitespace()) end--
                end - closer.length
            }
            val closerInRepStart = if (closer.isEmpty()) 0 else core.length - closer.length
            if (origStart < lastConsumedEnd) continue
            out.add(
                Segment(
                    origStart = origStart,
                    origEnd = origEnd,
                    replacement = replacement,
                    closerOrigStart = closerOrigStart,
                    closerInRepStart = closerInRepStart,
                    closerLength = closer.length,
                )
            )
            lastConsumedEnd = origEnd
        }
        return out
    }

    private fun computeCloser(text: String, lineStart: Int, lineEndExclusive: Int): String {
        var end = lineEndExclusive
        while (end > lineStart && text[end - 1].isWhitespace()) end--
        if (end <= lineStart) return ""
        if (end - lineStart >= 2 && text[end - 2] == '*' && text[end - 1] == '/') return "*/"
        return when (text[end - 1]) {
            '}', ')', ']' -> text[end - 1].toString()
            else -> ""
        }
    }

    fun originalToTransformed(segments: List<Segment>, original: Int): Int {
        if (segments.isEmpty()) return original
        var savings = 0
        for (seg in segments) {
            if (original < seg.origStart) return original - savings
            if (original < seg.origEnd) {
                val closerOrigEnd = seg.closerOrigStart + seg.closerLength
                return if (seg.closerLength > 0 && original in seg.closerOrigStart..closerOrigEnd) {
                    seg.origStart - savings + seg.closerInRepStart + (original - seg.closerOrigStart)
                } else {
                    seg.origStart - savings
                }
            }
            savings += (seg.origEnd - seg.origStart) - seg.replacement.length
        }
        return original - savings
    }

    fun transformedToOriginal(segments: List<Segment>, transformed: Int): Int {
        if (segments.isEmpty()) return transformed
        var savings = 0
        for (seg in segments) {
            val transStart = seg.origStart - savings
            val transEnd = transStart + seg.replacement.length
            if (transformed < transStart) return transformed + savings
            if (transformed < transEnd) {
                val relativeOff = transformed - transStart
                val closerInRepEnd = seg.closerInRepStart + seg.closerLength
                return if (seg.closerLength > 0 && relativeOff in seg.closerInRepStart..closerInRepEnd) {
                    seg.closerOrigStart + (relativeOff - seg.closerInRepStart)
                } else {
                    seg.origStart
                }
            }
            savings += (seg.origEnd - seg.origStart) - seg.replacement.length
        }
        return transformed + savings
    }

    fun foldedRegionAt(
        text: String,
        foldedRegions: Collection<Region>,
        transformedOffset: Int,
    ): Region? {
        if (foldedRegions.isEmpty()) return null
        val sorted = foldedRegions.distinct().sortedBy { it.startLine }
        val segments = segmentsFor(text, sorted)
        if (segments.isEmpty()) return null
        var savings = 0
        var hit: Segment? = null
        for (seg in segments) {
            val transStart = seg.origStart - savings
            val dotsOffset = seg.replacement.indexOf("...")
            if (dotsOffset < 0) {
                savings += (seg.origEnd - seg.origStart) - seg.replacement.length
                continue
            }
            val dotsEnd = transStart + dotsOffset + 3
            val hitStart = if (seg.replacement.startsWith(' ')) transStart + dotsOffset else transStart
            if (transformedOffset < hitStart) return null
            if (transformedOffset < dotsEnd) { hit = seg; break }
            savings += (seg.origEnd - seg.origStart) - seg.replacement.length
        }
        val target = hit ?: return null
        val lineStarts = mutableListOf(0)
        for (i in text.indices) if (text[i] == '\n') lineStarts.add(i + 1)
        val totalLines = lineStarts.size
        fun lineEndIndex(lineIdx: Int): Int =
            if (lineIdx + 1 < totalLines) lineStarts[lineIdx + 1] - 1 else text.length
        return sorted.firstOrNull { r ->
            r.startLine in 0 until totalLines &&
                r.endLine in 0 until totalLines &&
                r.endLine > r.startLine &&
                (
                    if (r.placeholderPrefix.isNotEmpty()) lineStarts[r.startLine] == target.origStart
                    else lineEndIndex(r.startLine) == target.origStart
                )
        }
    }
}
