package page.atlas

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import page.atlas.render.MapFilterState
import page.atlas.render.MapViewState

class MapViewStateTest {

    @Test
    fun `fresh view starts unfitted with no filter`() {
        val view = MapViewState()
        assertEquals(Offset.Zero, view.pan)
        assertEquals(0f, view.scale)
        assertFalse(view.fitted)
        assertTrue(view.userOffsets.isEmpty())
        assertTrue(view.expandOrder.isEmpty())
        assertNull(view.expandedDirs)
        assertFalse(view.filter.active)
    }

    @Test
    fun `reset returns every view field to its initial state`() {
        val view = MapViewState()
        view.pan = Offset(120f, -40f)
        view.scale = 2.5f
        view.fitted = true
        view.userOffsets["a"] = Offset(10f, 10f)
        view.expandOrder.add("dir")
        view.expandedDirs = setOf("dir")
        view.filter = MapFilterState(focusDir = "dir", hiddenDirs = setOf("x"), mutedDirs = setOf("y"))

        view.reset()

        assertEquals(Offset.Zero, view.pan)
        assertEquals(0f, view.scale)
        assertFalse(view.fitted)
        assertTrue(view.userOffsets.isEmpty())
        assertTrue(view.expandOrder.isEmpty())
        assertNull(view.expandedDirs)
        assertFalse(view.filter.active)
    }
}
