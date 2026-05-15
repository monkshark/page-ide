package page.lsp

object CodeActionPreview {

    enum class LineKind { CONTEXT, REMOVED, ADDED, OMITTED }

    data class HunkLine(val kind: LineKind, val text: String, val oldLineNo: Int?, val newLineNo: Int?)

    data class FilePreview(
        val uri: String,
        val basename: String,
        val isCurrent: Boolean,
        val editCount: Int,
        val lines: List<HunkLine>,
    )

    fun build(
        edit: RenameWorkspaceEdit,
        currentUri: String?,
        currentText: String?,
        contextLines: Int = 1,
    ): List<FilePreview> {
        if (edit.isEmpty) return emptyList()
        val out = mutableListOf<FilePreview>()
        for (change in edit.changes) {
            val baseName = uriBasename(change.uri)
            if (change.uri == currentUri && currentText != null) {
                val after = RenameApply.applyToText(currentText, change.edits)
                val lines = renderHunks(currentText, after, contextLines)
                out += FilePreview(change.uri, baseName, true, change.edits.size, lines)
            } else {
                out += FilePreview(change.uri, baseName, false, change.edits.size, emptyList())
            }
        }
        return out
    }

    private fun renderHunks(before: String, after: String, ctx: Int): List<HunkLine> {
        val a = before.split('\n')
        val b = after.split('\n')
        val ops = lineDiff(a, b)
        if (ops.none { it !is DiffOp.Equal }) return emptyList()
        val groups = groupHunks(ops, ctx)
        val out = mutableListOf<HunkLine>()
        var lastEndAIdx = -1
        for ((idx, hunk) in groups.withIndex()) {
            val firstAIdx = hunk.firstNotNullOfOrNull { aIdxOf(it) } ?: 0
            if (idx > 0 && firstAIdx > lastEndAIdx + 1) {
                out += HunkLine(LineKind.OMITTED, "…", null, null)
            }
            for (op in hunk) {
                when (op) {
                    is DiffOp.Equal -> {
                        val text = a.getOrNull(op.aIdx) ?: ""
                        out += HunkLine(LineKind.CONTEXT, text, op.aIdx + 1, op.bIdx + 1)
                    }
                    is DiffOp.Delete -> {
                        val text = a.getOrNull(op.aIdx) ?: ""
                        out += HunkLine(LineKind.REMOVED, text, op.aIdx + 1, null)
                    }
                    is DiffOp.Insert -> {
                        val text = b.getOrNull(op.bIdx) ?: ""
                        out += HunkLine(LineKind.ADDED, text, null, op.bIdx + 1)
                    }
                }
            }
            lastEndAIdx = hunk.mapNotNull { aIdxOf(it) }.maxOrNull() ?: lastEndAIdx
        }
        return out
    }

    private fun groupHunks(ops: List<DiffOp>, ctx: Int): List<List<DiffOp>> {
        val n = ops.size
        if (n == 0) return emptyList()
        val include = BooleanArray(n)
        for (i in 0 until n) {
            if (ops[i] !is DiffOp.Equal) {
                val from = (i - ctx).coerceAtLeast(0)
                val to = (i + ctx).coerceAtMost(n - 1)
                for (k in from..to) include[k] = true
            }
        }
        val groups = mutableListOf<List<DiffOp>>()
        var i = 0
        while (i < n) {
            if (!include[i]) { i++; continue }
            val start = i
            while (i < n && include[i]) i++
            groups += ops.subList(start, i)
        }
        return groups
    }

    private fun aIdxOf(op: DiffOp): Int? = when (op) {
        is DiffOp.Equal -> op.aIdx
        is DiffOp.Delete -> op.aIdx
        is DiffOp.Insert -> null
    }

    private sealed class DiffOp {
        data class Equal(val aIdx: Int, val bIdx: Int) : DiffOp()
        data class Delete(val aIdx: Int) : DiffOp()
        data class Insert(val bIdx: Int) : DiffOp()
    }

    private fun lineDiff(a: List<String>, b: List<String>): List<DiffOp> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1
                else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val ops = mutableListOf<DiffOp>()
        var i = 0
        var j = 0
        while (i < m && j < n) {
            when {
                a[i] == b[j] -> { ops += DiffOp.Equal(i, j); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { ops += DiffOp.Delete(i); i++ }
                else -> { ops += DiffOp.Insert(j); j++ }
            }
        }
        while (i < m) { ops += DiffOp.Delete(i); i++ }
        while (j < n) { ops += DiffOp.Insert(j); j++ }
        return ops
    }

    private fun uriBasename(uri: String): String {
        val slash = uri.lastIndexOf('/')
        return if (slash >= 0 && slash < uri.length - 1) uri.substring(slash + 1) else uri
    }
}
