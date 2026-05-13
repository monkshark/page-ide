package page.lsp

import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3

data class RenameEdit(
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int,
    val newText: String,
)

data class RenameFileChange(
    val uri: String,
    val edits: List<RenameEdit>,
)

data class RenameWorkspaceEdit(
    val changes: List<RenameFileChange>,
) {
    val isEmpty: Boolean get() = changes.isEmpty() || changes.all { it.edits.isEmpty() }

    val totalEditCount: Int get() = changes.sumOf { it.edits.size }

    companion object {
        val EMPTY: RenameWorkspaceEdit = RenameWorkspaceEdit(emptyList())

        fun fromLsp(edit: WorkspaceEdit?): RenameWorkspaceEdit {
            if (edit == null) return EMPTY
            val fromDocChanges = edit.documentChanges?.let(::fromDocumentChanges).orEmpty()
            if (fromDocChanges.isNotEmpty()) return RenameWorkspaceEdit(fromDocChanges)
            val fromChanges = edit.changes?.let(::fromChangesMap).orEmpty()
            return RenameWorkspaceEdit(fromChanges)
        }

        private fun fromDocumentChanges(
            docs: List<Either<TextDocumentEdit, ResourceOperation>>,
        ): List<RenameFileChange> {
            val out = mutableListOf<RenameFileChange>()
            for (entry in docs) {
                if (!entry.isLeft) continue
                val tde = entry.left ?: continue
                val uri = tde.textDocument?.uri ?: continue
                val edits = tde.edits.orEmpty().mapNotNull(::fromTextEdit).sortedDescending()
                if (edits.isNotEmpty()) out += RenameFileChange(uri, edits)
            }
            return out
        }

        private fun fromChangesMap(map: Map<String, List<TextEdit>>): List<RenameFileChange> {
            val out = mutableListOf<RenameFileChange>()
            for ((uri, edits) in map) {
                val mapped = edits.orEmpty().mapNotNull(::fromTextEdit).sortedDescending()
                if (mapped.isNotEmpty()) out += RenameFileChange(uri, mapped)
            }
            return out
        }

        private fun fromTextEdit(edit: TextEdit?): RenameEdit? {
            if (edit == null) return null
            val r = edit.range ?: return null
            return RenameEdit(
                startLine = r.start.line,
                startCharacter = r.start.character,
                endLine = r.end.line,
                endCharacter = r.end.character,
                newText = edit.newText.orEmpty(),
            )
        }

        private fun List<RenameEdit>.sortedDescending(): List<RenameEdit> =
            sortedWith(
                compareByDescending<RenameEdit> { it.startLine }
                    .thenByDescending { it.startCharacter }
            )
    }
}

data class RenamePrepare(
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int,
    val placeholder: String?,
) {
    companion object {
        fun fromLsp(
            either: Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>?,
        ): RenamePrepare? {
            if (either == null) return null
            return when {
                either.isFirst -> rangeOnly(either.first)
                either.isSecond -> withPlaceholder(either.second)
                either.isThird -> if (either.third?.isDefaultBehavior == true) DEFAULT else null
                else -> null
            }
        }

        val DEFAULT: RenamePrepare = RenamePrepare(-1, -1, -1, -1, null)

        private fun rangeOnly(r: Range?): RenamePrepare? {
            r ?: return null
            return RenamePrepare(
                startLine = r.start.line,
                startCharacter = r.start.character,
                endLine = r.end.line,
                endCharacter = r.end.character,
                placeholder = null,
            )
        }

        private fun withPlaceholder(p: PrepareRenameResult?): RenamePrepare? {
            p ?: return null
            val r = p.range ?: return null
            val placeholder = p.placeholder?.takeIf { it.isNotEmpty() }
            return RenamePrepare(
                startLine = r.start.line,
                startCharacter = r.start.character,
                endLine = r.end.line,
                endCharacter = r.end.character,
                placeholder = placeholder,
            )
        }
    }

    val isDefaultBehavior: Boolean get() = this === DEFAULT
}

object RenameApply {
    fun applyToText(text: String, edits: List<RenameEdit>): String {
        if (edits.isEmpty()) return text
        val ordered = edits.sortedWith(
            compareByDescending<RenameEdit> { it.startLine }.thenByDescending { it.startCharacter }
        )
        val lineStarts = computeLineStarts(text)
        val sb = StringBuilder(text)
        for (e in ordered) {
            val from = offsetOf(lineStarts, text.length, e.startLine, e.startCharacter)
            val to = offsetOf(lineStarts, text.length, e.endLine, e.endCharacter)
            if (from < 0 || to < 0 || to < from) continue
            sb.replace(from, to, e.newText)
        }
        return sb.toString()
    }

    private fun computeLineStarts(text: String): IntArray {
        val starts = mutableListOf(0)
        for (i in text.indices) {
            if (text[i] == '\n') starts += i + 1
        }
        return starts.toIntArray()
    }

    private fun offsetOf(lineStarts: IntArray, textLength: Int, line: Int, character: Int): Int {
        if (line < 0 || line >= lineStarts.size) return -1
        val start = lineStarts[line]
        val nextStart = if (line + 1 < lineStarts.size) lineStarts[line + 1] else textLength + 1
        val lineLen = (nextStart - 1).coerceAtLeast(start) - start
        val ch = character.coerceIn(0, lineLen)
        return start + ch
    }
}
