package page.atlas.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

internal fun revealExploration(camera: MapViewState, targets: List<Rect>, viewport: IntSize) {
    if (targets.isEmpty() || camera.scale <= 0f || viewport.width <= 0 || viewport.height <= 0) return
    val bounds = Rect(targets.minOf { it.left } - 16f, targets.minOf { it.top } - 30f,
        targets.maxOf { it.right } + 16f, targets.maxOf { it.bottom } + 46f)
    val margin = 20f
    val width = (viewport.width - margin * 2).coerceAtLeast(1f)
    val height = (viewport.height - margin * 2).coerceAtLeast(1f)
    val scale = minOf(camera.scale, width / bounds.width, height / bounds.height).coerceAtLeast(.12f)
    val pan = camera.pan + bounds.center * (camera.scale - scale)
    fun shift(start: Float, end: Float, limit: Float): Float = when {
        end - start > limit - margin * 2 -> limit / 2f - (start + end) / 2f
        start < margin -> margin - start
        end > limit - margin -> limit - margin - end
        else -> 0f
    }
    camera.pan = pan + Offset(
        shift(bounds.left * scale + pan.x, bounds.right * scale + pan.x, viewport.width.toFloat()),
        shift(bounds.top * scale + pan.y, bounds.bottom * scale + pan.y, viewport.height.toFloat()),
    )
    camera.scale = scale
}
