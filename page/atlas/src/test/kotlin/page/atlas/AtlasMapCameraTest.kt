package page.atlas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import page.atlas.render.MapBox
import page.atlas.render.minimalPanInto

class AtlasMapCameraTest {

    private fun box(x: Float, y: Float, w: Float, h: Float): MapBox = MapBox(
        id = "d",
        label = "d",
        folder = true,
        expanded = true,
        fileCount = 1,
        depth = 0,
        path = null,
        active = false,
        activeTrail = false,
        x = x,
        y = y,
        w = w,
        h = h,
    )

    private val canvas = IntSize(800, 600)

    @Test
    fun `box already in view needs no pan`() {
        assertNull(minimalPanInto(Offset.Zero, box(100f, 100f, 200f, 120f), 1f, canvas))
    }

    @Test
    fun `box past right edge pulls left minimally`() {
        val result = minimalPanInto(Offset.Zero, box(700f, 100f, 200f, 120f), 1f, canvas)
        assertEquals(Offset(776f - 900f, 0f), result)
    }

    @Test
    fun `box above top pulls down minimally`() {
        val result = minimalPanInto(Offset.Zero, box(100f, -50f, 200f, 120f), 1f, canvas)
        assertEquals(Offset(0f, 74f), result)
    }

    @Test
    fun `box larger than viewport aligns top-left to margin`() {
        val result = minimalPanInto(Offset.Zero, box(0f, 0f, 900f, 700f), 1f, canvas)
        assertEquals(Offset(24f, 24f), result)
    }

    @Test
    fun `scale is applied to box geometry`() {
        val result = minimalPanInto(Offset.Zero, box(390f, 100f, 100f, 100f), 2f, canvas)
        assertTrue(result != null && result.x < 0f && result.y == 0f)
    }

    @Test
    fun `zero canvas yields no pan`() {
        assertNull(minimalPanInto(Offset.Zero, box(100f, 100f, 200f, 120f), 1f, IntSize.Zero))
    }
}
