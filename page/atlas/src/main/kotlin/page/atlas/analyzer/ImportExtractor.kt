package page.atlas.analyzer

import java.nio.file.Path
import org.treesitter.TSLanguage
import org.treesitter.TSNode
import org.treesitter.TSParser
import org.treesitter.TSTreeCursor
import org.treesitter.TreeSitterGo
import org.treesitter.TreeSitterJava
import org.treesitter.TreeSitterJavascript
import org.treesitter.TreeSitterKotlin
import org.treesitter.TreeSitterPython
import org.treesitter.TreeSitterRust
import org.treesitter.TreeSitterTypescript

data class RawImport(val target: String, val relative: Boolean)

object ImportExtractor {

    private enum class Lang(val nodeTypes: Set<String>, val factory: () -> TSLanguage) {
        JAVA(setOf("import_declaration"), ::TreeSitterJava),
        KOTLIN(setOf("import_header"), ::TreeSitterKotlin),
        PYTHON(setOf("import_statement", "import_from_statement"), ::TreeSitterPython),
        JS(setOf("import_statement"), ::TreeSitterJavascript),
        TS(setOf("import_statement"), ::TreeSitterTypescript),
        GO(setOf("import_spec"), ::TreeSitterGo),
        RUST(setOf("use_declaration"), ::TreeSitterRust),
    }

    private val langs: Map<String, Lang> = mapOf(
        "java" to Lang.JAVA,
        "kt" to Lang.KOTLIN,
        "kts" to Lang.KOTLIN,
        "py" to Lang.PYTHON,
        "pyi" to Lang.PYTHON,
        "js" to Lang.JS,
        "jsx" to Lang.JS,
        "mjs" to Lang.JS,
        "cjs" to Lang.JS,
        "ts" to Lang.TS,
        "tsx" to Lang.TS,
        "go" to Lang.GO,
        "rs" to Lang.RUST,
    )

    private val parsers = mutableMapOf<Lang, TSParser>()

    fun supports(path: Path): Boolean = extOf(path) in langs

    fun extract(path: Path, text: String): List<RawImport> {
        val lang = langs[extOf(path)] ?: return emptyList()
        if (text.isBlank()) return emptyList()
        val parser = parserFor(lang)
        val tree = synchronized(parser) { parser.parseString(null, text) } ?: return emptyList()
        val byteToChar = buildByteToChar(text)
        val found = mutableListOf<Pair<String, String>>()
        collect(TSTreeCursor(tree.rootNode), lang.nodeTypes, text, byteToChar, found)
        return found.flatMap { (type, snippet) -> parse(lang, type, snippet) }
    }

    private fun parserFor(lang: Lang): TSParser = synchronized(parsers) {
        parsers.getOrPut(lang) { TSParser().also { it.setLanguage(lang.factory()) } }
    }

    private fun collect(
        c: TSTreeCursor,
        types: Set<String>,
        text: String,
        byteToChar: IntArray,
        out: MutableList<Pair<String, String>>,
    ) {
        val node = c.currentNode()
        val type = node.type ?: ""
        if (type in types) {
            out += type to nodeText(node, text, byteToChar)
            return
        }
        if (c.gotoFirstChild()) {
            do {
                collect(c, types, text, byteToChar, out)
            } while (c.gotoNextSibling())
            c.gotoParent()
        }
    }

    private fun nodeText(node: TSNode, text: String, byteToChar: IntArray): String {
        val start = byteToChar[node.startByte.coerceIn(0, byteToChar.lastIndex)]
        val end = byteToChar[node.endByte.coerceIn(0, byteToChar.lastIndex)]
        return if (end > start) text.substring(start, end) else ""
    }

    private fun buildByteToChar(text: String): IntArray {
        val byteLength = text.toByteArray(Charsets.UTF_8).size
        val map = IntArray(byteLength + 1)
        var byteIdx = 0
        var charIdx = 0
        while (charIdx < text.length) {
            val cp = text.codePointAt(charIdx)
            val charLen = Character.charCount(cp)
            val byteLen = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            repeat(byteLen) { map[byteIdx + it] = charIdx }
            byteIdx += byteLen
            charIdx += charLen
        }
        map[byteIdx] = text.length
        return map
    }

    private fun parse(lang: Lang, type: String, snippet: String): List<RawImport> = when (lang) {
        Lang.JAVA -> parseJava(snippet)
        Lang.KOTLIN -> parseKotlin(snippet)
        Lang.PYTHON -> if (type == "import_from_statement") parsePythonFrom(snippet) else parsePythonImport(snippet)
        Lang.JS, Lang.TS -> parseQuoted(snippet, pathStyle = true)
        Lang.GO -> parseQuoted(snippet, pathStyle = false)
        Lang.RUST -> parseRust(snippet)
    }

    private fun parseJava(snippet: String): List<RawImport> {
        var body = snippet.trim().removePrefix("import").trim().removeSuffix(";").trim()
        if (body.startsWith("static ")) body = body.removePrefix("static").trim()
        val target = body.removeSuffix(".*").removeSuffix(".")
        return if (target.isEmpty()) emptyList() else listOf(RawImport(target, false))
    }

    private fun parseKotlin(snippet: String): List<RawImport> {
        val body = snippet.trim().removePrefix("import").trim().substringBefore(" as ").trim()
        val target = body.removeSuffix(".*").removeSuffix(".")
        return if (target.isEmpty()) emptyList() else listOf(RawImport(target, false))
    }

    private fun parsePythonImport(snippet: String): List<RawImport> {
        val body = snippet.trim().removePrefix("import").trim()
        return body.split(',').mapNotNull { part ->
            val target = part.trim().substringBefore(" as ").trim()
            if (target.isEmpty()) null else RawImport(target, false)
        }
    }

    private fun parsePythonFrom(snippet: String): List<RawImport> {
        val tokens = snippet.trim().split(Regex("\\s+"))
        if (tokens.size < 2 || tokens[0] != "from") return emptyList()
        val target = tokens[1]
        return listOf(RawImport(target, target.startsWith(".")))
    }

    private fun parseQuoted(snippet: String, pathStyle: Boolean): List<RawImport> {
        val target = unquote(snippet) ?: return emptyList()
        if (target.isEmpty()) return emptyList()
        val relative = pathStyle && (target.startsWith("./") || target.startsWith("../"))
        return listOf(RawImport(target, relative))
    }

    private fun parseRust(snippet: String): List<RawImport> {
        val body = snippet.trim().removePrefix("use").trim().removeSuffix(";").trim()
            .substringBefore("::{").substringBefore(" as ").trim()
        if (body.isEmpty() || body.startsWith("{")) return emptyList()
        val relative = body == "crate" || body == "super" || body == "self" ||
            body.startsWith("crate::") || body.startsWith("super::") || body.startsWith("self::")
        return listOf(RawImport(body, relative))
    }

    private fun unquote(snippet: String): String? {
        val first = snippet.indexOfFirst { it == '"' || it == '\'' || it == '`' }
        if (first < 0) return null
        val quote = snippet[first]
        val end = snippet.indexOf(quote, first + 1)
        if (end <= first) return null
        return snippet.substring(first + 1, end)
    }

    private fun extOf(path: Path): String =
        path.fileName?.toString()?.substringAfterLast('.', "")?.lowercase() ?: ""
}
