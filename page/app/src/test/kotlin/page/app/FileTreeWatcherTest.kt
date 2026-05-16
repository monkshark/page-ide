package page.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileTreeWatcherTest {

    private fun newDir(): Path {
        val dir = Files.createTempDirectory("page-ide-watcher-test-")
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        })
        return dir
    }

    @Test
    fun `watchableDirs returns empty for null root`() {
        assertEquals(emptySet(), watchableDirs(null, emptySet()))
    }

    @Test
    fun `watchableDirs includes existing root directory`() {
        val root = newDir()
        val result = watchableDirs(root, emptySet())
        assertTrue(root in result)
    }

    @Test
    fun `watchableDirs includes expanded subdirectories`() {
        val root = newDir()
        val sub = Files.createDirectory(root.resolve("sub"))
        val result = watchableDirs(root, setOf(sub))
        assertTrue(root in result)
        assertTrue(sub in result)
    }

    @Test
    fun `watchableDirs skips non-directory expanded paths`() {
        val root = newDir()
        val file = Files.createFile(root.resolve("a.txt"))
        val result = watchableDirs(root, setOf(file))
        assertTrue(root in result)
        assertTrue(file !in result)
    }

    @Test
    fun `watchableDirs handles missing expanded paths`() {
        val root = newDir()
        val missing = root.resolve("ghost")
        val result = watchableDirs(root, setOf(missing))
        assertTrue(root in result)
        assertTrue(missing !in result)
    }

    @Test
    fun `FileTreeWatcher is inactive for empty dirs`() {
        val watcher = FileTreeWatcher(emptySet())
        assertTrue(!watcher.active)
        watcher.close()
    }

    @Test
    fun `FileTreeWatcher activates for real directory`() {
        val root = newDir()
        val watcher = FileTreeWatcher(setOf(root))
        assertTrue(watcher.active)
        watcher.close()
    }
}
