package page.atlas.graph

import java.nio.file.Path

data class ModuleNode(
    val id: String,
    val label: String,
    val dirPath: Path,
    val fileCount: Int,
    val kind: NodeKind,
    val language: String,
)

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

const val MODULE_DEPTH = 1
const val MODULE_MAX = 120
const val ROOT_MODULE_LABEL = "<root>"

fun aggregateModules(
    slice: GraphSlice,
    depth: Int = MODULE_DEPTH,
    activePath: Path? = null,
): ModuleGraph {
    val files = slice.nodes.filter { it.path != null }
    if (files.isEmpty()) return ModuleGraph.EMPTY
    val root = commonRoot(files.map { it.path!!.parent ?: it.path!! })
    val activeNorm = activePath?.toAbsolutePath()?.normalize()

    val fileModule = HashMap<String, String>()
    val accs = LinkedHashMap<String, ModuleAcc>()
    for (node in files) {
        val path = node.path!!
        val moduleDir = moduleDirOf(path, root, depth)
        val moduleId = moduleDir.toString()
        fileModule[node.id] = moduleId
        val acc = accs.getOrPut(moduleId) { ModuleAcc(moduleDir, moduleLabel(moduleDir, root)) }
        acc.fileCount++
        val ext = extensionOf(path)
        if (ext.isNotEmpty()) acc.languages.merge(ext, 1, Int::plus)
        if (node.kind == NodeKind.ACTIVE || (activeNorm != null && path.toAbsolutePath().normalize() == activeNorm)) {
            acc.active = true
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

private class ModuleAcc(val dir: Path, val label: String) {
    var fileCount = 0
    var active = false
    val languages = HashMap<String, Int>()
}

private fun dominantLanguage(languages: Map<String, Int>): String =
    languages.entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.key ?: ""

private fun moduleDirOf(file: Path, root: Path, depth: Int): Path {
    val parent = file.parent ?: return root
    if (parent == root) return root
    val rel = runCatching { root.relativize(parent) }.getOrNull() ?: return root
    val segments = rel.nameCount
    if (segments == 0 || rel.toString().isEmpty()) return root
    var dir = root
    val take = minOf(depth.coerceAtLeast(1), segments)
    for (i in 0 until take) dir = dir.resolve(rel.getName(i))
    return dir
}

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
