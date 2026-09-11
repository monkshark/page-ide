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
    private var history by mutableStateOf<List<Entry>>(emptyList())
    val canGoBack: Boolean get() = history.isNotEmpty()

    private data class Entry(val exploration: DependencyExploration, val pan: Offset, val scale: Float)

    fun update(next: DependencyExploration) {
        if (next == exploration) return
        if (exploration.selectedId != null) {
            history = (history + Entry(exploration, camera.pan, camera.scale)).takeLast(40)
        }
        exploration = next
    }

    fun start(slice: GraphSlice, id: String? = null) {
        update(DependencyExploration.start(slice, id))
        camera.scale = 0f
    }

    fun onSliceChanged(slice: GraphSlice, preferredId: String?) {
        val next = exploration.reconcile(slice)
        exploration = if (next.selectedId == null) DependencyExploration.start(slice, preferredId) else next
        if (next.selectedId == null) camera.scale = 0f
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
            return
        }
    }
}
