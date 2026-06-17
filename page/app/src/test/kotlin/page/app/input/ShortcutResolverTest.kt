package page.app.input

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals

class ShortcutResolverTest {

    private fun resolve(
        key: Key,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        hasSearch: Boolean = false,
    ): ShortcutAction = ShortcutResolver.resolve(key, ctrl, alt, shift, hasSearch)

    @Test
    fun `ctrl alt T takes priority over the ctrl block`() {
        assertEquals(ShortcutAction.CYCLE_PALETTE, resolve(Key.T, ctrl = true, alt = true))
        assertEquals(ShortcutAction.OPEN_WORKSPACE_SYMBOL, resolve(Key.T, ctrl = true))
    }

    @Test
    fun `ctrl S variants distinguish settings from save by alt`() {
        assertEquals(ShortcutAction.SAVE, resolve(Key.S, ctrl = true))
        assertEquals(ShortcutAction.OPEN_SETTINGS, resolve(Key.S, ctrl = true, alt = true))
    }

    @Test
    fun `ctrl O and F distinguish shift variants`() {
        assertEquals(ShortcutAction.OPEN_FILE, resolve(Key.O, ctrl = true))
        assertEquals(ShortcutAction.OPEN_FOLDER, resolve(Key.O, ctrl = true, shift = true))
        assertEquals(ShortcutAction.OPEN_SEARCH, resolve(Key.F, ctrl = true))
        assertEquals(ShortcutAction.TOGGLE_FIND_IN_FILES, resolve(Key.F, ctrl = true, shift = true))
    }

    @Test
    fun `undo redo are suppressed while a search field is focused`() {
        assertEquals(ShortcutAction.UNDO, resolve(Key.Z, ctrl = true))
        assertEquals(ShortcutAction.REDO, resolve(Key.Z, ctrl = true, shift = true))
        assertEquals(ShortcutAction.REDO, resolve(Key.Y, ctrl = true))
        assertEquals(ShortcutAction.NONE, resolve(Key.Z, ctrl = true, hasSearch = true))
        assertEquals(ShortcutAction.NONE, resolve(Key.Z, ctrl = true, shift = true, hasSearch = true))
        assertEquals(ShortcutAction.NONE, resolve(Key.Y, ctrl = true, hasSearch = true))
    }

    @Test
    fun `ctrl block swallows unknown keys without falling through to alt branches`() {
        assertEquals(ShortcutAction.NONE, resolve(Key.Enter, ctrl = true, alt = true, shift = true))
    }

    @Test
    fun `alt enter resolves format or code action by shift and search state`() {
        assertEquals(ShortcutAction.FORMAT, resolve(Key.Enter, alt = true, shift = true))
        assertEquals(ShortcutAction.CODE_ACTION, resolve(Key.Enter, alt = true))
        assertEquals(ShortcutAction.NONE, resolve(Key.Enter, alt = true, hasSearch = true))
        assertEquals(ShortcutAction.CODE_ACTION, resolve(Key.NumPadEnter, alt = true))
    }

    @Test
    fun `alt arrows switch tabs only without a focused search`() {
        assertEquals(ShortcutAction.PREV_TAB, resolve(Key.DirectionLeft, alt = true))
        assertEquals(ShortcutAction.NEXT_TAB, resolve(Key.DirectionRight, alt = true))
        assertEquals(ShortcutAction.NONE, resolve(Key.DirectionLeft, alt = true, hasSearch = true))
    }

    @Test
    fun `F8 direction depends on shift`() {
        assertEquals(ShortcutAction.JUMP_PROBLEM_NEXT, resolve(Key.F8))
        assertEquals(ShortcutAction.JUMP_PROBLEM_PREV, resolve(Key.F8, shift = true))
    }

    @Test
    fun `escape closes search only when a search is focused`() {
        assertEquals(ShortcutAction.CLOSE_SEARCH, resolve(Key.Escape, hasSearch = true))
        assertEquals(ShortcutAction.NONE, resolve(Key.Escape))
    }

    @Test
    fun `F5 refreshes the tree`() {
        assertEquals(ShortcutAction.REFRESH_TREE, resolve(Key.F5))
    }

    @Test
    fun `ctrl alt A focuses the active file in Atlas without colliding with plain ctrl A`() {
        assertEquals(ShortcutAction.FOCUS_IN_ATLAS, resolve(Key.A, ctrl = true, alt = true))
        assertEquals(ShortcutAction.NONE, resolve(Key.A, ctrl = true))
        assertEquals(ShortcutAction.NONE, resolve(Key.A, alt = true))
    }
}
