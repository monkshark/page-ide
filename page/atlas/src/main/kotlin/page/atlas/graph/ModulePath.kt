package page.atlas.graph

data class ModuleLink(val node: ModuleNode, val weight: Int)

fun moduleDependsOn(graph: ModuleGraph, moduleId: String): List<ModuleLink> =
    relatedModules(graph, moduleId, outgoing = true)

fun moduleUsedBy(graph: ModuleGraph, moduleId: String): List<ModuleLink> =
    relatedModules(graph, moduleId, outgoing = false)

private fun relatedModules(graph: ModuleGraph, moduleId: String, outgoing: Boolean): List<ModuleLink> {
    val byId = graph.nodes.associateBy { it.id }
    return graph.edges
        .asSequence()
        .filter { if (outgoing) it.from == moduleId else it.to == moduleId }
        .filter { it.from != it.to }
        .mapNotNull { edge ->
            val otherId = if (outgoing) edge.to else edge.from
            byId[otherId]?.let { ModuleLink(it, edge.weight) }
        }
        .sortedWith(compareByDescending<ModuleLink> { it.weight }.thenBy { it.node.id })
        .toList()
}
