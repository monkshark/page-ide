package page.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import page.app.mvi.IdeStore
import java.nio.file.Path

internal class WorkspaceState(
    private val scope: CoroutineScope,
    private val store: IdeStore = IdeStore(),
) {
    var rootDir by mutableStateOf<Path?>(null)
    var expanded: Set<Path>
        get() = store.tree.expanded
        set(value) = store.updateTree { it.copy(expanded = value) }
    var treeSelection: Set<Path>
        get() = store.tree.selection
        set(value) = store.updateTree { it.copy(selection = value) }
    var treeRevision: Int
        get() = store.tree.revision
        set(value) = store.updateTree { it.copy(revision = value) }
    var fileTreeFocused: Boolean
        get() = store.tree.focused
        set(value) = store.updateTree { it.copy(focused = value) }

    private var persistenceLaunched = false

    fun launchPersistence(
        loaders: List<suspend (Path?) -> Unit>,
        savers: List<DebouncedSaver>,
    ) {
        if (persistenceLaunched) return
        persistenceLaunched = true
        for (load in loaders) {
            scope.launch {
                snapshotFlow { rootDir }.collectLatest { load(it) }
            }
        }
        for (saver in savers) {
            scope.launch {
                snapshotFlow { rootDir to saver.revision() }
                    .collectLatest { (root, _) ->
                        if (root != null) {
                            delay(saver.debounceMs)
                            saver.save(root)
                        }
                    }
            }
        }
    }
}

class DebouncedSaver(
    val debounceMs: Long,
    val revision: () -> Any?,
    val save: suspend (Path) -> Unit,
)
