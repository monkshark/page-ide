package page.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryStoreTest {

    private fun newWorkspace(): Path {
        val dir = Files.createTempDirectory("page-ide-history-test-")
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        })
        return dir
    }

    @Test
    fun `load on fresh workspace returns empty file`() {
        val ws = newWorkspace()
        val loaded = HistoryStore.load(ws)
        assertEquals(1, loaded.version)
        assertTrue(loaded.recentFiles.isEmpty())
        assertTrue(loaded.searchHistory.isEmpty())
    }

    @Test
    fun `save then load round-trips`() {
        val ws = newWorkspace()
        val file = HistoryFile(
            recentFiles = listOf("a.kt", "b.kt"),
            searchHistory = listOf("foo", "bar"),
            replaceHistory = listOf("baz"),
        )
        HistoryStore.save(ws, file)
        val loaded = HistoryStore.load(ws)
        assertEquals(listOf("a.kt", "b.kt"), loaded.recentFiles)
        assertEquals(listOf("foo", "bar"), loaded.searchHistory)
        assertEquals(listOf("baz"), loaded.replaceHistory)
    }

    @Test
    fun `pushMru moves existing entry to front`() {
        val result = pushMru(listOf("a", "b", "c"), "b", 10)
        assertEquals(listOf("b", "a", "c"), result)
    }

    @Test
    fun `pushMru inserts new entry at front`() {
        val result = pushMru(listOf("a", "b"), "c", 10)
        assertEquals(listOf("c", "a", "b"), result)
    }

    @Test
    fun `pushMru caps at max length`() {
        val result = pushMru(listOf("a", "b", "c"), "d", 3)
        assertEquals(listOf("d", "a", "b"), result)
    }

    @Test
    fun `pushMru ignores blank values`() {
        val result = pushMru(listOf("a", "b"), "", 10)
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `pushMru dedup handles same value at front`() {
        val result = pushMru(listOf("a", "b"), "a", 10)
        assertEquals(listOf("a", "b"), result)
    }
}
