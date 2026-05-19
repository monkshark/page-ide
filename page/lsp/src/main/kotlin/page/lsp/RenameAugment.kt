package page.lsp

import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

object RenameAugment {

    fun augment(
        edit: RenameWorkspaceEdit,
        references: List<ReferenceLocation>,
        oldName: String,
        newName: String,
        readFileText: (Path) -> String?,
    ): RenameWorkspaceEdit {
        if (oldName.isEmpty() || newName.isEmpty() || oldName == newName) return edit
        if (references.isEmpty()) return edit

        val coveredByPath = HashMap<Path, MutableSet<Pair<Int, Int>>>()
        for (change in edit.changes) {
            val path = uriToPath(change.uri) ?: continue
            val set = coveredByPath.getOrPut(path) { mutableSetOf() }
            for (e in change.edits) {
                set.add(e.startLine to e.startCharacter)
            }
        }

        val byUri = LinkedHashMap<String, RenameFileChange>()
        for (change in edit.changes) byUri[change.uri] = change

        val textCache = HashMap<Path, String>()
        var added = 0
        for (ref in references) {
            if (ref.startLine != ref.endLine) continue
            if (ref.endCharacter - ref.startCharacter != oldName.length) continue

            val path = uriToPath(ref.uri) ?: continue
            val coverage = coveredByPath[path]
            if (coverage != null && (ref.startLine to ref.startCharacter) in coverage) continue

            val text = textCache.getOrPut(path) { readFileText(path).orEmpty() }
            if (text.isEmpty()) continue
            val slice = extractSlice(text, ref) ?: continue
            if (slice != oldName) continue

            val newEdit = RenameEdit(
                startLine = ref.startLine,
                startCharacter = ref.startCharacter,
                endLine = ref.endLine,
                endCharacter = ref.endCharacter,
                newText = newName,
            )
            val existing = byUri[ref.uri]
            byUri[ref.uri] = if (existing == null) {
                RenameFileChange(ref.uri, listOf(newEdit))
            } else {
                RenameFileChange(existing.uri, sortDescending(existing.edits + newEdit))
            }
            coveredByPath.getOrPut(path) { mutableSetOf() }.add(ref.startLine to ref.startCharacter)
            added++
        }

        return if (added == 0) edit else RenameWorkspaceEdit(byUri.values.toList())
    }

    fun augmentTextually(
        edit: RenameWorkspaceEdit,
        oldName: String,
        newName: String,
        readFileText: (Path) -> String?,
    ): RenameWorkspaceEdit {
        if (oldName.isEmpty() || newName.isEmpty() || oldName == newName) return edit
        if (edit.changes.isEmpty()) return edit

        val coveredByPath = HashMap<Path, MutableSet<Pair<Int, Int>>>()
        for (change in edit.changes) {
            val path = uriToPath(change.uri) ?: continue
            val set = coveredByPath.getOrPut(path) { mutableSetOf() }
            for (e in change.edits) set.add(e.startLine to e.startCharacter)
        }

        val byUri = LinkedHashMap<String, RenameFileChange>()
        for (change in edit.changes) byUri[change.uri] = change

        var added = 0
        for (change in edit.changes) {
            val path = uriToPath(change.uri) ?: continue
            val text = readFileText(path) ?: continue
            if (text.isEmpty()) continue
            val matches = TextualRenameScan.findQualifiedReceiverMatches(text, oldName)
            if (matches.isEmpty()) continue
            val covered = coveredByPath.getOrPut(path) { mutableSetOf() }
            val existing = byUri[change.uri]!!
            val collected = mutableListOf<RenameEdit>()
            for (m in matches) {
                if ((m.line to m.column) in covered) continue
                collected += RenameEdit(
                    startLine = m.line,
                    startCharacter = m.column,
                    endLine = m.line,
                    endCharacter = m.column + oldName.length,
                    newText = newName,
                )
                covered.add(m.line to m.column)
                added++
            }
            if (collected.isNotEmpty()) {
                byUri[change.uri] = RenameFileChange(existing.uri, sortDescending(existing.edits + collected))
            }
        }

        return if (added == 0) edit else RenameWorkspaceEdit(byUri.values.toList())
    }

    fun augmentImports(
        edit: RenameWorkspaceEdit,
        candidatePaths: Collection<Path>,
        oldName: String,
        newName: String,
        declarationPackage: String? = null,
        readFileText: (Path) -> String?,
    ): RenameWorkspaceEdit {
        if (oldName.isEmpty() || newName.isEmpty() || oldName == newName) return edit
        if (candidatePaths.isEmpty()) return edit

        val coveredByPath = HashMap<Path, MutableSet<Pair<Int, Int>>>()
        val uriByPath = HashMap<Path, String>()
        for (change in edit.changes) {
            val path = uriToPath(change.uri) ?: continue
            uriByPath[path] = change.uri
            val set = coveredByPath.getOrPut(path) { mutableSetOf() }
            for (e in change.edits) set.add(e.startLine to e.startCharacter)
        }

        val byUri = LinkedHashMap<String, RenameFileChange>()
        for (change in edit.changes) byUri[change.uri] = change

        var added = 0
        for (raw in candidatePaths) {
            val path = raw.toAbsolutePath().normalize()
            val text = readFileText(path) ?: continue
            if (text.isEmpty()) continue
            val matches = TextualRenameScan.findImportMatches(text, oldName, declarationPackage)
            if (matches.isEmpty()) continue

            val uri = uriByPath[path] ?: path.toUri().toString()
            val covered = coveredByPath.getOrPut(path) { mutableSetOf() }
            val collected = mutableListOf<RenameEdit>()
            for (m in matches) {
                if ((m.line to m.column) in covered) continue
                collected += RenameEdit(
                    startLine = m.line,
                    startCharacter = m.column,
                    endLine = m.line,
                    endCharacter = m.column + oldName.length,
                    newText = newName,
                )
                covered.add(m.line to m.column)
                added++
            }
            if (collected.isEmpty()) continue
            val existing = byUri[uri]
            byUri[uri] = if (existing == null) {
                RenameFileChange(uri, sortDescending(collected))
            } else {
                RenameFileChange(existing.uri, sortDescending(existing.edits + collected))
            }
        }

        return if (added == 0) edit else RenameWorkspaceEdit(byUri.values.toList())
    }

    fun augmentDeclarationFile(
        edit: RenameWorkspaceEdit,
        declarationPath: Path,
        oldName: String,
        newName: String,
        readFileText: (Path) -> String?,
    ): RenameWorkspaceEdit {
        if (oldName.isEmpty() || newName.isEmpty() || oldName == newName) return edit
        val target = declarationPath.toAbsolutePath().normalize()
        val text = readFileText(target) ?: return edit
        if (text.isEmpty()) return edit
        val matches = TextualRenameScan.findAllMatches(text, oldName)
        if (matches.isEmpty()) return edit

        val byUri = LinkedHashMap<String, RenameFileChange>()
        for (change in edit.changes) byUri[change.uri] = change

        var targetUri: String? = null
        val covered = mutableSetOf<Pair<Int, Int>>()
        for (change in edit.changes) {
            val p = uriToPath(change.uri) ?: continue
            if (p == target) {
                targetUri = change.uri
                for (e in change.edits) covered.add(e.startLine to e.startCharacter)
                break
            }
        }
        val resolvedUri = targetUri ?: target.toUri().toString()

        val collected = mutableListOf<RenameEdit>()
        for (m in matches) {
            if ((m.line to m.column) in covered) continue
            collected += RenameEdit(
                startLine = m.line,
                startCharacter = m.column,
                endLine = m.line,
                endCharacter = m.column + oldName.length,
                newText = newName,
            )
            covered.add(m.line to m.column)
        }
        if (collected.isEmpty()) return edit

        val existing = byUri[resolvedUri]
        byUri[resolvedUri] = if (existing == null) {
            RenameFileChange(resolvedUri, sortDescending(collected))
        } else {
            RenameFileChange(existing.uri, sortDescending(existing.edits + collected))
        }
        return RenameWorkspaceEdit(byUri.values.toList())
    }

    private fun uriToPath(uri: String): Path? = runCatching {
        Paths.get(URI.create(uri)).toAbsolutePath().normalize()
    }.getOrNull()

    private fun extractSlice(text: String, ref: ReferenceLocation): String? {
        var idx = 0
        var line = 0
        while (idx < text.length && line < ref.startLine) {
            if (text[idx] == '\n') line++
            idx++
        }
        if (line != ref.startLine) return null
        val lineStart = idx
        var lineEnd = idx
        while (lineEnd < text.length && text[lineEnd] != '\n') lineEnd++
        val startInText = lineStart + ref.startCharacter
        val endInText = lineStart + ref.endCharacter
        if (startInText < lineStart || endInText > lineEnd) return null
        if (endInText <= startInText) return null
        return text.substring(startInText, endInText)
    }

    private fun sortDescending(edits: List<RenameEdit>): List<RenameEdit> =
        edits.sortedWith(
            compareByDescending<RenameEdit> { it.startLine }
                .thenByDescending { it.startCharacter }
        )
}
