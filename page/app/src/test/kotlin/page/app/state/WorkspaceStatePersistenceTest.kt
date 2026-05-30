package page.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceStatePersistenceTest {

    @Test
    fun loadersRunOnInitialCompositionAndOnRootChange() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val state = WorkspaceState(scope)
        val loaded = mutableListOf<Path?>()

        state.launchPersistence(
            loaders = listOf({ root -> loaded.add(root) }),
            savers = emptyList(),
        )
        advanceUntilIdle()

        state.rootDir = Path.of("/proj")
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals(listOf(null, Path.of("/proj")), loaded)
    }

    @Test
    fun saverSkipsWhenRootNullAndWritesAfterDebounce() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val state = WorkspaceState(scope)
        var revision by mutableStateOf(0)
        val saved = mutableListOf<Path>()

        state.launchPersistence(
            loaders = emptyList(),
            savers = listOf(
                DebouncedSaver(
                    debounceMs = 500,
                    revision = { revision },
                    save = { root -> saved.add(root) },
                ),
            ),
        )
        advanceUntilIdle()
        assertTrue(saved.isEmpty(), "null root must not save")

        state.rootDir = Path.of("/proj")
        revision = 1
        Snapshot.sendApplyNotifications()

        advanceTimeBy(499)
        runCurrent()
        assertTrue(saved.isEmpty(), "must wait for debounce")

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(Path.of("/proj")), saved)
    }

    @Test
    fun saverCoalescesRapidRevisionChanges() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val state = WorkspaceState(scope)
        var revision by mutableStateOf(0)
        var saves = 0

        state.launchPersistence(
            loaders = emptyList(),
            savers = listOf(
                DebouncedSaver(
                    debounceMs = 500,
                    revision = { revision },
                    save = { saves++ },
                ),
            ),
        )
        state.rootDir = Path.of("/proj")
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        saves = 0

        revision = 1
        Snapshot.sendApplyNotifications()
        advanceTimeBy(200)
        runCurrent()

        revision = 2
        Snapshot.sendApplyNotifications()
        advanceTimeBy(200)
        runCurrent()

        revision = 3
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals(1, saves, "rapid changes within debounce window collapse to one save")
    }
}
