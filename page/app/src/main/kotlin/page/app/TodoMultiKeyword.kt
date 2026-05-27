package page.app

import page.runtime.*

import page.editor.Token
import page.editor.TokenKind

data class MultiKeywordComment(
    val commentRange: IntRange,
    val line: Int,
    val keywords: List<String>,
    val detectedKeywords: List<String>,
    val effectiveKeyword: String,
)

object TodoMultiKeyword {

    val STANDARD_KEYWORDS: List<String> = listOf("TODO", "FIXME", "HACK", "XXX", "NOTE", "BUG", "REVIEW", "EXCEPTION")

    fun analyze(
        text: String,
        tokens: List<Token>,
    ): List<MultiKeywordComment> {
        if (tokens.isEmpty()) return emptyList()
        val byComment = LinkedHashMap<IntRange, MutableList<String>>()
        for (i in tokens.indices) {
            val t = tokens[i]
            if (t.kind != TokenKind.TODO_TAG) continue
            val keyword = keywordOf(text, t.range) ?: continue
            for (j in i - 1 downTo 0) {
                val p = tokens[j]
                val isComment = p.kind == TokenKind.COMMENT || p.kind == TokenKind.DOC_COMMENT
                if (!isComment) continue
                if (p.range.first <= t.range.first && p.range.last >= t.range.last) {
                    byComment.getOrPut(p.range) { mutableListOf() } += keyword
                }
                break
            }
        }
        if (byComment.isEmpty()) return emptyList()
        val out = mutableListOf<MultiKeywordComment>()
        for ((range, keywords) in byComment) {
            val detected = keywords.distinct()
            if (detected.isEmpty()) continue
            val start = range.first.coerceAtLeast(0)
            val end = (range.last + 1).coerceAtMost(text.length)
            if (start >= end) continue
            val line = lineAt(text, start)
            out += MultiKeywordComment(
                commentRange = range,
                line = line,
                keywords = STANDARD_KEYWORDS,
                detectedKeywords = detected,
                effectiveKeyword = detected.first(),
            )
        }
        return out
    }

    private fun keywordOf(text: String, range: IntRange): String? {
        val start = range.first.coerceAtLeast(0)
        val end = (range.last + 1).coerceAtMost(text.length)
        if (start >= end) return null
        val span = text.substring(start, end)
        val colon = span.indexOf(':')
        val raw = if (colon > 0) span.substring(0, colon) else span
        return raw.takeIf { it.isNotEmpty() }
    }

    private fun lineAt(text: String, offset: Int): Int {
        var count = 0
        val limit = offset.coerceIn(0, text.length)
        for (i in 0 until limit) if (text[i] == '\n') count++
        return count
    }

    fun canonicalKey(rawComment: String): String {
        var s = rawComment.trim()
        if (s.startsWith("///")) s = s.removePrefix("///").trim()
        else if (s.startsWith("//")) s = s.removePrefix("//").trim()
        if (s.startsWith("/**")) s = s.removePrefix("/**").trim()
        else if (s.startsWith("/*")) s = s.removePrefix("/*").trim()
        if (s.endsWith("*/")) s = s.removeSuffix("*/").trim()
        return s.replace("\\s+".toRegex(), " ")
    }
}

internal fun rewriteFirstKeyword(
    text: String,
    commentRange: IntRange,
    oldKeyword: String,
    newKeyword: String,
): String? {
    val start = commentRange.first.coerceAtLeast(0)
    val end = (commentRange.last + 1).coerceAtMost(text.length)
    if (start >= end) return null
    val pattern = Regex("\\b" + Regex.escape(oldKeyword) + "\\b")
    val match = pattern.find(text, start) ?: return null
    if (match.range.last >= end) return null
    return text.substring(0, match.range.first) + newKeyword + text.substring(match.range.last + 1)
}
