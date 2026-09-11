package page.atlas.interaction

import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphInsights
import page.atlas.graph.GraphQueries
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

enum class ExplorationDirection { USES, USED_BY }
enum class ExplorationHighlight { PATH, CYCLE, IMPACT }

data class ExplorationSlot(val column: Int, val row: Int)

data class DependencyExploration(
    val selectedId: String? = null,
    val positions: Map<String, ExplorationSlot> = emptyMap(),
    val selectedEdge: GraphEdge? = null,
    val highlightedEdges: Set<GraphEdge> = emptySet(),
    val highlight: ExplorationHighlight? = null,
    val message: String? = null,
) {
    fun select(slice: GraphSlice, id: String): DependencyExploration {
        if (slice.nodes.none { it.id == id }) return this
        val base = if (positions.isEmpty()) copy(positions = mapOf(id to ExplorationSlot(0, 0)))
        else reveal(slice, listOf(id), selectedId, 1)
        if (id !in base.positions) return copy(message = "View limit reached. Use search to start a new exploration from this file.")
        return base.copy(selectedId = id, selectedEdge = null, highlightedEdges = emptySet(), highlight = null, message = null)
    }

    fun neighbors(slice: GraphSlice, direction: ExplorationDirection): List<String> {
        val byId = slice.nodes.associateBy { it.id }
        return slice.edges.asSequence()
            .filter { it.from in byId && it.to in byId && it.from != it.to }
            .mapNotNull {
                when (direction) {
                    ExplorationDirection.USES -> it.to.takeIf { _ -> it.from == selectedId }
                    ExplorationDirection.USED_BY -> it.from.takeIf { _ -> it.to == selectedId }
                }
            }
            .distinct()
            .sortedWith(compareBy({ byId.getValue(it).label.lowercase() }, { it }))
            .toList()
    }

    fun expand(slice: GraphSlice, direction: ExplorationDirection, count: Int = PAGE_SIZE): DependencyExploration {
        val ids = neighbors(slice, direction).filter { it !in positions }.take(count.coerceAtLeast(0))
        val column = if (direction == ExplorationDirection.USES) 1 else -1
        val next = reveal(slice, ids, selectedId, column)
        return next.copy(
            selectedEdge = null,
            highlightedEdges = emptySet(),
            highlight = null,
            message = if (ids.any { it !in next.positions }) "Showing at most $MAX_VISIBLE files. Start from another file to explore further." else null,
        )
    }

    fun inspect(slice: GraphSlice, edge: GraphEdge): DependencyExploration {
        val current = slice.edges.firstOrNull { it.sameRelationship(edge) } ?: return this
        if (current.from !in positions || current.to !in positions) return this
        return copy(selectedEdge = current, highlightedEdges = emptySet(), highlight = null, message = null)
    }

    fun traceTo(slice: GraphSlice, target: String): DependencyExploration {
        val from = selectedId ?: return this
        val valid = validSlice(slice)
        if (target == from) return copy(message = "Choose another file to trace a dependency path.")
        if (valid.nodes.none { it.id == target }) return this
        val path = GraphQueries.findPath(valid.edges, from, target)
            ?: return copy(selectedEdge = null, highlightedEdges = emptySet(), highlight = null, message = "No dependency path from the selected file to this target in the analyzed graph.")
        var next = this
        for (edge in path) next = next.reveal(slice, listOf(edge.to), edge.from, 1)
        val shown = path.filter { it.from in next.positions && it.to in next.positions }.toSet()
        return next.copy(
            selectedEdge = null,
            highlightedEdges = shown,
            highlight = ExplorationHighlight.PATH,
            message = if (shown.size == path.size) "Dependency path · ${path.size} relationships"
            else "Partial path · view limit reached ($MAX_VISIBLE files)",
        )
    }

    fun showCycle(slice: GraphSlice): DependencyExploration {
        val id = selectedId ?: return this
        val valid = validSlice(slice)
        val group = GraphInsights.cycles(valid.edges).firstOrNull { id in it }
            ?: return copy(selectedEdge = null, highlightedEdges = emptySet(), highlight = null, message = "No cycle found for this file in the analyzed graph.")
        val next = reveal(slice, group, id, 1)
        val groupEdges = valid.edges.filter { it.from in group && it.to in group }
        return next.copy(
            selectedEdge = null,
            highlightedEdges = groupEdges.filter { it.from in next.positions && it.to in next.positions }.toSet(),
            highlight = ExplorationHighlight.CYCLE,
            message = if (group.all { it in next.positions }) "Cycle group · ${group.size} files. Select a relationship to inspect its source."
            else "Partial cycle group · ${group.count { it in next.positions }} of ${group.size} files shown",
        )
    }

    fun showImpact(slice: GraphSlice): DependencyExploration {
        val id = selectedId ?: return this
        val valid = validSlice(slice)
        val impacted = GraphInsights.impact(valid, id).sortedWith(compareBy({ it.depth }, { it.node.id }))
        var next = this
        for (item in impacted) {
            val toward = valid.edges.firstOrNull { it.from == item.node.id && it.to in next.positions }?.to
            next = next.reveal(slice, listOf(item.node.id), toward ?: id, -1)
        }
        val ids = impacted.map { it.node.id }.toSet() + id
        val shown = impacted.count { it.node.id in next.positions }
        return next.copy(
            selectedEdge = null,
            highlightedEdges = valid.edges.filter {
                it.from in ids && it.to in ids && it.from in next.positions && it.to in next.positions
            }.toSet(),
            highlight = ExplorationHighlight.IMPACT,
            message = "Potential impact · $shown of ${impacted.size} dependents shown · ${impacted.count { it.depth == 1 }} direct. Static relationships only.",
        )
    }

    fun reconcile(slice: GraphSlice): DependencyExploration {
        val ids = slice.nodes.mapTo(HashSet()) { it.id }
        val remaining = positions.filterKeys { it in ids }
        if (remaining.isEmpty()) return DependencyExploration()
        val selectionRemains = selectedId in remaining
        val edge = selectedEdge?.let { selected -> slice.edges.firstOrNull { it.sameRelationship(selected) } }
        val highlighted = slice.edges.filter { current -> highlightedEdges.any { current.sameRelationship(it) } }.toSet()
        return copy(
            positions = remaining,
            selectedId = selectedId?.takeIf { it in remaining } ?: remaining.keys.first(),
            selectedEdge = edge?.takeIf { selectionRemains && it.from in remaining && it.to in remaining },
            highlightedEdges = if (selectionRemains) highlighted.filter { it.from in remaining && it.to in remaining }.toSet() else emptySet(),
            highlight = highlight.takeIf { selectionRemains },
            message = if (!selectionRemains) "The selected file is no longer in the analyzed graph."
            else if (selectedEdge != null && edge == null) "The selected relationship is no longer in the analyzed graph."
            else if (highlighted != highlightedEdges) "Analysis updated. Recheck the highlighted relationships." else message,
        )
    }

    private fun reveal(slice: GraphSlice, ids: List<String>, anchorId: String?, direction: Int): DependencyExploration {
        if (positions.size >= MAX_VISIBLE) return this
        val validIds = slice.nodes.mapTo(HashSet()) { it.id }
        val slots = positions.toMutableMap()
        val anchor = slots[anchorId] ?: ExplorationSlot(0, 0)
        val occupied = slots.values.toHashSet()
        for (id in ids) {
            if (id !in validIds || id in slots || slots.size >= MAX_VISIBLE) continue
            val column = anchor.column + direction
            val row = sequence {
                yield(anchor.row)
                for (distance in 1..MAX_VISIBLE) {
                    yield(anchor.row - distance)
                    yield(anchor.row + distance)
                }
            }.first { ExplorationSlot(column, it) !in occupied }
            val position = ExplorationSlot(column, row)
            slots[id] = position
            occupied += position
        }
        return copy(positions = slots)
    }

    companion object {
        const val PAGE_SIZE = 4
        const val MAX_VISIBLE = 80

        fun start(slice: GraphSlice, id: String? = null): DependencyExploration {
            val node = slice.nodes.firstOrNull { it.id == id }
                ?: slice.nodes.firstOrNull { it.kind == NodeKind.ACTIVE }
                ?: slice.nodes.filter { it.path != null }.minWithOrNull(compareBy({ it.label.lowercase() }, { it.id }))
                ?: return DependencyExploration()
            return DependencyExploration().select(slice, node.id)
                .expand(slice, ExplorationDirection.USED_BY, 3)
                .expand(slice, ExplorationDirection.USES, 3)
        }

        private fun validSlice(slice: GraphSlice): GraphSlice {
            val ids = slice.nodes.mapTo(HashSet()) { it.id }
            return slice.copy(edges = slice.edges.filter { it.from in ids && it.to in ids })
        }
    }
}

private fun GraphEdge.sameRelationship(other: GraphEdge): Boolean =
    from == other.from && to == other.to && kind == other.kind
