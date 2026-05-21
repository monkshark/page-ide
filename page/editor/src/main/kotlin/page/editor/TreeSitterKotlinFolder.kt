package page.editor

import org.treesitter.TSParser
import org.treesitter.TSTreeCursor
import org.treesitter.TreeSitterKotlin

object TreeSitterKotlinFolder : LanguageFolder {

    private val parser: TSParser by lazy {
        TSParser().also { it.setLanguage(TreeSitterKotlin()) }
    }

    override fun detect(text: String): List<FoldRegions.Region> {
        if (text.isEmpty()) return emptyList()
        val bytes = text.toByteArray(Charsets.UTF_8)
        val tree = synchronized(parser) { parser.parseString(null, text) } ?: return emptyList()
        val byteToChar = TreeSitterFoldUtil.buildByteToCharIndex(bytes, text)
        val lineStarts = TreeSitterFoldUtil.buildLineStarts(text)
        val regions = LinkedHashSet<FoldRegions.Region>()
        val cursor = TSTreeCursor(tree.rootNode)
        walk(cursor, text, byteToChar, lineStarts, regions)
        return regions
            .filter { it.endLine > it.startLine }
            .sortedBy { it.startLine }
    }

    private fun walk(
        c: TSTreeCursor,
        text: String,
        byteToChar: IntArray,
        lineStarts: IntArray,
        out: MutableSet<FoldRegions.Region>,
    ) {
        val node = c.currentNode()
        val type = node.type
        if (type != null && isFoldable(type)) {
            val startChar = TreeSitterFoldUtil.mapByte(byteToChar, node.startByte, text.length)
            val endCharExclusive = TreeSitterFoldUtil.mapByte(byteToChar, node.endByte, text.length)
            if (endCharExclusive > startChar) {
                val startLine = TreeSitterFoldUtil.lineOf(lineStarts, startChar)
                val lastIdx = TreeSitterFoldUtil.trimToContentEnd(text, lineStarts, startChar, endCharExclusive)
                if (lastIdx > startChar) {
                    val endLine = TreeSitterFoldUtil.lineOf(lineStarts, lastIdx)
                    if (endLine > startLine && !TreeSitterFoldUtil.hasTrailingContentAfter(text, lineStarts, lastIdx, endLine)) {
                        val prefix = if (type == "import_list") "import" else ""
                        out += FoldRegions.Region(startLine, endLine, prefix)
                    }
                }
            }
        }
        if (c.gotoFirstChild()) {
            do {
                walk(c, text, byteToChar, lineStarts, out)
            } while (c.gotoNextSibling())
            c.gotoParent()
        }
    }

    private fun isFoldable(type: String): Boolean = when (type) {
        "function_body",
        "class_body",
        "enum_class_body",
        "object_body",
        "interface_body",
        "anonymous_initializer",
        "getter",
        "setter",
        "import_list",
        "multiline_comment",
        "when_expression",
        "lambda_literal",
        "control_structure_body",
        -> true
        else -> false
    }
}
