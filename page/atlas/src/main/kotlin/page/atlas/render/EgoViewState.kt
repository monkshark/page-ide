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
    var depPage by mutableStateOf(0)
    var impPage by mutableStateOf(0)
    private var sliceKey: GraphSlice? = null
    private var focusKey: String? = null

    fun onFocusChanged(activeId: String?) {
        if (focusKey == activeId) return
        focusKey = activeId
        pan = Offset.Zero
        zoom = 1f
        selectedId = null
        hoveredId = null
        depPage = 0
        impPage = 0
    }

    fun pageColumn(column: EgoColumn, delta: Int) {
        when (column) {
            EgoColumn.DEPENDENT -> depPage = (depPage + delta).coerceAtLeast(0)
            EgoColumn.IMPORT -> impPage = (impPage + delta).coerceAtLeast(0)
            else -> Unit
        }
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
