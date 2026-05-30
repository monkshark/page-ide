package page.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.nio.file.Path

class WorkspaceState(
    private val scope: CoroutineScope,
) {
    var rootDir by mutableStateOf<Path?>(null)
    var expanded by mutableStateOf(emptySet<Path>())
    var treeSelection by mutableStateOf(emptySet<Path>())
    var treeRevision by mutableStateOf(0)
    var fileTreeFocused by mutableStateOf(false)

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
