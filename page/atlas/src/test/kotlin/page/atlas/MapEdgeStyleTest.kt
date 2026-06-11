package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.render.mapEdgeBaseAlpha

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
}
