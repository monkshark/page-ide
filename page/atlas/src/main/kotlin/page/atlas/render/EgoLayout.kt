package page.atlas.render

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
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

private const val DEP_X = 0.20f
private const val FOCUS_X = 0.50f
private const val IMPORT_X = 0.80f
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
    val order = compareByDescending<page.atlas.graph.GraphNode> { degree[it.id] ?: 0 }
        .thenBy { it.label.lowercase() }
        .thenBy { it.id }
    val dependents = slice.nodes.filter { it.id != focusId && it.id in incoming }.sortedWith(order)
    val imports = slice.nodes.filter { it.id != focusId && it.id in outgoing && it.id !in incoming }.sortedWith(order)
    val centerY = height * CENTER_Y
    val band = height * VERTICAL_BAND
    val nodes = ArrayList<EgoNode>()
    nodes += EgoNode(
        focus.id, focus.label, EgoColumn.FOCUS,
        Offset(width * FOCUS_X, centerY), radiusFor(EgoColumn.FOCUS, degree[focus.id] ?: 0), focus.path == null,
    )
    placeColumn(nodes, dependents, EgoColumn.DEPENDENT, width * columnX(EgoColumn.DEPENDENT), centerY, band, degree)
    placeColumn(nodes, imports, EgoColumn.IMPORT, width * columnX(EgoColumn.IMPORT), centerY, band, degree)
    val byId = nodes.associateBy { it.id }
    val focusNode = byId.getValue(focusId)
    val edges = ArrayList<EgoEdge>()
    for (dep in dependents) {
        val from = byId[dep.id] ?: continue
        if (from.id == focusId) continue
        edges += egoEdge(dep.id, focusId, toFocus = true, from = from, to = focusNode)
    }
    for (imp in imports) {
        val to = byId[imp.id] ?: continue
        if (to.id == focusId || to.column != EgoColumn.IMPORT) continue
        edges += egoEdge(focusId, imp.id, toFocus = false, from = focusNode, to = to)
    }
    return EgoModel(nodes, edges, focusId, width, height)
}

private fun placeColumn(
    nodes: ArrayList<EgoNode>,
    candidates: List<page.atlas.graph.GraphNode>,
    column: EgoColumn,
    baseX: Float,
    centerY: Float,
    band: Float,
    degree: Map<String, Int>,
) {
    val overflow = (candidates.size - VISIBLE_PER_COLUMN).coerceAtLeast(0)
    val shown = candidates.take(VISIBLE_PER_COLUMN)
    val count = shown.size + if (overflow > 0) 1 else 0
    if (count == 0) return
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
            nodes += EgoNode(
                node.id, node.label, column,
                Offset(x, y), radiusFor(column, degree[node.id] ?: 0), node.path == null,
            )
        } else {
            nodes += EgoNode(
                "$EGO_OVERFLOW_PREFIX${column.name}", "+$overflow more", column,
                Offset(x, y), NEIGHBOR_R * 0.78f, false, overflow = overflow,
            )
        }
    }
}

private fun egoEdge(fromId: String, toId: String, toFocus: Boolean, from: EgoNode, to: EgoNode): EgoEdge {
    val rightward = from.center.x <= to.center.x
    val start = Offset(from.center.x + if (rightward) from.radius else -from.radius, from.center.y)
    val end = Offset(to.center.x + if (rightward) -to.radius else to.radius, to.center.y)
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

private fun columnX(column: EgoColumn): Float = when (column) {
    EgoColumn.FOCUS -> FOCUS_X
    EgoColumn.IMPORT -> IMPORT_X
    EgoColumn.DEPENDENT, EgoColumn.EXTERNAL -> DEP_X
}
