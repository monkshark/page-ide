package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.render.VcsMark
import page.atlas.render.vcsFolderCounts

class VcsOverlayTest {

    @Test
    fun countsMarkedDescendantsPerFolder() {
        val marks = mapOf(
            "C:\\ws\\src\\a.kt" to VcsMark.MODIFIED,
            "C:\\ws\\src\\sub\\b.kt" to VcsMark.ADDED,
            "C:\\ws\\other\\c.kt" to VcsMark.MODIFIED,
        )
        val counts = vcsFolderCounts(marks, listOf("C:\\ws", "C:\\ws\\src", "C:\\ws\\src\\sub"))
        assertEquals(3, counts["C:\\ws"])
        assertEquals(2, counts["C:\\ws\\src"])
        assertEquals(1, counts["C:\\ws\\src\\sub"])
    }

    @Test
    fun foldersWithoutChangesAreOmitted() {
        val marks = mapOf("C:\\ws\\src\\a.kt" to VcsMark.MODIFIED)
        val counts = vcsFolderCounts(marks, listOf("C:\\ws\\src", "C:\\ws\\lib"))
        assertEquals(setOf("C:\\ws\\src"), counts.keys)
    }

    @Test
    fun prefixWithoutSeparatorDoesNotMatch() {
        val marks = mapOf("C:\\ws\\srcfile.kt" to VcsMark.MODIFIED)
        assertTrue(vcsFolderCounts(marks, listOf("C:\\ws\\src")).isEmpty())
    }

    @Test
    fun emptyMarksYieldEmptyCounts() {
        assertTrue(vcsFolderCounts(emptyMap(), listOf("C:\\ws")).isEmpty())
    }
}
