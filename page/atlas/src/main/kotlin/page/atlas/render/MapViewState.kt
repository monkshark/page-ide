package page.atlas.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

class MapViewState {
    var pan by mutableStateOf(Offset.Zero)
    var scale by mutableStateOf(0f)
    var fitted by mutableStateOf(false)
    val userOffsets = mutableStateMapOf<String, Offset>()
    val expandOrder = mutableStateListOf<String>()
    var expandedDirs by mutableStateOf<Set<String>?>(null)
    var filter by mutableStateOf(MapFilterState())
    var focusCenterId by mutableStateOf<String?>(null)

    fun reset() {
        focusCenterId = null
        pan = Offset.Zero
        scale = 0f
        fitted = false
        userOffsets.clear()
        expandOrder.clear()
        expandedDirs = null
        filter = MapFilterState()
    }
}
