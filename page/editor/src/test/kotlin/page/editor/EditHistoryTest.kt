package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EditHistoryTest {
    private fun snap(text: String, caret: Int = text.length) = EditSnapshot(text, caret)

    @Test
    fun `empty history undo returns null`() {
        assertNull(EditHistory().undo(snap("a")))
    }

    @Test
    fun `push then undo restores prior snapshot`() {
        val h = EditHistory().pushBeforeChange(snap("a"))
        val result = h.undo(snap("ab"))
        assertNotNull(result)
        val (newHist, restored) = result
        assertEquals(snap("a"), restored)
        assertEquals(0, newHist.past.size)
        assertEquals(1, newHist.future.size)
        assertEquals(snap("ab"), newHist.future.first())
    }

    @Test
    fun `redo replays after undo`() {
        val h = EditHistory().pushBeforeChange(snap("a"))
        val (afterUndo, _) = h.undo(snap("ab"))!!
        val (afterRedo, restored) = afterUndo.redo(snap("a"))!!
        assertEquals(snap("ab"), restored)
        assertEquals(1, afterRedo.past.size)
        assertEquals(0, afterRedo.future.size)
    }

    @Test
    fun `new edit clears future stack`() {
        var h = EditHistory().pushBeforeChange(snap("a"))
        h = h.undo(snap("ab"))!!.first
        assertEquals(1, h.future.size)
        h = h.pushBeforeChange(snap("ac"))
        assertEquals(0, h.future.size)
    }

    @Test
    fun `consecutive pushes keep granularity`() {
        var h = EditHistory()
        h = h.pushBeforeChange(snap(""))
        h = h.pushBeforeChange(snap("a"))
        h = h.pushBeforeChange(snap("ab"))
        assertEquals(3, h.past.size)
    }

    @Test
    fun `duplicate push is collapsed`() {
        var h = EditHistory().pushBeforeChange(snap("a"))
        h = h.pushBeforeChange(snap("a"))
        assertEquals(1, h.past.size)
    }

    @Test
    fun `stack is capped at max size`() {
        var h = EditHistory()
        for (i in 0..1100) {
            h = h.pushBeforeChange(snap("v$i"))
        }
        assertEquals(EditHistory.MAX_SIZE, h.past.size)
        assertEquals(snap("v1100"), h.past.last())
        assertEquals(snap("v101"), h.past.first())
    }

    @Test
    fun `undo across multiple snapshots steps back one at a time`() {
        var h = EditHistory()
        h = h.pushBeforeChange(snap(""))
        h = h.pushBeforeChange(snap("a"))
        h = h.pushBeforeChange(snap("ab"))

        val (h1, s1) = h.undo(snap("abc"))!!
        assertEquals(snap("ab"), s1)
        val (h2, s2) = h1.undo(s1)!!
        assertEquals(snap("a"), s2)
        val (h3, s3) = h2.undo(s2)!!
        assertEquals(snap(""), s3)
        assertNull(h3.undo(s3))
    }
}
