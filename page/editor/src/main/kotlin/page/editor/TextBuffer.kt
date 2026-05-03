package page.editor

data class LineCol(val line: Int, val col: Int)

class TextBuffer(initial: String = "") {
    private val sb = StringBuilder(initial)

    val length: Int get() = sb.length

    val lineCount: Int
        get() {
            var count = 1
            for (i in 0 until sb.length) {
                if (sb[i] == '\n') count++
            }
            return count
        }

    fun text(): String = sb.toString()

    fun lineAt(index: Int): String {
        require(index in 0 until lineCount) { "line $index out of bounds (lineCount=$lineCount)" }
        val start = lineStartOffset(index)
        val end = lineEndOffset(index)
        return sb.substring(start, end)
    }

    fun insert(offset: Int, text: String) {
        require(offset in 0..sb.length) { "offset $offset out of bounds (length=${sb.length})" }
        sb.insert(offset, text)
    }

    fun delete(start: Int, end: Int) {
        require(start in 0..sb.length) { "start $start out of bounds (length=${sb.length})" }
        require(end in start..sb.length) { "end $end out of bounds (start=$start, length=${sb.length})" }
        sb.delete(start, end)
    }

    fun insertAt(line: Int, col: Int, text: String) {
        insert(offsetOf(line, col), text)
    }

    fun deleteAt(startLine: Int, startCol: Int, endLine: Int, endCol: Int) {
        delete(offsetOf(startLine, startCol), offsetOf(endLine, endCol))
    }

    fun offsetOf(line: Int, col: Int): Int {
        require(line in 0 until lineCount) { "line $line out of bounds (lineCount=$lineCount)" }
        val start = lineStartOffset(line)
        val end = lineEndOffset(line)
        require(col in 0..(end - start)) { "col $col out of bounds (line length=${end - start})" }
        return start + col
    }

    fun lineColOf(offset: Int): LineCol {
        require(offset in 0..sb.length) { "offset $offset out of bounds (length=${sb.length})" }
        var line = 0
        var lineStart = 0
        for (i in 0 until offset) {
            if (sb[i] == '\n') {
                line++
                lineStart = i + 1
            }
        }
        return LineCol(line, offset - lineStart)
    }

    private fun lineStartOffset(line: Int): Int {
        if (line == 0) return 0
        var seen = 0
        for (i in 0 until sb.length) {
            if (sb[i] == '\n') {
                seen++
                if (seen == line) return i + 1
            }
        }
        return sb.length
    }

    private fun lineEndOffset(line: Int): Int {
        val start = lineStartOffset(line)
        for (i in start until sb.length) {
            if (sb[i] == '\n') return i
        }
        return sb.length
    }
}
