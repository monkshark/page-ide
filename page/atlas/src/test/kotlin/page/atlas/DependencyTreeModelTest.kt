package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.render.DepRow
import page.atlas.render.DepSection
import page.atlas.render.buildDependencyRows

class DependencyTreeModelTest {

    private fun node(id: String, kind: NodeKind = NodeKind.WORKSPACE_FILE) =
        GraphNode(id, id, null, kind)

    private fun slice(vararg edges: Pair<String, String>): GraphSlice {
        val ids = edges.flatMap { listOf(it.first, it.second) }.distinct()
        return GraphSlice(
            ids.map { node(it, if (it == "root") NodeKind.ACTIVE else NodeKind.WORKSPACE_FILE) },
            edges.map { (from, to) -> GraphEdge(from, to) },
        )
    }

    private fun rows(s: GraphSlice, expanded: Set<String> = emptySet(), collapsed: Set<DepSection> = emptySet()) =
        buildDependencyRows(s, expanded, collapsed)

    private fun nodeRows(rows: List<DepRow>, section: DepSection) =
        rows.filterIsInstance<DepRow.NodeRow>().filter { it.section == section }

    @Test
    fun `uses section lists outgoing sorted by label`() {
        val s = slice("root" to "zeta", "root" to "alpha")
        val uses = nodeRows(rows(s), DepSection.USES)
        assertEquals(listOf("alpha", "zeta"), uses.map { it.node.id })
        assertEquals(listOf(0, 0), uses.map { it.depth })
    }

    @Test
    fun `used by section lists incoming`() {
        val s = slice("caller1" to "root", "caller2" to "root", "root" to "dep")
        val usedBy = nodeRows(rows(s), DepSection.USED_BY)
        assertEquals(setOf("caller1", "caller2"), usedBy.map { it.node.id }.toSet())
    }

    @Test
    fun `header counts match direct children`() {
        val s = slice("root" to "a", "root" to "b", "c" to "root")
        val headers = rows(s).filterIsInstance<DepRow.Header>()
        assertEquals(2, headers.first { it.section == DepSection.USES }.count)
        assertEquals(1, headers.first { it.section == DepSection.USED_BY }.count)
    }

    @Test
    fun `expanding key reveals transitive children lazily`() {
        val s = slice("root" to "a", "a" to "b")
        val before = nodeRows(rows(s), DepSection.USES)
        assertEquals(listOf("a"), before.map { it.node.id })
        assertTrue(before[0].expandable)
        val key = before[0].key
        val after = nodeRows(rows(s, expanded = setOf(key)), DepSection.USES)
        assertEquals(listOf("a", "b"), after.map { it.node.id })
        assertEquals(listOf(0, 1), after.map { it.depth })
    }

    @Test
    fun `ancestor revisit is blocked from expanding`() {
        val s = slice("root" to "a", "a" to "root")
        val first = nodeRows(rows(s), DepSection.USES)
        val key = first.first { it.node.id == "a" }.key
        val expandedRows = nodeRows(rows(s, expanded = setOf(key)), DepSection.USES)
        val rootAgain = expandedRows.first { it.node.id == "root" && it.depth == 1 }
        assertTrue(rootAgain.revisit)
        assertFalse(rootAgain.expandable)
    }

    @Test
    fun `external nodes are not expandable`() {
        val ext = GraphNode("ext", "ext", null, NodeKind.EXTERNAL)
        val s = GraphSlice(
            listOf(node("root", NodeKind.ACTIVE), ext, node("other")),
            listOf(GraphEdge("root", "ext"), GraphEdge("ext", "other")),
        )
        val uses = nodeRows(rows(s), DepSection.USES)
        assertFalse(uses.first { it.node.id == "ext" }.expandable)
    }

    @Test
    fun `cycle members carry cyclic badge in tree sections`() {
        val s = slice("root" to "a", "a" to "b", "b" to "a")
        val first = nodeRows(rows(s), DepSection.USES)
        assertTrue(first.first { it.node.id == "a" }.cyclic)
    }

    @Test
    fun `cycles section lists groups and expands members`() {
        val s = slice("root" to "a", "a" to "b", "b" to "a")
        val collapsed = rows(s)
        val cycleRows = collapsed.filterIsInstance<DepRow.CycleRow>()
        assertEquals(1, cycleRows.size)
        assertEquals(2, cycleRows[0].size)
        val expanded = rows(s, expanded = setOf(cycleRows[0].key))
        val members = nodeRows(expanded, DepSection.CYCLES)
        assertEquals(setOf("a", "b"), members.map { it.node.id }.toSet())
        assertTrue(members.all { it.cyclic && !it.expandable })
    }

    @Test
    fun `collapsed section hides children but keeps header`() {
        val s = slice("root" to "a")
        val result = rows(s, collapsed = setOf(DepSection.USES))
        assertTrue(nodeRows(result, DepSection.USES).isEmpty())
        assertTrue(result.filterIsInstance<DepRow.Header>().first { it.section == DepSection.USES }.collapsed)
    }

    @Test
    fun `missing active file yields empty rows with message`() {
        val s = GraphSlice(listOf(node("a"), node("b")), listOf(GraphEdge("a", "b")))
        val result = rows(s)
        val empties = result.filterIsInstance<DepRow.EmptyRow>()
        assertEquals(
            setOf(DepSection.USES, DepSection.USED_BY),
            empties.filter { it.message == "활성 파일 없음" }.map { it.section }.toSet(),
        )
    }

    @Test
    fun `no dependencies yields empty message`() {
        val s = GraphSlice(listOf(node("root", NodeKind.ACTIVE)), emptyList())
        val result = rows(s)
        val empties = result.filterIsInstance<DepRow.EmptyRow>()
        assertTrue(empties.any { it.section == DepSection.USES && it.message == "없음" })
        assertTrue(empties.any { it.section == DepSection.CYCLES && it.message == "없음" })
    }

    @Test
    fun `row keys are unique`() {
        val s = slice("root" to "a", "root" to "b", "a" to "b", "b" to "a")
        val allKeys = ArrayList<String>()
        val first = rows(s)
        val expandable = first.filterIsInstance<DepRow.NodeRow>().filter { it.expandable }.map { it.key } +
            first.filterIsInstance<DepRow.CycleRow>().map { it.key }
        val result = rows(s, expanded = expandable.toSet())
        result.forEach { allKeys += it.key }
        assertEquals(allKeys.size, allKeys.toSet().size)
    }
}
