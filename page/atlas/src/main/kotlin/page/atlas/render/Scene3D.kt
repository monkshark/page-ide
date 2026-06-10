package page.atlas.render

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import page.atlas.graph.GraphSlice

data class Node3D(val id: String, val x: Float, val y: Float, val z: Float)

data class Ring3D(val y: Float, val radius: Float)

data class SceneModel(val nodes: List<Node3D>, val rings: List<Ring3D>) {
    companion object {
        val EMPTY = SceneModel(emptyList(), emptyList())
    }
}

data class ProjectedNode(val id: String, val x: Float, val y: Float, val scale: Float, val depth: Float)

fun buildScene(slice: GraphSlice): SceneModel {
    if (slice.nodes.isEmpty()) return SceneModel.EMPTY
    val deps = LinkedHashMap<String, MutableList<String>>()
    for (edge in slice.edges) {
        if (edge.from != edge.to) deps.getOrPut(edge.from) { mutableListOf() }.add(edge.to)
    }
    val ids = slice.nodes.map { it.id }
    val layer = assignLayers(ids, deps)
    val maxLayer = layer.values.maxOrNull() ?: 0
    val byLayer = ids.groupBy { layer[it] ?: 0 }
    val slot = HashMap<String, Int>()
    for (row in byLayer.values) row.forEachIndexed { i, id -> slot[id] = i }
    val nodes = slice.nodes.map { node ->
        val l = layer[node.id] ?: 0
        val i = slot[node.id] ?: 0
        val r = RING_STEP * sqrt(i.toFloat())
        val angle = i * GOLDEN_ANGLE
        Node3D(node.id, r * cos(angle), (l - maxLayer / 2f) * LAYER_HEIGHT, r * sin(angle))
    }
    val rings = byLayer.entries.sortedBy { it.key }.map { (l, row) ->
        Ring3D(
            y = (l - maxLayer / 2f) * LAYER_HEIGHT,
            radius = RING_STEP * sqrt((row.size - 1).toFloat()) + RING_PADDING,
        )
    }
    return SceneModel(nodes, rings)
}

fun sceneRadius(model: SceneModel): Float {
    var r = 1f
    for (n in model.nodes) {
        r = max(r, sqrt(n.x * n.x + n.z * n.z))
        r = max(r, abs(n.y))
    }
    return r
}

fun projectScene(
    model: SceneModel,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    width: Float,
    height: Float,
): List<ProjectedNode> {
    val cy = cos(yaw)
    val sy = sin(yaw)
    val cp = cos(pitch)
    val sp = sin(pitch)
    return model.nodes.map { n ->
        val x1 = n.x * cy + n.z * sy
        val z1 = -n.x * sy + n.z * cy
        val y1 = n.y * cp - z1 * sp
        val z2 = n.y * sp + z1 * cp
        val s = perspective(z2) * zoom
        ProjectedNode(n.id, width / 2f + x1 * s, height / 2f - y1 * s, s, z2)
    }.sortedByDescending { it.depth }
}

fun projectPoint(
    x: Float,
    y: Float,
    z: Float,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    width: Float,
    height: Float,
): ProjectedNode {
    val cy = cos(yaw)
    val sy = sin(yaw)
    val cp = cos(pitch)
    val sp = sin(pitch)
    val x1 = x * cy + z * sy
    val z1 = -x * sy + z * cy
    val y1 = y * cp - z1 * sp
    val z2 = y * sp + z1 * cp
    val s = perspective(z2) * zoom
    return ProjectedNode("", width / 2f + x1 * s, height / 2f - y1 * s, s, z2)
}

private fun perspective(depth: Float): Float = FOCAL / (CAMERA_DISTANCE + depth).coerceAtLeast(NEAR)

private fun assignLayers(ids: List<String>, deps: Map<String, List<String>>): Map<String, Int> {
    val layer = HashMap<String, Int>()
    val visiting = HashSet<String>()
    fun assign(id: String): Int {
        layer[id]?.let { return it }
        if (!visiting.add(id)) return 0
        var maxBelow = -1
        for (dep in deps[id].orEmpty()) {
            maxBelow = max(maxBelow, assign(dep))
        }
        visiting.remove(id)
        val value = maxBelow + 1
        layer[id] = value
        return value
    }
    for (id in ids) assign(id)
    return layer
}

private const val LAYER_HEIGHT = 110f
private const val RING_STEP = 70f
private const val RING_PADDING = 36f
private const val GOLDEN_ANGLE = 2.39996f
private const val FOCAL = 900f
private const val CAMERA_DISTANCE = 900f
private const val NEAR = 80f
