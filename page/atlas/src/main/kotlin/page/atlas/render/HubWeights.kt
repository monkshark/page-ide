package page.atlas.render

import kotlin.math.ln
import kotlin.math.min
import page.atlas.graph.GraphSlice

fun hubWeight(dependents: Int): Float =
    min(1f + ln(1f + dependents.coerceAtLeast(0)) * HUB_LOG_FACTOR, HUB_WEIGHT_CAP)

fun hubWeights(slice: GraphSlice): Map<String, Float> {
    val indegree = HashMap<String, Int>()
    for (edge in slice.edges) {
        if (edge.from == edge.to) continue
        indegree.merge(edge.to, 1, Int::plus)
    }
    return slice.nodes.associate { it.id to hubWeight(indegree[it.id] ?: 0) }
}

private const val HUB_LOG_FACTOR = 0.24f
private const val HUB_WEIGHT_CAP = 1.8f
