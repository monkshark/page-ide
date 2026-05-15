package page.lsp

object CodeActionDebug {

    fun format(
        entry: CodeActionEntry,
        currentUri: String?,
        currentText: String?,
        contextLines: Int = 2,
    ): String {
        val sb = StringBuilder()
        sb.append("─── action: \"${entry.title}\"")
        entry.kind?.let { sb.append(" [").append(it).append(']') }
        if (entry.isPreferred) sb.append(" ⭐")
        if (!entry.isExecutable) sb.append(" (no-edit)")
        sb.append('\n')
        if (entry.edit.isEmpty) {
            sb.append("  (workspace edit empty)\n")
            return sb.toString()
        }
        for (change in entry.edit.changes) {
            val basename = uriBasename(change.uri)
            sb.append("  ▸ ").append(basename).append(" — ").append(change.edits.size).append(" edit(s)\n")
            for ((idx, e) in change.edits.withIndex()) {
                sb.append("    #").append(idx + 1).append(' ')
                sb.append("[").append(e.startLine + 1).append(':').append(e.startCharacter + 1)
                    .append("..").append(e.endLine + 1).append(':').append(e.endCharacter + 1).append("]")
                val isInsertion = e.startLine == e.endLine && e.startCharacter == e.endCharacter
                val isDeletion = e.newText.isEmpty() && !isInsertion
                val kind = when {
                    isInsertion -> "insert"
                    isDeletion -> "delete"
                    else -> "replace"
                }
                sb.append(" (").append(kind).append(")\n")
                sb.append("      newText=").append(formatLiteral(e.newText)).append('\n')
            }
            val isCurrent = currentUri != null && change.uri == currentUri
            if (isCurrent && currentText != null) {
                sb.append("    ┌─ 기존코드 (before)\n")
                appendWindow(sb, currentText, change.edits, contextLines, prefix = "    │ ")
                sb.append("    ├─ 미리보기 (hunks)\n")
                val previews = CodeActionPreview.build(entry.edit, currentUri, currentText, contextLines)
                for (file in previews.filter { it.isCurrent }) {
                    for (line in file.lines) {
                        sb.append("    │ ").append(prefixFor(line.kind))
                        sb.append(line.text).append('\n')
                    }
                }
                val after = RenameApply.applyToText(currentText, change.edits)
                val afterAnchors = projectAnchors(change.edits)
                sb.append("    └─ 수정후코드 (after)\n")
                appendWindowAt(sb, after, afterAnchors, contextLines, prefix = "      ")
            } else if (isCurrent) {
                sb.append("    (no current text snapshot)\n")
            } else {
                sb.append("    (다른 파일 — before/after 생략)\n")
            }
        }
        return sb.toString()
    }

    private fun appendWindow(
        sb: StringBuilder,
        text: String,
        edits: List<RenameEdit>,
        ctx: Int,
        prefix: String,
    ) {
        val anchors = edits.map { it.startLine to it.endLine }
        appendWindowFrom(sb, text, anchors, ctx, prefix)
    }

    private fun appendWindowAt(
        sb: StringBuilder,
        text: String,
        anchors: List<Pair<Int, Int>>,
        ctx: Int,
        prefix: String,
    ) {
        appendWindowFrom(sb, text, anchors, ctx, prefix)
    }

    private fun appendWindowFrom(
        sb: StringBuilder,
        text: String,
        anchors: List<Pair<Int, Int>>,
        ctx: Int,
        prefix: String,
    ) {
        if (anchors.isEmpty()) return
        val lines = text.split('\n')
        val ranges = anchors
            .map { (s, e) ->
                (s - ctx).coerceAtLeast(0) to (e + ctx).coerceAtMost(lines.size - 1)
            }
            .sortedBy { it.first }
        var lastShown = -1
        for ((from, to) in ranges) {
            if (from > lastShown + 1 && lastShown >= 0) {
                sb.append(prefix).append("…\n")
            }
            val start = maxOf(from, lastShown + 1)
            for (i in start..to) {
                if (i in lines.indices) {
                    sb.append(prefix).append("%4d │ ".format(i + 1)).append(lines[i]).append('\n')
                }
            }
            lastShown = to
        }
    }

    private fun projectAnchors(edits: List<RenameEdit>): List<Pair<Int, Int>> {
        var offset = 0
        val out = mutableListOf<Pair<Int, Int>>()
        for (e in edits.sortedBy { it.startLine }) {
            val originalSpan = e.endLine - e.startLine
            val newSpan = e.newText.count { it == '\n' }
            val isInsertion = e.startLine == e.endLine && e.startCharacter == e.endCharacter
            val s = e.startLine + offset
            val end = if (isInsertion) s + newSpan else s + newSpan
            out += s to end
            offset += newSpan - originalSpan
        }
        return out
    }

    private fun prefixFor(kind: CodeActionPreview.LineKind): String = when (kind) {
        CodeActionPreview.LineKind.ADDED -> "+ "
        CodeActionPreview.LineKind.REMOVED -> "- "
        CodeActionPreview.LineKind.OMITTED -> "  …"
        CodeActionPreview.LineKind.CONTEXT -> "  "
    }

    private fun formatLiteral(s: String): String {
        if (s.isEmpty()) return "\"\""
        val escaped = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")
        return "\"" + (if (escaped.length > 80) escaped.take(80) + "…" else escaped) + "\""
    }

    private fun uriBasename(uri: String): String {
        val slash = uri.lastIndexOf('/')
        return if (slash >= 0 && slash < uri.length - 1) uri.substring(slash + 1) else uri
    }
}
