package page.app.filetree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileTreeWatcherControllerTest {

    @Test
    fun `withClosed runs the block when no watcher is held`() {
        val controller = FileTreeWatcherController()
        var ran = false
        controller.withClosed { ran = true }
        assertTrue(ran)
    }

    @Test
    fun `withClosed still runs the block when it throws and rethrows`() {
        val controller = FileTreeWatcherController()
        var ran = false
        var thrown: IllegalStateException? = null
        try {
            controller.withClosed {
                ran = true
                throw IllegalStateException("boom")
            }
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertTrue(ran)
        assertEquals("boom", thrown?.message)
    }
}
