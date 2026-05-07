package page.editor

import java.nio.file.Path

data class GrepHit(
    val offset: Int,
    val line: Int,
    val col: Int,
    val lineText: String,
    val matchStart: Int,
    val matchEnd: Int,
)

data class GrepFileResult(
    val file: IndexedFile,
    val hits: List<GrepHit>,
)

data class GrepStats(
    val files: Int,
    val hits: Int,
    val skippedBinary: Int,
    val truncated: Boolean,
)

data class GrepReport(
    val results: List<GrepFileResult>,
    val stats: GrepStats,
)

object ProjectGrep {

    private const val MAX_HITS_PER_FILE = 200
    private const val MAX_TOTAL_HITS = 5000
    private const val MAX_LINE_LENGTH = 4000
    private const val BINARY_PROBE = 8000

    fun search(
        files: List<IndexedFile>,
        query: String,
        caseSensitive: Boolean,
        loader: (Path) -> String?,
        cancelled: () -> Boolean = { false },
    ): GrepReport {
        if (query.isEmpty()) {
            return GrepReport(emptyList(), GrepStats(0, 0, 0, false))
        }
        val results = ArrayList<GrepFileResult>()
        var totalHits = 0
        var skippedBinary = 0
        var truncated = false
        for (f in files) {
            if (cancelled()) break
            if (totalHits >= MAX_TOTAL_HITS) {
                truncated = true
                break
            }
            if (!FileKinds.classify(f.path).isEditableAsText) continue
            val text = loader(f.path) ?: continue
            if (looksBinary(text)) {
                skippedBinary++
                continue
            }
            val budget = (MAX_TOTAL_HITS - totalHits).coerceAtMost(MAX_HITS_PER_FILE)
            val hits = scan(text, query, caseSensitive, budget)
            if (hits.isNotEmpty()) {
                results += GrepFileResult(f, hits)
                totalHits += hits.size
            }
        }
        return GrepReport(
            results = results,
            stats = GrepStats(
                files = results.size,
                hits = totalHits,
                skippedBinary = skippedBinary,
                truncated = truncated,
            ),
        )
    }

    private fun scan(
        text: String,
        query: String,
        caseSensitive: Boolean,
        budget: Int,
    ): List<GrepHit> {
        if (budget <= 0) return emptyList()
        val out = ArrayList<GrepHit>()
        val len = query.length
        var i = 0
        var line = 0
        var lineStart = 0
        var nextNewline = text.indexOf('\n')
        while (i <= text.length - len) {
            while (nextNewline in 0 until i) {
                line++
                lineStart = nextNewline + 1
                nextNewline = text.indexOf('\n', lineStart)
            }
            val matched = text.regionMatches(i, query, 0, len, ignoreCase = !caseSensitive)
            if (matched) {
                val lineEnd = if (nextNewline >= 0) nextNewline else text.length
                val rawLine = text.substring(lineStart, lineEnd)
                val matchInLine = i - lineStart
                val (clipped, clippedStart) = clipLine(rawLine, matchInLine)
                out += GrepHit(
                    offset = i,
                    line = line,
                    col = matchInLine,
                    lineText = clipped,
                    matchStart = clippedStart,
                    matchEnd = (clippedStart + len).coerceAtMost(clipped.length),
                )
                if (out.size >= budget) break
                i += len
            } else {
                i += 1
            }
        }
        return out
    }

    private fun clipLine(line: String, matchInLine: Int): Pair<String, Int> {
        if (line.length <= MAX_LINE_LENGTH) return line to matchInLine
        val window = MAX_LINE_LENGTH
        val half = window / 2
        val start = (matchInLine - half).coerceAtLeast(0)
        val end = (start + window).coerceAtMost(line.length)
        val realStart = (end - window).coerceAtLeast(0)
        return line.substring(realStart, end) to (matchInLine - realStart)
    }

    private fun looksBinary(text: String): Boolean {
        val limit = BINARY_PROBE.coerceAtMost(text.length)
        for (i in 0 until limit) {
            if (text[i].code == 0) return true
        }
        return false
    }
}
