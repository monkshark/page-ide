package page.atlas.render

import page.atlas.graph.ProjectMap

data class PlacedPart(val id: String, val row: Int, val x: Float, val y: Float)

data class ProjectMapPlacement(
    val parts: Map<String, PlacedPart>,
    val width: Float,
    val height: Float,
)

object ProjectMapLayout {

    const val BOX_W = 220f
    const val BOX_H = 96f
    const val GAP_X = 28f
    const val GAP_Y = 64f
    const val PAD = 24f

    fun place(map: ProjectMap): ProjectMapPlacement {
        if (map.parts.isEmpty()) return ProjectMapPlacement(emptyMap(), 0f, 0f)
        val top = map.layers - 1
        val rows = map.parts
            .groupBy { top - it.layer }
            .toSortedMap()
            .mapValues { (_, parts) -> parts.sortedWith(compareByDescending<page.atlas.graph.MapPart> { it.fileCount }.thenBy { it.name }) }
        val widest = rows.values.maxOf { it.size }
        val width = PAD * 2 + widest * BOX_W + (widest - 1) * GAP_X
        val placed = LinkedHashMap<String, PlacedPart>()
        for ((row, parts) in rows) {
            val rowWidth = parts.size * BOX_W + (parts.size - 1) * GAP_X
            val start = (width - rowWidth) / 2f
            parts.forEachIndexed { i, part ->
                placed[part.id] = PlacedPart(part.id, row, start + i * (BOX_W + GAP_X), PAD + row * (BOX_H + GAP_Y))
            }
        }
        val rowCount = rows.keys.max() + 1
        val height = PAD * 2 + rowCount * BOX_H + (rowCount - 1) * GAP_Y
        return ProjectMapPlacement(placed, width, height)
    }
}
