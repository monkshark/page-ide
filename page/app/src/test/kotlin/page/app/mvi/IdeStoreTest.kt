package page.app.mvi

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IdeStoreTest {

    @Test
    fun `dispatcher writes reduced slice back to store`() {
        val store = IdeStore()
        val dispatcher = IdeDispatcher(store, IdeEffectHandler())

        dispatcher.onEvent(IdeEvent.Panel.ToggleProblems)

        Snapshot.sendApplyNotifications()
        assertTrue(store.layout.problemsOpen)
    }

    @Test
    fun `apply skips write when slice is value-equal`() {
        val store = IdeStore()
        val before = store.layout

        store.apply(AppState(before.copy()))

        assertSame(before, store.layout)
    }

    @Test
    fun `apply swaps slice when value differs`() {
        val store = IdeStore()
        val before = store.layout

        store.apply(AppState(before.copy(sidebarWidth = 320.dp)))

        assertFalse(before === store.layout)
        assertEquals(320.dp, store.layout.sidebarWidth)
    }
}
