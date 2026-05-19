package page.app

import org.eclipse.lsp4j.SymbolKind
import page.lsp.DocumentSymbolEntry

object FileSymbolRename {

    private val RENAMABLE_KINDS = setOf(
        SymbolKind.Class,
        SymbolKind.Interface,
        SymbolKind.Enum,
        SymbolKind.Struct,
        SymbolKind.Object,
        SymbolKind.Function,
    )

    fun findRenamableTopLevelSymbol(
        fileStem: String,
        symbols: List<DocumentSymbolEntry>,
    ): DocumentSymbolEntry? {
        if (fileStem.isEmpty()) return null
        val topLevel = symbols.filter { it.containerName.isNullOrBlank() }
        val byKindPriority = topLevel.sortedBy { kindPriority(it.kind) }
        return byKindPriority.firstOrNull { it.name == fileStem }
    }

    fun isValidKotlinIdentifier(name: String): Boolean {
        if (name.isEmpty()) return false
        if (!name[0].isJavaIdentifierStart()) return false
        for (i in 1 until name.length) {
            if (!name[i].isJavaIdentifierPart()) return false
        }
        return name !in HARD_KEYWORDS
    }

    fun stripKotlinExtension(fileName: String): String? {
        return when {
            fileName.endsWith(".kt") -> fileName.removeSuffix(".kt")
            fileName.endsWith(".kts") -> fileName.removeSuffix(".kts")
            else -> null
        }
    }

    fun readPackageDeclaration(fileText: String): String? {
        for (rawLine in fileText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("//")) continue
            if (!line.startsWith("package")) {
                if (line.startsWith("@") || line.startsWith("/*")) continue
                return null
            }
            if (line.length == 7 || !line[7].isJavaIdentifierPart()) {
                val rest = line.substring(7).trimStart()
                val end = rest.indexOfFirst { !(it.isJavaIdentifierPart() || it == '.') }
                val pkg = if (end < 0) rest else rest.substring(0, end)
                return pkg.takeIf { it.isNotEmpty() }
            }
            return null
        }
        return null
    }

    private fun kindPriority(kind: SymbolKind?): Int = when (kind) {
        SymbolKind.Class, SymbolKind.Interface, SymbolKind.Enum, SymbolKind.Struct, SymbolKind.Object -> 0
        SymbolKind.Function -> 1
        in RENAMABLE_KINDS -> 2
        else -> 3
    }

    private val HARD_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
        "in", "interface", "is", "null", "object", "package", "return", "super", "this",
        "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
    )
}
