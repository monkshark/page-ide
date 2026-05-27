package page.app

import page.runtime.*

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.stream.Collectors

object LargeCopyExecutor {

    const val BYTE_THRESHOLD: Long = 5L * 1024 * 1024
    const val FILE_THRESHOLD: Int = 100
    private const val BUFFER_BYTES = 64 * 1024

    data class Estimate(val totalBytes: Long, val fileCount: Int) {
        val isLarge: Boolean get() = totalBytes >= BYTE_THRESHOLD || fileCount >= FILE_THRESHOLD
    }

    fun estimate(source: Path): Estimate {
        if (!Files.exists(source)) return Estimate(0L, 0)
        if (Files.isRegularFile(source)) {
            val size = runCatching { Files.size(source) }.getOrDefault(0L)
            return Estimate(size, 1)
        }
        if (!Files.isDirectory(source)) return Estimate(0L, 0)
        var bytes = 0L
        var count = 0
        runCatching {
            Files.walk(source).use { stream ->
                stream.collect(Collectors.toList()).forEach { p ->
                    if (Files.isRegularFile(p)) {
                        count += 1
                        bytes += runCatching { Files.size(p) }.getOrDefault(0L)
                    }
                }
            }
        }
        return Estimate(bytes, count)
    }

    fun estimateAll(sources: List<Path>): Estimate {
        var totalBytes = 0L
        var totalFiles = 0
        for (src in sources) {
            val e = estimate(src)
            totalBytes += e.totalBytes
            totalFiles += e.fileCount
        }
        return Estimate(totalBytes, totalFiles)
    }

    sealed interface CopyOutcome {
        data class Ok(val target: Path) : CopyOutcome
        data class Cancelled(val target: Path) : CopyOutcome
        data class Err(val message: String, val target: Path?) : CopyOutcome
    }

    fun copyWithProgress(
        source: Path,
        destParent: Path,
        newName: String,
        isCancelled: () -> Boolean,
        onProgress: (deltaBytes: Long, deltaFiles: Int) -> Unit,
        overwriteExisting: Boolean = false,
    ): CopyOutcome {
        if (!Files.isDirectory(destParent)) return CopyOutcome.Err("Destination is not a directory", null)
        if (!Files.exists(source)) return CopyOutcome.Err("Source does not exist", null)
        val target = destParent.resolve(newName)
        if (Files.exists(target)) {
            if (!overwriteExisting) return CopyOutcome.Err("'$newName' already exists", null)
            if (Files.isDirectory(target)) return CopyOutcome.Err("Cannot overwrite directory '$newName'", null)
            runCatching { Files.delete(target) }
                .getOrElse { return CopyOutcome.Err("Failed to overwrite '$newName'", null) }
        }
        val srcAbs = source.toAbsolutePath().normalize()
        val dstAbs = target.toAbsolutePath().normalize()
        if (Files.isDirectory(source) && dstAbs.startsWith(srcAbs)) {
            return CopyOutcome.Err("Cannot copy a folder into itself", null)
        }
        return runCatching {
            if (Files.isDirectory(source)) {
                copyTree(source, target, isCancelled, onProgress)
            } else {
                copySingleFile(source, target, isCancelled, onProgress)
            }
            if (isCancelled()) {
                cleanupPartial(target)
                CopyOutcome.Cancelled(target)
            } else {
                CopyOutcome.Ok(target)
            }
        }.getOrElse { e ->
            cleanupPartial(target)
            if (isCancelled()) CopyOutcome.Cancelled(target)
            else CopyOutcome.Err(e.message ?: "Failed to copy", target)
        }
    }

    private fun copyTree(
        source: Path,
        target: Path,
        isCancelled: () -> Boolean,
        onProgress: (Long, Int) -> Unit,
    ) {
        Files.walk(source).use { stream ->
            val items = stream.collect(Collectors.toList())
            for (path in items) {
                if (isCancelled()) return
                val rel = source.relativize(path)
                val dst = target.resolve(rel.toString())
                if (Files.isDirectory(path)) {
                    if (!Files.exists(dst)) Files.createDirectories(dst)
                } else if (Files.isRegularFile(path)) {
                    val dstParent = dst.parent
                    if (dstParent != null && !Files.exists(dstParent)) Files.createDirectories(dstParent)
                    copySingleFile(path, dst, isCancelled, onProgress)
                }
            }
        }
    }

    private fun copySingleFile(
        source: Path,
        target: Path,
        isCancelled: () -> Boolean,
        onProgress: (Long, Int) -> Unit,
    ) {
        val parent = target.parent
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
        Files.newInputStream(source).use { input ->
            Files.newOutputStream(target, StandardOpenOption.CREATE_NEW).use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    if (isCancelled()) return
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    if (n > 0) onProgress(n.toLong(), 0)
                }
            }
        }
        onProgress(0L, 1)
    }

    private fun cleanupPartial(target: Path) {
        runCatching {
            if (!Files.exists(target)) return@runCatching
            if (Files.isDirectory(target)) {
                Files.walk(target).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            } else {
                Files.deleteIfExists(target)
            }
        }
    }
}
