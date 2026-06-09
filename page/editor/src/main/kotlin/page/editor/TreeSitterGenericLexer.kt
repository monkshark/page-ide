package page.editor

import org.treesitter.TSLanguage
import org.treesitter.TSNode
import org.treesitter.TSParser
import org.treesitter.TSTreeCursor

class TreeSitterGenericLexer(
    private val languageFactory: () -> TSLanguage,
    private val overrides: Map<String, TokenKind> = emptyMap(),
) : SyntaxLexer {

    private val parser: TSParser by lazy {
        TSParser().also { it.setLanguage(languageFactory()) }
    }

    override fun tokenize(text: String): List<Token> {
        if (text.isEmpty()) return emptyList()
        val bytes = text.toByteArray(Charsets.UTF_8)
        val tree = synchronized(parser) { parser.parseString(null, text) } ?: return emptyList()
        val byteToChar = buildByteToCharIndex(bytes, text)
        val out = ArrayList<Token>()
        val cursor = TSTreeCursor(tree.rootNode)
        walk(cursor, text, byteToChar, text.length, out)
        return out
    }

    private fun walk(c: TSTreeCursor, text: String, byteToChar: IntArray, charLen: Int, out: MutableList<Token>) {
        val node = c.currentNode()
        val kind = classify(node)
        if (kind != null) {
            val start = mapByte(byteToChar, node.startByte, charLen)
            val end = mapByte(byteToChar, node.endByte, charLen)
            if (end > start) {
                val effective = if (kind == TokenKind.COMMENT && isDocCommentRange(text, start, end)) {
                    TokenKind.DOC_COMMENT
                } else kind
                out += Token(effective, start until end)
                if (kind == TokenKind.COMMENT) emitTodoSubTokens(text, start, end, out)
            }
            return
        }
        if (c.gotoFirstChild()) {
            do {
                walk(c, text, byteToChar, charLen, out)
            } while (c.gotoNextSibling())
            c.gotoParent()
        }
    }

    private fun isDocCommentRange(text: String, start: Int, end: Int): Boolean {
        if (end - start < 4) return false
        if (text[start] == '/' && text[start + 1] == '*' && text[start + 2] == '*' && text[start + 3] != '/') return true
        return false
    }

    private fun emitTodoSubTokens(text: String, start: Int, end: Int, out: MutableList<Token>) {
        val safeEnd = end.coerceAtMost(text.length)
        if (start >= safeEnd) return
        val slice = text.substring(start, safeEnd)
        for (m in todoPattern.findAll(slice)) {
            val absStart = start + m.range.first
            val absEnd = start + m.range.last + 1
            out += Token(TokenKind.TODO_TAG, absStart until absEnd)
        }
    }

    private fun classify(node: TSNode): TokenKind? {
        val type = node.type ?: return null
        overrides[type]?.let { return it }
        return defaultClassify(node, type)
    }

    private fun defaultClassify(node: TSNode, type: String): TokenKind? = when {
        type == "comment" || type == "line_comment" || type == "block_comment" -> TokenKind.COMMENT

        type == "string" || type == "string_literal" || type == "string_content" ||
            type == "raw_string" || type == "template_string" || type == "interpreted_string_literal" ||
            type == "char_literal" || type == "character_literal" || type == "rune_literal" -> TokenKind.STRING

        type == "integer" || type == "integer_literal" || type == "float" || type == "float_literal" ||
            type == "number" || type == "decimal_integer_literal" || type == "hex_integer_literal" -> TokenKind.NUMBER

        type == "type_identifier" || type == "type_name" || type == "builtin_type" ||
            type == "primitive_type" -> TokenKind.TYPE

        type == "decorator" || type == "attribute" || type == "attribute_item" ||
            type == "annotation" || type == "marker_annotation" -> TokenKind.ANNOTATION

        !node.isNamed && type.isNotEmpty() && type[0].isLetter() -> TokenKind.KEYWORD

        else -> null
    }

    private fun buildByteToCharIndex(bytes: ByteArray, text: String): IntArray {
        val map = IntArray(bytes.size + 1)
        var charIdx = 0
        var byteIdx = 0
        while (charIdx < text.length) {
            val cp = text.codePointAt(charIdx)
            val charLen = Character.charCount(cp)
            val byteLen = utf8Length(cp)
            for (b in 0 until byteLen) {
                if (byteIdx + b <= map.lastIndex) map[byteIdx + b] = charIdx
            }
            byteIdx += byteLen
            charIdx += charLen
        }
        for (i in byteIdx..map.lastIndex) map[i] = text.length
        return map
    }

    private fun mapByte(map: IntArray, byte: Int, charLen: Int): Int {
        if (byte < 0) return 0
        if (byte >= map.size) return charLen
        return map[byte]
    }

    private fun utf8Length(cp: Int): Int = when {
        cp < 0x80 -> 1
        cp < 0x800 -> 2
        cp < 0x10000 -> 3
        else -> 4
    }

    companion object {
        internal val todoPattern = Regex("""\b(TODO|FIXME|HACK|XXX|NOTE|BUG|REVIEW|EXCEPTION)\b(:[^\n]*)?""")
    }
}
