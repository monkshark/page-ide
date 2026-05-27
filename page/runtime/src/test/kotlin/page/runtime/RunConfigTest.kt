package page.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunConfigTest {

    private fun cfg(id: String, name: String = id, command: String = "echo") =
        RunConfig(id = id, name = name, command = command, args = listOf(id))

    @Test
    fun `isRunnable false when command is blank`() {
        assertFalse(RunConfig(id = "a", name = "a", command = "").isRunnable())
        assertFalse(RunConfig(id = "a", name = "a", command = "   ").isRunnable())
        assertTrue(RunConfig(id = "a", name = "a", command = "ls").isRunnable())
    }

    @Test
    fun `add first config makes it active`() {
        val s = RunConfigsState().add(cfg("a"))
        assertEquals("a", s.activeId)
        assertEquals(1, s.configs.size)
    }

    @Test
    fun `add second config keeps active`() {
        val s = RunConfigsState().add(cfg("a")).add(cfg("b"))
        assertEquals("a", s.activeId)
        assertEquals(2, s.configs.size)
    }

    @Test
    fun `add ignores duplicate id`() {
        val s = RunConfigsState().add(cfg("a")).add(cfg("a", name = "again"))
        assertEquals(1, s.configs.size)
        assertEquals("a", s.configs[0].name)
    }

    @Test
    fun `select switches active when id exists`() {
        val s = RunConfigsState().add(cfg("a")).add(cfg("b")).select("b")
        assertEquals("b", s.activeId)
    }

    @Test
    fun `select ignored when id unknown`() {
        val s = RunConfigsState().add(cfg("a")).select("zzz")
        assertEquals("a", s.activeId)
    }

    @Test
    fun `update replaces by id`() {
        val s = RunConfigsState()
            .add(cfg("a"))
            .update(RunConfig(id = "a", name = "renamed", command = "python"))
        assertEquals("renamed", s.active?.name)
        assertEquals("python", s.active?.command)
    }

    @Test
    fun `update ignores unknown id`() {
        val s = RunConfigsState()
            .add(cfg("a"))
            .update(RunConfig(id = "ghost", name = "x", command = "y"))
        assertEquals("a", s.active?.name)
    }

    @Test
    fun `remove active picks first remaining`() {
        val s = RunConfigsState().add(cfg("a")).add(cfg("b")).remove("a")
        assertEquals("b", s.activeId)
        assertEquals(1, s.configs.size)
    }

    @Test
    fun `remove last clears active`() {
        val s = RunConfigsState().add(cfg("a")).remove("a")
        assertNull(s.activeId)
        assertTrue(s.configs.isEmpty())
    }

    @Test
    fun `remove inactive keeps active`() {
        val s = RunConfigsState().add(cfg("a")).add(cfg("b")).remove("b")
        assertEquals("a", s.activeId)
        assertEquals(1, s.configs.size)
    }

    @Test
    fun `active returns null on empty state`() {
        assertNull(RunConfigsState().active)
    }

    @Test
    fun `select CURRENT_FILE_ID works on empty state`() {
        val s = RunConfigsState().select(CURRENT_FILE_ID)
        assertEquals(CURRENT_FILE_ID, s.activeId)
        assertTrue(s.isCurrentFileActive)
        assertNull(s.active)
    }

    @Test
    fun `select CURRENT_FILE_ID switches from saved config`() {
        val s = RunConfigsState().add(cfg("a")).select(CURRENT_FILE_ID)
        assertEquals(CURRENT_FILE_ID, s.activeId)
        assertTrue(s.isCurrentFileActive)
        assertEquals(1, s.configs.size)
    }

    @Test
    fun `select existing id switches back from CURRENT_FILE_ID`() {
        val s = RunConfigsState().add(cfg("a")).select(CURRENT_FILE_ID).select("a")
        assertEquals("a", s.activeId)
        assertFalse(s.isCurrentFileActive)
    }

    @Test
    fun `isCurrentFileActive false by default`() {
        assertFalse(RunConfigsState().isCurrentFileActive)
        assertFalse(RunConfigsState().add(cfg("a")).isCurrentFileActive)
    }

    @Test
    fun `add does not override CURRENT_FILE_ID active`() {
        val s = RunConfigsState().select(CURRENT_FILE_ID).add(cfg("a"))
        assertEquals(CURRENT_FILE_ID, s.activeId)
        assertEquals(1, s.configs.size)
    }
}
