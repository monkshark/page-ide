package page.atlas.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import page.atlas.graph.GraphSlice
import page.atlas.interaction.DependencyExploration

class ExplorationViewState {
    var exploration by mutableStateOf(DependencyExploration())
        private set
    val camera = MapViewState()
    var revealRequest by mutableStateOf<ExplorationReveal?>(null)
        private set
    private var revealSequence = 0
    var trail by mutableStateOf<List<String>>(emptyList())
        private set
    private var history by mutableStateOf<List<Entry>>(emptyList())
    val canGoBack: Boolean get() = history.isNotEmpty()

    private data class Entry(val exploration: DependencyExploration, val pan: Offset, val scale: Float, val trail: List<String>)

    fun update(next: DependencyExploration) {
        if (next == exploration) return
        if (exploration.selectedId != null) {
            history = (history + Entry(exploration, camera.pan, camera.scale, trail)).takeLast(40)
        }
        val added = next.positions.keys - exploration.positions.keys
        val changedSelection = next.selectedId != exploration.selectedId
        if (added.isNotEmpty() || changedSelection) {
            revealRequest = ExplorationReveal(++revealSequence, added + listOfNotNull(next.selectedId))
        }
        if (changedSelection) next.selectedId?.let { trail = (trail + it).takeLast(40) }
        exploration = next
    }

    fun start(slice: GraphSlice, id: String? = null) {
        update(DependencyExploration.start(slice, id))
        trail = listOfNotNull(exploration.selectedId)
        revealRequest = null
        camera.scale = 0f
    }

    fun onSliceChanged(slice: GraphSlice, preferredId: String?) {
        val next = exploration.reconcile(slice)
        exploration = if (next.selectedId == null) DependencyExploration.start(slice, preferredId) else next
        trail = trail.filter { id -> slice.nodes.any { it.id == id } }
        if (trail.lastOrNull() != exploration.selectedId) trail = trail + listOfNotNull(exploration.selectedId)
        if (next.selectedId == null) camera.scale = 0f
    }

    fun revisit(slice: GraphSlice, index: Int) {
        val id = trail.getOrNull(index) ?: return
        update(exploration.select(slice, id))
        if (exploration.selectedId == id) trail = trail.take(index + 1)
    }

    fun acknowledgeReveal(request: ExplorationReveal) {
        if (revealRequest == request) revealRequest = null
    }

    fun back(slice: GraphSlice) {
        while (history.isNotEmpty()) {
            val previous = history.last()
            history = history.dropLast(1)
            val next = previous.exploration.reconcile(slice)
            if (next.selectedId == null) continue
            exploration = next
            camera.pan = previous.pan
            camera.scale = previous.scale
            trail = previous.trail.filter { id -> slice.nodes.any { it.id == id } }
            revealRequest = null
            return
        }
    }
}

data class ExplorationReveal(val sequence: Int, val ids: Set<String>)
