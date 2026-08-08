package page.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentProjectsTest {

    private lateinit var dir: Path
    private var prevDir: String? = null

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("page-recent-")
        prevDir = System.getProperty("page.settings.dir")
        System.setProperty("page.settings.dir", dir.toString())
    }

    @AfterTest
    fun tearDown() {
        if (prevDir != null) System.setProperty("page.settings.dir", prevDir) else System.clearProperty("page.settings.dir")
        dir.toFile().deleteRecursively()
    }

    private fun project(name: String): Path = Files.createDirectory(dir.resolve(name))

    @Test
    fun emptyWhenNothingPushed() {
        assertEquals(emptyList(), RecentProjects.load())
    }

    @Test
    fun pushPutsMostRecentFirst() {
        val a = project("a")
        val b = project("b")
        RecentProjects.push(a)
        RecentProjects.push(b)
        assertEquals(listOf(b, a), RecentProjects.load())
    }

    @Test
    fun repushMovesToFrontWithoutDuplicating() {
        val a = project("a")
        val b = project("b")
        RecentProjects.push(a)
        RecentProjects.push(b)
        RecentProjects.push(a)
        assertEquals(listOf(a, b), RecentProjects.load())
    }

    @Test
    fun cappedAtMax() {
        val projects = (0 until RecentProjects.MAX + 3).map { project("p$it") }
        projects.forEach { RecentProjects.push(it) }
        val loaded = RecentProjects.load()
        assertEquals(RecentProjects.MAX, loaded.size)
        assertEquals(projects.last(), loaded.first())
        assertTrue(loaded.none { it == projects.first() })
    }

    @Test
    fun removeDropsOnlyThatProject() {
        val a = project("a")
        val b = project("b")
        RecentProjects.push(a)
        RecentProjects.push(b)
        RecentProjects.remove(b)
        assertEquals(listOf(a), RecentProjects.load())
    }

    @Test
    fun removeIgnoresProjectsNotListed() {
        val a = project("a")
        val b = project("b")
        RecentProjects.push(a)
        RecentProjects.remove(b)
        assertEquals(listOf(a), RecentProjects.load())
    }

    @Test
    fun clearEmptiesTheList() {
        RecentProjects.push(project("a"))
        RecentProjects.push(project("b"))
        RecentProjects.clear()
        assertEquals(emptyList(), RecentProjects.load())
    }

    @Test
    fun missingDirectoriesAreDropped() {
        val a = project("a")
        val b = project("b")
        RecentProjects.push(a)
        RecentProjects.push(b)
        b.toFile().deleteRecursively()
        assertEquals(listOf(a), RecentProjects.load())
    }
}
