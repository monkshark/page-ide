package page.lsp

import org.eclipse.lsp4j.SymbolKind

data class CompletionProfile(
    val languageId: String,
    val keywords: List<String>,
    val supportsAutoImport: Boolean,
    val importStatementTerminator: String = "",
    val keywordSnippets: Map<String, String> = emptyMap(),
) {
    companion object {
        private val KOTLIN = CompletionProfile(
            languageId = "kotlin",
            keywords = KotlinKeywords.ALL,
            supportsAutoImport = true,
            importStatementTerminator = "",
        )

        private val JAVA = CompletionProfile(
            languageId = "java",
            keywords = JavaKeywords.ALL,
            supportsAutoImport = false,
            importStatementTerminator = ";",
            keywordSnippets = JavaSnippets.ALL,
        )

        private val byId: Map<String, CompletionProfile> = mapOf(
            "kotlin" to KOTLIN,
            "java" to JAVA,
        )

        fun forLanguage(languageId: String?): CompletionProfile =
            byId[languageId] ?: CompletionProfile(languageId.orEmpty(), emptyList(), supportsAutoImport = false)
    }
}

object KotlinKeywords {
    val ALL: List<String> = listOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
        "if", "in", "interface", "is", "null", "object", "package", "return",
        "super", "this", "throw", "true", "try", "typealias", "val", "var",
        "when", "while",
        "by", "catch", "constructor", "delegate", "dynamic", "field", "file",
        "finally", "get", "import", "init", "param", "property", "receiver",
        "set", "setparam", "value", "where",
        "abstract", "actual", "annotation", "companion", "const", "crossinline",
        "data", "enum", "expect", "external", "final", "infix", "inline",
        "inner", "internal", "lateinit", "noinline", "open", "operator", "out",
        "override", "private", "protected", "public", "reified", "sealed",
        "suspend", "tailrec", "vararg",
    )
}

object JavaKeywords {
    val ALL: List<String> = listOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch",
        "char", "class", "const", "continue", "default", "do", "double", "else",
        "enum", "extends", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long",
        "native", "new", "package", "private", "protected", "public", "return",
        "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while",
        "true", "false", "null",
        "var", "yield", "record", "sealed", "permits", "non-sealed",
    )
}

object JavaSnippets {
    val ALL: Map<String, String> = mapOf(
        "record" to "record \$1(\$2) {\n    \$0\n}",
    )
}

data class FileHeader(
    val packageName: String?,
    val importedFqns: Set<String>,
    val insertLine: Int,
    val insertNeedsLeadingBlankLine: Boolean,
)

object CompletionAugmentor {

    fun augmentKeywords(
        list: CompletionList,
        prefix: String,
        keywords: List<String>,
        snippets: Map<String, String> = emptyMap(),
    ): CompletionList {
        val matched = keywords.filter { it.startsWith(prefix, ignoreCase = true) }
        if (matched.isEmpty()) return list
        val snippetKeywords = matched.filter { snippets.containsKey(it) }
        val existing = list.items.mapTo(HashSet()) { it.label }
        val plainToAdd = matched.filter { !snippets.containsKey(it) && it !in existing }
        if (snippetKeywords.isEmpty() && plainToAdd.isEmpty()) return list
        val baseItems =
            if (snippetKeywords.isEmpty()) list.items
            else list.items.filterNot { it.label in snippetKeywords }
        val snippetItems = snippetKeywords.map { kw -> keywordItem(kw, snippets.getValue(kw), isSnippet = true) }
        val plainItems = plainToAdd.map { kw -> keywordItem(kw, kw, isSnippet = false) }
        return list.copy(items = snippetItems + plainItems + baseItems)
    }

    private fun keywordItem(keyword: String, insert: String, isSnippet: Boolean): CompletionItem =
        CompletionItem(
            label = keyword,
            kind = CompletionItemKind.KEYWORD,
            detail = if (isSnippet) "snippet" else null,
            documentation = null,
            insertText = insert,
            isSnippet = isSnippet,
            edit = null,
            additionalEdits = emptyList(),
            filterText = keyword,
            sortText = keyword,
        )

    fun parseFileHeader(text: String): FileHeader {
        val lines = text.split('\n')
        var pkg: String? = null
        val imports = HashSet<String>()
        var lastImportLine = -1
        var packageLine = -1
        for (i in lines.indices) {
            val stripped = lines[i].trim().removeSuffix(";")
            when {
                stripped.startsWith("package ") -> {
                    pkg = stripped.removePrefix("package").trim()
                    packageLine = i
                }
                stripped.startsWith("import ") -> {
                    val body = stripped.removePrefix("import").trim()
                    val fqn = body.substringBefore(" as ").trim()
                    imports += fqn
                    lastImportLine = i
                }
            }
        }
        val insertLine: Int
        val needsBlank: Boolean
        when {
            lastImportLine >= 0 -> { insertLine = lastImportLine + 1; needsBlank = false }
            packageLine >= 0 -> { insertLine = packageLine + 1; needsBlank = true }
            else -> { insertLine = 0; needsBlank = false }
        }
        return FileHeader(pkg, imports, insertLine, needsBlank)
    }

    fun buildImportCandidates(
        symbols: List<WorkspaceSymbolEntry>,
        header: FileHeader,
        prefix: String,
        existingLabels: Set<String>,
        importTerminator: String = "",
    ): List<CompletionItem> {
        if (symbols.isEmpty()) return emptyList()
        val typeKinds = setOf(
            SymbolKind.Class,
            SymbolKind.Interface,
            SymbolKind.Enum,
            SymbolKind.Struct,
        )
        val seen = HashSet<String>()
        val candidates = mutableListOf<WorkspaceSymbolEntry>()
        for (sym in symbols) {
            if (sym.kind !in typeKinds) continue
            if (sym.name.isEmpty()) continue
            if (!sym.name.startsWith(prefix, ignoreCase = true)) continue
            val container = sym.containerName?.takeIf { it.isNotBlank() } ?: continue
            if (container == header.packageName) continue
            val fqn = "$container.${sym.name}"
            if (fqn in header.importedFqns) continue
            if (sym.name in existingLabels) continue
            if (!seen.add(fqn)) continue
            candidates += sym
            if (candidates.size >= 20) break
        }
        if (candidates.isEmpty()) return emptyList()
        return candidates.map { sym ->
            val container = sym.containerName!!
            val fqn = "$container.${sym.name}"
            val statement = "import $fqn$importTerminator\n"
            val newText = if (header.insertNeedsLeadingBlankLine) "\n$statement" else statement
            CompletionItem(
                label = sym.name,
                kind = mapSymbolKind(sym.kind),
                detail = "(import from $container)",
                documentation = null,
                insertText = sym.name,
                isSnippet = false,
                edit = null,
                additionalEdits = listOf(
                    CompletionEdit(
                        startLine = header.insertLine,
                        startCharacter = 0,
                        endLine = header.insertLine,
                        endCharacter = 0,
                        newText = newText,
                    )
                ),
                filterText = sym.name,
                sortText = sym.name,
            )
        }
    }

    fun mergeImportItems(list: CompletionList, importItems: List<CompletionItem>): CompletionList {
        if (importItems.isEmpty()) return list
        val leadingKeywordCount = list.items.takeWhile { it.kind == CompletionItemKind.KEYWORD }.size
        val head = list.items.take(leadingKeywordCount)
        val tail = list.items.drop(leadingKeywordCount)
        return list.copy(items = head + importItems + tail)
    }

    private fun mapSymbolKind(kind: SymbolKind?): CompletionItemKind = when (kind) {
        SymbolKind.Interface -> CompletionItemKind.INTERFACE
        SymbolKind.Enum -> CompletionItemKind.ENUM
        SymbolKind.Struct -> CompletionItemKind.STRUCT
        else -> CompletionItemKind.CLASS
    }
}
