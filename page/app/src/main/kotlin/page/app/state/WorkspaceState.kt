package page.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Path

class WorkspaceState {
    var rootDir by mutableStateOf<Path?>(null)
    var expanded by mutableStateOf(emptySet<Path>())
    var treeSelection by mutableStateOf(emptySet<Path>())
    var treeRevision by mutableStateOf(0)
    var fileTreeFocused by mutableStateOf(false)
}
