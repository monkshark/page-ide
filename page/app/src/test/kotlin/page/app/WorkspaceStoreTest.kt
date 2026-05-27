package page.app

import page.runtime.*

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceStoreTest {

    private fun newWorkspace(): Path {
        val dir = Files.createTempDirectory("page-ide-workspace-test-")
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        })
        return dir
    }

    @Test
    fun `load on fresh workspace returns defaults`() {
        val ws = newWorkspace()
        val file = WorkspaceStore.load(ws)
        assertEquals(1, file.version)
        assertNull(file.palette)
    }

    @Test
    fun `save then load round-trips palette`() {
        val ws = newWorkspace()
        WorkspaceStore.save(ws, WorkspaceFile(palette = "Warm"))
        val loaded = WorkspaceStore.load(ws)
        assertEquals("Warm", loaded.palette)
    }

    @Test
    fun `save creates workspace json file`() {
        val ws = newWorkspace()
        WorkspaceStore.save(ws, WorkspaceFile(palette = "Cool"))
        assertTrue(Files.exists(ws.resolve(PageIdeStore.DIR_NAME).resolve(WorkspaceStore.FILE_NAME)))
    }
}
