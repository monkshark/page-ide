package page.atlas.render

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import page.atlas.graph.ModuleGraph

data class FLPoint(val x: Double, val y: Double)

data class ForceParams(
    val centerStrength: Double = 0.48,
    val repelStrength: Double = 16.0,
    val linkStrength: Double = 0.44,
    val linkDistance: Double = 198.0,
    val modularityBonus: Double = 0.12,
    val iterations: Int = 300,
) {
    companion object {
        val DEFAULT = ForceParams()
    }
}

data class ForceLayoutResult(
    val positions: Map<String, FLPoint>,
    val width: Double,
    val height: Double,
    val iterations: Int,
    val maxStep: Double,
)

private const val INIT_SPACING = 36.0
private const val GOLDEN_ANGLE = 2.399963229728653
private const val MIN_DISTANCE = 0.01

fun forceLayout(graph: ModuleGraph, params: ForceParams = ForceParams.DEFAULT): ForceLayoutResult {
    val ids = graph.nodes.map { it.id }.sorted()
    val n = ids.size
    if (n == 0) return ForceLayoutResult(emptyMap(), 0.0, 0.0, params.iterations, 0.0)
    if (n == 1) return ForceLayoutResult(mapOf(ids[0] to FLPoint(0.0, 0.0)), 0.0, 0.0, params.iterations, 0.0)

    val index = HashMap<String, Int>(n * 2)
    for (i in ids.indices) index[ids[i]] = i

    val px = DoubleArray(n)
    val py = DoubleArray(n)
    for (i in 0 until n) {
        val r = INIT_SPACING * sqrt(i.toDouble())
        val a = i * GOLDEN_ANGLE
        px[i] = r * cos(a)
        py[i] = r * sin(a)
    }

    data class Link(val a: Int, val b: Int, val weight: Int)
    val links = ArrayList<Link>(graph.edges.size)
    for (edge in graph.edges) {
        val a = index[edge.from] ?: continue
        val b = index[edge.to] ?: continue
        if (a == b) continue
        links.add(Link(a, b, edge.weight))
    }
    links.sortWith(compareBy({ it.a }, { it.b }))

    val k = params.linkDistance
    val k2 = k * k
    val dx = DoubleArray(n)
    val dy = DoubleArray(n)
    var maxStep = 0.0

    for (iter in 0 until params.iterations) {
        java.util.Arrays.fill(dx, 0.0)
        java.util.Arrays.fill(dy, 0.0)

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                var vx = px[i] - px[j]
                var vy = py[i] - py[j]
                var d = sqrt(vx * vx + vy * vy)
                if (d < MIN_DISTANCE) {
                    vx = MIN_DISTANCE
                    vy = 0.0
                    d = MIN_DISTANCE
                }
                val f = params.repelStrength * k2 / d
                val ux = vx / d * f
                val uy = vy / d * f
                dx[i] += ux
                dy[i] += uy
                dx[j] -= ux
                dy[j] -= uy
            }
        }

        for (link in links) {
            val a = link.a
            val b = link.b
            var vx = px[a] - px[b]
            var vy = py[a] - py[b]
            var d = sqrt(vx * vx + vy * vy)
            if (d < MIN_DISTANCE) {
                vx = MIN_DISTANCE
                vy = 0.0
                d = MIN_DISTANCE
            }
            val weightFactor = 1.0 + params.modularityBonus * ln(link.weight.toDouble())
            val f = params.linkStrength * (d * d) / k * weightFactor
            val ux = vx / d * f
            val uy = vy / d * f
            dx[a] -= ux
            dy[a] -= uy
            dx[b] += ux
            dy[b] += uy
        }

        for (i in 0 until n) {
            dx[i] -= px[i] * params.centerStrength
            dy[i] -= py[i] * params.centerStrength
        }

        val temp = k * (1.0 - iter.toDouble() / params.iterations)
        var stepThisIter = 0.0
        for (i in 0 until n) {
            val len = sqrt(dx[i] * dx[i] + dy[i] * dy[i])
            if (len < MIN_DISTANCE) continue
            val move = if (len > temp) temp else len
            px[i] += dx[i] / len * move
            py[i] += dy[i] / len * move
            if (move > stepThisIter) stepThisIter = move
        }
        maxStep = stepThisIter
    }

    var cx = 0.0
    var cy = 0.0
    for (i in 0 until n) {
        cx += px[i]
        cy += py[i]
    }
    cx /= n
    cy /= n

    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    val positions = LinkedHashMap<String, FLPoint>(n * 2)
    for (i in 0 until n) {
        val x = px[i] - cx
        val y = py[i] - cy
        positions[ids[i]] = FLPoint(x, y)
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }

    return ForceLayoutResult(positions, maxX - minX, maxY - minY, params.iterations, maxStep)
}
