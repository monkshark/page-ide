package page.atlas.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import page.atlas.graph.GraphSlice

class EgoViewState {
    var pan by mutableStateOf(Offset.Zero)
    var zoom by mutableStateOf(1f)
    var selectedId by mutableStateOf<String?>(null)
    var hoveredId by mutableStateOf<String?>(null)
    var pendingFocusId by mutableStateOf<String?>(null)
    private var sliceKey: GraphSlice? = null
    private var focusKey: String? = null

    fun onFocusChanged(activeId: String?) {
        if (focusKey == activeId) return
        focusKey = activeId
        pan = Offset.Zero
        zoom = 1f
        selectedId = null
        hoveredId = null
    }

    fun onSliceChanged(slice: GraphSlice) {
        val pending = pendingFocusId
        if (pending != null && slice.nodes.any { it.id == pending }) {
            pendingFocusId = null
            sliceKey = slice
            selectedId = pending
            return
        }
        if (sliceKey == slice) return
        sliceKey = slice
    }

    fun zoomBy(factor: Float) {
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun reset() {
        pan = Offset.Zero
        zoom = 1f
    }

    private companion object {
        const val MIN_ZOOM = 0.4f
        const val MAX_ZOOM = 3f
    }
}
