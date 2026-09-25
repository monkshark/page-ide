package page.atlas.export

import java.nio.file.Files
import java.nio.file.Path
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.toNioPath

data class BundleFile(val id: String, val path: String, val text: String?, val notice: String? = null)

data class CodeBundle(val files: List<BundleFile>, val text: String) {
    val characterCount: Int get() = text.length
    val includedCount: Int get() = files.count { it.text != null }
}

object CodeBundles {
    const val FILE_LIMIT = 32_000
    const val CONTENT_LIMIT = 256_000

    fun candidates(slice: GraphSlice, focusId: String, uses: Boolean, usedBy: Boolean): Set<String> = buildSet {
        add(focusId)
        for (edge in slice.edges) {
            if (uses && edge.from == focusId) add(edge.to)
            if (usedBy && edge.to == focusId) add(edge.from)
        }
    }.intersect(slice.nodes.filter { it.path != null && it.kind != NodeKind.EXTERNAL }.mapTo(hashSetOf()) { it.id })

    fun paths(slice: GraphSlice): Map<String, String> {
        val nodes = slice.nodes.filter { it.path != null && it.kind != NodeKind.EXTERNAL }
        val paths = nodes.map { it.path!!.toNioPath().toAbsolutePath().normalize() }
        var root = paths.firstOrNull()?.parent
        for (path in paths) while (root != null && !path.startsWith(root)) root = root.parent
        return nodes.zip(paths).associate { (node, path) -> node.id to
            (root?.relativize(path)?.toString() ?: path.fileName.toString()).replace('\\', '/') }
    }

    fun build(
        slice: GraphSlice,
        selectedIds: Set<String>,
        read: (Path, Int) -> String = ::readBounded,
    ): CodeBundle {
        val paths = paths(slice)
        var remaining = CONTENT_LIMIT
        val files = slice.nodes.filter { it.id in selectedIds && it.id in paths }
            .distinctBy { it.path }.sortedBy { paths[it.id] }.map { node ->
                val path = paths.getValue(node.id)
                if (remaining <= 0) return@map BundleFile(node.id, path, null, "Bundle size limit reached")
                try {
                    val limit = minOf(FILE_LIMIT, remaining)
                    val raw = read(node.path!!.toNioPath(), limit + 1)
                    if ('\u0000' in raw) return@map BundleFile(node.id, path, null, "Binary content omitted")
                    val text = raw.take(limit)
                    remaining -= text.length
                    BundleFile(node.id, path, text, if (raw.length > limit) "Excerpt limited to $limit characters" else null)
                } catch (_: java.io.IOException) {
                    BundleFile(node.id, path, null, "File unavailable or not readable as UTF-8")
                } catch (_: SecurityException) {
                    BundleFile(node.id, path, null, "File access denied")
                }
            }
        val included = files.filter { it.text != null }.mapTo(hashSetOf()) { it.id }
        val text = buildString {
            appendLine("# Code context")
            appendLine("Saved file contents. Relationships are from the current static analysis.")
            appendLine()
            appendLine("## Files")
            for (file in files) {
                appendLine("- ${file.path}${file.notice?.let { " [$it]" }.orEmpty()}")
            }
            appendLine()
            appendLine("## Relationships")
            val edges = slice.edges.filter { it.from in included && it.to in included }.distinct()
            if (edges.isEmpty()) appendLine("No relationships between the included files.")
            for (edge in edges) {
                appendLine("${paths[edge.from]} -> ${paths[edge.to]} (${edge.kind.name.lowercase()})" +
                    (edge.evidence?.let { " at line ${it.line + 1}" }.orEmpty()))
            }
            for (file in files) {
                val content = file.text ?: continue
                appendLine()
                appendLine("===== ${file.path} =====")
                appendLine(content)
                file.notice?.let { appendLine("[$it]") }
            }
        }
        return CodeBundle(files, text)
    }

    private fun readBounded(path: Path, limit: Int): String = Files.newBufferedReader(path).use { reader ->
        val buffer = CharArray(limit)
        var count = 0
        while (count < limit) {
            val read = reader.read(buffer, count, limit - count)
            if (read < 0) break
            count += read
        }
        String(buffer, 0, count)
    }
}
