package page.lsp

data class HoverRange(
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int,
)

data class HoverInfo(
    val markdown: String,
    val range: HoverRange?,
) {
    companion object {
        val EMPTY: HoverInfo = HoverInfo(markdown = "", range = null)

        fun fromLsp(hover: org.eclipse.lsp4j.Hover?): HoverInfo? {
            if (hover == null) return null
            val md = renderContents(hover.contents) ?: return null
            if (md.isBlank()) return null
            if (isSyntheticErrorType(md)) return null
            if (hover.range == null && isTrivialTypeOnly(md)) return null
            val r = hover.range?.let {
                HoverRange(
                    startLine = it.start.line,
                    startCharacter = it.start.character,
                    endLine = it.end.line,
                    endCharacter = it.end.character,
                )
            }
            return HoverInfo(markdown = md, range = r)
        }

        private val TRIVIAL_HOVER_TYPES = setOf("Unit", "Nothing", "null")

        private fun isTrivialTypeOnly(md: String): Boolean {
            val body = extractBareKotlinType(md) ?: return false
            return body in TRIVIAL_HOVER_TYPES
        }

        private val ERROR_TYPE_RE = Regex("""^\[Error type:.*\]$""")

        private fun isSyntheticErrorType(md: String): Boolean {
            val body = extractBareKotlinType(md)
            if (body != null && ERROR_TYPE_RE.matches(body)) return true
            return ERROR_TYPE_RE.matches(md.trim())
        }

        private fun renderContents(
            contents: org.eclipse.lsp4j.jsonrpc.messages.Either<
                MutableList<org.eclipse.lsp4j.jsonrpc.messages.Either<String, org.eclipse.lsp4j.MarkedString>>,
                org.eclipse.lsp4j.MarkupContent,
                >?,
        ): String? {
            if (contents == null) return null
            return when {
                contents.isRight -> contents.right?.value
                contents.isLeft -> contents.left
                    .orEmpty()
                    .mapNotNull { renderMarked(it) }
                    .joinToString("\n\n")
                else -> null
            }
        }

        private fun renderMarked(
            entry: org.eclipse.lsp4j.jsonrpc.messages.Either<String, org.eclipse.lsp4j.MarkedString>,
        ): String? = when {
            entry.isLeft -> entry.left?.takeIf { it.isNotBlank() }
            entry.isRight -> entry.right?.let { ms ->
                val v = ms.value?.takeIf { it.isNotBlank() } ?: return@let null
                val lang = ms.language?.takeIf { it.isNotBlank() }
                if (lang != null) "```$lang\n$v\n```" else v
            }
            else -> null
        }
    }
}

fun HoverInfo.enrichForPropertyDecl(lineText: String, character: Int): HoverInfo {
    val typeBody = extractBareKotlinType(markdown) ?: return this
    if (!isLikelyBareType(typeBody)) return this
    val decl = detectPropertyDeclAt(lineText, character) ?: return this
    val enriched = "```kotlin\n${decl.keyword} ${decl.name}: $typeBody\n```"
    return copy(markdown = enriched)
}

fun HoverInfo.needsKdocEnrichment(): Boolean {
    if (markdown.isBlank()) return false
    if (KDOC_TAG_AT_LINE_START.containsMatchIn(markdown)) return false
    return markdown.contains("```") && markdown.contains("\n---")
        || markdown.contains("```") && markdown.contains("\n\n")
}

fun HoverInfo.enrichWithKDocFromDefinition(definitionFileText: String, definitionLine: Int): HoverInfo {
    if (!needsKdocEnrichment()) return this
    val tagLines = extractKdocTagsAbove(definitionFileText, definitionLine)
    if (tagLines.isEmpty()) return this
    val sep = if (markdown.endsWith("\n\n")) "" else if (markdown.endsWith("\n")) "\n" else "\n\n"
    return copy(markdown = markdown + sep + tagLines.joinToString("\n"))
}

private val KDOC_TAG_AT_LINE_START = Regex(
    """(?m)^\s*@(param|return|returns|throws|exception|property|see|since|sample|author|receiver|constructor|suppress)\b"""
)

private val KDOC_OPEN_LINE = Regex("""^\s*/\*\*""")
private val KDOC_TAG_BODY = Regex("""^@(\w+)(?:\s+(.*))?$""")

internal fun extractKdocTagsAbove(text: String, declarationLine: Int): List<String> {
    if (declarationLine <= 0) return emptyList()
    val lines = text.split('\n')
    if (declarationLine >= lines.size) return emptyList()

    var i = declarationLine - 1
    while (i >= 0) {
        val t = lines[i].trim()
        if (t.isEmpty()) { i--; continue }
        if (t.startsWith("@") && !t.startsWith("@param") && !t.startsWith("@return") &&
            !t.startsWith("@throws") && !t.startsWith("@property") && !t.startsWith("@see") &&
            !t.startsWith("@since") && !t.startsWith("@sample") && !t.startsWith("@author") &&
            !t.startsWith("@receiver") && !t.startsWith("@constructor") && !t.startsWith("@suppress") &&
            !t.startsWith("@exception")
        ) { i--; continue }
        break
    }
    if (i < 0) return emptyList()

    val singleLine = Regex("""^\s*/\*\*\s*(.*?)\s*\*/\s*$""").matchEntire(lines[i])
    if (singleLine != null) {
        return collectTags(splitOnAt(singleLine.groupValues[1]))
    }

    val endTrim = lines[i].trim()
    if (!endTrim.endsWith("*/")) return emptyList()
    val closeLine = i
    var openLine = -1
    var j = closeLine
    while (j >= 0) {
        if (KDOC_OPEN_LINE.containsMatchIn(lines[j])) { openLine = j; break }
        j--
    }
    if (openLine < 0) return emptyList()

    val bodyLines = mutableListOf<String>()
    for (k in openLine..closeLine) {
        var line = lines[k]
        if (k == openLine) {
            val idx = line.indexOf("/**")
            if (idx >= 0) line = line.substring(idx + 3)
        }
        if (k == closeLine) {
            val idx = line.lastIndexOf("*/")
            if (idx >= 0) line = line.substring(0, idx)
        }
        bodyLines += line
    }
    return collectTags(bodyLines)
}

private fun collectTags(bodyLines: List<String>): List<String> {
    val stripped = bodyLines.map { stripLeadingStar(it).trim() }
    val out = mutableListOf<String>()
    var current: StringBuilder? = null
    for (l in stripped) {
        if (l.isEmpty()) {
            current?.let { out += it.toString() }
            current = null
            continue
        }
        val m = KDOC_TAG_BODY.matchEntire(l)
        if (m != null) {
            current?.let { out += it.toString() }
            val tag = m.groupValues[1]
            val rest = m.groupValues.getOrNull(2)?.trim().orEmpty()
            current = StringBuilder("@").append(tag)
            if (rest.isNotEmpty()) current!!.append(' ').append(rest)
        } else if (current != null) {
            current!!.append(' ').append(l)
        }
    }
    current?.let { out += it.toString() }
    return out
}

private fun splitOnAt(body: String): List<String> {
    if (!body.contains('@')) return emptyList()
    val out = mutableListOf<String>()
    var idx = body.indexOf('@')
    while (idx >= 0) {
        val nextAt = body.indexOf('@', idx + 1)
        val piece = if (nextAt < 0) body.substring(idx) else body.substring(idx, nextAt)
        out += piece.trim()
        idx = nextAt
    }
    return out
}

private fun stripLeadingStar(line: String): String {
    val t = line.trimStart()
    return when {
        t.startsWith("* ") -> t.removePrefix("* ")
        t == "*" -> ""
        t.startsWith("*") -> t.removePrefix("*")
        else -> line
    }
}

private data class PropertyDeclAt(val keyword: String, val name: String)

private val PROPERTY_DECL_LINE = Regex(
    """^\s*(?:(?:private|public|protected|internal|const|lateinit|override|open|final|abstract|companion|inline|@\S+)\s+)*(val|var)\s+(\w+)\s*:"""
)

private fun detectPropertyDeclAt(lineText: String, character: Int): PropertyDeclAt? {
    val m = PROPERTY_DECL_LINE.find(lineText) ?: return null
    val nameGroup = m.groups[2] ?: return null
    if (character !in nameGroup.range) return null
    return PropertyDeclAt(keyword = m.groupValues[1], name = nameGroup.value)
}

private fun extractBareKotlinType(md: String): String? {
    val t = md.trim()
    if (!t.startsWith("```kotlin")) return null
    if (!t.endsWith("```")) return null
    val body = t.removePrefix("```kotlin").trim().removeSuffix("```").trim()
    if (body.isEmpty() || body.contains('\n')) return null
    return body
}

private val SIGNATURE_PREFIXES = listOf(
    "val ", "var ", "fun ", "class ", "interface ", "object ", "enum ",
    "constructor ", "typealias ", "companion ", "inline ", "private ",
    "public ", "protected ", "internal ", "abstract ", "open ", "final ",
    "override ", "sealed ", "data ", "annotation ",
)

private fun isLikelyBareType(body: String): Boolean {
    if (body.isEmpty()) return false
    return SIGNATURE_PREFIXES.none { body.startsWith(it) }
}
