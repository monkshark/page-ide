package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import page.shared.path.FilePath
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.HUB_DEPENDENTS_MIN
import page.atlas.graph.NodeKind
import page.atlas.graph.dependencyDigest

class ProjectDependencyDigestTest {

    private fun node(id: String): GraphNode =
        GraphNode(id, id, FilePath.of(id), NodeKind.WORKSPACE_FILE)

    private fun slice(ids: List<String>, edges: List<Pair<String, String>>): GraphSlice =
        GraphSlice(ids.map { node(it) }, edges.map { GraphEdge(it.first, it.second) })

    @Test
    fun `dependents count incoming edges per node`() {
        val digest = dependencyDigest(
            slice(listOf("a", "b", "c"), listOf("a" to "c", "b" to "c", "a" to "b")),
        )
        assertEquals(2, digest.roleOf("c").dependents)
        assertEquals(1, digest.roleOf("b").dependents)
        assertEquals(0, digest.roleOf("a").dependents)
    }

    @Test
    fun `mutual and longer cycles mark every member`() {
        val digest = dependencyDigest(
            slice(
                listOf("a", "b", "c", "d", "x"),
                listOf("a" to "b", "b" to "c", "c" to "a", "c" to "d", "x" to "a"),
            ),
        )
        assertTrue(digest.roleOf("a").inCycle)
        assertTrue(digest.roleOf("b").inCycle)
        assertTrue(digest.roleOf("c").inCycle)
        assertFalse(digest.roleOf("d").inCycle, "d is downstream of the cycle, not in it")
        assertFalse(digest.roleOf("x").inCycle, "x only feeds the cycle")
    }

    @Test
    fun `self loop counts as a cycle`() {
        val digest = dependencyDigest(slice(listOf("a"), listOf("a" to "a")))
        assertTrue(digest.roleOf("a").inCycle)
    }

    @Test
    fun `hub is flagged at the dependents threshold`() {
        val dependents = (0 until HUB_DEPENDENTS_MIN).map { "d$it" }
        val edges = dependents.map { it to "hub" }
        val digest = dependencyDigest(slice(dependents + "hub", edges))
        assertTrue(digest.roleOf("hub").isHub, "hub has $HUB_DEPENDENTS_MIN dependents")
        assertFalse(digest.roleOf("d0").isHub, "leaf is not a hub")
    }

    @Test
    fun `one fewer dependent stays below the hub threshold`() {
        val dependents = (0 until HUB_DEPENDENTS_MIN - 1).map { "d$it" }
        val edges = dependents.map { it to "node" }
        val digest = dependencyDigest(slice(dependents + "node", edges))
        assertFalse(digest.roleOf("node").isHub)
    }

    @Test
    fun `truncated flag propagates into every role`() {
        val digest = dependencyDigest(slice(listOf("a", "b"), listOf("a" to "b")), truncated = true)
        assertTrue(digest.roleOf("b").truncated)
        assertTrue(digest.roleOf("a").truncated)
    }

    @Test
    fun `empty slice yields empty digest`() {
        val digest = dependencyDigest(GraphSlice.EMPTY)
        assertEquals(0, digest.roleOf("anything").dependents)
        assertFalse(digest.roleOf("anything").inCycle)
        assertFalse(digest.roleOf("anything").isHub)
    }

    @Test
    fun `a multi file cycle is grouped once with every member`() {
        val digest = dependencyDigest(
            slice(
                listOf("a", "b", "c", "d", "x"),
                listOf("a" to "b", "b" to "c", "c" to "a", "c" to "d", "x" to "a"),
            ),
        )
        assertEquals(1, digest.cycleGroups.size)
        assertEquals(setOf("a", "b", "c"), digest.cycleGroups.single().map { it.id }.toSet())
    }

    @Test
    fun `a self loop is not reported as a multi file cycle group`() {
        val digest = dependencyDigest(slice(listOf("a"), listOf("a" to "a")))
        assertTrue(digest.roleOf("a").inCycle)
        assertTrue(digest.cycleGroups.isEmpty())
    }

    @Test
    fun `independent cycles become separate groups`() {
        val digest = dependencyDigest(
            slice(
                listOf("a", "b", "c", "d"),
                listOf("a" to "b", "b" to "a", "c" to "d", "d" to "c"),
            ),
        )
        val grouped = digest.cycleGroups.map { group -> group.map { it.id }.toSet() }.toSet()
        assertEquals(setOf(setOf("a", "b"), setOf("c", "d")), grouped)
    }

    @Test
    fun `an acyclic slice has no cycle groups`() {
        val digest = dependencyDigest(
            slice(listOf("a", "b", "c"), listOf("a" to "b", "b" to "c")),
        )
        assertTrue(digest.cycleGroups.isEmpty())
    }
}
