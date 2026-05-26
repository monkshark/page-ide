package page.app

import androidx.compose.ui.graphics.Color

data class TerminalStyle(
    val fg: Color? = null,
    val bg: Color? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
) {
    companion object {
        val Default = TerminalStyle()
    }
}

data class TerminalSpan(val text: String, val style: TerminalStyle)

data class TerminalLine(val spans: List<TerminalSpan>) {
    val plain: String by lazy { spans.joinToString("") { it.text } }
}

internal class MutableLine {
    private val chars = StringBuilder()
    private val styles = mutableListOf<TerminalStyle>()
    var cursorCol: Int = 0

    fun write(text: String, style: TerminalStyle) {
        for (ch in text) {
            while (chars.length < cursorCol) {
                chars.append(' ')
                styles.add(TerminalStyle.Default)
            }
            if (cursorCol < chars.length) {
                chars.setCharAt(cursorCol, ch)
                styles[cursorCol] = style
            } else {
                chars.append(ch)
                styles.add(style)
            }
            cursorCol++
        }
    }

    fun carriageReturn() {
        cursorCol = 0
    }

    fun backspace() {
        if (cursorCol > 0) {
            cursorCol--
            if (cursorCol == chars.length - 1) {
                chars.setLength(cursorCol)
                if (styles.size > cursorCol) styles.removeAt(styles.size - 1)
            }
        }
    }

    fun clearFromCursor() {
        if (cursorCol < chars.length) {
            chars.setLength(cursorCol)
            while (styles.size > cursorCol) styles.removeAt(styles.size - 1)
        }
    }

    fun clearToCursor() {
        val limit = minOf(cursorCol, chars.length)
        for (i in 0 until limit) {
            chars.setCharAt(i, ' ')
            styles[i] = TerminalStyle.Default
        }
    }

    fun clearAll() {
        chars.clear()
        styles.clear()
        cursorCol = 0
    }

    val plain: String get() = chars.toString()

    fun toSpans(): List<TerminalSpan> {
        if (chars.isEmpty()) return emptyList()
        val result = mutableListOf<TerminalSpan>()
        var start = 0
        var curStyle = styles[0]
        for (i in 1..chars.length) {
            if (i == chars.length || styles[i] != curStyle) {
                result.add(TerminalSpan(chars.substring(start, i), curStyle))
                if (i < chars.length) {
                    start = i
                    curStyle = styles[i]
                }
            }
        }
        return result
    }
}

class TerminalBuffer(private val maxLines: Int = 5000) {
    private val lines: ArrayDeque<MutableLine> =
        ArrayDeque<MutableLine>().apply { add(MutableLine()) }
    private var currentStyle: TerminalStyle = TerminalStyle.Default
    private val parser = AnsiParser()

    val snapshot: List<TerminalLine>
        get() = lines.map { TerminalLine(it.toSpans()) }

    fun feed(chunk: String) {
        parser.parseLegacy(chunk, this)
    }

    internal fun applyStyle(style: TerminalStyle) {
        currentStyle = style
    }

    internal fun resetStyle() {
        currentStyle = TerminalStyle.Default
    }

    internal fun writeText(text: String) {
        if (text.isEmpty()) return
        lines.last().write(text, currentStyle)
    }

    internal fun newline() {
        lines.addLast(MutableLine())
        while (lines.size > maxLines) lines.removeFirst()
    }

    internal fun carriageReturn() {
        lines.last().carriageReturn()
    }

    internal fun backspace() {
        lines.last().backspace()
    }

    internal fun clearLineFromCursor() {
        lines.last().clearFromCursor()
    }

    internal fun clearLineToCursor() {
        lines.last().clearToCursor()
    }

    internal fun clearLineAll() {
        lines.last().clearAll()
    }

    internal fun clearScreen() {
        lines.clear()
        lines.add(MutableLine())
    }
}
