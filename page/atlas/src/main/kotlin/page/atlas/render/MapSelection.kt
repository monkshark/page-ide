package page.atlas.render

data class MapNeighbors(
    val dependents: Set<String>,
    val dependencies: Set<String>,
    val dependentWeight: Int,
    val dependencyWeight: Int,
) {
    val any: Boolean get() = dependents.isNotEmpty() || dependencies.isNotEmpty()

    companion object {
        val EMPTY = MapNeighbors(emptySet(), emptySet(), 0, 0)
    }
}

fun mapNeighbors(edges: List<MapEdge>, selectedId: String?): MapNeighbors {
    if (selectedId == null) return MapNeighbors.EMPTY
    val dependents = LinkedHashSet<String>()
    val dependencies = LinkedHashSet<String>()
    var dependentWeight = 0
    var dependencyWeight = 0
    for (edge in edges) {
        if (edge.to == selectedId && edge.from != selectedId) {
            dependents += edge.from
            dependentWeight += edge.weight
        }
        if (edge.from == selectedId && edge.to != selectedId) {
            dependencies += edge.to
            dependencyWeight += edge.weight
        }
    }
    return MapNeighbors(dependents, dependencies, dependentWeight, dependencyWeight)
}

fun isMapBoxDimmed(boxId: String, selectedId: String?, neighbors: MapNeighbors): Boolean {
    if (selectedId == null || !neighbors.any) return false
    if (belongsTo(boxId, selectedId) || belongsTo(selectedId, boxId)) return false
    return boxId !in neighbors.dependents && boxId !in neighbors.dependencies
}
