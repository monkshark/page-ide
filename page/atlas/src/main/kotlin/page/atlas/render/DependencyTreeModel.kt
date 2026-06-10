package page.atlas.render

import page.atlas.graph.GraphNode
import page.atlas.graph.GraphQueries
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

enum class AtlasViewTab { DEPENDENCY, GRAPH }

enum class DepSection { USES, USED_BY, CYCLES }

sealed interface DepRow {
    val key: String

    data class Header(val section: DepSection, val count: Int, val collapsed: Boolean) : DepRow {
        override val key: String get() = "H|${section.name}"
    }

    data class NodeRow(
        override val key: String,
        val node: GraphNode,
        val depth: Int,
        val section: DepSection,
        val expandable: Boolean,
        val expanded: Boolean,
        val cyclic: Boolean,
        val revisit: Boolean,
    ) : DepRow

    data class CycleRow(
        override val key: String,
        val index: Int,
        val size: Int,
        val expanded: Boolean,
    ) : DepRow

    data class EmptyRow(val section: DepSection, val message: String) : DepRow {
        override val key: String get() = "E|${section.name}"
    }
}

fun buildDependencyRows(
    slice: GraphSlice,
    expandedKeys: Set<String>,
    collapsedSections: Set<DepSection>,
): List<DepRow> {
    val nodesById = slice.nodes.associateBy { it.id }
    val outgoing = HashMap<String, MutableList<String>>()
    val incoming = HashMap<String, MutableList<String>>()
    for (edge in slice.edges) {
        if (edge.from !in nodesById || edge.to !in nodesById || edge.from == edge.to) continue
        outgoing.getOrPut(edge.from) { mutableListOf() }.add(edge.to)
        incoming.getOrPut(edge.to) { mutableListOf() }.add(edge.from)
    }
    val cycles = GraphQueries.cycles(slice)
    val cyclicIds = cycles.flatMapTo(HashSet()) { component -> component.map { it.id } }
    val root = slice.nodes.firstOrNull { it.kind == NodeKind.ACTIVE }
    val rows = ArrayList<DepRow>()

    fun childrenOf(section: DepSection, id: String): List<GraphNode> {
        val adjacency = if (section == DepSection.USES) outgoing else incoming
        return adjacency[id].orEmpty()
            .mapNotNull { nodesById[it] }
            .distinctBy { it.id }
            .sortedBy { it.label.lowercase() }
    }

    fun addChildren(section: DepSection, parentId: String, chain: List<String>, depth: Int) {
        for (child in childrenOf(section, parentId)) {
            val childChain = chain + child.id
            val key = "${section.name}|${childChain.joinToString("|")}"
            val revisit = child.id in chain
            val expandable = !revisit &&
                child.kind != NodeKind.EXTERNAL &&
                childrenOf(section, child.id).isNotEmpty()
            val expanded = expandable && key in expandedKeys
            rows += DepRow.NodeRow(
                key = key,
                node = child,
                depth = depth,
                section = section,
                expandable = expandable,
                expanded = expanded,
                cyclic = child.id in cyclicIds,
                revisit = revisit,
            )
            if (expanded) addChildren(section, child.id, childChain, depth + 1)
        }
    }

    fun addTreeSection(section: DepSection) {
        val count = root?.let { childrenOf(section, it.id).size } ?: 0
        val collapsed = section in collapsedSections
        rows += DepRow.Header(section, count, collapsed)
        if (collapsed) return
        when {
            root == null -> rows += DepRow.EmptyRow(section, "활성 파일 없음")
            count == 0 -> rows += DepRow.EmptyRow(section, "없음")
            else -> addChildren(section, root.id, listOf(root.id), 0)
        }
    }

    addTreeSection(DepSection.USES)
    addTreeSection(DepSection.USED_BY)

    val collapsed = DepSection.CYCLES in collapsedSections
    rows += DepRow.Header(DepSection.CYCLES, cycles.size, collapsed)
    if (!collapsed) {
        if (cycles.isEmpty()) {
            rows += DepRow.EmptyRow(DepSection.CYCLES, "없음")
        } else {
            cycles.forEachIndexed { index, component ->
                val cycleKey = "CYCLES|$index"
                val expanded = cycleKey in expandedKeys
                rows += DepRow.CycleRow(cycleKey, index, component.size, expanded)
                if (expanded) {
                    for (member in component) {
                        rows += DepRow.NodeRow(
                            key = "$cycleKey|${member.id}",
                            node = member,
                            depth = 1,
                            section = DepSection.CYCLES,
                            expandable = false,
                            expanded = false,
                            cyclic = true,
                            revisit = false,
                        )
                    }
                }
            }
        }
    }
    return rows
}
