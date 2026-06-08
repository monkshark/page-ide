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

    @Test
    fun `dispatcher writes dialog slice back to store`() {
        val store = IdeStore()
        val dispatcher = IdeDispatcher(store, IdeEffectHandler())

        dispatcher.onEvent(IdeEvent.Dialog.OpenFindInFiles)

        Snapshot.sendApplyNotifications()
        assertTrue(store.dialogs.findInFilesOpen)
    }

    @Test
    fun `update dialogs skips write when value-equal`() {
        val store = IdeStore()
        val before = store.dialogs

        store.updateDialogs { it.copy() }

        assertSame(before, store.dialogs)
    }

    @Test
    fun `dispatcher writes code action slice back to store`() {
        val store = IdeStore()
        val dispatcher = IdeDispatcher(store, IdeEffectHandler())

        dispatcher.onEvent(IdeEvent.Internal.CodeActionsResult(emptyList(), null, null, 0, open = true))

        Snapshot.sendApplyNotifications()
        assertTrue(store.codeAction.open)
    }

    @Test
    fun `dispatcher writes references slice back to store`() {
        val store = IdeStore()
        val dispatcher = IdeDispatcher(store, IdeEffectHandler())
        val query = page.app.ReferencesQueryState(
            symbolName = "foo",
            originUri = "file:///x.kt",
            results = emptyList(),
            isLoading = true,
        )

        dispatcher.onEvent(IdeEvent.Internal.ReferencesResult(query))

        Snapshot.sendApplyNotifications()
        assertEquals(query, store.references.query)
    }

    @Test
    fun `effect handler sink receives dispatched event`() {
        val store = IdeStore()
        val handler = IdeEffectHandler()
        val received = mutableListOf<IdeEvent>()
        handler.bind { event, _, _ -> received.add(event) }
        val dispatcher = IdeDispatcher(store, handler)

        dispatcher.onEvent(IdeEvent.CodeAction.Dismiss)

        assertEquals(listOf<IdeEvent>(IdeEvent.CodeAction.Dismiss), received)
    }
}
