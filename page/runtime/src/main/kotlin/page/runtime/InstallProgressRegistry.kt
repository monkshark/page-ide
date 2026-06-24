package page.runtime

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global tracker for in-flight LSP installer runs.
 *
 * Used to bridge the InstallGuideDialog (which can be minimized) with the status bar progress
 * row — both read from this registry so install progress survives a dialog dismiss. The job and
 * cancel flag are kept here too, so a reopened dialog can drive the same background install.
 */
object InstallProgressRegistry {

    data class Entry(
        val installerId: String,
        val displayName: String,
        val startedAtMs: Long,
        val progress: LspInstaller.Progress?,
        val job: Job? = null,
        val cancelled: AtomicBoolean? = null,
    )

    private val _entries: SnapshotStateMap<String, Entry> = mutableStateMapOf()

    val entries: Map<String, Entry> get() = _entries

    private val _completed: SnapshotStateList<String> = mutableStateListOf()

    val completed: List<String> get() = _completed

    fun start(installerId: String, displayName: String, cancelled: AtomicBoolean? = null) {
        _entries[installerId] = Entry(
            installerId = installerId,
            displayName = displayName,
            startedAtMs = System.currentTimeMillis(),
            progress = null,
            cancelled = cancelled,
        )
    }

    fun attachJob(installerId: String, job: Job) {
        val existing = _entries[installerId] ?: return
        _entries[installerId] = existing.copy(job = job)
    }

    fun update(installerId: String, progress: LspInstaller.Progress) {
        val existing = _entries[installerId] ?: return
        _entries[installerId] = existing.copy(progress = progress)
    }

    fun finish(installerId: String) {
        _entries.remove(installerId)
    }

    fun complete(installerId: String) {
        _entries.remove(installerId)
        if (installerId !in _completed) _completed.add(installerId)
    }

    fun consumeCompleted(installerId: String) {
        _completed.remove(installerId)
    }

    fun get(installerId: String): Entry? = _entries[installerId]
}
