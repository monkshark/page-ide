package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RunConfigStoreTest {

    private fun newWorkspace(): Path {
        val dir = Files.createTempDirectory("page-ide-run-config-test-")
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        })
        return dir
    }

    @Test
    fun `load returns empty state on fresh workspace`() {
        val ws = newWorkspace()
        val state = RunConfigStore.load(ws)
        assertTrue(state.configs.isEmpty())
    }

    @Test
    fun `save then load round-trips`() {
        val ws = newWorkspace()
        val state = RunConfigsState(
            configs = listOf(
                RunConfig(id = "py", name = "Python · main.py", command = "python", args = listOf("main.py")),
                RunConfig(
                    id = "node",
                    name = "Node",
                    command = "node",
                    args = listOf("server.js"),
                    workingDir = "/tmp/proj",
                    env = mapOf("PORT" to "8080"),
                ),
            ),
            activeId = "node",
        )
        RunConfigStore.save(ws, state)
        val loaded = RunConfigStore.load(ws)
        assertEquals(2, loaded.configs.size)
        assertEquals("node", loaded.activeId)
        val node = loaded.configs.first { it.id == "node" }
        assertEquals("8080", node.env["PORT"])
        assertEquals("/tmp/proj", node.workingDir)
        assertEquals(listOf("server.js"), node.args)
    }

    @Test
    fun `save creates page-ide dir and run-configs file`() {
        val ws = newWorkspace()
        RunConfigStore.save(ws, RunConfigsState())
        assertTrue(Files.exists(ws.resolve(PageIdeStore.DIR_NAME)))
        assertTrue(Files.exists(ws.resolve(PageIdeStore.DIR_NAME).resolve(RunConfigStore.FILE_NAME)))
    }

    @Test
    fun `load drops configs with blank id or command`() {
        val ws = newWorkspace()
        val state = RunConfigsState(
            configs = listOf(
                RunConfig(id = "", name = "bad", command = "x"),
                RunConfig(id = "ok", name = "ok", command = ""),
                RunConfig(id = "real", name = "real", command = "ls"),
            ),
            activeId = "ok",
        )
        RunConfigStore.save(ws, state)
        val loaded = RunConfigStore.load(ws)
        assertEquals(1, loaded.configs.size)
        assertEquals("real", loaded.configs[0].id)
        assertEquals("real", loaded.activeId)
    }

    @Test
    fun `load falls back to first config when active is invalid`() {
        val ws = newWorkspace()
        val state = RunConfigsState(
            configs = listOf(
                RunConfig(id = "a", name = "a", command = "x"),
                RunConfig(id = "b", name = "b", command = "y"),
            ),
            activeId = "ghost",
        )
        RunConfigStore.save(ws, state)
        val loaded = RunConfigStore.load(ws)
        assertEquals("a", loaded.activeId)
    }

    @Test
    fun `load preserves CURRENT_FILE_ID active across save round-trip`() {
        val ws = newWorkspace()
        val state = RunConfigsState(
            configs = listOf(RunConfig(id = "a", name = "a", command = "ls")),
            activeId = CURRENT_FILE_ID,
        )
        RunConfigStore.save(ws, state)
        val loaded = RunConfigStore.load(ws)
        assertEquals(CURRENT_FILE_ID, loaded.activeId)
        assertEquals(1, loaded.configs.size)
    }

    @Test
    fun `load preserves CURRENT_FILE_ID even with no saved configs`() {
        val ws = newWorkspace()
        RunConfigStore.save(ws, RunConfigsState(activeId = CURRENT_FILE_ID))
        val loaded = RunConfigStore.load(ws)
        assertEquals(CURRENT_FILE_ID, loaded.activeId)
        assertTrue(loaded.configs.isEmpty())
    }
}
