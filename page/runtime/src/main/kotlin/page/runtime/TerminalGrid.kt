package page.runtime

data class Cell(
    var char: Char = ' ',
    var style: TerminalStyle = TerminalStyle.Default,
)

class TerminalGrid(
    var cols: Int = 120,
    var rows: Int = 40,
    private val maxScrollback: Int = 5000,
) {
    var cells: Array<Array<Cell>> = Array(rows) { Array(cols) { Cell() } }
        private set

    val scrollback: ArrayDeque<Array<Cell>> = ArrayDeque()

    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set

    var cursorVisible: Boolean = true
    var autoWrap: Boolean = true

    private var savedCursorRow: Int = 0
    private var savedCursorCol: Int = 0
    private var savedStyle: TerminalStyle = TerminalStyle.Default

    var scrollTop: Int = 0
        private set
    var scrollBottom: Int = rows - 1
        private set

    var currentStyle: TerminalStyle = TerminalStyle.Default

    fun putChar(ch: Char, style: TerminalStyle) {
        if (autoWrap && cursorCol >= cols) {
            cursorCol = 0
            linefeed()
        }
        if (cursorRow in 0 until rows && cursorCol in 0 until cols) {
            cells[cursorRow][cursorCol].char = ch
            cells[cursorRow][cursorCol].style = style
            cursorCol++
        }
    }

    fun newline() {
        cursorCol = 0
        linefeed()
    }

    fun linefeed() {
        if (cursorRow == scrollBottom) {
            scrollUp()
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    fun carriageReturn() {
        cursorCol = 0
    }

    fun backspace() {
        if (cursorCol > 0) cursorCol--
    }

    fun tab() {
        val nextTab = ((cursorCol / 8) + 1) * 8
        cursorCol = nextTab.coerceAtMost(cols - 1)
    }

    fun moveCursorUp(n: Int) {
        cursorRow = (cursorRow - n).coerceAtLeast(scrollTop)
    }

    fun moveCursorDown(n: Int) {
        cursorRow = (cursorRow + n).coerceAtMost(scrollBottom)
    }

    fun moveCursorLeft(n: Int) {
        cursorCol = (cursorCol - n).coerceAtLeast(0)
    }

    fun moveCursorRight(n: Int) {
        cursorCol = (cursorCol + n).coerceAtMost(cols - 1)
    }

    fun setCursorPosition(row: Int, col: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorCol = col.coerceIn(0, cols - 1)
    }

    fun setCursorColumn(col: Int) {
        cursorCol = col.coerceIn(0, cols - 1)
    }

    fun setCursorRow(row: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
    }

    fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
        savedStyle = currentStyle
    }

    fun restoreCursor() {
        cursorRow = savedCursorRow.coerceIn(0, rows - 1)
        cursorCol = savedCursorCol.coerceIn(0, cols - 1)
        currentStyle = savedStyle
    }

    fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                clearRow(cursorRow, cursorCol, cols)
                for (r in (cursorRow + 1) until rows) clearRow(r, 0, cols)
            }
            1 -> {
                for (r in 0 until cursorRow) clearRow(r, 0, cols)
                clearRow(cursorRow, 0, cursorCol + 1)
            }
            2, 3 -> {
                for (r in 0 until rows) clearRow(r, 0, cols)
                cursorRow = 0
                cursorCol = 0
            }
        }
    }

    fun eraseInLine(mode: Int) {
        when (mode) {
            0 -> clearRow(cursorRow, cursorCol, cols)
            1 -> clearRow(cursorRow, 0, cursorCol + 1)
            2 -> clearRow(cursorRow, 0, cols)
        }
    }

    private fun clearRow(row: Int, fromCol: Int, toCol: Int) {
        if (row !in 0 until rows) return
        val start = fromCol.coerceIn(0, cols)
        val end = toCol.coerceIn(0, cols)
        for (c in start until end) {
            cells[row][c].char = ' '
            cells[row][c].style = TerminalStyle.Default
        }
    }

    fun scrollUp() {
        val topLine = cells[scrollTop].map { Cell(it.char, it.style) }.toTypedArray()
        scrollback.addLast(topLine)
        while (scrollback.size > maxScrollback) scrollback.removeFirst()

        for (r in scrollTop until scrollBottom) {
            cells[r] = cells[r + 1]
        }
        cells[scrollBottom] = Array(cols) { Cell() }
    }

    fun scrollDown() {
        for (r in scrollBottom downTo scrollTop + 1) {
            cells[r] = cells[r - 1]
        }
        cells[scrollTop] = Array(cols) { Cell() }
    }

    fun setScrollRegion(top: Int, bottom: Int) {
        scrollTop = top.coerceIn(0, rows - 1)
        scrollBottom = bottom.coerceIn(scrollTop, rows - 1)
        cursorRow = 0
        cursorCol = 0
    }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        val newCells = Array(newRows) { r ->
            Array(newCols) { c ->
                if (r < rows && c < cols) Cell(cells[r][c].char, cells[r][c].style)
                else Cell()
            }
        }
        cells = newCells
        cols = newCols
        rows = newRows
        scrollTop = 0
        scrollBottom = rows - 1
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
    }

    fun snapshot(): List<TerminalLine> {
        val result = mutableListOf<TerminalLine>()
        for (scrollLine in scrollback) {
            result.add(rowToTerminalLine(scrollLine))
        }
        for (r in 0 until rows) {
            result.add(rowToTerminalLine(cells[r]))
        }
        return result
    }

    fun visibleSnapshot(): List<TerminalLine> {
        val result = mutableListOf<TerminalLine>()
        for (r in 0 until rows) {
            result.add(rowToTerminalLine(cells[r]))
        }
        return result
    }

    private fun rowToTerminalLine(row: Array<Cell>): TerminalLine {
        if (row.isEmpty()) return TerminalLine(emptyList())

        var lastNonSpace = row.size - 1
        while (lastNonSpace >= 0 && row[lastNonSpace].char == ' ' && row[lastNonSpace].style == TerminalStyle.Default) {
            lastNonSpace--
        }
        if (lastNonSpace < 0) return TerminalLine(listOf(TerminalSpan("", TerminalStyle.Default)))

        val spans = mutableListOf<TerminalSpan>()
        val sb = StringBuilder()
        var curStyle = row[0].style
        for (c in 0..lastNonSpace) {
            val cell = row[c]
            if (cell.style != curStyle) {
                if (sb.isNotEmpty()) {
                    spans.add(TerminalSpan(sb.toString(), curStyle))
                    sb.clear()
                }
                curStyle = cell.style
            }
            sb.append(cell.char)
        }
        if (sb.isNotEmpty()) {
            spans.add(TerminalSpan(sb.toString(), curStyle))
        }
        if (spans.isEmpty()) {
            spans.add(TerminalSpan("", TerminalStyle.Default))
        }
        return TerminalLine(spans)
    }
}
