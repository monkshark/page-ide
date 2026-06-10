package page.atlas.render

import androidx.compose.ui.geometry.Offset
import java.nio.file.Path
import java.util.TreeSet
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import page.atlas.graph.GraphEdge
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

internal fun belongsTo(id: String, ancestorId: String): Boolean =
    id == ancestorId ||
        (id.length > ancestorId.length && id.startsWith(ancestorId) &&
            (id[ancestorId.length] == '\\' || id[ancestorId.length] == '/'))

internal fun applyUserOffsets(boxes: List<MapBox>, offsets: Map<String, Offset>): List<MapBox> {
    if (offsets.isEmpty()) return boxes
    val out = boxes.map { box ->
        var dx = 0f
        var dy = 0f
        for ((key, off) in offsets) {
            if (belongsTo(box.id, key)) {
                dx += off.x
                dy += off.y
            }
        }
        if (dx == 0f && dy == 0f) box else box.copy(x = box.x + dx, y = box.y + dy)
    }.toMutableList()
    val resized = out.withIndex()
        .filter { (_, box) ->
            box.folder && box.expanded && offsets.keys.any { it != box.id && belongsTo(it, box.id) }
        }
        .sortedByDescending { it.value.depth }
        .map { it.index }
    for (index in resized) {
        val folder = out[index]
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (box in out) {
            if (box.id == folder.id || !belongsTo(box.id, folder.id)) continue
            minX = min(minX, box.x)
            minY = min(minY, box.y)
            maxX = max(maxX, box.x + box.w)
            maxY = max(maxY, box.y + box.h)
        }
        if (minX > maxX) continue
        out[index] = folder.copy(
            x = minX - MAP_PAD,
            y = minY - MAP_HEADER_H - MAP_PAD,
            w = maxX - minX + MAP_PAD * 2,
            h = maxY - minY + MAP_HEADER_H + MAP_PAD * 2,
        )
    }
    return out
}

fun defaultExpandedDirs(slice: GraphSlice): Set<String> {
    val active = slice.nodes.firstOrNull { it.kind == NodeKind.ACTIVE }?.path?.parent ?: return emptySet()
    return generateSequence(active) { it.parent }.map(Path::toString).toSet()
}

fun buildMap(
    slice: GraphSlice,
    expanded: Set<String>,
    labelWidth: (String) -> Float,
    offsets: Map<String, Offset> = emptyMap(),
    expandOrder: List<String> = emptyList(),
): MapModel {
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
    orderTree(root, slice.edges)

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
    val rank = HashMap<String, Int>()
    for ((index, dirId) in expandOrder.asReversed().withIndex()) {
        if (dirId in expanded) rank.putIfAbsent(dirId, index)
    }
    val topKids = orderedKids(root, 0, expanded, trail, activeId, labelWidth, offsets, rank)
    val (boxes, width, height) = shelfPack(topKids, offsets, rank)
    return MapModel(boxes, weights.map { (key, w) -> MapEdge(key.first, key.second, w) }, width, height)
}

private class DirTree(val label: String, val dirPath: Path) {
    val dirs = LinkedHashMap<String, DirTree>()
    val files = mutableListOf<GraphNode>()
    var unitOrder: List<String> = emptyList()
    val id: String get() = dirPath.toString()
    fun fileCount(): Int = files.size + dirs.values.sumOf { it.fileCount() }
}

private class Placed(
    val id: String,
    val w: Float,
    val h: Float,
    val stableW: Float,
    val stableH: Float,
    val boxes: List<MapBox>,
)

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

private fun orderTree(dir: DirTree, edges: List<GraphEdge>): Set<String> {
    val unitFiles = LinkedHashMap<String, Set<String>>()
    val unitLabel = HashMap<String, String>()
    for (child in dir.dirs.values) {
        unitFiles[child.id] = orderTree(child, edges)
        unitLabel[child.id] = child.label
    }
    for (file in dir.files) {
        unitFiles[file.id] = setOf(file.id)
        unitLabel[file.id] = file.label
    }
    val fileUnit = HashMap<String, String>()
    for ((unit, files) in unitFiles) for (f in files) fileUnit[f] = unit

    val adj = HashMap<String, MutableSet<String>>()
    val indeg = HashMap<String, Int>()
    for (unit in unitFiles.keys) indeg[unit] = 0
    for (edge in edges) {
        val from = fileUnit[edge.from] ?: continue
        val to = fileUnit[edge.to] ?: continue
        if (from != to && adj.getOrPut(from) { mutableSetOf() }.add(to)) {
            indeg[to] = indeg.getValue(to) + 1
        }
    }
    val byLabel = compareBy<String> { unitLabel.getValue(it).lowercase() }.thenBy { it }
    val ready = TreeSet(byLabel)
    for ((unit, degree) in indeg) if (degree == 0) ready.add(unit)
    val order = ArrayList<String>(unitFiles.size)
    while (ready.isNotEmpty()) {
        val unit = ready.pollFirst()!!
        order += unit
        for (next in adj[unit].orEmpty()) {
            val degree = indeg.getValue(next) - 1
            indeg[next] = degree
            if (degree == 0) ready.add(next)
        }
    }
    if (order.size < unitFiles.size) {
        val placed = order.toHashSet()
        order += unitFiles.keys.filter { it !in placed }.sortedWith(byLabel)
    }
    dir.unitOrder = order
    return fileUnit.keys
}

private fun orderedKids(
    dir: DirTree,
    depth: Int,
    expanded: Set<String>,
    trail: Set<String>,
    activeId: String?,
    labelWidth: (String) -> Float,
    offsets: Map<String, Offset>,
    rank: Map<String, Int>,
): List<Placed> {
    val dirsById = dir.dirs.values.associateBy { it.id }
    val filesById = dir.files.associateBy { it.id }
    return dir.unitOrder.mapNotNull { key ->
        dirsById[key]?.let { layoutDir(it, depth, expanded, trail, activeId, labelWidth, offsets, rank) }
            ?: filesById[key]?.let { placedFile(it, depth, activeId, labelWidth) }
    }
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
    return Placed(node.id, w, MAP_FILE_H, w, MAP_FILE_H, listOf(box))
}

private fun layoutDir(
    dir: DirTree,
    depth: Int,
    expanded: Set<String>,
    trail: Set<String>,
    activeId: String?,
    labelWidth: (String) -> Float,
    offsets: Map<String, Offset>,
    rank: Map<String, Int>,
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
        return Placed(dir.id, chipW, MAP_CHIP_H, chipW, MAP_CHIP_H, listOf(box))
    }
    val kids = orderedKids(dir, depth + 1, expanded, trail, activeId, labelWidth, offsets, rank)
    val (inner, contentW, contentH) = shelfPack(kids, offsets, rank)
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
    return Placed(dir.id, w, h, chipW, MAP_CHIP_H, listOf(self) + shifted)
}

private fun shelfPack(
    items: List<Placed>,
    offsets: Map<String, Offset>,
    rank: Map<String, Int>,
): Triple<List<MapBox>, Float, Float> {
    val area = items.fold(0f) { acc, p -> acc + (p.stableW + MAP_GAP) * (p.stableH + MAP_GAP) }
    val target = max(items.maxOf { it.stableW }, sqrt(area) * 1.3f)
    val homes = ArrayList<Pair<Float, Float>>(items.size)
    var x = 0f
    var y = 0f
    var rowH = 0f
    for (item in items) {
        if (x > 0f && x + item.stableW > target) {
            x = 0f
            y += rowH + MAP_GAP
            rowH = 0f
        }
        homes += x to y
        x += item.stableW + MAP_GAP
        rowH = max(rowH, item.stableH)
    }
    val placed = ArrayList<FloatArray>(items.size)
    val anchors = arrayOfNulls<Pair<Float, Float>>(items.size)
    val order = items.indices.sortedBy { index ->
        val item = items[index]
        if (item.id in offsets) -1 else rank[item.id] ?: Int.MAX_VALUE
    }
    for (index in order) {
        val item = items[index]
        val off = offsets[item.id]
        var px = homes[index].first + (off?.x ?: 0f)
        var py = homes[index].second + (off?.y ?: 0f)
        while (off == null) {
            val hit = placed.firstOrNull { r ->
                px < r[0] + r[2] && r[0] < px + item.w && py < r[1] + r[3] && r[1] < py + item.h
            } ?: break
            val dx = hit[0] + hit[2] + MAP_GAP - px
            val dy = hit[1] + hit[3] + MAP_GAP - py
            if (dx <= dy) px += dx else py += dy
        }
        placed += floatArrayOf(px, py, item.w, item.h)
        anchors[index] = (px - (off?.x ?: 0f)) to (py - (off?.y ?: 0f))
    }
    val out = mutableListOf<MapBox>()
    var maxW = 0f
    var maxH = 0f
    for ((index, item) in items.withIndex()) {
        val (lx, ly) = anchors[index] ?: continue
        out += item.boxes.map { it.copy(x = it.x + lx, y = it.y + ly) }
        maxW = max(maxW, lx + item.w)
        maxH = max(maxH, ly + item.h)
    }
    return Triple(out, maxW, maxH)
}
