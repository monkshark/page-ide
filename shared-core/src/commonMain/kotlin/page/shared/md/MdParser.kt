package page.shared.md

object MdParser {

    fun parse(src: String): Document {
        val lines = src.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val slugs = mutableSetOf<String>()
        return Document(parseBlocks(lines, 0, lines.size, slugs))
    }

    private fun parseBlocks(lines: List<String>, from: Int, to: Int, slugs: MutableSet<String>): List<MdNode> {
        val out = ArrayList<MdNode>()
        var i = from
        while (i < to) {
            val line = lines[i]
            val t = line.trim()
            when {
                t.isEmpty() -> i++

                isFence(t) -> {
                    val marker = t.take(3)
                    val lang = t.substring(3).trim().ifEmpty { null }
                    val body = ArrayList<String>()
                    var j = i + 1
                    while (j < to && lines[j].trim().let { !(it.startsWith(marker) && it.all { c -> c == marker[0] }) }) {
                        body.add(lines[j]); j++
                    }
                    out.add(CodeBlock(lang, body.joinToString("\n")))
                    i = if (j < to) j + 1 else j
                }

                t.startsWith(":::") -> {
                    val kind = calloutKind(t.substring(3).trim())
                    val inner = ArrayList<String>()
                    var j = i + 1
                    while (j < to && lines[j].trim() != ":::") { inner.add(lines[j]); j++ }
                    out.add(Callout(kind, parseBlocks(inner, 0, inner.size, slugs)))
                    i = if (j < to) j + 1 else j
                }

                WIDGET.matches(t) -> {
                    out.add(parseWidget(t)); i++
                }

                headingLevel(t) > 0 -> {
                    val level = headingLevel(t)
                    val raw = t.substring(level).trim().trimEnd('#').trim()
                    val inlines = parseInlines(raw)
                    out.add(Heading(level, inlines, uniqueSlug(slugify(inlinesToPlain(inlines)), slugs)))
                    i++
                }

                t.startsWith(">") -> {
                    val inner = ArrayList<String>()
                    var j = i
                    while (j < to && lines[j].trim().startsWith(">")) {
                        inner.add(stripQuote(lines[j])); j++
                    }
                    out.add(BlockQuote(parseBlocks(inner, 0, inner.size, slugs)))
                    i = j
                }

                isTableStart(lines, i, to) -> {
                    val header = splitRow(lines[i]).map { parseInlines(it) }
                    val rows = ArrayList<List<List<Inline>>>()
                    var j = i + 2
                    while (j < to && lines[j].contains('|') && lines[j].trim().isNotEmpty()) {
                        rows.add(splitRow(lines[j]).map { parseInlines(it) }); j++
                    }
                    out.add(Table(header, rows))
                    i = j
                }

                listMarker(t) != null -> {
                    val ordered = listMarker(t) == Marker.ORDERED
                    val items = ArrayList<Pair<Boolean?, List<Inline>>>()
                    var j = i
                    while (j < to) {
                        val lt = lines[j].trim()
                        val m = listMarker(lt) ?: break
                        if ((m == Marker.ORDERED) != ordered) break
                        var content = markerContent(lt)
                        var k = j + 1
                        while (k < to && lines[k].isNotBlank() && listMarker(lines[k].trim()) == null &&
                            !isBlockStart(lines, k, to)) {
                            content += " " + lines[k].trim(); k++
                        }
                        val task = TASK.find(content)
                        if (task != null) items.add((task.groupValues[1].lowercase() == "x") to parseInlines(task.groupValues[2]))
                        else items.add(null to parseInlines(content))
                        j = k
                    }
                    if (!ordered && items.any { it.first != null }) {
                        out.add(TaskList(items.map { TaskItem(it.first == true, it.second) }))
                    } else {
                        out.add(BulletList(items.map { listOf<MdNode>(Paragraph(it.second)) }, ordered))
                    }
                    i = j
                }

                else -> {
                    val buf = ArrayList<String>()
                    buf.add(t)
                    var j = i + 1
                    while (j < to && lines[j].isNotBlank() && !isBlockStart(lines, j, to)) {
                        buf.add(lines[j].trim()); j++
                    }
                    out.add(Paragraph(parseInlines(buf.joinToString(" "))))
                    i = j
                }
            }
        }
        return out
    }

    private enum class Marker { UNORDERED, ORDERED }

    private val WIDGET = Regex("^@Render\\((.*)\\)$")
    private val UNORDERED = Regex("^[-*+]\\s+(.*)$")
    private val ORDERED = Regex("^\\d+[.)]\\s+(.*)$")
    private val TASK = Regex("^\\[( |x|X)]\\s+(.*)$")

    private fun isFence(t: String): Boolean = t.startsWith("```") || t.startsWith("~~~")

    private fun headingLevel(t: String): Int {
        var n = 0
        while (n < t.length && t[n] == '#') n++
        return if (n in 1..6 && n < t.length && t[n] == ' ') n else 0
    }

    private fun listMarker(t: String): Marker? = when {
        UNORDERED.matches(t) -> Marker.UNORDERED
        ORDERED.matches(t) -> Marker.ORDERED
        else -> null
    }

    private fun markerContent(t: String): String =
        UNORDERED.find(t)?.groupValues?.get(1) ?: ORDERED.find(t)?.groupValues?.get(1) ?: t

    private fun isBlockStart(lines: List<String>, i: Int, to: Int): Boolean {
        val t = lines[i].trim()
        return isFence(t) || t.startsWith(":::") || t.startsWith(">") ||
            WIDGET.matches(t) || headingLevel(t) > 0 || listMarker(t) != null ||
            isTableStart(lines, i, to)
    }

    private fun isTableStart(lines: List<String>, i: Int, to: Int): Boolean {
        if (!lines[i].contains('|')) return false
        if (i + 1 >= to) return false
        return isTableSeparator(lines[i + 1])
    }

    private fun isTableSeparator(line: String): Boolean {
        val cells = splitRow(line)
        if (cells.isEmpty()) return false
        return cells.all { it.isNotEmpty() && it.contains('-') && it.all { c -> c == '-' || c == ':' } }
    }

    private fun splitRow(line: String): List<String> {
        var s = line.trim()
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|")) s = s.substring(0, s.length - 1)
        return s.split("|").map { it.trim() }
    }

    private fun stripQuote(line: String): String {
        val t = line.trim()
        val body = t.removePrefix(">")
        return if (body.startsWith(" ")) body.substring(1) else body
    }

    private fun calloutKind(word: String): CalloutKind = when (word.lowercase()) {
        "warning", "warn", "caution" -> CalloutKind.WARNING
        "note" -> CalloutKind.NOTE
        "tip" -> CalloutKind.TIP
        "danger", "error" -> CalloutKind.DANGER
        else -> CalloutKind.INFO
    }

    private fun parseWidget(t: String): WidgetRef {
        val inner = WIDGET.find(t)!!.groupValues[1]
        val parts = inner.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val name = parts.firstOrNull() ?: ""
        val args = LinkedHashMap<String, String>()
        for (p in parts.drop(1)) {
            val eq = p.indexOf('=')
            if (eq > 0) args[p.substring(0, eq).trim()] = p.substring(eq + 1).trim().trim('"', '\'')
        }
        return WidgetRef(name, args)
    }

    private fun slugify(text: String): String {
        val sb = StringBuilder()
        for (c in text.lowercase()) {
            when {
                c.isLetterOrDigit() -> sb.append(c)
                c == ' ' || c == '-' || c == '_' -> sb.append('-')
                else -> {}
            }
        }
        return sb.toString().trim('-').replace(Regex("-+"), "-")
    }

    private fun uniqueSlug(base: String, used: MutableSet<String>): String {
        val root = base.ifEmpty { "section" }
        if (used.add(root)) return root
        var n = 2
        while (!used.add("$root-$n")) n++
        return "$root-$n"
    }

    private fun inlinesToPlain(inlines: List<Inline>): String = buildString {
        for (n in inlines) when (n) {
            is Text -> append(n.value)
            is CodeSpan -> append(n.value)
            is Link -> append(n.text)
            is Emphasis -> append(inlinesToPlain(n.inlines))
            is Strikethrough -> append(inlinesToPlain(n.inlines))
        }
    }

    fun parseInlines(text: String): List<Inline> {
        val out = ArrayList<Inline>()
        val sb = StringBuilder()
        var i = 0
        fun flush() { if (sb.isNotEmpty()) { out.add(Text(sb.toString())); sb.clear() } }
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val run = runLength(text, i, '`')
                    val close = findRun(text, i + run, '`', run)
                    if (close >= 0) { flush(); out.add(CodeSpan(text.substring(i + run, close))); i = close + run }
                    else { sb.append(text, i, i + run); i += run }
                }
                c == '[' -> {
                    val link = parseLink(text, i)
                    if (link != null) { flush(); out.add(link.first); i = link.second }
                    else { sb.append(c); i++ }
                }
                c == '~' && text.startsWith("~~", i) -> {
                    val close = text.indexOf("~~", i + 2)
                    if (close >= 0) { flush(); out.add(Strikethrough(parseInlines(text.substring(i + 2, close)))); i = close + 2 }
                    else { sb.append(c); i++ }
                }
                c == '*' || c == '_' -> {
                    val i2 = emphasis(text, i, c, out, sb, ::flush)
                    if (i2 > i) i = i2 else { sb.append(c); i++ }
                }
                else -> { sb.append(c); i++ }
            }
        }
        flush()
        return out
    }

    private fun emphasis(text: String, i: Int, c: Char, out: ArrayList<Inline>, sb: StringBuilder, flush: () -> Unit): Int {
        val double = text.startsWith("$c$c", i)
        if (double) {
            val close = text.indexOf("$c$c", i + 2)
            if (close >= 0) { flush(); out.add(Emphasis(parseInlines(text.substring(i + 2, close)), strong = true)); return close + 2 }
            return i
        }
        if (c == '_' && i > 0 && text[i - 1].isLetterOrDigit()) return i
        var j = i + 1
        while (j < text.length) {
            if (text[j] == c && (j + 1 >= text.length || text[j + 1] != c)) {
                if (c == '_' && j + 1 < text.length && text[j + 1].isLetterOrDigit()) { j++; continue }
                flush(); out.add(Emphasis(parseInlines(text.substring(i + 1, j)), strong = false)); return j + 1
            }
            j++
        }
        return i
    }

    private fun parseLink(text: String, i: Int): Pair<Link, Int>? {
        val close = text.indexOf(']', i + 1)
        if (close < 0 || close + 1 >= text.length || text[close + 1] != '(') return null
        val paren = text.indexOf(')', close + 2)
        if (paren < 0) return null
        val label = text.substring(i + 1, close)
        val href = text.substring(close + 2, paren).trim()
        return Link(label, href) to paren + 1
    }

    private fun runLength(text: String, i: Int, c: Char): Int {
        var n = 0
        while (i + n < text.length && text[i + n] == c) n++
        return n
    }

    private fun findRun(text: String, from: Int, c: Char, len: Int): Int {
        var i = from
        while (i < text.length) {
            if (text[i] == c) {
                val r = runLength(text, i, c)
                if (r == len) return i
                i += r
            } else i++
        }
        return -1
    }
}
