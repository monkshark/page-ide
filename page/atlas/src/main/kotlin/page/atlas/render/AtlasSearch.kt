package page.atlas.render

import page.atlas.graph.GraphNode

fun atlasSearchMatches(nodes: List<GraphNode>, query: String): List<GraphNode> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return nodes.asSequence()
        .filter { it.path != null }
        .mapNotNull { node ->
            val label = node.label.lowercase()
            val score = when {
                label.startsWith(q) -> 0
                q in label -> 1
                isSubsequence(q, label) -> 2
                else -> return@mapNotNull null
            }
            Triple(score, node.label.length, node)
        }
        .sortedWith(compareBy({ it.first }, { it.second }, { it.third.label.lowercase() }))
        .map { it.third }
        .toList()
}

private fun isSubsequence(query: String, text: String): Boolean {
    var qi = 0
    for (ch in text) {
        if (qi < query.length && query[qi] == ch) qi++
    }
    return qi == query.length
}
