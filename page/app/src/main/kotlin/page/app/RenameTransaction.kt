package page.app

import page.runtime.*

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

object RenameTransaction {

    private const val MARKER_FILE = "rename-active"
    private const val LOG_FILE = "rename.log"
    private const val LOG_MAX_BYTES: Long = 256 * 1024
    private val LOG_TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    enum class Stage { COPY, DELETE }

    data class Marker(val from: Path, val to: Path, val stage: Stage)

    sealed interface RecoveryResult {
        data object NoMarker : RecoveryResult
        data class Resumed(val marker: Marker) : RecoveryResult
        data class RolledBack(val marker: Marker) : RecoveryResult
        data class Skipped(val marker: Marker, val reason: String) : RecoveryResult
        data class Failed(val marker: Marker, val message: String) : RecoveryResult
    }

    fun markerFile(workspaceRoot: Path): Path =
        PageIdeStore.fileOf(workspaceRoot, MARKER_FILE)

    fun logFile(workspaceRoot: Path): Path =
        PageIdeStore.fileOf(workspaceRoot, LOG_FILE)

    fun current(workspaceRoot: Path): Marker? {
        val file = markerFile(workspaceRoot)
        if (!Files.isRegularFile(file)) return null
        return runCatching { parse(Files.readAllLines(file)) }.getOrNull()
    }

    fun execute(workspaceRoot: Path, from: Path, to: Path) {
        log(workspaceRoot, "begin", from, to)
        write(workspaceRoot, Marker(from, to, Stage.COPY))
        FileTreeActions.copyTree(from, to)
        log(workspaceRoot, "copy-done", from, to)
        write(workspaceRoot, Marker(from, to, Stage.DELETE))
        val deleted = FileTreeActions.deleteTreeWithRetry(from)
        if (!deleted) {
            log(workspaceRoot, "delete-failed", from, to)
            throw java.nio.file.AccessDeniedException(
                from.toString(),
                to.toString(),
                "Could not remove source directory after copy",
            )
        }
        clear(workspaceRoot)
        log(workspaceRoot, "commit", from, to)
    }

    fun recover(workspaceRoot: Path): RecoveryResult {
        val marker = current(workspaceRoot) ?: return RecoveryResult.NoMarker
        log(workspaceRoot, "recover-start stage=${marker.stage}", marker.from, marker.to)
        if (!Files.exists(marker.from) && !Files.exists(marker.to)) {
            clear(workspaceRoot)
            val result = RecoveryResult.Skipped(marker, "neither source nor target exists")
            log(workspaceRoot, "recover-skipped reason=${result.reason}", marker.from, marker.to)
            return result
        }
        val result = when (marker.stage) {
            Stage.COPY -> recoverCopyStage(workspaceRoot, marker)
            Stage.DELETE -> recoverDeleteStage(workspaceRoot, marker)
        }
        val label = when (result) {
            is RecoveryResult.Resumed -> "recover-resumed"
            is RecoveryResult.RolledBack -> "recover-rolled-back"
            is RecoveryResult.Skipped -> "recover-skipped reason=${result.reason}"
            is RecoveryResult.Failed -> "recover-failed reason=${result.message}"
            else -> "recover-done"
        }
        log(workspaceRoot, label, marker.from, marker.to)
        return result
    }

    private fun recoverCopyStage(workspaceRoot: Path, marker: Marker): RecoveryResult {
        if (!Files.exists(marker.from)) {
            clear(workspaceRoot)
            return RecoveryResult.Skipped(marker, "source missing during copy stage")
        }
        val targetReady = Files.exists(marker.to) && treeMatches(marker.from, marker.to)
        if (!targetReady) {
            val rollback = runCatching {
                if (Files.exists(marker.to)) {
                    Files.walk(marker.to).use { stream ->
                        stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                    }
                }
            }
            clear(workspaceRoot)
            return if (rollback.isSuccess) RecoveryResult.RolledBack(marker)
            else RecoveryResult.Failed(marker, rollback.exceptionOrNull()?.message ?: "rollback failed")
        }
        write(workspaceRoot, marker.copy(stage = Stage.DELETE))
        val deleted = FileTreeActions.deleteTreeWithRetry(marker.from)
        if (!deleted) {
            return RecoveryResult.Failed(marker, "could not delete source after resume")
        }
        clear(workspaceRoot)
        return RecoveryResult.Resumed(marker)
    }

    private fun recoverDeleteStage(workspaceRoot: Path, marker: Marker): RecoveryResult {
        if (!Files.exists(marker.to)) {
            clear(workspaceRoot)
            return RecoveryResult.Skipped(marker, "target missing during delete stage")
        }
        if (!Files.exists(marker.from)) {
            clear(workspaceRoot)
            return RecoveryResult.Resumed(marker)
        }
        val deleted = FileTreeActions.deleteTreeWithRetry(marker.from)
        if (!deleted) {
            return RecoveryResult.Failed(marker, "could not delete source during resume")
        }
        clear(workspaceRoot)
        return RecoveryResult.Resumed(marker)
    }

    private fun treeMatches(source: Path, target: Path): Boolean {
        val sourceEntries = walkEntries(source) ?: return false
        val targetEntries = walkEntries(target) ?: return false
        if (sourceEntries.keys != targetEntries.keys) return false
        for ((rel, srcSize) in sourceEntries) {
            val dstSize = targetEntries[rel] ?: return false
            if (srcSize != dstSize) return false
        }
        return true
    }

    private fun walkEntries(root: Path): Map<String, Long>? {
        return runCatching {
            Files.walk(root).use { stream ->
                stream
                    .filter { it != root && Files.isRegularFile(it) }
                    .collect(Collectors.toMap(
                        { p -> root.relativize(p).toString().replace('\\', '/') },
                        { p -> Files.size(p) },
                    ))
            }
        }.getOrNull()
    }

    internal fun write(workspaceRoot: Path, marker: Marker) {
        val file = markerFile(workspaceRoot)
        Files.createDirectories(file.parent)
        runCatching { PageIdeStore.ensureGitignore(workspaceRoot) }
        val text = buildString {
            append("from=").append(marker.from.toAbsolutePath().normalize()).append('\n')
            append("to=").append(marker.to.toAbsolutePath().normalize()).append('\n')
            append("stage=").append(marker.stage.name).append('\n')
        }
        Files.writeString(file, text)
    }

    private fun clear(workspaceRoot: Path) {
        val file = markerFile(workspaceRoot)
        runCatching { Files.deleteIfExists(file) }
    }

    internal fun log(workspaceRoot: Path, event: String, from: Path, to: Path) {
        runCatching {
            val file = logFile(workspaceRoot)
            Files.createDirectories(file.parent)
            runCatching { PageIdeStore.ensureGitignore(workspaceRoot) }
            rotateIfNeeded(file)
            val line = buildString {
                append(LocalDateTime.now().format(LOG_TS))
                append(' ').append(event)
                append(" from=").append(from.toAbsolutePath().normalize())
                append(" to=").append(to.toAbsolutePath().normalize())
                append('\n')
            }
            Files.writeString(
                file,
                line,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    private fun rotateIfNeeded(file: Path) {
        runCatching {
            if (!Files.exists(file)) return@runCatching
            if (Files.size(file) < LOG_MAX_BYTES) return@runCatching
            val rotated = file.resolveSibling(file.fileName.toString() + ".1")
            Files.move(file, rotated, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun parse(lines: List<String>): Marker? {
        val map = HashMap<String, String>()
        for (line in lines) {
            val idx = line.indexOf('=')
            if (idx <= 0) continue
            map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
        }
        val from = map["from"]?.let { runCatching { Paths.get(it) }.getOrNull() } ?: return null
        val to = map["to"]?.let { runCatching { Paths.get(it) }.getOrNull() } ?: return null
        val stage = map["stage"]?.let { runCatching { Stage.valueOf(it) }.getOrNull() } ?: return null
        return Marker(from, to, stage)
    }
}
