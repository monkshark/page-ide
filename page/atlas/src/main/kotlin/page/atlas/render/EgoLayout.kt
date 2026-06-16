package page.atlas.render

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice

enum class EgoColumn { DEPENDENT, FOCUS, IMPORT, EXTERNAL }

data class EgoNode(
    val id: String,
    val label: String,
    val column: EgoColumn,
    val center: Offset,
    val radius: Float,
    val unresolved: Boolean,
    val overflow: Int = 0,
)

data class EgoEdge(
    val from: String,
    val to: String,
    val toFocus: Boolean,
    val start: Offset,
    val c1: Offset,
    val c2: Offset,
    val end: Offset,
)

data class EgoModel(
    val nodes: List<EgoNode>,
    val edges: List<EgoEdge>,
    val focusId: String?,
    val width: Float,
    val height: Float,
) {
    companion object {
        val EMPTY = EgoModel(emptyList(), emptyList(), null, 0f, 0f)
    }
}

data class EgoTransform(val scale: Float, val offset: Offset) {
    fun toScreen(p: Offset): Offset = Offset(p.x * scale + offset.x, p.y * scale + offset.y)
}

const val EGO_WIDTH = 1280f
const val EGO_HEIGHT = 800f
const val EGO_OVERFLOW_PREFIX = "__more__:"

private const val DEP_X = 0.18f
private const val FOCUS_X = 0.5f
private const val IMPORT_X = 0.82f
private const val CENTER_Y = 0.505f
private const val VERTICAL_BAND = 0.82f
private const val MAX_SPACING = 96f
private const val MAX_ROWS = 10
private const val COL_SPACING = 66f
private const val FOCUS_R = 46f
private const val NEIGHBOR_R = 27f
private const val EXTERNAL_R = 15f
private const val DEGREE_K = 3.5f
private const val VISIBLE_PER_COLUMN = 8

fun buildEgoModel(
    slice: GraphSlice,
    focusId: String,
    width: Float = EGO_WIDTH,
    height: Float = EGO_HEIGHT,
): EgoModel {
    val focus = slice.nodes.firstOrNull { it.id == focusId } ?: return EgoModel.EMPTY
    val incoming = slice.edges.filter { it.to == focusId }.mapTo(HashSet()) { it.from }
    val outgoing = slice.edges.filter { it.from == focusId }.mapTo(HashSet()) { it.to }
    val degree = HashMap<String, Int>()
    for (e in slice.edges) {
        degree[e.from] = (degree[e.from] ?: 0) + 1
        degree[e.to] = (degree[e.to] ?: 0) + 1
    }
    fun sortedColumn(ids: Set<String>): List<GraphNode> = slice.nodes
        .filter { it.id != focusId && it.id in ids }
        .sortedWith(
            compareByDescending<GraphNode> { degree[it.id] ?: 0 }
                .thenBy { it.label.lowercase() }
                .thenBy { it.id },
        )
    val deps = sortedColumn(incoming)
    val imps = sortedColumn(outgoing).filter { it.id !in incoming }
    val centerY = height * CENTER_Y
    val band = height * VERTICAL_BAND
    val nodes = ArrayList<EgoNode>()
    nodes += EgoNode(
        focus.id, focus.label, EgoColumn.FOCUS,
        Offset(width * FOCUS_X, centerY), radiusFor(EgoColumn.FOCUS, degree[focus.id] ?: 0), focus.path == null,
    )
    nodes += placeColumn(deps, EgoColumn.DEPENDENT, width * DEP_X, centerY, band, degree)
    nodes += placeColumn(imps, EgoColumn.IMPORT, width * IMPORT_X, centerY, band, degree)

    val byId = nodes.associateBy { it.id }
    val focusNode = byId[focusId] ?: return EgoModel(nodes, emptyList(), focusId, width, height)
    val edges = ArrayList<EgoEdge>()
    for (e in slice.edges) {
        when {
            e.to == focusId -> {
                val from = byId[e.from] ?: continue
                if (from.column != EgoColumn.DEPENDENT) continue
                edges += egoEdge(e.from, e.to, toFocus = true, from = from, to = focusNode)
            }
            e.from == focusId -> {
                val to = byId[e.to] ?: continue
                if (to.column != EgoColumn.IMPORT) continue
                edges += egoEdge(e.from, e.to, toFocus = false, from = focusNode, to = to)
            }
        }
    }
    return EgoModel(nodes, edges, focusId, width, height)
}

private fun placeColumn(
    sorted: List<GraphNode>,
    column: EgoColumn,
    baseX: Float,
    centerY: Float,
    band: Float,
    degree: Map<String, Int>,
): List<EgoNode> {
    val overflow = (sorted.size - VISIBLE_PER_COLUMN).coerceAtLeast(0)
    val shown = sorted.take(VISIBLE_PER_COLUMN)
    val count = shown.size + if (overflow > 0) 1 else 0
    if (count == 0) return emptyList()
    val result = ArrayList<EgoNode>(count)
    val cols = ((count + MAX_ROWS - 1) / MAX_ROWS).coerceAtLeast(1)
    val rows = (count + cols - 1) / cols
    val rowSpacing = if (rows <= 1) 0f else min(band / (rows - 1), MAX_SPACING)
    for (i in 0 until count) {
        val c = i / rows
        val r = i % rows
        val rowsInCol = if (c < cols - 1) rows else count - rows * c
        val x = baseX + (c - (cols - 1) / 2f) * COL_SPACING
        val y = centerY + (r - (rowsInCol - 1) / 2f) * rowSpacing
        if (i < shown.size) {
            val node = shown[i]
            result += EgoNode(
                node.id, node.label, column,
                Offset(x, y), radiusFor(column, degree[node.id] ?: 0), node.path == null,
            )
        } else {
            result += EgoNode(
                "$EGO_OVERFLOW_PREFIX${column.name}", "+$overflow more", column,
                Offset(x, y), NEIGHBOR_R * 0.78f, false, overflow = overflow,
            )
        }
    }
    return result
}

private fun egoEdge(
    fromId: String,
    toId: String,
    toFocus: Boolean,
    from: EgoNode,
    to: EgoNode,
): EgoEdge {
    val start = Offset(from.center.x + from.radius, from.center.y)
    val end = Offset(to.center.x - to.radius, to.center.y)
    val midX = (start.x + end.x) / 2f
    return EgoEdge(fromId, toId, toFocus, start, Offset(midX, start.y), Offset(midX, end.y), end)
}

fun egoTransform(
    model: EgoModel,
    canvasWidth: Float,
    canvasHeight: Float,
    pan: Offset,
    zoom: Float,
): EgoTransform {
    if (model.width <= 0f || model.height <= 0f) return EgoTransform(1f, Offset.Zero)
    val base = min(canvasWidth / model.width, canvasHeight / model.height)
    val scale = base * zoom
    val offsetX = (canvasWidth - model.width * scale) / 2f + pan.x
    val offsetY = (canvasHeight - model.height * scale) / 2f + pan.y
    return EgoTransform(scale, Offset(offsetX, offsetY))
}

fun egoNodeAt(model: EgoModel, transform: EgoTransform, pos: Offset, minTouch: Float = 14f): EgoNode? =
    model.nodes
        .filter {
            val s = transform.toScreen(it.center)
            hypot(pos.x - s.x, pos.y - s.y) <= max(it.radius * transform.scale, minTouch)
        }
        .minByOrNull {
            val s = transform.toScreen(it.center)
            hypot(pos.x - s.x, pos.y - s.y)
        }

private fun radiusFor(column: EgoColumn, degree: Int): Float {
    val base = when (column) {
        EgoColumn.FOCUS -> FOCUS_R
        EgoColumn.EXTERNAL -> EXTERNAL_R
        else -> NEIGHBOR_R
    }
    return base + DEGREE_K * sqrt((degree - 1).coerceAtLeast(0).toFloat())
}
