package page.atlas

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphInsights
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

class GraphInsightsTest {

    private fun file(id: String) = GraphNode(id, "$id.kt", Path.of("/src/$id.kt"), NodeKind.WORKSPACE_FILE)

    private fun external(id: String) = GraphNode(id, id, null, NodeKind.EXTERNAL)

    private fun edge(from: String, to: String) = GraphEdge(from, to)

    @Test
    fun `impact records direct and transitive dependents with depth`() {
        val slice = GraphSlice(
            listOf(file("focus"), file("a"), file("b")),
            listOf(edge("a", "focus"), edge("b", "a")),
        )
        val impact = GraphInsights.impact(slice, "focus")
        assertEquals(listOf("a", "b"), impact.map { it.node.id })
        assertEquals(1, impact.first { it.node.id == "a" }.depth)
        assertEquals(2, impact.first { it.node.id == "b" }.depth)
    }

    @Test
    fun `impact excludes the focus itself and external nodes`() {
        val slice = GraphSlice(
            listOf(file("focus"), file("a"), external("ext")),
            listOf(edge("a", "focus"), edge("ext", "focus")),
        )
        val impact = GraphInsights.impact(slice, "focus")
        assertEquals(listOf("a"), impact.map { it.node.id })
    }

    @Test
    fun `impact carries each dependent own incoming count and sorts by it`() {
        val slice = GraphSlice(
            listOf(file("focus"), file("hub"), file("leaf"), file("p"), file("q"), file("r")),
            listOf(
                edge("hub", "focus"),
                edge("leaf", "focus"),
                edge("p", "hub"),
                edge("q", "hub"),
                edge("r", "hub"),
            ),
        )
        val impact = GraphInsights.impact(slice, "focus")
        val hub = impact.first { it.node.id == "hub" }
        val leaf = impact.first { it.node.id == "leaf" }
        assertEquals(3, hub.ownDependents)
        assertEquals(0, leaf.ownDependents)
        assertEquals("hub", impact.first().node.id)
    }

    @Test
    fun `impact is empty when focus is absent`() {
        val slice = GraphSlice(listOf(file("a")), emptyList())
        assertTrue(GraphInsights.impact(slice, "missing").isEmpty())
    }

    @Test
    fun `impact breaks ties by case-insensitive label when depth and ownDependents are equal`() {
        val slice = GraphSlice(
            listOf(file("focus"), file("Zeta"), file("alpha")),
            listOf(edge("Zeta", "focus"), edge("alpha", "focus")),
        )
        val impact = GraphInsights.impact(slice, "focus")
        assertEquals(listOf("alpha", "Zeta"), impact.map { it.node.id })
        impact.forEach {
            assertEquals(1, it.depth)
            assertEquals(0, it.ownDependents)
        }
    }

    @Test
    fun `impact breaks fully equal label ties by node id`() {
        val a1 = GraphNode("a1", "same.kt", Path.of("/src/a1.kt"), NodeKind.WORKSPACE_FILE)
        val a2 = GraphNode("a2", "same.kt", Path.of("/src/a2.kt"), NodeKind.WORKSPACE_FILE)
        val slice = GraphSlice(
            listOf(file("focus"), a2, a1),
            listOf(edge("a2", "focus"), edge("a1", "focus")),
        )
        val impact = GraphInsights.impact(slice, "focus")
        assertEquals(listOf("a1", "a2"), impact.map { it.node.id })
    }

    @Test
    fun `impact ignores duplicate edges for depth and own dependents`() {
        val slice = GraphSlice(
            listOf(file("focus"), file("a"), file("b")),
            listOf(edge("a", "focus"), edge("a", "focus"), edge("b", "a"), edge("b", "a")),
        )
        val impact = GraphInsights.impact(slice, "focus")
        assertEquals(listOf("a", "b"), impact.map { it.node.id })
        val a = impact.first { it.node.id == "a" }
        val b = impact.first { it.node.id == "b" }
        assertEquals(1, a.depth)
        assertEquals(2, b.depth)
        assertEquals(1, a.ownDependents)
        assertEquals(0, b.ownDependents)
    }

    @Test
    fun `impact excludes focus when focus is itself in a cycle`() {
        val slice = GraphSlice(
            listOf(file("focus"), file("a"), file("b")),
            listOf(edge("focus", "a"), edge("a", "focus"), edge("b", "a")),
        )
        val impact = GraphInsights.impact(slice, "focus")
        assertEquals(listOf("a", "b"), impact.map { it.node.id })
        assertEquals(1, impact.first { it.node.id == "a" }.depth)
        assertEquals(2, impact.first { it.node.id == "b" }.depth)
        assertTrue(impact.none { it.node.id == "focus" }, "focus must not appear in its own impact")

        val cycleMembers = GraphInsights.cycleGroups(slice)
            .flatMap { group -> group.members.map { it.id } }
            .toSet()
        assertTrue("focus" in cycleMembers && "a" in cycleMembers, "focus and a form a cycle group")
    }

    @Test
    fun `cycles groups a mutual dependency`() {
        assertEquals(listOf(listOf("a", "b")), GraphInsights.cycles(listOf(edge("a", "b"), edge("b", "a"))))
    }

    @Test
    fun `cycles keeps only the loop members of a longer cycle`() {
        val edges = listOf(edge("a", "b"), edge("b", "c"), edge("c", "a"), edge("c", "d"), edge("x", "a"))
        assertEquals(listOf(listOf("a", "b", "c")), GraphInsights.cycles(edges))
    }

    @Test
    fun `cycles separates two disjoint loops`() {
        val edges = listOf(edge("a", "b"), edge("b", "a"), edge("c", "d"), edge("d", "c"), edge("b", "c"))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), GraphInsights.cycles(edges))
    }

    @Test
    fun `cycles is empty for an acyclic graph`() {
        assertTrue(GraphInsights.cycles(listOf(edge("a", "b"), edge("b", "c"))).isEmpty())
    }

    @Test
    fun `cycles reports a self loop as a single member group`() {
        assertEquals(listOf(listOf("a")), GraphInsights.cycles(listOf(edge("a", "a"))))
    }

    @Test
    fun `cycles dedupes a self loop inside an SCC and orders groups by smallest member`() {
        val edges = listOf(
            edge("a", "b"), edge("b", "a"), edge("a", "a"),
            edge("x", "y"), edge("y", "x"),
            edge("m", "m"),
        )
        assertEquals(
            listOf(listOf("a", "b"), listOf("m"), listOf("x", "y")),
            GraphInsights.cycles(edges),
        )
    }

    @Test
    fun `indegrees ignores self references and counts duplicate edges once`() {
        val edges = listOf(
            edge("a", "hub"), edge("b", "hub"), edge("c", "hub"),
            edge("a", "hub"), edge("hub", "a"), edge("a", "a"),
        )
        val indegree = GraphInsights.indegrees(edges)
        assertEquals(3, indegree["hub"])
        assertEquals(1, indegree["a"])
        assertEquals(null, indegree["b"])
    }

    @Test
    fun `hubs ranks workspace files by incoming count and respects the limit`() {
        val slice = GraphSlice(
            listOf(file("hub"), file("mid"), file("leaf"), external("lib")),
            listOf(
                edge("mid", "hub"), edge("leaf", "hub"), edge("a", "hub"),
                edge("leaf", "mid"),
                edge("hub", "lib"), edge("mid", "lib"),
            ),
        )
        val hubs = GraphInsights.hubs(slice, limit = 2)
        assertEquals(listOf("hub", "mid"), hubs.map { it.node.id })
        assertEquals(3, hubs.first().dependents)
        assertTrue(hubs.none { it.node.id == "lib" }, "external libraries are not project hubs")
    }

    @Test
    fun `hubs breaks indegree ties by label then id at the limit boundary`() {
        val slice = GraphSlice(
            listOf(file("b"), file("a"), file("c")),
            listOf(
                edge("x", "a"), edge("y", "a"),
                edge("x", "b"), edge("y", "b"),
                edge("x", "c"), edge("y", "c"),
            ),
        )
        val hubs = GraphInsights.hubs(slice, limit = 2)
        assertEquals(listOf("a", "b"), hubs.map { it.node.id })
        assertTrue(hubs.all { it.dependents == 2 }, "all three hubs share indegree 2")
    }

    @Test
    fun `neighborhood splits direct dependents from direct imports`() {
        val slice = GraphSlice(
            listOf(file("focus"), file("dep"), file("imp"), external("lib")),
            listOf(edge("dep", "focus"), edge("focus", "imp"), edge("focus", "lib")),
        )
        val hood = GraphInsights.neighborhood(slice, "focus")
        assertEquals("focus", hood.focus?.id)
        assertEquals(listOf("dep"), hood.incoming.map { it.node.id })
        assertEquals(listOf("imp", "lib"), hood.outgoing.map { it.node.id })
        assertEquals(1, hood.incomingTotal)
        assertEquals(2, hood.outgoingTotal)
    }

    @Test
    fun `neighborhood is one hop only and ignores transitive edges`() {
        val slice = GraphSlice(
            listOf(file("focus"), file("dep"), file("far")),
            listOf(edge("dep", "focus"), edge("far", "dep")),
        )
        val hood = GraphInsights.neighborhood(slice, "focus")
        assertEquals(listOf("dep"), hood.incoming.map { it.node.id })
        assertTrue(hood.incoming.none { it.node.id == "far" }, "two-hop dependents are excluded")
    }

    @Test
    fun `neighborhood weights each side by its own dependents and sorts by weight`() {
        val slice = GraphSlice(
            listOf(file("focus"), file("big"), file("small"), file("hub"), file("p"), file("q")),
            listOf(
                edge("big", "focus"), edge("small", "focus"),
                edge("focus", "hub"),
                edge("p", "big"), edge("q", "big"),
                edge("p", "hub"), edge("q", "hub"),
            ),
        )
        val hood = GraphInsights.neighborhood(slice, "focus")
        assertEquals(listOf("big", "small"), hood.incoming.map { it.node.id })
        assertEquals(2, hood.incoming.first { it.node.id == "big" }.weight)
        assertEquals(0, hood.incoming.first { it.node.id == "small" }.weight)
        assertEquals(3, hood.outgoing.first { it.node.id == "hub" }.weight)
    }

    @Test
    fun `neighborhood caps each side at the limit but reports the full total`() {
        val deps = (1..9).map { file("d$it") }
        val edges = deps.map { edge(it.id, "focus") }
        val slice = GraphSlice(listOf(file("focus")) + deps, edges)
        val hood = GraphInsights.neighborhood(slice, "focus", limit = 4)
        assertEquals(4, hood.incoming.size)
        assertEquals(9, hood.incomingTotal)
        assertEquals(0, hood.outgoingTotal)
    }

    @Test
    fun `neighborhood dedupes multi-symbol edges to the same neighbor`() {
        val slice = GraphSlice(
            listOf(file("focus"), file("dep")),
            listOf(edge("dep", "focus"), edge("dep", "focus")),
        )
        val hood = GraphInsights.neighborhood(slice, "focus")
        assertEquals(listOf("dep"), hood.incoming.map { it.node.id })
        assertEquals(1, hood.incomingTotal)
    }

    @Test
    fun `neighborhood flags a focus that sits in a cycle`() {
        val slice = GraphSlice(
            listOf(file("focus"), file("a")),
            listOf(edge("focus", "a"), edge("a", "focus")),
        )
        val hood = GraphInsights.neighborhood(slice, "focus")
        assertTrue(hood.inCycle, "focus and a form a cycle")
        assertEquals(listOf("a"), hood.incoming.map { it.node.id })
        assertEquals(listOf("a"), hood.outgoing.map { it.node.id })
    }

    @Test
    fun `neighborhood is empty when focus is absent`() {
        val slice = GraphSlice(listOf(file("a")), listOf(edge("a", "a")))
        val hood = GraphInsights.neighborhood(slice, "missing")
        assertEquals(null, hood.focus)
        assertTrue(hood.incoming.isEmpty() && hood.outgoing.isEmpty())
    }

    @Test
    fun `cycleGroups resolves member ids to nodes`() {
        val slice = GraphSlice(
            listOf(file("a"), file("b"), file("c")),
            listOf(edge("a", "b"), edge("b", "a")),
        )
        val groups = GraphInsights.cycleGroups(slice)
        assertEquals(1, groups.size)
        assertEquals(listOf("a", "b"), groups.first().members.map { it.id })
    }
}
