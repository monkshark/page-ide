package page.editor

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

data class TodoItem(
    val uri: String,
    val line: Int,
    val column: Int,
    val keyword: String,
    val message: String,
    val rawLine: String,
)

object TodoScanner {

    fun scanText(uri: String, text: String, lexer: SyntaxLexer): List<TodoItem> {
        if (text.isEmpty()) return emptyList()
        val tokens = lexer.tokenize(text)
        if (tokens.none { it.kind == TokenKind.TODO_TAG }) return emptyList()
        val out = mutableListOf<TodoItem>()
        val lineStartOffsets = computeLineStartOffsets(text)
        for (t in tokens) {
            if (t.kind != TokenKind.TODO_TAG) continue
            val (line, col) = offsetToLineCol(t.range.first, lineStartOffsets)
            val span = text.substring(t.range.first, (t.range.last + 1).coerceAtMost(text.length))
            val colonIdx = span.indexOf(':')
            val keyword = if (colonIdx > 0) span.substring(0, colonIdx) else span
            val message = if (colonIdx > 0) span.substring(colonIdx + 1).trim() else ""
            val rawLine = readLine(text, lineStartOffsets, line)
            out += TodoItem(uri, line, col, keyword, message, rawLine)
        }
        return out
    }

    fun scanFile(path: Path): List<TodoItem> {
        if (!path.isRegularFile()) return emptyList()
        val lexer = SyntaxLexers.forPath(path) ?: return emptyList()
        val text = try {
            Files.readString(path)
        } catch (e: Exception) {
            return emptyList()
        }
        val uri = path.toUri().toString()
        return scanText(uri, text, lexer)
    }

    fun scanWorkspace(
        root: Path,
        exclude: (Path) -> Boolean = defaultExcludes,
    ): List<TodoItem> {
        if (!Files.isDirectory(root)) return emptyList()
        val out = mutableListOf<TodoItem>()
        Files.walk(root).use { stream ->
            stream.forEach { path ->
                if (!path.isRegularFile()) return@forEach
                if (exclude(path)) return@forEach
                out += scanFile(path)
            }
        }
        return out
    }

    val defaultExcludes: (Path) -> Boolean = { p ->
        val parts = p.toString().replace('\\', '/').split('/').map { it.lowercase() }
        parts.any { it == ".git" || it == "build" || it == "out" || it == ".gradle" || it == "node_modules" || it == ".idea" }
    }

    private fun computeLineStartOffsets(text: String): IntArray {
        val starts = mutableListOf(0)
        for (i in text.indices) {
            if (text[i] == '\n') starts += i + 1
        }
        return starts.toIntArray()
    }

    private fun offsetToLineCol(offset: Int, lineStartOffsets: IntArray): Pair<Int, Int> {
        var lo = 0
        var hi = lineStartOffsets.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (lineStartOffsets[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo to (offset - lineStartOffsets[lo])
    }

    private fun readLine(text: String, lineStartOffsets: IntArray, line: Int): String {
        val start = lineStartOffsets.getOrNull(line) ?: return ""
        val end = lineStartOffsets.getOrNull(line + 1)?.minus(1) ?: text.length
        val trimEnd = if (end > start && text[end - 1] == '\r') end - 1 else end
        return text.substring(start, trimEnd.coerceAtLeast(start))
    }
}
