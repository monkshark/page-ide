package page.app

import page.runtime.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService

internal class FileTreeWatcher(
    private val dirs: Set<Path>,
    private val debounceMs: Long = 250L,
) : AutoCloseable {
    private val service: WatchService? = runCatching {
        if (dirs.isEmpty()) null else dirs.first().fileSystem.newWatchService()
    }.getOrNull()
    private val keys: List<WatchKey> = if (service == null) emptyList() else dirs.mapNotNull { dir ->
        runCatching {
            if (!Files.isDirectory(dir)) return@runCatching null
            dir.register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY,
            )
        }.getOrNull()
    }

    val active: Boolean get() = service != null && keys.isNotEmpty()

    suspend fun runLoop(onChange: () -> Unit) {
        val svc = service ?: return
        if (keys.isEmpty()) return
        while (true) {
            val key = withContext(Dispatchers.IO) {
                runCatching { svc.take() }.getOrNull()
            } ?: return
            runCatching { key.pollEvents() }
            runCatching { key.reset() }
            delay(debounceMs)
            drain(svc)
            onChange()
        }
    }

    private suspend fun drain(svc: WatchService) {
        while (true) {
            val next = withContext(Dispatchers.IO) {
                runCatching { svc.poll() }.getOrNull()
            } ?: return
            runCatching { next.pollEvents() }
            runCatching { next.reset() }
        }
    }

    override fun close() {
        runCatching { service?.close() }
    }
}

internal fun watchableDirs(root: Path?, expanded: Set<Path>): Set<Path> {
    if (root == null) return emptySet()
    val result = mutableSetOf<Path>()
    if (Files.isDirectory(root)) result.add(root)
    for (p in expanded) {
        if (Files.isDirectory(p)) result.add(p)
    }
    return result
}
