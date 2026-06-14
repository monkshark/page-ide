package page.atlas

import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.render.MapBox
import page.atlas.render.buildMap

class MapGoStoneLayoutTest {
    private val width: (String) -> Float = { it.length * 6f }
    private fun id(p: String): String = Path.of(p).toString()
    private fun node(p: String): GraphNode {
        val path = Path.of(p)
        return GraphNode(path.toString(), path.fileName.toString(), path, NodeKind.WORKSPACE_FILE)
    }
    private fun overlaps(a: MapBox, b: MapBox): Boolean =
        a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
    private fun contains(outer: MapBox, inner: MapBox): Boolean {
        val cx = inner.x + inner.w / 2
        val cy = inner.y + inner.h / 2
        return outer.x <= cx && cx <= outer.x + outer.w && outer.y <= cy && cy <= outer.y + outer.h
    }

    private fun grid(): GraphSlice {
        val nodes = ArrayList<GraphNode>()
        val edges = ArrayList<GraphEdge>()
        for (f in 0 until 9) {
            for (k in 0 until 3) nodes += node("ws/f$f/file$k.kt")
            edges += GraphEdge(id("ws/f$f/file0.kt"), id("ws/f$f/file1.kt"))
        }
        return GraphSlice(nodes, edges)
    }

    @Test
    fun `expanding a folder leaves unaffected siblings exactly where they were`() {
        val slice = grid()
        val target = id("ws/f0")
        val before = buildMap(slice, emptySet(), width).boxes.associateBy { it.id }
        val after = buildMap(slice, setOf(target), width, emptyMap(), listOf(target), target)

        var stayed = 0
        for (box in after.boxes) {
            val prev = before[box.id] ?: continue
            if (box.id == target) continue
            if (abs(box.x - prev.x) < 0.5f && abs(box.y - prev.y) < 0.5f) stayed++
        }
        assertTrue(stayed > 0, "some siblings must stay put")
    }

    @Test
    fun `displaced siblings move along a single axis only`() {
        val slice = grid()
        val target = id("ws/f0")
        val before = buildMap(slice, emptySet(), width).boxes.associateBy { it.id }
        val after = buildMap(slice, setOf(target), width, emptyMap(), listOf(target), target)

        for (box in after.boxes) {
            val prev = before[box.id] ?: continue
            if (box.id == target) continue
            val dx = abs(box.x - prev.x)
            val dy = abs(box.y - prev.y)
            if (dx >= 0.5f && dy >= 0.5f) {
                throw AssertionError("box ${box.id} moved diagonally dx=$dx dy=$dy")
            }
        }
    }

    @Test
    fun `expanded layout has no overlapping siblings`() {
        val slice = grid()
        val target = id("ws/f0")
        val boxes = buildMap(slice, setOf(target), width, emptyMap(), listOf(target), target).boxes
        var collisions = 0
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val a = boxes[i]
            val b = boxes[j]
            if (overlaps(a, b) && !contains(a, b) && !contains(b, a)) collisions++
        }
        assertEquals(0, collisions)
    }
}
