package page.atlas.graph

import java.nio.file.Path

data class ModuleNode(
    val id: String,
    val label: String,
    val dirPath: Path,
    val fileCount: Int,
    val kind: NodeKind,
    val language: String,
    val files: List<ModuleFile> = emptyList(),
    val splittable: Boolean = false,
)

data class ModuleFile(val id: String, val name: String, val path: Path)

data class ModuleEdge(val from: String, val to: String, val weight: Int)

data class ModuleGraph(
    val nodes: List<ModuleNode>,
    val edges: List<ModuleEdge>,
    val droppedModules: Int = 0,
    val droppedFiles: Int = 0,
) {
    companion object {
        val EMPTY = ModuleGraph(emptyList(), emptyList())
    }
}

const val MODULE_MAX = 120
const val TARGET_MODULES = 18
const val ROOT_MODULE_LABEL = "<root>"

fun aggregateModules(slice: GraphSlice, activePath: Path? = null, scopeRoot: Path? = null): ModuleGraph {
    val all = slice.nodes.filter { it.path != null }
    val files = if (scopeRoot == null) all
    else all.filter { (it.path!!.parent ?: it.path!!).startsWith(scopeRoot) }
    if (files.isEmpty()) return ModuleGraph.EMPTY
    val root = scopeRoot ?: commonRoot(files.map { it.path!!.parent ?: it.path!! })
    val activeNorm = activePath?.toAbsolutePath()?.normalize()

    val tree = DirNode(root)
    for (node in files) {
        val parent = node.path!!.parent
        var cur = tree
        if (parent != null && parent != root) {
            val rel = runCatching { root.relativize(parent) }.getOrNull()
            if (rel != null && rel.toString().isNotEmpty()) {
                var dir = root
                for (seg in rel) {
                    dir = dir.resolve(seg)
                    cur = cur.dirs.getOrPut(seg.toString()) { DirNode(dir) }
                }
            }
        }
        cur.files.add(node)
    }
    compress(tree)
    countSubtree(tree)

    val total = tree.subtreeCount
    val target = maxOf(1, total / TARGET_MODULES)
    val frontier = mutableListOf(Cut(tree, loose = false))
    while (frontier.size < MODULE_MAX) {
        val candidate = frontier
            .filter { !it.loose && it.node.dirs.isNotEmpty() && it.count() > target }
            .sortedWith(compareByDescending<Cut> { it.count() }.thenBy { it.node.dir.toString() })
            .firstOrNull() ?: break
        frontier.remove(candidate)
        for (child in candidate.node.dirs.values.sortedBy { it.dir.toString() }) {
            frontier.add(Cut(child, loose = false))
        }
        if (candidate.node.files.isNotEmpty()) frontier.add(Cut(candidate.node, loose = true))
    }

    val fileModule = HashMap<String, String>()
    val accs = LinkedHashMap<String, ModuleAcc>()
    for (cut in frontier) {
        val moduleId = cut.node.dir.toString()
        val acc = accs.getOrPut(moduleId) { ModuleAcc(cut.node.dir, moduleLabel(cut.node.dir, root)) }
        if (!cut.loose && cut.node.dirs.isNotEmpty()) acc.splittable = true
        val owned = if (cut.loose) cut.node.files else cut.subtreeFiles()
        for (node in owned) {
            fileModule[node.id] = moduleId
            acc.fileCount++
            acc.files.add(ModuleFile(node.id, node.label, node.path!!))
            val ext = extensionOf(node.path)
            if (ext.isNotEmpty()) acc.languages.merge(ext, 1, Int::plus)
            if (node.kind == NodeKind.ACTIVE ||
                (activeNorm != null && node.path.toAbsolutePath().normalize() == activeNorm)
            ) {
                acc.active = true
            }
        }
    }

    val weights = LinkedHashMap<Pair<String, String>, Int>()
    for (edge in slice.edges) {
        val from = fileModule[edge.from] ?: continue
        val to = fileModule[edge.to] ?: continue
        if (from == to) continue
        weights.merge(from to to, 1, Int::plus)
    }

    var nodes = accs.map { (id, acc) ->
        ModuleNode(
            id = id,
            label = acc.label,
            dirPath = acc.dir,
            fileCount = acc.fileCount,
            kind = if (acc.active) NodeKind.ACTIVE else NodeKind.WORKSPACE_FILE,
            language = dominantLanguage(acc.languages),
            files = acc.files.sortedWith(compareBy({ it.name }, { it.id })),
            splittable = acc.splittable,
        )
    }
    var edges = weights.map { (key, weight) -> ModuleEdge(key.first, key.second, weight) }

    var droppedModules = 0
    var droppedFiles = 0
    if (nodes.size > MODULE_MAX) {
        val ranked = nodes.sortedWith(compareByDescending<ModuleNode> { it.fileCount }.thenBy { it.id })
        val kept = ranked.take(MODULE_MAX)
        val keptIds = kept.mapTo(HashSet()) { it.id }
        val dropped = ranked.drop(MODULE_MAX)
        droppedModules = dropped.size
        droppedFiles = dropped.sumOf { it.fileCount }
        println("[atlas] aggregateModules dropped $droppedModules modules / $droppedFiles files (cap $MODULE_MAX)")
        nodes = kept
        edges = edges.filter { it.from in keptIds && it.to in keptIds }
    }

    return ModuleGraph(nodes, edges, droppedModules, droppedFiles)
}

private class DirNode(val dir: Path) {
    val dirs = LinkedHashMap<String, DirNode>()
    val files = ArrayList<GraphNode>()
    var subtreeCount = 0
}

private class Cut(val node: DirNode, val loose: Boolean) {
    fun count(): Int = if (loose) node.files.size else node.subtreeCount
    fun subtreeFiles(): List<GraphNode> {
        val out = ArrayList<GraphNode>(node.subtreeCount)
        val stack = ArrayDeque<DirNode>()
        stack.addLast(node)
        while (stack.isNotEmpty()) {
            val d = stack.removeLast()
            out.addAll(d.files)
            for (child in d.dirs.values) stack.addLast(child)
        }
        return out
    }
}

private class ModuleAcc(val dir: Path, val label: String) {
    var fileCount = 0
    var active = false
    var splittable = false
    val languages = HashMap<String, Int>()
    val files = ArrayList<ModuleFile>()
}

private fun compress(dir: DirNode) {
    val children = dir.dirs.values.toList()
    dir.dirs.clear()
    for (start in children) {
        var child = start
        while (child.files.isEmpty() && child.dirs.size == 1) {
            child = child.dirs.values.first()
        }
        compress(child)
        dir.dirs[child.dir.toString()] = child
    }
}

private fun countSubtree(dir: DirNode): Int {
    var sum = dir.files.size
    for (child in dir.dirs.values) sum += countSubtree(child)
    dir.subtreeCount = sum
    return sum
}

private fun dominantLanguage(languages: Map<String, Int>): String =
    languages.entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.key ?: ""

private fun moduleLabel(moduleDir: Path, root: Path): String {
    if (moduleDir == root) return ROOT_MODULE_LABEL
    val rel = runCatching { root.relativize(moduleDir) }.getOrNull()?.toString()
    if (rel.isNullOrEmpty()) return moduleDir.fileName?.toString() ?: moduleDir.toString()
    return rel.replace('\\', '/')
}

private fun extensionOf(path: Path): String {
    val name = path.fileName?.toString() ?: return ""
    val dot = name.lastIndexOf('.')
    return if (dot in 1 until name.length - 1) name.substring(dot + 1).lowercase() else ""
}

private fun commonRoot(dirs: List<Path>): Path {
    var prefix = dirs.first()
    for (dir in dirs.drop(1)) {
        while (!dir.startsWith(prefix)) {
            prefix = prefix.parent ?: return prefix
        }
    }
    return prefix
}
