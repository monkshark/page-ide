package page.editor

object Replace {
    data class Result(val text: String, val caret: Int, val replacedCount: Int)

    fun applyCurrent(text: String, range: IntRange, replacement: String): Result {
        val start = range.first
        val end = range.last + 1
        val newText = text.substring(0, start) + replacement + text.substring(end)
        val caret = start + replacement.length
        return Result(newText, caret, 1)
    }

    fun applyAll(text: String, matches: List<IntRange>, replacement: String): Result {
        if (matches.isEmpty()) return Result(text, 0, 0)
        val sb = StringBuilder()
        var prev = 0
        for (range in matches) {
            sb.append(text, prev, range.first)
            sb.append(replacement)
            prev = range.last + 1
        }
        sb.append(text, prev, text.length)
        return Result(sb.toString(), 0, matches.size)
    }
}
