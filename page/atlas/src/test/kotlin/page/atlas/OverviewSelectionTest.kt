package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import page.atlas.interaction.OverviewSelection
import page.atlas.interaction.OverviewSelection.Kind

class OverviewSelectionTest {

    @Test
    fun `select module sets module kind`() {
        val s = OverviewSelection.NONE.selectModule("a")
        assertEquals(Kind.MODULE, s.kind)
        assertEquals("a", s.moduleId)
    }

    @Test
    fun `clear resets to none but keeps drill level`() {
        val s = OverviewSelection.NONE.drillInto("page").selectModule("a").clear()
        assertEquals(Kind.NONE, s.kind)
        assertEquals(null, s.moduleId)
        assertEquals(listOf("page"), s.drillPath)
    }

    @Test
    fun `trace path requires a selected module and a distinct target`() {
        val base = OverviewSelection.NONE.selectModule("a")
        assertEquals(Kind.PATH, base.tracePath("b").kind)
        assertEquals("b", base.tracePath("b").pathTarget)
        assertEquals(Kind.MODULE, base.tracePath("a").kind)
        assertEquals(Kind.NONE, OverviewSelection.NONE.tracePath("b").kind)
    }

    @Test
    fun `clear path falls back to module selection`() {
        val s = OverviewSelection.NONE.selectModule("a").tracePath("b").clearPath()
        assertEquals(Kind.MODULE, s.kind)
        assertEquals(null, s.pathTarget)
    }

    @Test
    fun `drill push and pop walk the path stack`() {
        val pushed = OverviewSelection.NONE.drillInto("page").drillInto("page/app")
        assertEquals(listOf("page", "page/app"), pushed.drillPath)
        assertEquals(listOf("page"), pushed.drillUp().drillPath)
        assertEquals(emptyList(), pushed.drillUp().drillUp().drillPath)
        assertEquals(emptyList(), pushed.drillUpTo(0).drillPath)
    }

    @Test
    fun `select file sets file kind and id`() {
        val s = OverviewSelection.NONE.selectModule("a").selectFile("a/Foo.kt")
        assertEquals(Kind.FILE, s.kind)
        assertEquals("a/Foo.kt", s.fileId)
    }

    @Test
    fun `clear resets file selection`() {
        val s = OverviewSelection.NONE.selectFile("a/Foo.kt").clear()
        assertEquals(Kind.NONE, s.kind)
        assertEquals(null, s.fileId)
    }

    @Test
    fun `selecting a module drops a prior file selection`() {
        val s = OverviewSelection.NONE.selectFile("a/Foo.kt").selectModule("b")
        assertEquals(Kind.MODULE, s.kind)
        assertEquals(null, s.fileId)
    }

    @Test
    fun `drilling clears active selection`() {
        val s = OverviewSelection.NONE.selectModule("a").drillInto("page")
        assertEquals(Kind.NONE, s.kind)
        assertEquals(null, s.moduleId)
    }
}
