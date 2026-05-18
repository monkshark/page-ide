package page.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class TerminalManagerTest {

    private lateinit var workspace: java.nio.file.Path
    private val scope = CoroutineScope(SupervisorJob())

    @BeforeTest
    fun setup() {
        workspace = Files.createTempDirectory("term-manager-test")
    }

    @AfterTest
    fun teardown() {
        scope.cancel()
    }

    @Test
    fun `newTab adds tab and makes active`() {
        val mgr = TerminalManager(workspace, scope)
        val tab = mgr.newTab(autoStart = false)
        assertEquals(1, mgr.tabs.size)
        assertEquals(tab.id, mgr.activeId)
        assertSame(tab, mgr.activeTab)
    }

    @Test
    fun `newTab assigns sequential default names`() {
        val mgr = TerminalManager(workspace, scope)
        val t1 = mgr.newTab(autoStart = false)
        val t2 = mgr.newTab(autoStart = false)
        assertEquals("Terminal 1", t1.name)
        assertEquals("Terminal 2", t2.name)
        assertNotEquals(t1.id, t2.id)
    }

    @Test
    fun `newTab honors explicit name`() {
        val mgr = TerminalManager(workspace, scope)
        val tab = mgr.newTab(name = "빌드", autoStart = false)
        assertEquals("빌드", tab.name)
    }

    @Test
    fun `newTab with blank explicit name falls back to default`() {
        val mgr = TerminalManager(workspace, scope)
        val tab = mgr.newTab(name = "   ", autoStart = false)
        assertEquals("Terminal 1", tab.name)
    }

    @Test
    fun `selectTab changes active`() {
        val mgr = TerminalManager(workspace, scope)
        val t1 = mgr.newTab(autoStart = false)
        mgr.newTab(autoStart = false)
        mgr.selectTab(t1.id)
        assertEquals(t1.id, mgr.activeId)
    }

    @Test
    fun `selectTab ignores unknown id`() {
        val mgr = TerminalManager(workspace, scope)
        val t1 = mgr.newTab(autoStart = false)
        mgr.selectTab("nope")
        assertEquals(t1.id, mgr.activeId)
    }

    @Test
    fun `closeTab removes tab and advances active to neighbor`() {
        val mgr = TerminalManager(workspace, scope)
        val t1 = mgr.newTab(autoStart = false)
        val t2 = mgr.newTab(autoStart = false)
        val t3 = mgr.newTab(autoStart = false)
        mgr.selectTab(t2.id)
        mgr.closeTab(t2.id)
        assertEquals(2, mgr.tabs.size)
        assertEquals(t3.id, mgr.activeId)
        assertEquals(listOf(t1.id, t3.id), mgr.tabs.map { it.id })
    }

    @Test
    fun `closeTab on rightmost falls back to previous`() {
        val mgr = TerminalManager(workspace, scope)
        val t1 = mgr.newTab(autoStart = false)
        val t2 = mgr.newTab(autoStart = false)
        mgr.closeTab(t2.id)
        assertEquals(t1.id, mgr.activeId)
    }

    @Test
    fun `closing last tab clears active`() {
        val mgr = TerminalManager(workspace, scope)
        val t1 = mgr.newTab(autoStart = false)
        mgr.closeTab(t1.id)
        assertEquals(0, mgr.tabs.size)
        assertNull(mgr.activeId)
    }

    @Test
    fun `closing inactive tab keeps active untouched`() {
        val mgr = TerminalManager(workspace, scope)
        val t1 = mgr.newTab(autoStart = false)
        val t2 = mgr.newTab(autoStart = false)
        mgr.selectTab(t1.id)
        mgr.closeTab(t2.id)
        assertEquals(t1.id, mgr.activeId)
    }

    @Test
    fun `renameTab updates name`() {
        val mgr = TerminalManager(workspace, scope)
        val tab = mgr.newTab(autoStart = false)
        mgr.renameTab(tab.id, "배포")
        assertEquals("배포", tab.name)
    }

    @Test
    fun `renameTab trims and falls back to default on blank`() {
        val mgr = TerminalManager(workspace, scope)
        val tab = mgr.newTab(autoStart = false)
        mgr.renameTab(tab.id, "   ")
        assertEquals("Terminal", tab.name)
    }

    @Test
    fun `closeAll empties tabs and active`() {
        val mgr = TerminalManager(workspace, scope)
        mgr.newTab(autoStart = false)
        mgr.newTab(autoStart = false)
        mgr.closeAll()
        assertEquals(0, mgr.tabs.size)
        assertNull(mgr.activeId)
    }

    @Test
    fun `snapshotNames returns tab names in order`() {
        val mgr = TerminalManager(workspace, scope)
        mgr.newTab(name = "빌드", autoStart = false)
        mgr.newTab(name = "테스트", autoStart = false)
        assertEquals(listOf("빌드", "테스트"), mgr.snapshotNames())
    }

    @Test
    fun `activeIndex matches active tab position`() {
        val mgr = TerminalManager(workspace, scope)
        val t1 = mgr.newTab(autoStart = false)
        mgr.newTab(autoStart = false)
        mgr.selectTab(t1.id)
        assertEquals(0, mgr.activeIndex())
    }

    @Test
    fun `activeIndex returns -1 when no tabs`() {
        val mgr = TerminalManager(workspace, scope)
        assertEquals(-1, mgr.activeIndex())
    }

    @Test
    fun `restoreFrom creates tabs with given names`() {
        val mgr = TerminalManager(workspace, scope)
        mgr.restoreFrom(listOf("A", "B", "C"), activeIndex = 1, autoStart = false)
        assertEquals(3, mgr.tabs.size)
        assertEquals(listOf("A", "B", "C"), mgr.tabs.map { it.name })
        assertEquals(mgr.tabs[1].id, mgr.activeId)
    }

    @Test
    fun `restoreFrom skips when tabs already exist`() {
        val mgr = TerminalManager(workspace, scope)
        mgr.newTab(name = "기존", autoStart = false)
        mgr.restoreFrom(listOf("새로", "탭"), activeIndex = 0, autoStart = false)
        assertEquals(1, mgr.tabs.size)
        assertEquals("기존", mgr.tabs[0].name)
    }

    @Test
    fun `restoreFrom with empty names is a no-op`() {
        val mgr = TerminalManager(workspace, scope)
        mgr.restoreFrom(emptyList(), activeIndex = -1, autoStart = false)
        assertEquals(0, mgr.tabs.size)
        assertNull(mgr.activeId)
    }

    @Test
    fun `restoreFrom clamps invalid activeIndex by leaving last-created active`() {
        val mgr = TerminalManager(workspace, scope)
        mgr.restoreFrom(listOf("A", "B"), activeIndex = 99, autoStart = false)
        assertEquals(2, mgr.tabs.size)
        assertEquals(mgr.tabs[1].id, mgr.activeId)
    }
}
