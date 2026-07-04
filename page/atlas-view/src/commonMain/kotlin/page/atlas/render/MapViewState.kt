package page.atlas.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

data class MapFilterState(
    val focusDir: String? = null,
    val hiddenDirs: Set<String> = emptySet(),
    val mutedDirs: Set<String> = emptySet(),
) {
    val active: Boolean get() = focusDir != null || hiddenDirs.isNotEmpty() || mutedDirs.isNotEmpty()
}

class MapViewState {
    var pan by mutableStateOf(Offset.Zero)
    var scale by mutableStateOf(0f)
    var fitted by mutableStateOf(false)
    val savedViews = mutableStateMapOf<String, Pair<Offset, Float>>()
    val userOffsets = mutableStateMapOf<String, Offset>()
    val expandOrder = mutableStateListOf<String>()
    var expandedDirs by mutableStateOf<Set<String>?>(null)
    var filter by mutableStateOf(MapFilterState())
    var focusCenterId by mutableStateOf<String?>(null)
    var pinnedIds by mutableStateOf<Set<String>>(emptySet())

    fun togglePin(id: String) {
        pinnedIds = if (id in pinnedIds) pinnedIds - id else pinnedIds + id
    }

    fun reset() {
        focusCenterId = null
        pan = Offset.Zero
        scale = 0f
        fitted = false
        savedViews.clear()
        userOffsets.clear()
        expandOrder.clear()
        expandedDirs = null
        filter = MapFilterState()
        pinnedIds = emptySet()
    }
}
