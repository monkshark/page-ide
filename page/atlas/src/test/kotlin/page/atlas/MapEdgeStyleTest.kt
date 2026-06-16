package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.render.mapEdgeBaseAlpha
import page.atlas.render.mapEdgeDegreeFade

class MapEdgeStyleTest {

    @Test
    fun `sparse maps draw edges at full strength`() {
        assertEquals(0.8f, mapEdgeBaseAlpha(0))
        assertEquals(0.8f, mapEdgeBaseAlpha(12))
    }

    @Test
    fun `alpha fades as the edge count grows`() {
        val mid = mapEdgeBaseAlpha(36)
        assertTrue(mid < mapEdgeBaseAlpha(12))
        assertTrue(mid > mapEdgeBaseAlpha(60))
    }

    @Test
    fun `dense maps clamp at the floor`() {
        assertEquals(0.3f, mapEdgeBaseAlpha(60))
        assertEquals(0.3f, mapEdgeBaseAlpha(500))
    }

    @Test
    fun `leaf edges fade more than hub edges`() {
        assertTrue(mapEdgeDegreeFade(1) < mapEdgeDegreeFade(3))
        assertTrue(mapEdgeDegreeFade(3) < mapEdgeDegreeFade(10))
    }

    @Test
    fun `well connected endpoints keep full strength`() {
        assertEquals(1f, mapEdgeDegreeFade(5))
        assertEquals(1f, mapEdgeDegreeFade(100))
    }
}
