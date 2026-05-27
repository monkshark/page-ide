package page.app

import page.runtime.*

import java.nio.file.Files
import java.nio.file.Path

object FileTreeActions {

    sealed interface CreateResult {
        data class Ok(val path: Path) : CreateResult
        data class Err(val message: String) : CreateResult
    }

    fun createFile(parent: Path, name: String): CreateResult {
        val validation = validateName(name)
        if (validation != null) return CreateResult.Err(validation)
        if (!Files.isDirectory(parent)) return CreateResult.Err("Parent is not a directory")
        val target = parent.resolve(name)
        if (Files.exists(target)) return CreateResult.Err("'$name' already exists")
        return runCatching {
            Files.createFile(target)
            CreateResult.Ok(target)
        }.getOrElse { e -> CreateResult.Err(e.message ?: "Failed to create file") }
    }

    fun createFolder(parent: Path, name: String): CreateResult {
        val validation = validateName(name)
        if (validation != null) return CreateResult.Err(validation)
        if (!Files.isDirectory(parent)) return CreateResult.Err("Parent is not a directory")
        val target = parent.resolve(name)
        if (Files.exists(target)) return CreateResult.Err("'$name' already exists")
        return runCatching {
            Files.createDirectories(target)
            CreateResult.Ok(target)
        }.getOrElse { e -> CreateResult.Err(e.message ?: "Failed to create folder") }
    }

    sealed interface RenameResult {
        data class Ok(val path: Path) : RenameResult
        data class Err(val message: String) : RenameResult
    }

    fun rename(path: Path, newName: String, workspaceRoot: Path? = null): RenameResult {
        val validation = validateName(newName)
        if (validation != null) return RenameResult.Err(validation)
        val current = path.fileName?.toString()
        if (current == newName) return RenameResult.Err("Name unchanged")
        val parent = path.parent ?: return RenameResult.Err("No parent directory")
        val target = parent.resolve(newName)
        if (Files.exists(target)) return RenameResult.Err("'$newName' already exists")
        val moveAttempt = runCatching { Files.move(path, target) }
        if (moveAttempt.isSuccess) {
            return RenameResult.Ok(moveAttempt.getOrThrow())
        }
        val moveError = moveAttempt.exceptionOrNull()
        if (Files.isDirectory(path)) {
            val fallback = if (workspaceRoot != null) {
                runCatching { RenameTransaction.execute(workspaceRoot, path, target) }
            } else {
                runCatching { renameDirectoryByCopy(path, target) }
            }
            if (fallback.isSuccess) {
                return RenameResult.Ok(target)
            }
            val cause = fallback.exceptionOrNull() ?: moveError
            return RenameResult.Err(cause?.message ?: "Failed to rename")
        }
        return RenameResult.Err(moveError?.message ?: "Failed to rename")
    }

    internal fun renameDirectoryByCopy(source: Path, target: Path) {
        copyTreeRecursive(source, target)
        val deleted = deleteTreeWithRetry(source)
        if (!deleted) {
            runCatching {
                Files.walk(target).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
            throw java.nio.file.AccessDeniedException(source.toString(), target.toString(), "Could not remove source directory after copy")
        }
    }

    internal fun copyTree(source: Path, target: Path) = copyTreeRecursive(source, target)

    internal fun deleteTreeWithRetry(path: Path, attempts: Int = 5, sleepMs: Long = 200L): Boolean {
        for (i in 1..attempts) {
            val ok = runCatching {
                Files.walk(path).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
                !Files.exists(path)
            }.getOrDefault(false)
            if (ok) return true
            if (i < attempts) {
                runCatching { Thread.sleep(sleepMs) }
                runCatching { System.gc() }
            }
        }
        return !Files.exists(path)
    }

    sealed interface DeleteResult {
        object Ok : DeleteResult
        data class Err(val message: String) : DeleteResult
    }

    fun delete(path: Path): DeleteResult {
        if (!Files.exists(path)) return DeleteResult.Err("Path does not exist")
        return runCatching {
            val desktop = if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop() else null
            val trashed = desktop != null
                && desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)
                && desktop.moveToTrash(path.toFile())
            if (!trashed) {
                if (Files.isDirectory(path)) {
                    Files.walk(path).use { stream ->
                        stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                    }
                } else {
                    Files.delete(path)
                }
            }
            DeleteResult.Ok
        }.getOrElse { e -> DeleteResult.Err(e.message ?: "Failed to delete") }
    }

    fun pruneRedundantDescendants(paths: Collection<Path>): List<Path> {
        if (paths.isEmpty()) return emptyList()
        val sorted = paths.toSet().toList().sortedBy { it.toAbsolutePath().normalize().toString() }
        val kept = mutableListOf<Path>()
        for (p in sorted) {
            val abs = p.toAbsolutePath().normalize()
            val covered = kept.any { existing ->
                val e = existing.toAbsolutePath().normalize()
                abs.startsWith(e) && abs != e
            }
            if (!covered) kept.add(p)
        }
        return kept
    }

    data class BatchDeleteOutcome(
        val results: List<Pair<Path, DeleteResult>>,
    ) {
        val successCount: Int get() = results.count { it.second is DeleteResult.Ok }
        val failureCount: Int get() = results.count { it.second is DeleteResult.Err }
        val allOk: Boolean get() = failureCount == 0
    }

    fun deleteBatch(paths: Collection<Path>): BatchDeleteOutcome {
        val pruned = pruneRedundantDescendants(paths)
        val results = pruned.map { it to delete(it) }
        return BatchDeleteOutcome(results)
    }

    sealed interface TrashResult {
        data class Ok(val entries: List<FileOpHistory.TrashEntry>) : TrashResult
        data class Err(val message: String, val partialEntries: List<FileOpHistory.TrashEntry>) : TrashResult
    }

    fun deleteToTrash(paths: Collection<Path>, workspaceRoot: Path): TrashResult {
        val pruned = pruneRedundantDescendants(paths)
        if (pruned.isEmpty()) return TrashResult.Ok(emptyList())
        val trashRoot = workspaceRoot.resolve(".page-ide").resolve(".trash")
        val bucketName = java.util.UUID.randomUUID().toString()
        val bucket = trashRoot.resolve(bucketName)
        return runCatching {
            Files.createDirectories(bucket)
            val entries = mutableListOf<FileOpHistory.TrashEntry>()
            for ((index, src) in pruned.withIndex()) {
                if (!Files.exists(src)) continue
                val safeName = "$index-${src.fileName ?: "entry"}"
                val dest = bucket.resolve(safeName)
                val moved = runCatching { Files.move(src, dest) }
                if (moved.isFailure && Files.isDirectory(src)) {
                    copyTreeRecursive(src, dest)
                    if (!deleteTreeWithRetry(src)) {
                        Files.walk(dest).use { stream ->
                            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                        }
                        return@runCatching TrashResult.Err(
                            "Could not remove ${src.fileName}",
                            entries.toList(),
                        )
                    }
                } else if (moved.isFailure) {
                    return@runCatching TrashResult.Err(
                        moved.exceptionOrNull()?.message ?: "Failed to move to trash",
                        entries.toList(),
                    )
                }
                entries.add(FileOpHistory.TrashEntry(originalPath = src, trashedPath = dest))
            }
            TrashResult.Ok(entries.toList()) as TrashResult
        }.getOrElse { e -> TrashResult.Err(e.message ?: "Failed to move to trash", emptyList()) }
    }

    fun purgeTrashOlderThan(workspaceRoot: Path, retainNewestBuckets: Int = 32) {
        val trashRoot = workspaceRoot.resolve(".page-ide").resolve(".trash")
        if (!Files.isDirectory(trashRoot)) return
        runCatching {
            val buckets = Files.list(trashRoot).use { it.toList() }
                .filter { Files.isDirectory(it) }
                .sortedByDescending { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) }
            buckets.drop(retainNewestBuckets).forEach { stale ->
                runCatching {
                    Files.walk(stale).use { stream ->
                        stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                    }
                }
            }
        }
    }

    sealed interface CopyResult {
        data class Ok(val path: Path) : CopyResult
        data class Err(val message: String) : CopyResult
    }

    fun copyFile(
        source: Path,
        destParent: Path,
        newName: String,
        overwriteExisting: Boolean = false,
    ): CopyResult {
        val validation = validateName(newName)
        if (validation != null) return CopyResult.Err(validation)
        if (!Files.isDirectory(destParent)) return CopyResult.Err("Destination is not a directory")
        if (!Files.exists(source)) return CopyResult.Err("Source does not exist")
        val target = destParent.resolve(newName)
        if (Files.exists(target)) {
            if (!overwriteExisting) return CopyResult.Err("'$newName' already exists")
            if (Files.isDirectory(target)) return CopyResult.Err("Cannot overwrite directory '$newName'")
            runCatching { Files.delete(target) }
                .getOrElse { return CopyResult.Err("Failed to overwrite '$newName'") }
        }
        val srcAbs = source.toAbsolutePath().normalize()
        val dstAbs = target.toAbsolutePath().normalize()
        if (Files.isDirectory(source) && dstAbs.startsWith(srcAbs)) {
            return CopyResult.Err("Cannot copy a folder into itself")
        }
        return runCatching {
            if (Files.isDirectory(source)) copyTreeRecursive(source, target)
            else Files.copy(source, target)
            CopyResult.Ok(target)
        }.getOrElse { e -> CopyResult.Err(e.message ?: "Failed to copy") }
    }

    sealed interface MoveResult {
        data class Ok(val path: Path) : MoveResult
        data class Err(val message: String) : MoveResult
    }

    fun moveFile(
        source: Path,
        destParent: Path,
        newName: String,
        workspaceRoot: Path? = null,
        overwriteExisting: Boolean = false,
    ): MoveResult {
        val validation = validateName(newName)
        if (validation != null) return MoveResult.Err(validation)
        if (!Files.isDirectory(destParent)) return MoveResult.Err("Destination is not a directory")
        if (!Files.exists(source)) return MoveResult.Err("Source does not exist")
        val target = destParent.resolve(newName)
        if (Files.exists(target)) {
            if (!overwriteExisting) return MoveResult.Err("'$newName' already exists")
            if (Files.isDirectory(target)) return MoveResult.Err("Cannot overwrite directory '$newName'")
            runCatching { Files.delete(target) }
                .getOrElse { return MoveResult.Err("Failed to overwrite '$newName'") }
        }
        val srcAbs = source.toAbsolutePath().normalize()
        val dstAbs = target.toAbsolutePath().normalize()
        if (Files.isDirectory(source) && dstAbs.startsWith(srcAbs)) {
            return MoveResult.Err("Cannot move a folder into itself")
        }
        val moveAttempt = runCatching { Files.move(source, target) }
        if (moveAttempt.isSuccess) {
            return MoveResult.Ok(moveAttempt.getOrThrow())
        }
        val moveError = moveAttempt.exceptionOrNull()
        if (Files.isDirectory(source)) {
            val fallback = if (workspaceRoot != null) {
                runCatching { RenameTransaction.execute(workspaceRoot, source, target) }
            } else {
                runCatching { renameDirectoryByCopy(source, target) }
            }
            if (fallback.isSuccess) {
                return MoveResult.Ok(target)
            }
            val cause = fallback.exceptionOrNull() ?: moveError
            return MoveResult.Err(cause?.message ?: "Failed to move")
        }
        return MoveResult.Err(moveError?.message ?: "Failed to move")
    }

    private fun copyTreeRecursive(source: Path, target: Path) {
        Files.walk(source).use { stream ->
            stream.forEach { src ->
                val rel = source.relativize(src)
                val dst = target.resolve(rel.toString())
                if (Files.isDirectory(src)) Files.createDirectories(dst)
                else Files.copy(src, dst, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES)
            }
        }
    }

    fun parentDirOf(path: Path): Path? = when {
        Files.isDirectory(path) -> path
        else -> path.parent
    }

    fun relativeTo(workspaceRoot: Path?, path: Path): String {
        if (workspaceRoot == null) return path.toString()
        return runCatching { workspaceRoot.relativize(path).toString() }.getOrDefault(path.toString())
    }

    private val INVALID_CHARS = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    private fun validateName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Name cannot be empty"
        if (trimmed == "." || trimmed == "..") return "Reserved name"
        val bad = trimmed.firstOrNull { it in INVALID_CHARS || it.code < 0x20 }
        if (bad != null) return "Invalid character: '$bad'"
        return null
    }
}
