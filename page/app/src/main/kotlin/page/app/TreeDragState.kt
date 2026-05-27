package page.app

import page.runtime.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import java.nio.file.Path

class TreeDragState {

    data class Active(
        val sources: List<Path>,
        val origin: TreeDragController.Source,
        val initialMode: TreeDragController.Mode,
        val currentMode: TreeDragController.Mode,
        val pointer: Offset,
        val hovered: Path?,
    )

    var active by mutableStateOf<Active?>(null)
        private set

    fun start(sources: List<Path>, mode: TreeDragController.Mode, origin: TreeDragController.Source, at: Offset) {
        active = Active(
            sources = sources,
            origin = origin,
            initialMode = mode,
            currentMode = mode,
            pointer = at,
            hovered = null,
        )
    }

    fun updatePointer(offset: Offset) {
        val a = active ?: return
        active = a.copy(pointer = offset)
    }

    fun hover(path: Path?) {
        val a = active ?: return
        if (a.hovered == path) return
        active = a.copy(hovered = path)
    }

    fun setMode(mode: TreeDragController.Mode) {
        val a = active ?: return
        if (a.currentMode == mode) return
        active = a.copy(currentMode = mode)
    }

    fun cancel() {
        active = null
    }

    val isActive: Boolean get() = active != null
}

@Composable
fun rememberTreeDragState(): TreeDragState = remember { TreeDragState() }
