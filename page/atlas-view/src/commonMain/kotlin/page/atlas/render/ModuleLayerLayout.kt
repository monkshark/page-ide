package page.atlas.render

import page.atlas.graph.ModuleGraph
import page.atlas.graph.ModuleLayer
import page.atlas.graph.ModuleNode
import page.atlas.graph.classifyModuleLayers

data class FLPoint(val x: Double, val y: Double)

data class ModuleLayerLayout(
    val positions: Map<String, FLPoint>,
    val layerOf: Map<String, ModuleLayer>,
    val columns: List<ModuleLayer>,
    val columnX: Map<ModuleLayer, Double>,
    val width: Double,
    val height: Double,
)

private const val COLUMN_SPACING = 168.0
private const val ROW_SPACING = 96.0

private val LAYER_ORDER = listOf(
    ModuleLayer.ENTRY,
    ModuleLayer.FEATURES,
    ModuleLayer.CORE,
    ModuleLayer.PLATFORM,
    ModuleLayer.EXTERNAL,
)

fun layeredModuleLayout(graph: ModuleGraph): ModuleLayerLayout {
    val layerOf = classifyModuleLayers(graph)
    val byLayer = LAYER_ORDER.associateWith { layer ->
        graph.nodes
            .filter { layerOf[it.id] == layer }
            .sortedWith(compareByDescending<ModuleNode> { it.fileCount }.thenBy { it.id })
    }

    val positions = HashMap<String, FLPoint>(graph.nodes.size)
    val columnX = LinkedHashMap<ModuleLayer, Double>()
    val columns = ArrayList<ModuleLayer>()
    var column = 0
    var maxRows = 0
    for (layer in LAYER_ORDER) {
        val mods = byLayer.getValue(layer)
        if (mods.isEmpty()) continue
        val x = column * COLUMN_SPACING
        columnX[layer] = x
        columns.add(layer)
        mods.forEachIndexed { row, node -> positions[node.id] = FLPoint(x, row * ROW_SPACING) }
        maxRows = maxOf(maxRows, mods.size)
        column++
    }

    val width = if (column == 0) 0.0 else (column - 1) * COLUMN_SPACING
    val height = if (maxRows == 0) 0.0 else (maxRows - 1) * ROW_SPACING
    return ModuleLayerLayout(positions, layerOf, columns, columnX, width, height)
}
