package page.atlas.analyzer

import java.nio.file.Files
import java.nio.file.Path
import page.atlas.graph.CodeGraphProvider
import page.atlas.graph.EdgeKind
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

class ImportGraphProvider(root: Path) : CodeGraphProvider {

    private val index = WorkspaceIndex(root)
    private val cache = HashMap<String, CachedAnalysis>()

    private data class CachedAnalysis(val mtime: Long, val analysis: FileAnalysis)

    override fun nodesForFile(path: Path, text: String): GraphSlice {
        if (!ImportExtractor.supports(path)) return GraphSlice.EMPTY
        val activePath = path.toAbsolutePath().normalize()
        val activeId = activePath.toString()
        val nodes = LinkedHashMap<String, GraphNode>()
        val edges = LinkedHashMap<Pair<String, String>, GraphEdge>()
        val queue = ArrayDeque<Pair<Path, String?>>()
        val processed = HashSet<String>()
        nodes[activeId] = GraphNode(activeId, activePath.fileName.toString(), activePath, NodeKind.ACTIVE)
        queue += activePath to text
        while (queue.isNotEmpty()) {
            val (file, override) = queue.removeFirst()
            val fileId = nodeId(file)
            if (!processed.add(fileId)) continue
            val analysis =
                if (override != null) ImportExtractor.analyze(file, override)
                else cachedAnalysis(file) ?: continue
            val imported = ArrayList<Pair<RawImport, GraphNode>>()
            for (raw in mergeByTarget(analysis.imports)) {
                val resolved = ImportResolver.resolve(raw, file, index)
                val id = resolved?.let(::nodeId) ?: raw.target
                if (id == fileId) continue
                val node = nodes[id] ?: addNode(nodes, queue, id, raw, resolved) ?: continue
                edges.putIfAbsent(fileId to id, GraphEdge(fileId, id))
                imported += raw to node
            }
            applyRelations(file, fileId, analysis.relations, imported, nodes, edges, queue)
        }
        return GraphSlice(nodes.values.toList(), edges.values.toList())
    }

    private fun addNode(
        nodes: LinkedHashMap<String, GraphNode>,
        queue: ArrayDeque<Pair<Path, String?>>,
        id: String,
        raw: RawImport,
        resolved: Path?,
    ): GraphNode? {
        if (nodes.size >= MAX_NODES) return null
        val node = if (resolved != null) {
            GraphNode(id, resolved.fileName.toString(), resolved, NodeKind.WORKSPACE_FILE)
        } else {
            GraphNode(id, raw.target, null, NodeKind.EXTERNAL)
        }
        nodes[id] = node
        if (resolved != null) queue += resolved to null
        return node
    }

    private fun applyRelations(
        file: Path,
        fileId: String,
        relations: List<RawRelation>,
        imported: List<Pair<RawImport, GraphNode>>,
        nodes: LinkedHashMap<String, GraphNode>,
        edges: LinkedHashMap<Pair<String, String>, GraphEdge>,
        queue: ArrayDeque<Pair<Path, String?>>,
    ) {
        for (relation in relations) {
            val simple = relation.typeName.substringAfterLast('.').substringAfterLast(':')
            if (simple.isEmpty()) continue
            val target = imported.firstOrNull { (raw, node) -> matches(simple, raw, node) }?.second
                ?: sameDirTarget(file, simple, nodes, queue)
                ?: continue
            if (target.id == fileId) continue
            val key = fileId to target.id
            val current = edges[key]
            if (current == null || rank(relation.kind) > rank(current.kind)) {
                edges[key] = GraphEdge(fileId, target.id, relation.kind)
            }
        }
    }

    private fun mergeByTarget(imports: List<RawImport>): List<RawImport> =
        imports.groupBy { it.target }.map { (_, group) ->
            if (group.size == 1) group[0]
            else group[0].copy(symbols = group.flatMap { it.symbols }.distinct())
        }

    private fun matches(simple: String, raw: RawImport, node: GraphNode): Boolean {
        if (raw.symbols.any { it == simple }) return true
        val path = node.path
        return if (path != null) {
            path.fileName.toString().substringBeforeLast('.').equals(simple, ignoreCase = true)
        } else {
            raw.target.substringAfterLast('.').substringAfterLast('/').substringAfterLast(':')
                .equals(simple, ignoreCase = true)
        }
    }

    private fun sameDirTarget(
        file: Path,
        simple: String,
        nodes: LinkedHashMap<String, GraphNode>,
        queue: ArrayDeque<Pair<Path, String?>>,
    ): GraphNode? {
        val dir = file.parent ?: return null
        val exts = siblingExts(extOf(file)) ?: return null
        val sibling = exts.asSequence()
            .map { dir.resolve("$simple.$it") }
            .firstOrNull { Files.isRegularFile(it) }
            ?: return null
        val id = nodeId(sibling)
        nodes[id]?.let { return it }
        if (nodes.size >= MAX_NODES) return null
        val resolved = sibling.toAbsolutePath().normalize()
        val node = GraphNode(id, resolved.fileName.toString(), resolved, NodeKind.WORKSPACE_FILE)
        nodes[id] = node
        queue += resolved to null
        return node
    }

    private fun siblingExts(ext: String): List<String>? = when (ext) {
        "java" -> listOf("java")
        "kt", "kts" -> listOf("kt", "kts")
        "py", "pyi" -> listOf("py", "pyi")
        "js", "jsx", "mjs", "cjs", "ts", "tsx" -> listOf("ts", "tsx", "js", "jsx")
        else -> null
    }

    private fun cachedAnalysis(file: Path): FileAnalysis? {
        val key = nodeId(file)
        val mtime = try {
            Files.getLastModifiedTime(file).toMillis()
        } catch (e: Exception) {
            return null
        }
        cache[key]?.takeIf { it.mtime == mtime }?.let { return it.analysis }
        val text = try {
            Files.readString(file)
        } catch (e: Exception) {
            return null
        }
        val analysis = ImportExtractor.analyze(file, text)
        if (cache.size >= MAX_CACHE) cache.clear()
        cache[key] = CachedAnalysis(mtime, analysis)
        return analysis
    }

    private fun rank(kind: EdgeKind): Int = when (kind) {
        EdgeKind.IMPORT -> 0
        EdgeKind.IMPLEMENTS -> 1
        EdgeKind.EXTENDS -> 2
    }

    private fun nodeId(path: Path): String = path.toAbsolutePath().normalize().toString()

    private fun extOf(path: Path): String =
        path.fileName?.toString()?.substringAfterLast('.', "")?.lowercase() ?: ""

    private companion object {
        const val MAX_NODES = 100
        const val MAX_CACHE = 2048
    }
}
