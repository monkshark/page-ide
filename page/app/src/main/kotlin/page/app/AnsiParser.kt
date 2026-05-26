package page.app

import androidx.compose.ui.graphics.Color

private const val ESC = 0x1B
private const val BEL = 0x07
private const val SO = 0x0E
private const val SI = 0x0F

internal class AnsiParser {
    private var style: TerminalStyle = TerminalStyle.Default

    fun parse(chunk: String, sink: TerminalGrid) {
        var i = 0
        while (i < chunk.length) {
            val code = chunk[i].code
            when {
                code == ESC -> {
                    val consumed = handleEscape(chunk, i, sink)
                    i += if (consumed == 0) 1 else consumed
                }
                chunk[i] == '\n' -> { sink.linefeed(); i += 1 }
                chunk[i] == '\r' -> { sink.carriageReturn(); i += 1 }
                chunk[i] == '\b' -> { sink.backspace(); i += 1 }
                chunk[i] == '\t' -> { sink.tab(); i += 1 }
                code == BEL || code == SO || code == SI -> i += 1
                else -> { sink.putChar(chunk[i], style); i += 1 }
            }
        }
    }

    fun parseLegacy(chunk: String, sink: TerminalBuffer) {
        var i = 0
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) {
                sink.applyStyle(style)
                sink.writeText(plain.toString())
                plain.clear()
            }
        }
        while (i < chunk.length) {
            val code = chunk[i].code
            when {
                code == ESC -> {
                    flush()
                    val consumed = handleEscapeLegacy(chunk, i, sink)
                    i += if (consumed == 0) 1 else consumed
                }
                chunk[i] == '\n' -> { flush(); sink.newline(); i += 1 }
                chunk[i] == '\r' -> { flush(); sink.carriageReturn(); i += 1 }
                chunk[i] == '\b' -> { flush(); sink.backspace(); i += 1 }
                code == BEL || code == SO || code == SI -> i += 1
                else -> { plain.append(chunk[i]); i += 1 }
            }
        }
        flush()
    }

    private fun handleEscape(chunk: String, start: Int, sink: TerminalGrid): Int {
        if (start + 1 >= chunk.length) return 0
        val next = chunk[start + 1]
        return when (next) {
            '[' -> handleCsi(chunk, start, sink)
            ']' -> handleOsc(chunk, start)
            '(' , ')' -> if (start + 2 < chunk.length) 3 else 0
            '7' -> { sink.saveCursor(); 2 }
            '8' -> { sink.restoreCursor(); 2 }
            'M' -> { sink.scrollDown(); 2 }
            'D' -> { sink.linefeed(); 2 }
            'E' -> { sink.newline(); 2 }
            '=', '>' -> 2
            else -> 2
        }
    }

    private fun handleEscapeLegacy(chunk: String, start: Int, sink: TerminalBuffer): Int {
        if (start + 1 >= chunk.length) return 0
        val next = chunk[start + 1]
        return when (next) {
            '[' -> handleCsiLegacy(chunk, start, sink)
            ']' -> handleOsc(chunk, start)
            '(', ')' -> if (start + 2 < chunk.length) 3 else 0
            '=', '>' -> 2
            else -> 2
        }
    }

    private fun handleCsi(chunk: String, start: Int, sink: TerminalGrid): Int {
        var i = start + 2
        val params = StringBuilder()
        while (i < chunk.length) {
            val c = chunk[i]
            if (c in '0'..'9' || c == ';' || c == '?' || c == '>') {
                params.append(c)
                i += 1
            } else {
                break
            }
        }
        if (i >= chunk.length) return 0
        val finalChar = chunk[i]
        val consumed = i - start + 1
        val raw = params.toString()
        val isPrivate = raw.startsWith("?")
        val cleaned = raw.removePrefix("?").removePrefix(">")
        val parts = cleaned.split(';').map { it.toIntOrNull() }

        when (finalChar) {
            'm' -> applySgr(cleaned)
            'A' -> sink.moveCursorUp((parts.getOrNull(0) ?: 1).coerceAtLeast(1))
            'B' -> sink.moveCursorDown((parts.getOrNull(0) ?: 1).coerceAtLeast(1))
            'C' -> sink.moveCursorRight((parts.getOrNull(0) ?: 1).coerceAtLeast(1))
            'D' -> sink.moveCursorLeft((parts.getOrNull(0) ?: 1).coerceAtLeast(1))
            'H', 'f' -> {
                val row = ((parts.getOrNull(0) ?: 1) - 1).coerceAtLeast(0)
                val col = ((parts.getOrNull(1) ?: 1) - 1).coerceAtLeast(0)
                sink.setCursorPosition(row, col)
            }
            'J' -> sink.eraseInDisplay(parts.getOrNull(0) ?: 0)
            'K' -> sink.eraseInLine(parts.getOrNull(0) ?: 0)
            'G' -> sink.setCursorColumn(((parts.getOrNull(0) ?: 1) - 1).coerceAtLeast(0))
            'd' -> sink.setCursorRow(((parts.getOrNull(0) ?: 1) - 1).coerceAtLeast(0))
            's' -> sink.saveCursor()
            'u' -> sink.restoreCursor()
            'r' -> {
                val top = ((parts.getOrNull(0) ?: 1) - 1).coerceAtLeast(0)
                val bottom = ((parts.getOrNull(1) ?: sink.rows) - 1).coerceAtLeast(0)
                sink.setScrollRegion(top, bottom)
            }
            'h' -> {
                if (isPrivate) {
                    when (parts.getOrNull(0)) {
                        25 -> sink.cursorVisible = true
                        7 -> sink.autoWrap = true
                    }
                }
            }
            'l' -> {
                if (isPrivate) {
                    when (parts.getOrNull(0)) {
                        25 -> sink.cursorVisible = false
                        7 -> sink.autoWrap = false
                    }
                }
            }
            'L' -> {
                val n = (parts.getOrNull(0) ?: 1).coerceAtLeast(1)
                repeat(n) { sink.scrollDown() }
            }
            'M' -> {
                val n = (parts.getOrNull(0) ?: 1).coerceAtLeast(1)
                repeat(n) { sink.scrollUp() }
            }
            'S' -> {
                val n = (parts.getOrNull(0) ?: 1).coerceAtLeast(1)
                repeat(n) { sink.scrollUp() }
            }
            'T' -> {
                val n = (parts.getOrNull(0) ?: 1).coerceAtLeast(1)
                repeat(n) { sink.scrollDown() }
            }
            else -> Unit
        }
        return consumed
    }

    private fun handleCsiLegacy(chunk: String, start: Int, sink: TerminalBuffer): Int {
        var i = start + 2
        val params = StringBuilder()
        while (i < chunk.length) {
            val c = chunk[i]
            if (c in '0'..'9' || c == ';' || c == '?' || c == '>') {
                params.append(c)
                i += 1
            } else {
                break
            }
        }
        if (i >= chunk.length) return 0
        val finalChar = chunk[i]
        val consumed = i - start + 1
        when (finalChar) {
            'm' -> {
                applySgr(params.toString().removePrefix("?").removePrefix(">"))
                sink.applyStyle(style)
            }
            'K' -> {
                val mode = params.toString().toIntOrNull() ?: 0
                when (mode) {
                    0 -> sink.clearLineFromCursor()
                    1 -> sink.clearLineToCursor()
                    2 -> sink.clearLineAll()
                }
            }
            'J' -> if (params.toString() == "2" || params.toString() == "3") sink.clearScreen()
            else -> Unit
        }
        return consumed
    }

    private fun handleOsc(chunk: String, start: Int): Int {
        var i = start + 2
        while (i < chunk.length) {
            val code = chunk[i].code
            if (code == BEL) return i - start + 1
            if (code == ESC && i + 1 < chunk.length && chunk[i + 1] == '\\') return i - start + 2
            i += 1
        }
        return 0
    }

    private fun applySgr(cleaned: String) {
        val parts = if (cleaned.isEmpty()) listOf("0") else cleaned.split(';')
        var idx = 0
        while (idx < parts.size) {
            val code = parts[idx].toIntOrNull() ?: 0
            when (code) {
                0 -> style = TerminalStyle.Default
                1 -> style = style.copy(bold = true)
                3 -> style = style.copy(italic = true)
                4 -> style = style.copy(underline = true)
                22 -> style = style.copy(bold = false)
                23 -> style = style.copy(italic = false)
                24 -> style = style.copy(underline = false)
                in 30..37 -> style = style.copy(fg = ansi16(code - 30, bright = false))
                in 90..97 -> style = style.copy(fg = ansi16(code - 90, bright = true))
                in 40..47 -> style = style.copy(bg = ansi16(code - 40, bright = false))
                in 100..107 -> style = style.copy(bg = ansi16(code - 100, bright = true))
                38 -> {
                    val mode = parts.getOrNull(idx + 1)?.toIntOrNull()
                    if (mode == 5) {
                        parts.getOrNull(idx + 2)?.toIntOrNull()?.let { style = style.copy(fg = ansi256(it)) }
                        idx += 2
                    } else if (mode == 2) {
                        val r = parts.getOrNull(idx + 2)?.toIntOrNull() ?: 0
                        val g = parts.getOrNull(idx + 3)?.toIntOrNull() ?: 0
                        val b = parts.getOrNull(idx + 4)?.toIntOrNull() ?: 0
                        style = style.copy(fg = Color(r, g, b))
                        idx += 4
                    }
                }
                39 -> style = style.copy(fg = null)
                48 -> {
                    val mode = parts.getOrNull(idx + 1)?.toIntOrNull()
                    if (mode == 5) {
                        parts.getOrNull(idx + 2)?.toIntOrNull()?.let { style = style.copy(bg = ansi256(it)) }
                        idx += 2
                    } else if (mode == 2) {
                        val r = parts.getOrNull(idx + 2)?.toIntOrNull() ?: 0
                        val g = parts.getOrNull(idx + 3)?.toIntOrNull() ?: 0
                        val b = parts.getOrNull(idx + 4)?.toIntOrNull() ?: 0
                        style = style.copy(bg = Color(r, g, b))
                        idx += 4
                    }
                }
                49 -> style = style.copy(bg = null)
                else -> Unit
            }
            idx += 1
        }
    }
}

private val ansi16Palette = arrayOf(
    Color(0xFF000000), Color(0xFFCD3131), Color(0xFF0DBC79), Color(0xFFE5E510),
    Color(0xFF2472C8), Color(0xFFBC3FBC), Color(0xFF11A8CD), Color(0xFFE5E5E5),
)
private val ansi16BrightPalette = arrayOf(
    Color(0xFF666666), Color(0xFFF14C4C), Color(0xFF23D18B), Color(0xFFF5F543),
    Color(0xFF3B8EEA), Color(0xFFD670D6), Color(0xFF29B8DB), Color(0xFFFFFFFF),
)

private fun ansi16(idx: Int, bright: Boolean): Color =
    if (bright) ansi16BrightPalette[idx.coerceIn(0, 7)] else ansi16Palette[idx.coerceIn(0, 7)]

private fun ansi256(n: Int): Color {
    val v = n.coerceIn(0, 255)
    if (v < 16) return if (v < 8) ansi16Palette[v] else ansi16BrightPalette[v - 8]
    if (v >= 232) {
        val gray = 8 + (v - 232) * 10
        return Color(gray, gray, gray)
    }
    val k = v - 16
    val r = (k / 36) % 6
    val g = (k / 6) % 6
    val b = k % 6
    fun comp(i: Int) = if (i == 0) 0 else 55 + 40 * i
    return Color(comp(r), comp(g), comp(b))
}
