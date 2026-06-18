package page.atlas.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import page.atlas.interaction.OverviewSelection

class OverviewViewState {
    val selectionState = mutableStateOf(OverviewSelection.NONE)
    var selection by selectionState
    val camera = MapViewState()

    fun reset() {
        selection = OverviewSelection.NONE
        camera.reset()
    }
}
