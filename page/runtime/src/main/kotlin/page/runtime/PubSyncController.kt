package page.runtime

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

object PubSyncRegistry {

    data class Entry(val key: String, val label: String, val startedAtMs: Long)

    private val _entries: SnapshotStateMap<String, Entry> = mutableStateMapOf()

    val entries: Map<String, Entry> get() = _entries

    fun start(key: String, label: String) {
        _entries[key] = Entry(key, label, System.currentTimeMillis())
    }

    fun finish(key: String) {
        _entries.remove(key)
    }
}

class PubSyncController(
    private val scope: CoroutineScope,
    private val workspaceRoot: () -> Path?,
    private val emitFailureOutput: (List<RunEvent>) -> Unit,
) {

    private val inFlight = ConcurrentHashMap.newKeySet<Path>()
    private val failedFingerprints = ConcurrentHashMap<Path, Int>()

    fun onFileSaved(path: Path) {
        val name = path.fileName?.toString() ?: return
        if (!name.equals("pubspec.yaml", ignoreCase = true)) return
        trigger(path)
    }

    fun onFileOpened(path: Path) {
        val name = path.fileName?.toString() ?: return
        if (!name.endsWith(".dart", ignoreCase = true)) return
        val pubspec = FlutterProjectDetector.findPubspec(path, workspaceRoot()) ?: return
        trigger(pubspec)
    }

    private fun trigger(pubspecPath: Path) {
        val pubspec = pubspecPath.toAbsolutePath().normalize()
        val projectDir = pubspec.parent ?: return
        if (!inFlight.add(projectDir)) return
        scope.launch(Dispatchers.IO) {
            try {
                if (!PubDependencySync.needsSync(pubspec)) return@launch
                val content = runCatching { Files.readString(pubspec) }.getOrNull() ?: return@launch
                if (!PubDependencySync.isSanePubspec(content)) return@launch
                if (failedFingerprints[projectDir] == content.hashCode()) return@launch
                runPubGet(projectDir, content)
            } finally {
                inFlight.remove(projectDir)
            }
        }
    }

    private fun runPubGet(projectDir: Path, content: String) {
        val plan = PubDependencySync.plan(content, flutterExecutable(), dartExecutable())
        val projectName = projectDir.fileName?.toString() ?: projectDir.toString()
        val key = projectDir.toString()
        PubSyncRegistry.start(key, "${plan.label} · $projectName")
        val startedAt = System.currentTimeMillis()
        val lines = mutableListOf<String>()
        var exit: Int? = null
        var failureMessage: String? = null
        try {
            val process = ProcessBuilder(plan.command)
                .directory(projectDir.toFile())
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().useLines { seq -> seq.forEach { lines.add(it) } }
            exit = process.waitFor()
        } catch (t: Throwable) {
            failureMessage = t.message ?: t.javaClass.simpleName
        } finally {
            PubSyncRegistry.finish(key)
        }
        if (exit == 0) {
            failedFingerprints.remove(projectDir)
            return
        }
        failedFingerprints[projectDir] = content.hashCode()
        val events = buildList {
            add(RunEvent.Started(plan.command.first(), plan.command.drop(1), projectDir.toString()))
            lines.forEach { add(RunEvent.Stdout(it + "\n")) }
            val code = exit
            if (code != null) add(RunEvent.Exited(code, System.currentTimeMillis() - startedAt))
            else add(RunEvent.Failed(failureMessage ?: "pub get failed"))
        }
        emitFailureOutput(events)
    }

    private fun flutterExecutable(): String? = runCatching {
        val installer = FlutterSdkInstaller()
        installer.currentInstalledVersion()?.let { version ->
            installer.flutterCommand(version).takeIf(Files::exists)?.toAbsolutePath()?.toString()
        }
    }.getOrNull()

    private fun dartExecutable(): String? = runCatching {
        val dart = DartSdkInstaller()
        dart.currentInstalledVersion()?.let { version ->
            dart.dartBinary(version).takeIf(Files::exists)?.toAbsolutePath()?.toString()
        } ?: run {
            val flutter = FlutterSdkInstaller()
            flutter.currentInstalledVersion()?.let { version ->
                flutter.dartBinary(version).takeIf(Files::exists)?.toAbsolutePath()?.toString()
            }
        }
    }.getOrNull()
}
