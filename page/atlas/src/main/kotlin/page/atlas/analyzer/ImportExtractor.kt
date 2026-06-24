package page.atlas.analyzer

import java.nio.file.Path
import org.treesitter.TSLanguage
import org.treesitter.TSNode
import org.treesitter.TSParser
import org.treesitter.TSTreeCursor
import org.treesitter.TreeSitterC
import org.treesitter.TreeSitterCpp
import org.treesitter.TreeSitterDart
import org.treesitter.TreeSitterGo
import org.treesitter.TreeSitterJava
import org.treesitter.TreeSitterJavascript
import org.treesitter.TreeSitterKotlin
import org.treesitter.TreeSitterPython
import org.treesitter.TreeSitterRust
import org.treesitter.TreeSitterTypescript
import page.atlas.graph.EdgeKind

data class RawImport(
    val target: String,
    val relative: Boolean,
    val symbols: List<String> = emptyList(),
)

data class RawRelation(val typeName: String, val kind: EdgeKind)

data class FileDeclarations(val packageName: String, val symbols: List<String>) {
    companion object {
        val EMPTY = FileDeclarations("", emptyList())
    }
}

data class FileAnalysis(
    val imports: List<RawImport>,
    val relations: List<RawRelation>,
    val declarations: FileDeclarations = FileDeclarations.EMPTY,
) {
    companion object {
        val EMPTY = FileAnalysis(emptyList(), emptyList())
    }
}

object ImportExtractor {

    private enum class Lang(
        val nodeTypes: Set<String>,
        val relationTypes: Set<String>,
        val declTypes: Set<String>,
        val packageType: String?,
        val factory: () -> TSLanguage,
        val softImportTypes: Set<String> = emptySet(),
    ) {
        JAVA(
            setOf("import_declaration"),
            setOf("superclass", "super_interfaces", "extends_interfaces"),
            setOf(
                "class_declaration",
                "interface_declaration",
                "enum_declaration",
                "record_declaration",
                "annotation_type_declaration",
            ),
            "package_declaration",
            ::TreeSitterJava,
        ),
        KOTLIN(
            setOf("import_header"),
            setOf("delegation_specifier"),
            setOf(
                "class_declaration",
                "object_declaration",
                "function_declaration",
                "property_declaration",
                "type_alias",
            ),
            "package_header",
            ::TreeSitterKotlin,
        ),
        PYTHON(
            setOf("import_statement", "import_from_statement"),
            setOf("class_definition"),
            emptySet(),
            null,
            ::TreeSitterPython,
        ),
        JS(
            setOf("import_statement"),
            setOf("class_heritage"),
            emptySet(),
            null,
            ::TreeSitterJavascript,
            setOf("call_expression", "export_statement"),
        ),
        TS(
            setOf("import_statement"),
            setOf("extends_clause", "implements_clause", "extends_type_clause"),
            emptySet(),
            null,
            ::TreeSitterTypescript,
            setOf("call_expression", "export_statement"),
        ),
        GO(setOf("import_spec"), emptySet(), emptySet(), null, ::TreeSitterGo),
        RUST(setOf("use_declaration"), emptySet(), emptySet(), null, ::TreeSitterRust),
        DART(
            setOf("import_specification", "library_export"),
            setOf("superclass", "interfaces"),
            emptySet(),
            null,
            ::TreeSitterDart,
        ),
        C(setOf("preproc_include"), emptySet(), emptySet(), null, ::TreeSitterC),
        CPP(
            setOf("preproc_include"),
            setOf("base_class_clause"),
            emptySet(),
            null,
            ::TreeSitterCpp,
        ),
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
        "dart" to Lang.DART,
        "c" to Lang.C,
        "h" to Lang.CPP,
        "hpp" to Lang.CPP,
        "hh" to Lang.CPP,
        "hxx" to Lang.CPP,
        "cpp" to Lang.CPP,
        "cc" to Lang.CPP,
        "cxx" to Lang.CPP,
    )

    private val parsers = mutableMapOf<Lang, TSParser>()

    private val DECL_MODIFIERS = setOf(
        "public", "private", "protected", "internal", "abstract", "final", "sealed", "open", "data",
        "inline", "value", "companion", "external", "override", "lateinit", "const", "suspend", "operator",
        "infix", "tailrec", "static", "native", "synchronized", "transient", "volatile", "strictfp",
        "default", "expect", "actual",
    )

    private val DECL_KEYWORDS = setOf(
        "class", "interface", "object", "fun", "val", "var", "typealias", "record", "enum", "annotation",
    )

    fun supports(path: Path): Boolean = extOf(path) in langs

    fun extract(path: Path, text: String): List<RawImport> = analyze(path, text).imports

    fun analyze(path: Path, text: String): FileAnalysis {
        val lang = langs[extOf(path)] ?: return FileAnalysis.EMPTY
        if (text.isBlank()) return FileAnalysis.EMPTY
        val parser = parserFor(lang)
        val tree = synchronized(parser) { parser.parseString(null, text) } ?: return FileAnalysis.EMPTY
        val byteToChar = buildByteToChar(text)
        val imports = mutableListOf<Pair<String, String>>()
        val relations = mutableListOf<Pair<String, String>>()
        collect(TSTreeCursor(tree.rootNode), lang, text, byteToChar, imports, relations)
        return FileAnalysis(
            imports.flatMap { (type, snippet) -> parse(lang, type, snippet) },
            relations.flatMap { (type, snippet) -> parseRelation(lang, type, snippet) },
            collectDeclarations(tree.rootNode, lang, text, byteToChar),
        )
    }

    private fun collectDeclarations(root: TSNode, lang: Lang, text: String, byteToChar: IntArray): FileDeclarations {
        if (lang.declTypes.isEmpty() && lang.packageType == null) return FileDeclarations.EMPTY
        val cursor = TSTreeCursor(root)
        var packageName = ""
        val symbols = mutableListOf<String>()
        if (cursor.gotoFirstChild()) {
            do {
                val node = cursor.currentNode()
                val type = node.type ?: ""
                when {
                    type == lang.packageType -> packageName = parsePackage(nodeText(node, text, byteToChar))
                    type in lang.declTypes ->
                        declaredName(nodeText(node, text, byteToChar))?.let { symbols += it }
                }
            } while (cursor.gotoNextSibling())
        }
        return FileDeclarations(packageName, symbols)
    }

    private fun parsePackage(snippet: String): String =
        snippet.trim().removePrefix("package").trim().removeSuffix(";").substringBefore('\n').trim()

    private fun declaredName(snippet: String): String? {
        val header = snippet.substringBefore('{').substringBefore('=').replace("@interface", " interface ")
        val cleaned = header.replace(Regex("@[\\w.]+(\\s*\\([^)]*\\))?"), " ")
        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        var i = 0
        while (i < tokens.size && tokens[i] in DECL_MODIFIERS) i++
        if (i >= tokens.size) return null
        if ((tokens[i] == "enum" || tokens[i] == "annotation") && i + 1 < tokens.size && tokens[i + 1] == "class") i++
        if (tokens[i] !in DECL_KEYWORDS || i + 1 >= tokens.size) return null
        val name = tokens[i + 1]
            .substringBefore('<').substringBefore('(').substringBefore(':').substringAfterLast('.').trim()
        return name.takeIf {
            it.isNotEmpty() && it.first().isJavaIdentifierStart() && it.all { c -> c.isJavaIdentifierPart() }
        }
    }

    private fun parserFor(lang: Lang): TSParser = synchronized(parsers) {
        parsers.getOrPut(lang) { TSParser().also { it.setLanguage(lang.factory()) } }
    }

    private fun collect(
        c: TSTreeCursor,
        lang: Lang,
        text: String,
        byteToChar: IntArray,
        imports: MutableList<Pair<String, String>>,
        relations: MutableList<Pair<String, String>>,
    ) {
        val node = c.currentNode()
        val type = node.type ?: ""
        if (type in lang.nodeTypes) {
            imports += type to nodeText(node, text, byteToChar)
            return
        }
        if (type in lang.softImportTypes) {
            softImportSnippet(node, type, text, byteToChar)?.let { imports += type to it }
        }
        if (type in lang.relationTypes) {
            relations += type to nodeText(node, text, byteToChar)
        }
        if (c.gotoFirstChild()) {
            do {
                collect(c, lang, text, byteToChar, imports, relations)
            } while (c.gotoNextSibling())
            c.gotoParent()
        }
    }

    private val FROM_KEYWORD = Regex("\\bfrom\\b")

    private val EXPORT_KEYWORDS = setOf("export", "type", "typeof")

    private fun softImportSnippet(node: TSNode, type: String, text: String, byteToChar: IntArray): String? = when (type) {
        "call_expression" -> {
            val fn = node.getChildByFieldName("function")
            if (fn == null || fn.isNull) {
                null
            } else {
                val fnType = fn.type ?: ""
                val isImportCall =
                    fnType == "import" || (fnType == "identifier" && nodeText(fn, text, byteToChar) == "require")
                if (isImportCall) nodeText(node, text, byteToChar) else null
            }
        }
        "export_statement" -> {
            val snippet = nodeText(node, text, byteToChar)
            if (FROM_KEYWORD.containsMatchIn(snippet)) snippet else null
        }
        else -> null
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
        Lang.JS, Lang.TS -> when (type) {
            "call_expression" -> parseJsCall(snippet)
            "export_statement" -> parseJsExportFrom(snippet)
            else -> parseQuoted(snippet, pathStyle = true)
        }
        Lang.GO -> parseQuoted(snippet, pathStyle = false)
        Lang.RUST -> parseRust(snippet)
        Lang.DART -> parseDart(snippet)
        Lang.C, Lang.CPP -> parseInclude(snippet)
    }

    private fun parseRelation(lang: Lang, type: String, snippet: String): List<RawRelation> = when (lang) {
        Lang.JAVA -> parseJavaRelation(type, snippet)
        Lang.KOTLIN -> parseKotlinRelation(snippet)
        Lang.PYTHON -> parsePythonBases(snippet)
        Lang.JS -> typeNames(snippet.trim().removePrefix("extends")).map { RawRelation(it, EdgeKind.EXTENDS) }
        Lang.TS -> parseTsRelation(type, snippet)
        Lang.DART -> parseDartRelation(type, snippet)
        Lang.CPP -> parseCppRelation(snippet)
        Lang.GO, Lang.RUST, Lang.C -> emptyList()
    }

    private fun parseJavaRelation(type: String, snippet: String): List<RawRelation> = when (type) {
        "superclass" -> typeNames(snippet.trim().removePrefix("extends")).map { RawRelation(it, EdgeKind.EXTENDS) }
        "super_interfaces" ->
            typeNames(snippet.trim().removePrefix("implements")).map { RawRelation(it, EdgeKind.IMPLEMENTS) }
        "extends_interfaces" ->
            typeNames(snippet.trim().removePrefix("extends")).map { RawRelation(it, EdgeKind.EXTENDS) }
        else -> emptyList()
    }

    private fun parseKotlinRelation(snippet: String): List<RawRelation> {
        val body = snippet.trim()
        if (body.isEmpty()) return emptyList()
        return if (body.contains('(')) {
            typeNames(body.substringBefore('(')).map { RawRelation(it, EdgeKind.EXTENDS) }
        } else {
            typeNames(body.substringBefore(" by ")).map { RawRelation(it, EdgeKind.IMPLEMENTS) }
        }
    }

    private fun parseTsRelation(type: String, snippet: String): List<RawRelation> = when (type) {
        "extends_clause", "extends_type_clause" ->
            typeNames(snippet.trim().removePrefix("extends")).map { RawRelation(it, EdgeKind.EXTENDS) }
        "implements_clause" ->
            typeNames(snippet.trim().removePrefix("implements")).map { RawRelation(it, EdgeKind.IMPLEMENTS) }
        else -> emptyList()
    }

    private fun parsePythonBases(snippet: String): List<RawRelation> {
        val header = snippet.substringBefore(':')
        if (!header.contains('(')) return emptyList()
        val inside = header.substringAfter('(').substringBeforeLast(')')
        return splitTopLevel(inside).mapNotNull { part ->
            val base = part.trim()
            if (base.isEmpty() || base.contains('=') || base.startsWith("*")) null
            else {
                val name = base.substringBefore('[').trim()
                if (name.isEmpty() || !name.all { it.isJavaIdentifierPart() || it == '.' }) null
                else RawRelation(name, EdgeKind.EXTENDS)
            }
        }
    }

    private fun typeNames(body: String): List<String> =
        splitTopLevel(body).mapNotNull { part ->
            val name = part.trim().substringBefore('<').substringBefore('(').trim()
            name.takeIf {
                it.isNotEmpty() && it.first().isJavaIdentifierStart() &&
                    it.all { c -> c.isJavaIdentifierPart() || c == '.' }
            }
        }

    private fun splitTopLevel(body: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (ch in body) {
            when (ch) {
                '<', '(', '[' -> { depth++; current.append(ch) }
                '>', ')', ']' -> { depth--; current.append(ch) }
                ',' -> if (depth <= 0) { parts.add(current.toString()); current.clear() } else current.append(ch)
                else -> current.append(ch)
            }
        }
        parts.add(current.toString())
        return parts
    }

    private fun parseJava(snippet: String): List<RawImport> {
        var body = snippet.trim().removePrefix("import").trim().removeSuffix(";").trim()
        if (body.startsWith("static ")) body = body.removePrefix("static").trim()
        val target = body.removeSuffix(".*").removeSuffix(".")
        return if (target.isEmpty()) emptyList()
        else listOf(RawImport(target, false, dottedSymbols(body, target)))
    }

    private fun parseKotlin(snippet: String): List<RawImport> {
        val body = snippet.trim().removePrefix("import").trim()
        val target = body.substringBefore(" as ").trim().removeSuffix(".*").removeSuffix(".")
        if (target.isEmpty()) return emptyList()
        val symbols =
            if (" as " in body) listOfNotNull(localName(body.substringAfterLast(" as ").trim()))
            else dottedSymbols(body, target)
        return listOf(RawImport(target, false, symbols))
    }

    private fun dottedSymbols(body: String, target: String): List<String> =
        if (body.endsWith(".*")) emptyList()
        else listOfNotNull(target.substringAfterLast('.').takeIf { it.isNotEmpty() })

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
        val symbols = snippet.substringAfter(" import ", "").split(',').mapNotNull { part ->
            localName(part.trim().trim('(', ')').trim())
        }
        return listOf(RawImport(target, target.startsWith("."), symbols))
    }

    private fun parseQuoted(snippet: String, pathStyle: Boolean): List<RawImport> {
        val target = unquote(snippet) ?: return emptyList()
        if (target.isEmpty()) return emptyList()
        val relative = pathStyle && (target.startsWith("./") || target.startsWith("../"))
        val symbols = if (pathStyle) jsSymbols(snippet) else emptyList()
        return listOf(RawImport(target, relative, symbols))
    }

    private fun parseJsCall(snippet: String): List<RawImport> {
        val target = unquote(snippet) ?: return emptyList()
        if (target.isEmpty()) return emptyList()
        val relative = target.startsWith("./") || target.startsWith("../")
        return listOf(RawImport(target, relative))
    }

    private fun parseJsExportFrom(snippet: String): List<RawImport> {
        val from = FROM_KEYWORD.find(snippet) ?: return emptyList()
        val target = unquote(snippet.substring(from.range.last + 1)) ?: return emptyList()
        if (target.isEmpty()) return emptyList()
        val relative = target.startsWith("./") || target.startsWith("../")
        val symbols = snippet.substring(0, from.range.first)
            .split(',', '{', '}')
            .mapNotNull { localName(it.trim()) }
            .filter { it !in EXPORT_KEYWORDS }
        return listOf(RawImport(target, relative, symbols))
    }

    private fun jsSymbols(snippet: String): List<String> {
        val head = snippet.substringAfter("import", "").substringBefore(" from ")
        return head.split(',', '{', '}').mapNotNull { localName(it.trim()) }
    }

    private fun localName(token: String): String? {
        if (token.isEmpty()) return null
        val local = if (" as " in token) token.substringAfterLast(" as ").trim() else token
        return local.takeIf {
            it.isNotEmpty() && it.first().isJavaIdentifierStart() && it.all { c -> c.isJavaIdentifierPart() }
        }
    }

    private fun parseDart(snippet: String): List<RawImport> {
        val target = unquote(snippet) ?: return emptyList()
        if (target.isEmpty()) return emptyList()
        val relative = !target.startsWith("dart:") && !target.startsWith("package:")
        val tail = snippet.substringAfter(target).removeSuffix(";")
        val symbols = when {
            " as " in tail -> listOfNotNull(localName(tail.substringAfter(" as ").trim().substringBefore(' ')))
            " show " in tail ->
                tail.substringAfter(" show ").substringBefore(" hide ").split(',')
                    .mapNotNull { localName(it.trim()) }
            else -> emptyList()
        }
        return listOf(RawImport(target, relative, symbols))
    }

    private fun parseDartRelation(type: String, snippet: String): List<RawRelation> = when (type) {
        "superclass" -> {
            val body = snippet.trim().removePrefix("extends").trim()
            val base = if (body.startsWith("with")) "" else body.substringBefore(" with ")
            val mixins =
                if (body.startsWith("with")) body.removePrefix("with")
                else body.substringAfter(" with ", "")
            typeNames(base).map { RawRelation(it, EdgeKind.EXTENDS) } +
                typeNames(mixins).map { RawRelation(it, EdgeKind.IMPLEMENTS) }
        }
        "interfaces" ->
            typeNames(snippet.trim().removePrefix("implements")).map { RawRelation(it, EdgeKind.IMPLEMENTS) }
        else -> emptyList()
    }

    private fun parseRust(snippet: String): List<RawImport> {
        val body = snippet.trim().removePrefix("use").trim().removeSuffix(";").trim()
            .substringBefore("::{").substringBefore(" as ").trim()
        if (body.isEmpty() || body.startsWith("{")) return emptyList()
        val relative = body == "crate" || body == "super" || body == "self" ||
            body.startsWith("crate::") || body.startsWith("super::") || body.startsWith("self::")
        return listOf(RawImport(body, relative))
    }

    private fun parseInclude(snippet: String): List<RawImport> {
        unquote(snippet)?.let { return if (it.isEmpty()) emptyList() else listOf(RawImport(it, true)) }
        val lt = snippet.indexOf('<')
        val gt = snippet.indexOf('>', lt + 1)
        if (lt < 0 || gt <= lt) return emptyList()
        val target = snippet.substring(lt + 1, gt).trim()
        return if (target.isEmpty()) emptyList() else listOf(RawImport(target, false))
    }

    private val CPP_ACCESS = setOf("public", "private", "protected", "virtual")

    private fun parseCppRelation(snippet: String): List<RawRelation> {
        val body = snippet.trim().removePrefix(":").trim()
        return splitTopLevel(body).mapNotNull { part ->
            val name = part.trim().split(Regex("\\s+"))
                .filter { it.isNotBlank() && it !in CPP_ACCESS }
                .lastOrNull()
                ?.substringBefore('<')
                ?.trim()
                ?: return@mapNotNull null
            name.takeIf {
                it.isNotEmpty() && (it.first().isJavaIdentifierStart() || it.startsWith("::")) &&
                    it.all { c -> c.isJavaIdentifierPart() || c == ':' }
            }?.let { RawRelation(it, EdgeKind.EXTENDS) }
        }
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
