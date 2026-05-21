package page.editor

import org.treesitter.TSParser
import org.treesitter.TSTreeCursor
import org.treesitter.TreeSitterJava

object TreeSitterJavaFolder : LanguageFolder {

    private val parser: TSParser by lazy {
        TSParser().also { it.setLanguage(TreeSitterJava()) }
    }

    override fun detect(text: String): List<FoldRegions.Region> {
        if (text.isEmpty()) return emptyList()
        val bytes = text.toByteArray(Charsets.UTF_8)
        val tree = synchronized(parser) { parser.parseString(null, text) } ?: return emptyList()
        val byteToChar = TreeSitterFoldUtil.buildByteToCharIndex(bytes, text)
        val lineStarts = TreeSitterFoldUtil.buildLineStarts(text)
        val regions = LinkedHashSet<FoldRegions.Region>()
        val importLines = mutableListOf<IntRange>()
        val cursor = TSTreeCursor(tree.rootNode)
        walk(cursor, text, byteToChar, lineStarts, regions, importLines)
        mergeImportGroups(importLines, regions)
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
        imports: MutableList<IntRange>,
    ) {
        val node = c.currentNode()
        val type = node.type
        if (type == "import_declaration") {
            val startChar = TreeSitterFoldUtil.mapByte(byteToChar, node.startByte, text.length)
            val endCharExclusive = TreeSitterFoldUtil.mapByte(byteToChar, node.endByte, text.length)
            if (endCharExclusive > startChar) {
                val s = TreeSitterFoldUtil.lineOf(lineStarts, startChar)
                val e = TreeSitterFoldUtil.lineOf(lineStarts, endCharExclusive - 1)
                imports += s..e
            }
        } else if (type != null && isFoldable(type)) {
            val startChar = TreeSitterFoldUtil.mapByte(byteToChar, node.startByte, text.length)
            val endCharExclusive = TreeSitterFoldUtil.mapByte(byteToChar, node.endByte, text.length)
            if (endCharExclusive > startChar) {
                val startLine = TreeSitterFoldUtil.lineOf(lineStarts, startChar)
                val lastIdx = TreeSitterFoldUtil.trimToContentEnd(text, lineStarts, startChar, endCharExclusive)
                if (lastIdx > startChar) {
                    val endLine = TreeSitterFoldUtil.lineOf(lineStarts, lastIdx)
                    if (endLine > startLine && !TreeSitterFoldUtil.hasTrailingContentAfter(text, lineStarts, lastIdx, endLine)) {
                        out += FoldRegions.Region(startLine, endLine, "")
                    }
                }
            }
        }
        if (c.gotoFirstChild()) {
            do {
                walk(c, text, byteToChar, lineStarts, out, imports)
            } while (c.gotoNextSibling())
            c.gotoParent()
        }
    }

    private fun mergeImportGroups(imports: List<IntRange>, out: MutableSet<FoldRegions.Region>) {
        if (imports.isEmpty()) return
        val sorted = imports.sortedBy { it.first }
        var groupStart = sorted[0].first
        var groupEnd = sorted[0].last
        for (i in 1 until sorted.size) {
            val r = sorted[i]
            if (r.first <= groupEnd + 1) {
                if (r.last > groupEnd) groupEnd = r.last
            } else {
                if (groupEnd > groupStart) out += FoldRegions.Region(groupStart, groupEnd, "import")
                groupStart = r.first
                groupEnd = r.last
            }
        }
        if (groupEnd > groupStart) out += FoldRegions.Region(groupStart, groupEnd, "import")
    }

    private fun isFoldable(type: String): Boolean = when (type) {
        "class_body",
        "interface_body",
        "enum_body",
        "annotation_type_body",
        "record_body",
        "block",
        "constructor_body",
        "switch_block",
        "block_comment",
        "array_initializer",
        -> true
        else -> false
    }
}
