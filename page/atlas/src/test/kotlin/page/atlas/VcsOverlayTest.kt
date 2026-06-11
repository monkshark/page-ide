package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.NodeKind
import page.atlas.render.VcsMark
import page.atlas.render.vcsFolderCounts
import page.atlas.render.vcsImpactEntries
import page.atlas.render.vcsImpacted

class VcsOverlayTest {

    @Test
    fun countsMarkedDescendantsPerFolder() {
        val marks = mapOf(
            "C:\\ws\\src\\a.kt" to VcsMark.MODIFIED,
            "C:\\ws\\src\\sub\\b.kt" to VcsMark.ADDED,
            "C:\\ws\\other\\c.kt" to VcsMark.MODIFIED,
        )
        val counts = vcsFolderCounts(marks, listOf("C:\\ws", "C:\\ws\\src", "C:\\ws\\src\\sub"))
        assertEquals(3, counts["C:\\ws"])
        assertEquals(2, counts["C:\\ws\\src"])
        assertEquals(1, counts["C:\\ws\\src\\sub"])
    }

    @Test
    fun foldersWithoutChangesAreOmitted() {
        val marks = mapOf("C:\\ws\\src\\a.kt" to VcsMark.MODIFIED)
        val counts = vcsFolderCounts(marks, listOf("C:\\ws\\src", "C:\\ws\\lib"))
        assertEquals(setOf("C:\\ws\\src"), counts.keys)
    }

    @Test
    fun prefixWithoutSeparatorDoesNotMatch() {
        val marks = mapOf("C:\\ws\\srcfile.kt" to VcsMark.MODIFIED)
        assertTrue(vcsFolderCounts(marks, listOf("C:\\ws\\src")).isEmpty())
    }

    @Test
    fun emptyMarksYieldEmptyCounts() {
        assertTrue(vcsFolderCounts(emptyMap(), listOf("C:\\ws")).isEmpty())
    }

    @Test
    fun reverseBfsFindsDependentsWithDistance() {
        val edges = listOf(
            GraphEdge("a", "changed"),
            GraphEdge("b", "a"),
        )
        val impacted = vcsImpacted(edges, setOf("changed"))
        assertEquals(mapOf("a" to 1, "b" to 2), impacted)
    }

    @Test
    fun depthCapExcludesFartherDependents() {
        val edges = listOf(
            GraphEdge("a", "changed"),
            GraphEdge("b", "a"),
            GraphEdge("c", "b"),
        )
        val impacted = vcsImpacted(edges, setOf("changed"), maxDepth = 2)
        assertEquals(setOf("a", "b"), impacted.keys)
    }

    @Test
    fun changedFilesAreNotReportedAsImpacted() {
        val edges = listOf(
            GraphEdge("other", "changed"),
            GraphEdge("changed", "other"),
        )
        val impacted = vcsImpacted(edges, setOf("changed"))
        assertEquals(setOf("other"), impacted.keys)
    }

    @Test
    fun cyclesTerminateWithShortestDistance() {
        val edges = listOf(
            GraphEdge("a", "changed"),
            GraphEdge("b", "a"),
            GraphEdge("a", "b"),
        )
        val impacted = vcsImpacted(edges, setOf("changed"))
        assertEquals(mapOf("a" to 1, "b" to 2), impacted)
    }

    @Test
    fun forwardDependenciesAreNotImpacted() {
        val edges = listOf(GraphEdge("changed", "dep"))
        assertTrue(vcsImpacted(edges, setOf("changed")).isEmpty())
    }

    @Test
    fun impactEntriesSortByDistanceThenLabel() {
        val nodes = listOf(
            GraphNode("d2-b", "Zeta.kt", null, NodeKind.WORKSPACE_FILE),
            GraphNode("d1-a", "beta.kt", null, NodeKind.WORKSPACE_FILE),
            GraphNode("d1-b", "Alpha.kt", null, NodeKind.WORKSPACE_FILE),
            GraphNode("skip", "Skip.kt", null, NodeKind.WORKSPACE_FILE),
        )
        val entries = vcsImpactEntries(nodes, mapOf("d1-a" to 1, "d1-b" to 1, "d2-b" to 2))
        assertEquals(listOf("Alpha.kt", "beta.kt", "Zeta.kt"), entries.map { it.label })
    }
}
