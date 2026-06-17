package page.atlas.graph

enum class ModuleLayer { ENTRY, FEATURES, CORE, PLATFORM, EXTERNAL }

fun classifyModuleLayers(graph: ModuleGraph): Map<String, ModuleLayer> {
    val externalIds = graph.nodes.filter { it.external }.map { it.id }
    val internalIds = graph.nodes.filter { !it.external }.map { it.id }
    val internalSet = internalIds.toHashSet()
    val edges = graph.edges.filter { it.from != it.to && it.from in internalSet && it.to in internalSet }

    val rank = HashMap<String, Int>(internalIds.size)
    for (id in internalIds) rank[id] = 0
    for (pass in internalIds.indices) {
        var changed = false
        for (e in edges) {
            val candidate = (rank[e.from] ?: 0) + 1
            if (candidate > (rank[e.to] ?: 0)) {
                rank[e.to] = candidate
                changed = true
            }
        }
        if (!changed) break
    }

    val maxRank = rank.values.maxOrNull() ?: 0
    val result = HashMap<String, ModuleLayer>(graph.nodes.size)
    for (id in externalIds) result[id] = ModuleLayer.EXTERNAL
    for (id in internalIds) {
        val r = rank[id] ?: 0
        result[id] = if (maxRank == 0) {
            ModuleLayer.ENTRY
        } else {
            when ((r * 3 + maxRank / 2) / maxRank) {
                0 -> ModuleLayer.ENTRY
                1 -> ModuleLayer.FEATURES
                2 -> ModuleLayer.CORE
                else -> ModuleLayer.PLATFORM
            }
        }
    }
    return result
}
