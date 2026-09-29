package page.atlas.render

import page.atlas.graph.MapPart
import page.atlas.graph.ProjectMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectMapLayoutTest {

    private fun part(id: String, layer: Int, files: Int = 1) =
        MapPart(id, id, "", null, List(files) { java.nio.file.Path.of("$id$it.kt") }, layer)

    @Test
    fun `what nothing relies on is drawn on the top row`() {
        val map = ProjectMap("p", listOf(part("app", 2), part("feature", 1), part("core", 0)), emptyList())
        val placed = ProjectMapLayout.place(map)
        assertEquals(0, placed.parts.getValue("app").row)
        assertEquals(2, placed.parts.getValue("core").row)
        assertTrue(placed.parts.getValue("app").y < placed.parts.getValue("core").y)
    }

    @Test
    fun `boxes in a row never overlap and a short row is centred`() {
        val map = ProjectMap("p", listOf(part("a", 1), part("b", 0, 3), part("c", 0, 2), part("d", 0)), emptyList())
        val placed = ProjectMapLayout.place(map)
        val bottom = listOf("b", "c", "d").map { placed.parts.getValue(it) }.sortedBy { it.x }
        bottom.zipWithNext().forEach { (l, r) -> assertTrue(r.x - l.x >= ProjectMapLayout.BOX_W) }
        val a = placed.parts.getValue("a")
        assertEquals(placed.width / 2f, a.x + ProjectMapLayout.BOX_W / 2f, 0.01f)
    }

    @Test
    fun `bigger parts come first in their row`() {
        val map = ProjectMap("p", listOf(part("small", 0, 1), part("big", 0, 9)), emptyList())
        val placed = ProjectMapLayout.place(map)
        assertTrue(placed.parts.getValue("big").x < placed.parts.getValue("small").x)
    }

    @Test
    fun `the canvas is big enough for every box`() {
        val map = ProjectMap("p", listOf(part("a", 1), part("b", 0), part("c", 0)), emptyList())
        val placed = ProjectMapLayout.place(map)
        placed.parts.values.forEach {
            assertTrue(it.x >= 0f && it.x + ProjectMapLayout.BOX_W <= placed.width)
            assertTrue(it.y >= 0f && it.y + ProjectMapLayout.BOX_H <= placed.height)
        }
    }

    @Test
    fun `an empty map places nothing`() {
        assertTrue(ProjectMapLayout.place(ProjectMap.EMPTY).parts.isEmpty())
    }
}
