package page.atlas

import androidx.compose.ui.geometry.Offset
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import page.atlas.graph.EdgeKind
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.render.MapBox
import page.atlas.render.ancestorDirIds
import page.atlas.render.applyUserOffsets
import page.atlas.render.belongsTo
import page.atlas.render.buildMap
import page.atlas.render.defaultExpandedDirs

class MapLayoutTest {

    private val width: (String) -> Float = { it.length * 6f }

    private fun id(p: String): String = Path.of(p).toString()

    private fun node(p: String, kind: NodeKind = NodeKind.WORKSPACE_FILE): GraphNode {
        val path = Path.of(p)
        return GraphNode(path.toString(), path.fileName.toString(), path, kind)
    }

    private fun edge(from: String, to: String): GraphEdge = GraphEdge(id(from), id(to))

    private fun overlaps(a: MapBox, b: MapBox): Boolean =
        a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

    @Test
    fun `ancestorDirIds returns parent chain for a file id`() {
        val ancestors = ancestorDirIds(id("ws/a/b/x.kt"))
        assertTrue(id("ws/a/b") in ancestors)
        assertTrue(id("ws/a") in ancestors)
        assertTrue(id("ws") in ancestors)
        assertFalse(id("ws/a/b/x.kt") in ancestors)
    }

    @Test
    fun `file box grows with dependents`() {
        val slice = GraphSlice(
            listOf(node("ws/hub.kt"), node("ws/aaa.kt"), node("ws/bbb.kt"), node("ws/ccc.kt")),
            listOf(
                edge("ws/aaa.kt", "ws/hub.kt"),
                edge("ws/bbb.kt", "ws/hub.kt"),
                edge("ws/ccc.kt", "ws/hub.kt"),
            ),
        )
        val map = buildMap(slice, emptySet(), width)
        val hub = map.boxes.first { it.id == id("ws/hub.kt") }
        val leaf = map.boxes.first { it.id == id("ws/aaa.kt") }
        assertTrue(hub.w > leaf.w)
        assertTrue(hub.h > leaf.h)
    }

    @Test
    fun `collapsed folders aggregate file counts and edge weights`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/x.kt", NodeKind.ACTIVE),
                node("ws/a/y.kt"),
                node("ws/b/z.kt"),
            ),
            listOf(
                edge("ws/a/x.kt", "ws/a/y.kt"),
                edge("ws/a/x.kt", "ws/b/z.kt"),
                edge("ws/a/y.kt", "ws/b/z.kt"),
            ),
        )
        val map = buildMap(slice, emptySet(), width)
        assertEquals(2, map.boxes.size)
        assertTrue(map.boxes.all { it.folder && !it.expanded })
        assertEquals(2, map.boxes.first { it.id == id("ws/a") }.fileCount)
        assertEquals(1, map.boxes.first { it.id == id("ws/b") }.fileCount)
        assertEquals(1, map.edges.size)
        val aggregated = map.edges.single()
        assertEquals(id("ws/a"), aggregated.from)
        assertEquals(id("ws/b"), aggregated.to)
        assertEquals(2, aggregated.weight)
    }

    @Test
    fun `expanding a folder shows its files and reroutes edges to them`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/x.kt", NodeKind.ACTIVE),
                node("ws/a/y.kt"),
                node("ws/b/z.kt"),
            ),
            listOf(
                edge("ws/a/x.kt", "ws/b/z.kt"),
                edge("ws/a/y.kt", "ws/b/z.kt"),
            ),
        )
        val map = buildMap(slice, setOf(id("ws/a")), width)
        assertNotNull(map.boxes.firstOrNull { it.id == id("ws/a/x.kt") && !it.folder })
        assertNotNull(map.boxes.firstOrNull { it.id == id("ws/a/y.kt") && !it.folder })
        assertEquals(
            setOf(id("ws/a/x.kt") to id("ws/b"), id("ws/a/y.kt") to id("ws/b")),
            map.edges.map { it.from to it.to }.toSet(),
        )
        assertTrue(map.edges.all { it.weight == 1 })
    }

    @Test
    fun `single child folder chains compress into one label`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/b/c/f.kt", NodeKind.ACTIVE),
                node("ws/d/g.kt"),
            ),
            emptyList(),
        )
        val map = buildMap(slice, emptySet(), width)
        val chain = map.boxes.first { it.id == id("ws/a/b/c") }
        assertEquals("a/b/c", chain.label)
        assertEquals(1, chain.fileCount)
    }

    @Test
    fun `nested expansion keeps children inside parent bounds`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/x.kt", NodeKind.ACTIVE),
                node("ws/a/sub/y.kt"),
                node("ws/b/z.kt"),
            ),
            emptyList(),
        )
        val map = buildMap(slice, setOf(id("ws/a"), id("ws/a/sub")), width)
        val parent = map.boxes.first { it.id == id("ws/a") }
        assertTrue(parent.expanded)
        val sub = map.boxes.first { it.id == id("ws/a/sub") }
        val file = map.boxes.first { it.id == id("ws/a/sub/y.kt") }
        for (child in listOf(sub, file, map.boxes.first { it.id == id("ws/a/x.kt") })) {
            assertTrue(child.x >= parent.x && child.x + child.w <= parent.x + parent.w)
            assertTrue(child.y >= parent.y && child.y + child.h <= parent.y + parent.h)
        }
        assertTrue(file.x >= sub.x && file.x + file.w <= sub.x + sub.w)
        assertTrue(file.y >= sub.y && file.y + file.h <= sub.y + sub.h)
    }

    @Test
    fun `sibling boxes never overlap`() {
        val slice = GraphSlice(
            (1..7).map { node("ws/d$it/f$it.kt") } + node("ws/a/x.kt", NodeKind.ACTIVE),
            emptyList(),
        )
        val map = buildMap(slice, emptySet(), width)
        for (i in map.boxes.indices) {
            for (j in i + 1 until map.boxes.size) {
                assertFalse(overlaps(map.boxes[i], map.boxes[j]))
            }
        }
    }

    @Test
    fun `same input produces identical output`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/x.kt", NodeKind.ACTIVE),
                node("ws/a/sub/y.kt"),
                node("ws/b/z.kt"),
            ),
            listOf(edge("ws/a/x.kt", "ws/b/z.kt")),
        )
        val expanded = setOf(id("ws/a"))
        assertEquals(buildMap(slice, expanded, width), buildMap(slice, expanded, width))
    }

    @Test
    fun `active file and its ancestor folders are flagged`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/sub/y.kt", NodeKind.ACTIVE),
                node("ws/a/x.kt"),
                node("ws/b/z.kt"),
            ),
            emptyList(),
        )
        val map = buildMap(slice, setOf(id("ws/a"), id("ws/a/sub")), width)
        assertTrue(map.boxes.first { it.id == id("ws/a/sub/y.kt") }.active)
        assertTrue(map.boxes.first { it.id == id("ws/a") }.activeTrail)
        assertTrue(map.boxes.first { it.id == id("ws/a/sub") }.activeTrail)
        assertFalse(map.boxes.first { it.id == id("ws/b") }.activeTrail)
        assertFalse(map.boxes.first { it.id == id("ws/a/x.kt") }.active)
    }

    @Test
    fun `default expanded dirs reveal the active file`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/sub/y.kt", NodeKind.ACTIVE),
                node("ws/b/z.kt"),
            ),
            emptyList(),
        )
        val expanded = defaultExpandedDirs(slice)
        assertTrue(id("ws/a/sub") in expanded)
        val map = buildMap(slice, expanded, width)
        assertNotNull(map.boxes.firstOrNull { it.id == id("ws/a/sub/y.kt") && it.active })
        assertFalse(map.boxes.first { it.id == id("ws/b") }.expanded)
    }

    @Test
    fun `files at the common root render as plain file boxes`() {
        val slice = GraphSlice(
            listOf(
                node("ws/x.kt", NodeKind.ACTIVE),
                node("ws/y.kt"),
            ),
            listOf(edge("ws/x.kt", "ws/y.kt")),
        )
        val map = buildMap(slice, emptySet(), width)
        assertEquals(2, map.boxes.size)
        assertTrue(map.boxes.none { it.folder })
        assertEquals(1, map.edges.size)
        assertEquals(id("ws/x.kt"), map.edges.single().from)
    }

    @Test
    fun `siblings sort in dependency order with users first`() {
        val slice = GraphSlice(
            listOf(
                node("ws/app/main.kt", NodeKind.ACTIVE),
                node("ws/core/util.kt"),
                node("ws/ui/view.kt"),
            ),
            listOf(
                edge("ws/app/main.kt", "ws/core/util.kt"),
                edge("ws/ui/view.kt", "ws/core/util.kt"),
            ),
        )
        val map = buildMap(slice, emptySet(), width)
        val ids = map.boxes.map { it.id }
        assertTrue(ids.indexOf(id("ws/app")) < ids.indexOf(id("ws/core")))
        assertTrue(ids.indexOf(id("ws/ui")) < ids.indexOf(id("ws/core")))
        assertTrue(ids.indexOf(id("ws/app")) < ids.indexOf(id("ws/ui")))
    }

    @Test
    fun `cyclic siblings fall back to label order without hanging`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/x.kt", NodeKind.ACTIVE),
                node("ws/b/y.kt"),
            ),
            listOf(
                edge("ws/a/x.kt", "ws/b/y.kt"),
                edge("ws/b/y.kt", "ws/a/x.kt"),
            ),
        )
        val map = buildMap(slice, emptySet(), width)
        assertEquals(2, map.boxes.size)
        assertTrue(map.boxes.indexOfFirst { it.id == id("ws/a") } < map.boxes.indexOfFirst { it.id == id("ws/b") })
    }

    @Test
    fun `belongsTo matches self and path descendants only`() {
        assertTrue(belongsTo(id("ws/a"), id("ws/a")))
        assertTrue(belongsTo(id("ws/a/x.kt"), id("ws/a")))
        assertTrue(belongsTo(id("ws/a/sub/y.kt"), id("ws/a")))
        assertFalse(belongsTo(id("ws/ab/x.kt"), id("ws/a")))
        assertFalse(belongsTo(id("ws"), id("ws/a")))
    }

    @Test
    fun `expanding a folder grows in place and moves only boxes in its footprint`() {
        val slice = GraphSlice(
            listOf(node("ws/a/x.kt", NodeKind.ACTIVE), node("ws/a/y.kt")) +
                ('b'..'h').map { node("ws/$it/f.kt") },
            emptyList(),
        )
        val collapsed = buildMap(slice, emptySet(), width)
        val expanded = buildMap(slice, setOf(id("ws/a")), width)
        val before = collapsed.boxes.first { it.id == id("ws/a") }
        val grown = expanded.boxes.first { it.id == id("ws/a") }
        assertEquals(before.x, grown.x)
        assertEquals(before.y, grown.y)
        assertTrue(grown.w > before.w || grown.h > before.h)
        val top = expanded.boxes.filter { it.depth == 0 }
        for (i in top.indices) {
            for (j in i + 1 until top.size) {
                assertFalse(overlaps(top[i], top[j]))
            }
        }
        val stayed = collapsed.boxes.count { chip ->
            chip.id != grown.id && expanded.boxes.first { it.id == chip.id }.let { it.x == chip.x && it.y == chip.y }
        }
        assertTrue(stayed > 0)
    }

    @Test
    fun `expanding pushes an already expanded sibling and collapsing restores it`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/alpha.kt", NodeKind.ACTIVE),
                node("ws/a/bravo.kt"),
                node("ws/b/alpha.kt"),
                node("ws/b/bravo.kt"),
            ) + ('c'..'f').map { node("ws/$it/f.kt") },
            emptyList(),
        )
        val collapsed = buildMap(slice, emptySet(), width)
        val aHome = collapsed.boxes.first { it.id == id("ws/a") }
        val bHome = collapsed.boxes.first { it.id == id("ws/b") }
        val aOnly = buildMap(slice, setOf(id("ws/a")), width, emptyMap(), listOf(id("ws/a")))
        val aFirst = aOnly.boxes.first { it.id == id("ws/a") }
        assertEquals(aHome.x, aFirst.x)
        assertEquals(aHome.y, aFirst.y)
        val both = buildMap(
            slice,
            setOf(id("ws/a"), id("ws/b")),
            width,
            emptyMap(),
            listOf(id("ws/a"), id("ws/b")),
        )
        val bBoth = both.boxes.first { it.id == id("ws/b") }
        assertEquals(bHome.x, bBoth.x)
        assertEquals(bHome.y, bBoth.y)
        val aBoth = both.boxes.first { it.id == id("ws/a") }
        assertTrue(aBoth.x != aHome.x || aBoth.y != aHome.y)
        val top = both.boxes.filter { it.depth == 0 }
        for (i in top.indices) {
            for (j in i + 1 until top.size) {
                assertFalse(overlaps(top[i], top[j]))
            }
        }
        val restored = buildMap(slice, setOf(id("ws/a")), width, emptyMap(), listOf(id("ws/a")))
        val aBack = restored.boxes.first { it.id == id("ws/a") }
        assertEquals(aHome.x, aBack.x)
        assertEquals(aHome.y, aBack.y)
    }

    @Test
    fun `expansion does not push boxes already dragged out of the footprint`() {
        val slice = GraphSlice(
            listOf(node("ws/a/x.kt", NodeKind.ACTIVE), node("ws/a/y.kt")) +
                ('b'..'h').map { node("ws/$it/f.kt") },
            emptyList(),
        )
        val collapsed = buildMap(slice, emptySet(), width)
        val pushed = buildMap(slice, setOf(id("ws/a")), width)
        val victim = collapsed.boxes.first { chip ->
            chip.id != id("ws/a") &&
                pushed.boxes.first { it.id == chip.id }.let { it.x != chip.x || it.y != chip.y }
        }
        val offsets = mapOf(victim.id to Offset(1000f, 1000f))
        val expanded = buildMap(slice, setOf(id("ws/a")), width, offsets, listOf(id("ws/a")), id("ws/a"))
        val after = expanded.boxes.first { it.id == victim.id }
        assertEquals(victim.x, after.x)
        assertEquals(victim.y, after.y)
        assertTrue(expanded.pushes.isEmpty())
    }

    @Test
    fun `expansion pushes user moved boxes in its footprint by upgrading their offsets`() {
        val slice = GraphSlice(
            listOf(node("ws/a/x.kt", NodeKind.ACTIVE), node("ws/a/y.kt")) +
                ('b'..'h').map { node("ws/$it/f.kt") },
            emptyList(),
        )
        val collapsed = buildMap(slice, emptySet(), width)
        val pushed = buildMap(slice, setOf(id("ws/a")), width, emptyMap(), listOf(id("ws/a")), id("ws/a"))
        val victim = collapsed.boxes.first { chip ->
            chip.id != id("ws/a") &&
                pushed.boxes.first { it.id == chip.id }.let { it.x != chip.x || it.y != chip.y }
        }
        val offsets = mapOf(victim.id to Offset(1f, 1f))
        val expanded = buildMap(slice, setOf(id("ws/a")), width, offsets, listOf(id("ws/a")), id("ws/a"))
        val push = expanded.pushes[victim.id]
        assertNotNull(push)
        val folder = expanded.boxes.first { it.id == id("ws/a") }
        val after = expanded.boxes.first { it.id == victim.id }
        val display = after.copy(x = after.x + 1f + push.x, y = after.y + 1f + push.y)
        assertFalse(overlaps(display, folder))
        val collapsedAgain = buildMap(
            slice,
            emptySet(),
            width,
            mapOf(victim.id to Offset(1f + push.x, 1f + push.y)),
        )
        assertTrue(collapsedAgain.pushes.isEmpty())
    }

    @Test
    fun `nested expansion keeps its ancestors pinned and pushes expanded siblings`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/alpha.kt", NodeKind.ACTIVE),
                node("ws/a/bravo.kt"),
                node("ws/b/direct.kt"),
                node("ws/b/sub/alpha.kt"),
                node("ws/b/sub/bravo.kt"),
            ) + ('c'..'f').map { node("ws/$it/f.kt") },
            emptyList(),
        )
        val collapsed = buildMap(slice, emptySet(), width)
        val aHome = collapsed.boxes.first { it.id == id("ws/a") }
        val bHome = collapsed.boxes.first { it.id == id("ws/b") }
        val expanded = setOf(id("ws/a"), id("ws/b"), id("ws/b/sub"))
        val order = listOf(id("ws/a"), id("ws/b"), id("ws/b/sub"))
        val map = buildMap(slice, expanded, width, emptyMap(), order, id("ws/b/sub"))
        val b = map.boxes.first { it.id == id("ws/b") }
        assertEquals(bHome.x, b.x)
        assertEquals(bHome.y, b.y)
        val a = map.boxes.first { it.id == id("ws/a") }
        assertTrue(a.x != aHome.x || a.y != aHome.y)
        val top = map.boxes.filter { it.depth == 0 }
        for (i in top.indices) {
            for (j in i + 1 until top.size) {
                assertFalse(overlaps(top[i], top[j]))
            }
        }
    }

    @Test
    fun `moving a child stretches its expanded parent to keep containing it`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/x.kt", NodeKind.ACTIVE),
                node("ws/a/y.kt"),
                node("ws/b/z.kt"),
            ),
            emptyList(),
        )
        val map = buildMap(slice, setOf(id("ws/a")), width)
        val before = map.boxes.first { it.id == id("ws/a") }
        val moved = applyUserOffsets(map.boxes, mapOf(id("ws/a/x.kt") to Offset(400f, 300f)))
        val parent = moved.first { it.id == id("ws/a") }
        val child = moved.first { it.id == id("ws/a/x.kt") }
        val sibling = moved.first { it.id == id("ws/a/y.kt") }
        for (inner in listOf(child, sibling)) {
            assertTrue(inner.x >= parent.x && inner.x + inner.w <= parent.x + parent.w)
            assertTrue(inner.y >= parent.y && inner.y + inner.h <= parent.y + parent.h)
        }
        assertTrue(parent.w > before.w)
        assertTrue(parent.h > before.h)
    }

    @Test
    fun `moving a folder carries its children without resizing`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/x.kt", NodeKind.ACTIVE),
                node("ws/b/z.kt"),
            ),
            emptyList(),
        )
        val map = buildMap(slice, setOf(id("ws/a")), width)
        val moved = applyUserOffsets(map.boxes, mapOf(id("ws/a") to Offset(120f, 80f)))
        val beforeParent = map.boxes.first { it.id == id("ws/a") }
        val beforeChild = map.boxes.first { it.id == id("ws/a/x.kt") }
        val parent = moved.first { it.id == id("ws/a") }
        val child = moved.first { it.id == id("ws/a/x.kt") }
        assertEquals(beforeParent.x + 120f, parent.x)
        assertEquals(beforeParent.y + 80f, parent.y)
        assertEquals(beforeParent.w, parent.w)
        assertEquals(beforeParent.h, parent.h)
        assertEquals(beforeChild.x + 120f, child.x)
        assertEquals(beforeChild.y + 80f, child.y)
        assertEquals(map.boxes.first { it.id == id("ws/b") }, moved.first { it.id == id("ws/b") })
    }

    @Test
    fun `collapsed edges keep the strongest relation kind`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/impl.kt", NodeKind.ACTIVE),
                node("ws/a/sub.kt"),
                node("ws/b/base.kt"),
            ),
            listOf(
                GraphEdge(id("ws/a/impl.kt"), id("ws/b/base.kt"), EdgeKind.IMPORT),
                GraphEdge(id("ws/a/sub.kt"), id("ws/b/base.kt"), EdgeKind.EXTENDS),
            ),
        )
        val map = buildMap(slice, emptySet(), width)
        val aggregated = map.edges.single { it.from == id("ws/a") && it.to == id("ws/b") }
        assertEquals(2, aggregated.weight)
        assertEquals(EdgeKind.EXTENDS, aggregated.kind)
    }

    @Test
    fun `self and ghost edges are dropped`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/x.kt", NodeKind.ACTIVE),
                node("ws/b/z.kt"),
            ),
            listOf(
                edge("ws/a/x.kt", "ws/a/x.kt"),
                GraphEdge(id("ws/a/x.kt"), "ghost"),
            ),
        )
        val map = buildMap(slice, emptySet(), width)
        assertTrue(map.edges.isEmpty())
    }
}
