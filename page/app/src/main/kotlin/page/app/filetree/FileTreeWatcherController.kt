package page.app.filetree

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import page.workspace.FileTreeWatcher
import page.workspace.watchableDirs

class FileTreeWatcherController {
    private val holder = AtomicReference<FileTreeWatcher?>(null)
    private var epoch by mutableStateOf(0)

    @Composable
    fun WatchLoop(rootDir: Path?, expanded: Set<Path>, onTreeChanged: () -> Unit) {
        LaunchedEffect(rootDir, expanded, epoch) {
            val dirs = watchableDirs(rootDir, expanded)
            if (dirs.isEmpty()) return@LaunchedEffect
            val watcher = FileTreeWatcher(dirs)
            if (!watcher.active) {
                watcher.close()
                return@LaunchedEffect
            }
            holder.set(watcher)
            try {
                watcher.runLoop { onTreeChanged() }
            } finally {
                watcher.close()
                holder.compareAndSet(watcher, null)
            }
        }
    }

    fun withClosed(block: () -> Unit) {
        val w = holder.getAndSet(null)
        runCatching { w?.close() }
        if (w != null) {
            runCatching { Thread.sleep(200L) }
        }
        try {
            block()
        } finally {
            epoch++
        }
    }
}

@Composable
fun rememberFileTreeWatcherController(): FileTreeWatcherController =
    remember { FileTreeWatcherController() }
