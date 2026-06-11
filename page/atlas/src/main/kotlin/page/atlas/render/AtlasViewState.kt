package page.atlas.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import page.atlas.graph.GraphSlice

class AtlasViewState {
    var yaw by mutableStateOf(0.6f)
    var pitch by mutableStateOf(0.5f)
    var zoomUser by mutableStateOf(1f)
    var selectedId by mutableStateOf<String?>(null)
    var lastInteractNanos = 0L
    private val rotationHolds = mutableStateMapOf<Any, Boolean>()
    private var lastFrameNanos = 0L
    private var sliceKey: GraphSlice? = null
    private var cameraKey: Pair<String?, Boolean>? = null

    fun onSliceChanged(slice: GraphSlice) {
        if (sliceKey == slice) return
        sliceKey = slice
        selectedId = null
    }

    fun onCameraSubject(activeId: String?, projectMode: Boolean) {
        val key = activeId to projectMode
        if (cameraKey == key) return
        cameraKey = key
        zoomUser = 1f
        pitch = 0.5f
    }

    fun holdRotation(owner: Any, hold: Boolean) {
        rotationHolds[owner] = hold
    }

    fun releaseRotation(owner: Any) {
        rotationHolds.remove(owner)
    }

    fun autoRotateTick(nowNanos: Long) {
        if (nowNanos == lastFrameNanos) return
        val previous = lastFrameNanos
        lastFrameNanos = nowNanos
        if (previous == 0L) return
        if (rotationHolds.values.any { it }) return
        if (nowNanos - lastInteractNanos <= 3_000_000_000L) return
        yaw += (nowNanos - previous) / 1_000_000_000f * 0.1f
    }
}
