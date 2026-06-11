package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.render.MapBox
import page.atlas.render.traceEdgeKeys

class MapTraceTest {

    private fun box(id: String, folder: Boolean = false, depth: Int = 1) = MapBox(
        id = id,
        label = id.substringAfterLast('/'),
        folder = folder,
        expanded = false,
        fileCount = if (folder) 2 else 0,
        depth = depth,
        path = null,
        active = false,
        activeTrail = false,
        x = 0f,
        y = 0f,
        w = 10f,
        h = 10f,
    )

    @Test
    fun `visible file pairs map to themselves`() {
        val boxes = listOf(box("/ws/A.kt"), box("/ws/B.kt"), box("/ws/C.kt"))
        val keys = traceEdgeKeys(boxes, listOf("/ws/A.kt", "/ws/B.kt", "/ws/C.kt"))
        assertEquals(
            setOf("/ws/A.kt" to "/ws/B.kt", "/ws/B.kt" to "/ws/C.kt"),
            keys,
        )
    }

    @Test
    fun `file inside collapsed folder maps to folder box`() {
        val boxes = listOf(box("/ws/A.kt"), box("/ws/lib", folder = true))
        val keys = traceEdgeKeys(boxes, listOf("/ws/A.kt", "/ws/lib/B.kt"))
        assertEquals(setOf("/ws/A.kt" to "/ws/lib"), keys)
    }

    @Test
    fun `pairs inside the same collapsed folder are skipped`() {
        val boxes = listOf(box("/ws/lib", folder = true), box("/ws/C.kt"))
        val keys = traceEdgeKeys(boxes, listOf("/ws/lib/A.kt", "/ws/lib/B.kt", "/ws/C.kt"))
        assertEquals(setOf("/ws/lib" to "/ws/C.kt"), keys)
    }

    @Test
    fun `deepest visible ancestor wins`() {
        val boxes = listOf(
            box("/ws", folder = true, depth = 0),
            box("/ws/lib", folder = true, depth = 1),
            box("/ws/A.kt", depth = 1),
        )
        val keys = traceEdgeKeys(boxes, listOf("/ws/A.kt", "/ws/lib/inner/B.kt"))
        assertEquals(setOf("/ws/A.kt" to "/ws/lib"), keys)
    }

    @Test
    fun `short or empty path yields no keys`() {
        val boxes = listOf(box("/ws/A.kt"))
        assertTrue(traceEdgeKeys(boxes, emptyList()).isEmpty())
        assertTrue(traceEdgeKeys(boxes, listOf("/ws/A.kt")).isEmpty())
    }
}
