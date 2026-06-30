package page.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

internal class LineMetrics private constructor(
    private val starts: IntArray,
    private val ends: IntArray,
) {
    val lineCount: Int get() = starts.size

    fun lineStart(line: Int): Int = starts[line.coerceIn(0, starts.size - 1)]

    fun lineEnd(line: Int): Int = ends[line.coerceIn(0, ends.size - 1)]

    fun lineLength(line: Int): Int {
        val i = line.coerceIn(0, starts.size - 1)
        return ends[i] - starts[i]
    }

    fun lineForOffset(offset: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun columnIn(line: Int, offset: Int): Int =
        (offset - lineStart(line)).coerceIn(0, lineLength(line))

    companion object {
        fun of(text: CharSequence): LineMetrics {
            val starts = ArrayList<Int>()
            val ends = ArrayList<Int>()
            var lineStart = 0
            var i = 0
            val n = text.length
            while (i < n) {
                if (text[i] == '\n') {
                    starts.add(lineStart)
                    ends.add(i)
                    lineStart = i + 1
                }
                i++
            }
            starts.add(lineStart)
            ends.add(n)
            return LineMetrics(starts.toIntArray(), ends.toIntArray())
        }
    }
}

internal class LineLayout(
    val text: AnnotatedString,
    private val lines: List<TextLayoutResult>,
    private val metrics: LineMetrics,
    val lineHeightPx: Float,
    private val maxWidthPx: Int,
) {
    val lineCount: Int get() = metrics.lineCount

    val size: IntSize get() = IntSize(maxWidthPx, ceil(lineHeightPx * lineCount).toInt())

    fun getLineForOffset(offset: Int): Int = metrics.lineForOffset(offset)

    fun getLineStart(line: Int): Int = metrics.lineStart(line)

    fun getLineEnd(line: Int, visibleEnd: Boolean = false): Int = metrics.lineEnd(line)

    fun getLineTop(line: Int): Float = line.coerceIn(0, lineCount - 1) * lineHeightPx

    fun getLineBottom(line: Int): Float = (line.coerceIn(0, lineCount - 1) + 1) * lineHeightPx

    fun getCursorRect(offset: Int): Rect {
        val line = metrics.lineForOffset(offset)
        val col = metrics.columnIn(line, offset)
        return lines[line].getCursorRect(col).translate(0f, line * lineHeightPx)
    }

    fun getOffsetForPosition(position: Offset): Int {
        if (lineCount == 0) return 0
        val line = (position.y / lineHeightPx).toInt().coerceIn(0, lineCount - 1)
        val localY = (position.y - line * lineHeightPx).coerceIn(0f, lineHeightPx - 1f)
        val col = lines[line].getOffsetForPosition(Offset(position.x, localY))
        return metrics.lineStart(line) + col
    }

    fun getSelectionPath(start: Int, end: Int): Path {
        val path = Path()
        if (start >= end) return path
        val startLine = metrics.lineForOffset(start)
        val endLine = metrics.lineForOffset(end)
        val sliver = lineHeightPx * 0.4f
        for (line in startLine..endLine) {
            val tlr = lines[line]
            val lineRight = tlr.size.width.toFloat()
            val left = if (line == startLine) {
                tlr.getCursorRect(metrics.columnIn(line, start)).left
            } else {
                0f
            }
            var right = if (line == endLine) {
                tlr.getCursorRect(metrics.columnIn(line, end)).left
            } else {
                lineRight
            }
            if (line != endLine && right <= left) right = left + sliver
            val top = line * lineHeightPx
            if (right > left) {
                path.addRect(Rect(left, top, right, top + lineHeightPx))
            }
        }
        return path
    }

    fun draw(scope: DrawScope) {
        for (line in 0 until lineCount) {
            scope.drawText(lines[line], topLeft = Offset(0f, line * lineHeightPx))
        }
    }
}

internal class LineLayoutCache(private val measurer: TextMeasurer) {
    private var cache: HashMap<AnnotatedString, TextLayoutResult> = HashMap()
    private var lastStyle: TextStyle? = null

    fun layout(text: AnnotatedString, style: TextStyle): LineLayout {
        if (style != lastStyle) {
            cache = HashMap()
            lastStyle = style
        }
        val metrics = LineMetrics.of(text)
        val next = HashMap<AnnotatedString, TextLayoutResult>(metrics.lineCount * 2)
        val lines = ArrayList<TextLayoutResult>(metrics.lineCount)
        var maxWidth = 0
        for (line in 0 until metrics.lineCount) {
            val slice = text.subSequence(metrics.lineStart(line), metrics.lineEnd(line))
            val measured = cache[slice]
                ?: next[slice]
                ?: measurer.measure(text = slice, style = style, softWrap = false)
            next[slice] = measured
            lines.add(measured)
            if (measured.size.width > maxWidth) maxWidth = measured.size.width
        }
        cache = next
        val lineHeight = lines.firstOrNull()?.getLineBottom(0)
            ?: measurer.measure(AnnotatedString(""), style = style, softWrap = false).getLineBottom(0)
        return LineLayout(text, lines, metrics, lineHeight, maxWidth)
    }
}
