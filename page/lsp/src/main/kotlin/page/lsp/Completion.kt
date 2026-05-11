package page.lsp

import org.eclipse.lsp4j.InsertTextFormat as LspInsertTextFormat

enum class CompletionItemKind {
    TEXT, METHOD, FUNCTION, CONSTRUCTOR, FIELD, VARIABLE, CLASS, INTERFACE,
    MODULE, PROPERTY, UNIT, VALUE, ENUM, KEYWORD, SNIPPET, COLOR, FILE,
    REFERENCE, FOLDER, ENUM_MEMBER, CONSTANT, STRUCT, EVENT, OPERATOR,
    TYPE_PARAMETER, OTHER;

    companion object {
        fun fromLsp(k: org.eclipse.lsp4j.CompletionItemKind?): CompletionItemKind = when (k) {
            org.eclipse.lsp4j.CompletionItemKind.Text -> TEXT
            org.eclipse.lsp4j.CompletionItemKind.Method -> METHOD
            org.eclipse.lsp4j.CompletionItemKind.Function -> FUNCTION
            org.eclipse.lsp4j.CompletionItemKind.Constructor -> CONSTRUCTOR
            org.eclipse.lsp4j.CompletionItemKind.Field -> FIELD
            org.eclipse.lsp4j.CompletionItemKind.Variable -> VARIABLE
            org.eclipse.lsp4j.CompletionItemKind.Class -> CLASS
            org.eclipse.lsp4j.CompletionItemKind.Interface -> INTERFACE
            org.eclipse.lsp4j.CompletionItemKind.Module -> MODULE
            org.eclipse.lsp4j.CompletionItemKind.Property -> PROPERTY
            org.eclipse.lsp4j.CompletionItemKind.Unit -> UNIT
            org.eclipse.lsp4j.CompletionItemKind.Value -> VALUE
            org.eclipse.lsp4j.CompletionItemKind.Enum -> ENUM
            org.eclipse.lsp4j.CompletionItemKind.Keyword -> KEYWORD
            org.eclipse.lsp4j.CompletionItemKind.Snippet -> SNIPPET
            org.eclipse.lsp4j.CompletionItemKind.Color -> COLOR
            org.eclipse.lsp4j.CompletionItemKind.File -> FILE
            org.eclipse.lsp4j.CompletionItemKind.Reference -> REFERENCE
            org.eclipse.lsp4j.CompletionItemKind.Folder -> FOLDER
            org.eclipse.lsp4j.CompletionItemKind.EnumMember -> ENUM_MEMBER
            org.eclipse.lsp4j.CompletionItemKind.Constant -> CONSTANT
            org.eclipse.lsp4j.CompletionItemKind.Struct -> STRUCT
            org.eclipse.lsp4j.CompletionItemKind.Event -> EVENT
            org.eclipse.lsp4j.CompletionItemKind.Operator -> OPERATOR
            org.eclipse.lsp4j.CompletionItemKind.TypeParameter -> TYPE_PARAMETER
            null -> OTHER
        }
    }
}

data class CompletionEdit(
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int,
    val newText: String,
)

data class CompletionItem(
    val label: String,
    val kind: CompletionItemKind,
    val detail: String? = null,
    val documentation: String? = null,
    val insertText: String,
    val isSnippet: Boolean,
    val edit: CompletionEdit? = null,
    val additionalEdits: List<CompletionEdit> = emptyList(),
    val filterText: String = label,
    val sortText: String = label,
) {
    companion object {
        fun fromLsp(item: org.eclipse.lsp4j.CompletionItem): CompletionItem {
            val labelText = item.label ?: ""
            val explicitInsert = item.insertText
            val edit = item.textEdit?.left?.let { te -> te.toCompletionEdit() }
            val resolvedInsert = edit?.newText ?: explicitInsert ?: labelText
            val docText = item.documentation?.let { docHolder ->
                if (docHolder.isLeft) docHolder.left
                else docHolder.right?.value
            }
            val additional = item.additionalTextEdits.orEmpty().map { it.toCompletionEdit() }
            return CompletionItem(
                label = labelText,
                kind = CompletionItemKind.fromLsp(item.kind),
                detail = item.detail,
                documentation = docText,
                insertText = resolvedInsert,
                isSnippet = item.insertTextFormat == LspInsertTextFormat.Snippet,
                edit = edit,
                additionalEdits = additional,
                filterText = item.filterText ?: labelText,
                sortText = item.sortText ?: labelText,
            )
        }

        private fun org.eclipse.lsp4j.TextEdit.toCompletionEdit(): CompletionEdit = CompletionEdit(
            startLine = range.start.line,
            startCharacter = range.start.character,
            endLine = range.end.line,
            endCharacter = range.end.character,
            newText = newText ?: "",
        )
    }
}

data class SnippetTabstop(val number: Int, val start: Int, val end: Int)

data class ExpandedSnippet(
    val text: String,
    val finalCaret: Int,
    val tabstops: List<SnippetTabstop> = emptyList(),
)

object SnippetExpander {
    fun expand(snippet: String): ExpandedSnippet {
        val out = StringBuilder()
        var finalCaret = -1
        val stops = mutableListOf<SnippetTabstop>()
        var i = 0
        while (i < snippet.length) {
            val c = snippet[i]
            if (c == '\\' && i + 1 < snippet.length) {
                val next = snippet[i + 1]
                if (next == '$' || next == '}' || next == '\\') {
                    out.append(next)
                    i += 2
                    continue
                }
            }
            if (c == '$' && i + 1 < snippet.length) {
                val next = snippet[i + 1]
                if (next.isDigit()) {
                    var j = i + 1
                    val numStart = j
                    while (j < snippet.length && snippet[j].isDigit()) j++
                    val n = snippet.substring(numStart, j).toIntOrNull() ?: 0
                    if (n == 0) {
                        if (finalCaret < 0) finalCaret = out.length
                    } else {
                        stops += SnippetTabstop(n, out.length, out.length)
                    }
                    i = j
                    continue
                }
                if (next == '{') {
                    val end = findClosingBrace(snippet, i + 1)
                    if (end > 0) {
                        val body = snippet.substring(i + 2, end)
                        val (placeholder, num) = parsePlaceholder(body)
                        val startPos = out.length
                        out.append(placeholder)
                        val endPos = out.length
                        if (num == 0) {
                            if (finalCaret < 0) finalCaret = startPos
                        } else if (num > 0) {
                            stops += SnippetTabstop(num, startPos, endPos)
                        }
                        i = end + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        val orderedStops = stops
            .groupBy { it.number }
            .toSortedMap()
            .map { (_, group) -> group.first() }
        return ExpandedSnippet(
            text = out.toString(),
            finalCaret = if (finalCaret >= 0) finalCaret else out.length,
            tabstops = orderedStops,
        )
    }

    private fun findClosingBrace(s: String, openAt: Int): Int {
        var depth = 0
        var i = openAt
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { i += 2; continue }
            if (c == '{') depth++
            else if (c == '}') { depth--; if (depth == 0) return i }
            i++
        }
        return -1
    }

    private fun parsePlaceholder(body: String): Pair<String, Int> {
        var j = 0
        while (j < body.length && body[j].isDigit()) j++
        val num = body.substring(0, j).toIntOrNull() ?: -1
        if (j >= body.length) return "" to num
        val rest = body.substring(j)
        return when {
            rest.startsWith(":") -> rest.substring(1) to num
            rest.startsWith("|") -> {
                val end = rest.indexOf('|', 1)
                val choices = if (end > 0) rest.substring(1, end) else rest.substring(1)
                (choices.split(',').firstOrNull().orEmpty()) to num
            }
            else -> "" to num
        }
    }
}

data class CompletionList(
    val isIncomplete: Boolean,
    val items: List<CompletionItem>,
) {
    companion object {
        val EMPTY: CompletionList = CompletionList(isIncomplete = false, items = emptyList())

        fun fromLsp(
            result: org.eclipse.lsp4j.CompletionList,
            triggerCharacter: String? = null,
            prefix: String? = null,
        ): CompletionList = CompletionList(
            isIncomplete = result.isIncomplete,
            items = CompletionEnhancer.enhance(
                result.items.orEmpty().map(CompletionItem::fromLsp),
                triggerCharacter,
                prefix,
            ),
        )

        fun fromLspItems(
            items: List<org.eclipse.lsp4j.CompletionItem>,
            triggerCharacter: String? = null,
            prefix: String? = null,
        ): CompletionList = CompletionList(
            isIncomplete = false,
            items = CompletionEnhancer.enhance(items.map(CompletionItem::fromLsp), triggerCharacter, prefix),
        )
    }
}

object CompletionEnhancer {
    fun enhance(
        items: List<CompletionItem>,
        triggerCharacter: String? = null,
        prefix: String? = null,
    ): List<CompletionItem> {
        val existingLabels = items.map { it.label }.toMutableSet()
        val enhanced = items.map { item -> enhanceOne(item, existingLabels) }
        val processed = if (triggerCharacter != ".") {
            enhanced
        } else {
            val filtered = enhanced.filter { keepForDot(it) }
            val source = if (filtered.isEmpty() && enhanced.isNotEmpty()) enhanced else filtered
            source.withIndex()
                .sortedWith(compareBy({ tierOf(it.value) }, { it.index }))
                .map { it.value }
        }
        val deduped = dedup(processed)
        return if (prefix.isNullOrEmpty()) deduped else sortByPrefix(deduped, prefix)
    }

    fun applyPrefixSort(items: List<CompletionItem>, prefix: String?): List<CompletionItem> =
        if (prefix.isNullOrEmpty()) items else sortByPrefix(items, prefix)

    fun filterByPrefix(items: List<CompletionItem>, prefix: String): List<CompletionItem> {
        if (prefix.isEmpty()) return items
        return items.filter { item ->
            item.label.startsWith(prefix, ignoreCase = true) ||
                item.insertText.startsWith(prefix, ignoreCase = true) ||
                item.filterText.startsWith(prefix, ignoreCase = true)
        }
    }

    private fun sortByPrefix(items: List<CompletionItem>, prefix: String): List<CompletionItem> {
        val lower = prefix.lowercase()
        val prefixUppercase = prefix.firstOrNull()?.isUpperCase() == true
        return items.withIndex()
            .sortedWith(
                compareBy(
                    { prefixRank(it.value.label, prefix, lower) },
                    { if (prefixUppercase) caseAffinityRank(it.value) else 0 },
                    { it.index },
                )
            )
            .map { it.value }
    }

    private fun prefixRank(label: String, prefix: String, lowerPrefix: String): Int {
        if (label.startsWith(prefix)) return 0
        if (label.lowercase().startsWith(lowerPrefix)) return 1
        return 2
    }

    private val typeKinds = setOf(
        CompletionItemKind.CLASS,
        CompletionItemKind.INTERFACE,
        CompletionItemKind.ENUM,
        CompletionItemKind.STRUCT,
        CompletionItemKind.CONSTRUCTOR,
        CompletionItemKind.ENUM_MEMBER,
        CompletionItemKind.TYPE_PARAMETER,
    )

    private fun caseAffinityRank(item: CompletionItem): Int {
        if (item.kind in typeKinds) return 0
        if (item.label.firstOrNull()?.isUpperCase() == true) return 1
        if (item.kind == CompletionItemKind.KEYWORD) return 3
        return 2
    }

    private fun dedup(items: List<CompletionItem>): List<CompletionItem> {
        val seen = HashSet<Triple<String, String?, CompletionItemKind>>()
        return items.filter { seen.add(Triple(it.label, it.detail, it.kind)) }
    }

    private fun keepForDot(item: CompletionItem): Boolean {
        if (item.kind == CompletionItemKind.KEYWORD) return false
        val typeKinds = setOf(
            CompletionItemKind.CLASS,
            CompletionItemKind.INTERFACE,
            CompletionItemKind.ENUM,
        )
        if (item.kind in typeKinds && item.detail?.contains("(import from ") == true) return false
        return true
    }

    private fun tierOf(item: CompletionItem): Int {
        val detail = item.detail ?: return 0
        if (detail.contains('!')) return 3
        return when (extensionReceiverKind(detail)) {
            ReceiverKind.None -> 0
            ReceiverKind.TypeSpecific -> 1
            ReceiverKind.Generic -> 2
        }
    }

    private enum class ReceiverKind { None, TypeSpecific, Generic }

    private val extensionRegex = Regex("""\bfun\s+(?:<[^>]*>\s+)?([\w<>,?\s]+?)\.\w+\s*\(""")

    private fun extensionReceiverKind(detail: String): ReceiverKind {
        val match = extensionRegex.find(detail) ?: return ReceiverKind.None
        val receiver = match.groupValues[1].trim()
        return if (isGenericReceiver(receiver)) ReceiverKind.Generic else ReceiverKind.TypeSpecific
    }

    private fun isGenericReceiver(s: String): Boolean {
        val cleaned = s.removeSuffix("?").trim()
        if (cleaned.length == 1 && cleaned[0].isUpperCase()) return true
        if (cleaned == "Any") return true
        return false
    }

    private fun enhanceOne(item: CompletionItem, existingLabels: MutableSet<String>): CompletionItem {
        if (item.isSnippet) return item
        if (item.kind != CompletionItemKind.CLASS) return item
        if (item.detail?.startsWith("(import from ") == true) return item
        val params = extractParams(item) ?: return item
        if (params.isEmpty()) return item
        val baseName = item.insertText.substringBefore('(').ifBlank { item.label }
        val snippetBody = params.mapIndexed { i, name -> "\${${i + 1}:$name}" }.joinToString(", ")
        val snippet = "$baseName($snippetBody)\$0"
        val displayLabel = "$baseName(${params.joinToString(", ")})"
        if (displayLabel in existingLabels) return item
        existingLabels += displayLabel
        return item.copy(
            label = displayLabel,
            insertText = snippet,
            isSnippet = true,
            filterText = item.filterText.takeIf { it.isNotBlank() } ?: baseName,
        )
    }

    private fun extractParams(item: CompletionItem): List<String>? {
        val sources = listOfNotNull(item.label, item.detail, item.insertText)
        for (source in sources) {
            val params = paramsFrom(source) ?: continue
            return params
        }
        return null
    }

    private fun paramsFrom(s: String): List<String>? {
        val open = s.indexOf('(')
        if (open < 0) return null
        var depth = 0
        var close = -1
        for (i in open until s.length) {
            when (s[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) { close = i; break }
                }
            }
        }
        if (close < 0) return null
        val inside = s.substring(open + 1, close).trim()
        if (inside.isEmpty()) return emptyList()
        val parts = splitTopLevel(inside)
        if (parts.isEmpty()) return null
        return parts.map { paramName(it) }.takeIf { it.all(String::isNotBlank) }
    }

    private fun splitTopLevel(s: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in s.indices) {
            when (s[i]) {
                '(', '<', '[' -> depth++
                ')', '>', ']' -> depth--
                ',' -> if (depth == 0) {
                    out += s.substring(start, i).trim()
                    start = i + 1
                }
            }
        }
        out += s.substring(start).trim()
        return out.filter { it.isNotEmpty() }
    }

    private fun paramName(part: String): String {
        val colon = part.indexOf(':')
        val raw = if (colon >= 0) part.substring(0, colon).trim() else part.trim()
        return raw.removePrefix("vararg ").removePrefix("val ").removePrefix("var ").trim()
    }
}
