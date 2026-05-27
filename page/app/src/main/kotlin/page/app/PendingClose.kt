package page.app

import page.runtime.*

import java.nio.file.Path

internal sealed interface PendingClose {
    data class Tab(val side: PaneSide, val index: Int) : PendingClose
    data object App : PendingClose
    data class Batch(val targets: List<Pair<PaneSide, Path>>) : PendingClose
}
