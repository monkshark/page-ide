package page.atlas.render

import java.nio.file.Path
import kotlin.math.max
import kotlin.math.sqrt
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

data class MapBox(
    val id: String,
    val label: String,
    val folder: Boolean,
    val expanded: Boolean,
    val fileCount: Int,
    val depth: Int,
    val path: Path?,
    val active: Boolean,
    val activeTrail: Boolean,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

data class MapEdge(val from: String, val to: String, val weight: Int)

data class MapModel(
    val boxes: List<MapBox>,
    val edges: List<MapEdge>,
    val width: Float,
    val height: Float,
) {
    companion object {
        val EMPTY = MapModel(emptyList(), emptyList(), 0f, 0f)
    }
}

internal const val MAP_GAP = 22f
internal const val MAP_PAD = 14f
internal const val MAP_HEADER_H = 16f
internal const val MAP_FILE_H = 20f
internal const val MAP_CHIP_H = 24f
internal const val MAP_TEXT_PAD = 14f

fun defaultExpandedDirs(slice: GraphSlice): Set<String> {
    val active = slice.nodes.firstOrNull { it.kind == NodeKind.ACTIVE }?.path?.parent ?: return emptySet()
    return generateSequence(active) { it.parent }.map(Path::toString).toSet()
}

fun buildMap(slice: GraphSlice, expanded: Set<String>, labelWidth: (String) -> Float): MapModel {
    val files = slice.nodes.filter { it.path != null }
    if (files.isEmpty()) return MapModel.EMPTY
    val rootDir = commonRoot(files.map { it.path!!.parent ?: it.path!! })
    val root = DirTree("", rootDir)
    for (node in files) {
        var cur = root
        val parent = node.path!!.parent
        if (parent != null && parent != rootDir) {
            val rel = runCatching { rootDir.relativize(parent) }.getOrNull()
            if (rel != null && rel.toString().isNotEmpty()) {
                var dirPath = rootDir
                for (seg in rel) {
                    dirPath = dirPath.resolve(seg)
                    cur = cur.dirs.getOrPut(seg.toString()) { DirTree(seg.toString(), dirPath) }
                }
            }
        }
        cur.files.add(node)
    }
    compress(root)
    sortTree(root)

    val activeNode = files.firstOrNull { it.kind == NodeKind.ACTIVE }
    val trail = activeNode?.path?.parent
        ?.let { generateSequence(it) { p -> p.parent }.map(Path::toString).toSet() }
        ?: emptySet()

    val repOf = HashMap<String, String>()
    fun assignReps(dir: DirTree, hiddenUnder: String?) {
        for (f in dir.files) repOf[f.id] = hiddenUnder ?: f.id
        for (d in dir.dirs.values) {
            assignReps(d, hiddenUnder ?: d.id.takeIf { it !in expanded })
        }
    }
    assignReps(root, null)

    val weights = LinkedHashMap<Pair<String, String>, Int>()
    for (edge in slice.edges) {
        val from = repOf[edge.from] ?: continue
        val to = repOf[edge.to] ?: continue
        if (from == to) continue
        weights.merge(from to to, 1, Int::plus)
    }

    val activeId = activeNode?.id
    val topKids = root.dirs.values.map { layoutDir(it, 0, expanded, trail, activeId, labelWidth) } +
        root.files.map { placedFile(it, 0, activeId, labelWidth) }
    val (boxes, width, height) = shelfPack(topKids)
    return MapModel(boxes, weights.map { (key, w) -> MapEdge(key.first, key.second, w) }, width, height)
}

private class DirTree(val label: String, val dirPath: Path) {
    val dirs = LinkedHashMap<String, DirTree>()
    val files = mutableListOf<GraphNode>()
    val id: String get() = dirPath.toString()
    fun fileCount(): Int = files.size + dirs.values.sumOf { it.fileCount() }
}

private class Placed(val w: Float, val h: Float, val stableW: Float, val stableH: Float, val boxes: List<MapBox>)

private fun commonRoot(dirs: List<Path>): Path {
    var prefix = dirs.first()
    for (dir in dirs.drop(1)) {
        while (!dir.startsWith(prefix)) {
            prefix = prefix.parent ?: return prefix
        }
    }
    return prefix
}

private fun compress(dir: DirTree) {
    val children = dir.dirs.values.toList()
    dir.dirs.clear()
    for (start in children) {
        var child = start
        while (child.files.isEmpty() && child.dirs.size == 1) {
            val only = child.dirs.values.first()
            val merged = DirTree("${child.label}/${only.label}", only.dirPath)
            merged.dirs.putAll(only.dirs)
            merged.files.addAll(only.files)
            child = merged
        }
        compress(child)
        dir.dirs[child.label] = child
    }
}

private fun sortTree(dir: DirTree) {
    val sorted = dir.dirs.values.sortedBy { it.label.lowercase() }
    dir.dirs.clear()
    for (child in sorted) {
        dir.dirs[child.label] = child
        sortTree(child)
    }
    dir.files.sortBy { it.label.lowercase() }
}

private fun placedFile(node: GraphNode, depth: Int, activeId: String?, labelWidth: (String) -> Float): Placed {
    val w = labelWidth(node.label) + MAP_TEXT_PAD
    val box = MapBox(
        id = node.id,
        label = node.label,
        folder = false,
        expanded = false,
        fileCount = 1,
        depth = depth,
        path = node.path,
        active = node.id == activeId,
        activeTrail = false,
        x = 0f,
        y = 0f,
        w = w,
        h = MAP_FILE_H,
    )
    return Placed(w, MAP_FILE_H, w, MAP_FILE_H, listOf(box))
}

private fun layoutDir(
    dir: DirTree,
    depth: Int,
    expanded: Set<String>,
    trail: Set<String>,
    activeId: String?,
    labelWidth: (String) -> Float,
): Placed {
    val count = dir.fileCount()
    val chipW = labelWidth("${dir.label} ($count)") + MAP_TEXT_PAD
    if (dir.id !in expanded) {
        val box = MapBox(
            id = dir.id,
            label = dir.label,
            folder = true,
            expanded = false,
            fileCount = count,
            depth = depth,
            path = dir.dirPath,
            active = false,
            activeTrail = dir.id in trail,
            x = 0f,
            y = 0f,
            w = chipW,
            h = MAP_CHIP_H,
        )
        return Placed(chipW, MAP_CHIP_H, chipW, MAP_CHIP_H, listOf(box))
    }
    val kids = dir.dirs.values.map { layoutDir(it, depth + 1, expanded, trail, activeId, labelWidth) } +
        dir.files.map { placedFile(it, depth + 1, activeId, labelWidth) }
    val (inner, contentW, contentH) = shelfPack(kids)
    val w = max(contentW, labelWidth(dir.label)) + MAP_PAD * 2
    val h = contentH + MAP_HEADER_H + MAP_PAD * 2
    val self = MapBox(
        id = dir.id,
        label = dir.label,
        folder = true,
        expanded = true,
        fileCount = count,
        depth = depth,
        path = dir.dirPath,
        active = false,
        activeTrail = dir.id in trail,
        x = 0f,
        y = 0f,
        w = w,
        h = h,
    )
    val shifted = inner.map { it.copy(x = it.x + MAP_PAD, y = it.y + MAP_HEADER_H + MAP_PAD) }
    return Placed(w, h, chipW, MAP_CHIP_H, listOf(self) + shifted)
}

private fun shelfPack(items: List<Placed>): Triple<List<MapBox>, Float, Float> {
    val area = items.fold(0f) { acc, p -> acc + (p.stableW + MAP_GAP) * (p.stableH + MAP_GAP) }
    val target = max(items.maxOf { it.stableW }, sqrt(area) * 1.3f)
    val out = mutableListOf<MapBox>()
    var stableX = 0f
    var x = 0f
    var y = 0f
    var rowH = 0f
    var maxW = 0f
    for (item in items) {
        if (stableX > 0f && stableX + item.stableW > target) {
            stableX = 0f
            x = 0f
            y += rowH + MAP_GAP
            rowH = 0f
        }
        out += item.boxes.map { it.copy(x = it.x + x, y = it.y + y) }
        stableX += item.stableW + MAP_GAP
        x += item.w + MAP_GAP
        rowH = max(rowH, item.h)
        maxW = max(maxW, x - MAP_GAP)
    }
    return Triple(out, maxW, y + rowH)
}
