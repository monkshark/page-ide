package page.app

import page.runtime.*

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque

object FileOpHistory {

    sealed interface UndoResult {
        data class Ok(val touched: List<Path>) : UndoResult
        data class Err(val message: String) : UndoResult
    }

    sealed interface Op {
        fun undo(): UndoResult
        fun redo(): UndoResult
        fun describe(): String
    }

    private fun summarizeNames(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> "${names[0]} + ${names.size - 1} more"
    }

    data class CreateOp(
        val created: Path,
        val isDirectory: Boolean = false,
    ) : Op {
        override fun undo(): UndoResult {
            if (!Files.exists(created)) return UndoResult.Err("Already gone")
            return runCatching {
                if (Files.isDirectory(created)) {
                    Files.walk(created).use { stream ->
                        stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                    }
                } else {
                    Files.delete(created)
                }
                UndoResult.Ok(listOf(created))
            }.getOrElse { e -> UndoResult.Err(e.message ?: "Failed to undo create") }
        }
        override fun redo(): UndoResult {
            if (Files.exists(created)) return UndoResult.Err("Already exists")
            return runCatching {
                val parent = created.parent
                if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
                if (isDirectory) Files.createDirectory(created) else Files.createFile(created)
                UndoResult.Ok(listOf(created))
            }.getOrElse { e -> UndoResult.Err(e.message ?: "Failed to redo create") }
        }
        override fun describe(): String = "Create ${created.fileName}"
    }

    data class RenameOp(val from: Path, val to: Path) : Op {
        override fun undo(): UndoResult {
            if (!Files.exists(to)) return UndoResult.Err("Renamed entry no longer exists")
            if (Files.exists(from)) return UndoResult.Err("Original path is occupied")
            return runCatching {
                Files.move(to, from)
                UndoResult.Ok(listOf(to, from))
            }.getOrElse { e -> UndoResult.Err(e.message ?: "Failed to undo rename") }
        }
        override fun redo(): UndoResult {
            if (!Files.exists(from)) return UndoResult.Err("Source path missing")
            if (Files.exists(to)) return UndoResult.Err("Target path is occupied")
            return runCatching {
                Files.move(from, to)
                UndoResult.Ok(listOf(from, to))
            }.getOrElse { e -> UndoResult.Err(e.message ?: "Failed to redo rename") }
        }
        override fun describe(): String = "Rename ${from.fileName} → ${to.fileName}"
    }

    data class TrashEntry(val originalPath: Path, val trashedPath: Path)

    data class DeleteOp(val entries: List<TrashEntry>) : Op {
        override fun undo(): UndoResult {
            val touched = mutableListOf<Path>()
            val errors = mutableListOf<String>()
            for (entry in entries) {
                if (!Files.exists(entry.trashedPath)) {
                    errors.add("Missing trash entry: ${entry.originalPath.fileName}")
                    continue
                }
                if (Files.exists(entry.originalPath)) {
                    errors.add("Already exists: ${entry.originalPath.fileName}")
                    continue
                }
                runCatching {
                    val parent = entry.originalPath.parent
                    if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
                    Files.move(entry.trashedPath, entry.originalPath)
                    touched.add(entry.originalPath)
                }.onFailure { e -> errors.add(e.message ?: "Restore failed") }
            }
            return if (errors.isEmpty()) UndoResult.Ok(touched)
            else UndoResult.Err(errors.joinToString("; "))
        }
        override fun redo(): UndoResult {
            val touched = mutableListOf<Path>()
            val errors = mutableListOf<String>()
            for (entry in entries) {
                if (!Files.exists(entry.originalPath)) {
                    errors.add("Already removed: ${entry.originalPath.fileName}")
                    continue
                }
                if (Files.exists(entry.trashedPath)) {
                    errors.add("Trash slot occupied: ${entry.trashedPath.fileName}")
                    continue
                }
                runCatching {
                    val parent = entry.trashedPath.parent
                    if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
                    Files.move(entry.originalPath, entry.trashedPath)
                    touched.add(entry.originalPath)
                }.onFailure { e -> errors.add(e.message ?: "Redo delete failed") }
            }
            return if (errors.isEmpty()) UndoResult.Ok(touched)
            else UndoResult.Err(errors.joinToString("; "))
        }
        override fun describe(): String =
            "Delete ${summarizeNames(entries.map { it.originalPath.fileName.toString() })}"
    }

    data class CopyEntry(val source: Path, val dest: Path)

    data class PasteCopyOp(val entries: List<CopyEntry>) : Op {
        val created: List<Path> get() = entries.map { it.dest }

        override fun undo(): UndoResult {
            val touched = mutableListOf<Path>()
            val errors = mutableListOf<String>()
            for (entry in entries) {
                val p = entry.dest
                if (!Files.exists(p)) continue
                runCatching {
                    if (Files.isDirectory(p)) {
                        Files.walk(p).use { stream ->
                            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                        }
                    } else {
                        Files.delete(p)
                    }
                    touched.add(p)
                }.onFailure { e -> errors.add(e.message ?: "Failed to remove pasted entry") }
            }
            return if (errors.isEmpty()) UndoResult.Ok(touched)
            else UndoResult.Err(errors.joinToString("; "))
        }
        override fun redo(): UndoResult {
            val touched = mutableListOf<Path>()
            val errors = mutableListOf<String>()
            for (entry in entries) {
                val src = entry.source
                val dst = entry.dest
                if (!Files.exists(src)) {
                    errors.add("Source missing: ${src.fileName}")
                    continue
                }
                runCatching {
                    val parent = dst.parent
                    if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
                    if (Files.isDirectory(src)) {
                        Files.walk(src).use { stream ->
                            stream.forEach { p ->
                                val rel = src.relativize(p).toString()
                                val target = if (rel.isEmpty()) dst else dst.resolve(rel)
                                if (Files.isDirectory(p)) {
                                    if (!Files.exists(target)) Files.createDirectories(target)
                                } else {
                                    target.parent?.let { tp ->
                                        if (!Files.exists(tp)) Files.createDirectories(tp)
                                    }
                                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING)
                                }
                            }
                        }
                    } else {
                        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
                    }
                    touched.add(dst)
                }.onFailure { e -> errors.add(e.message ?: "Failed to redo copy") }
            }
            return if (errors.isEmpty()) UndoResult.Ok(touched)
            else UndoResult.Err(errors.joinToString("; "))
        }
        override fun describe(): String =
            "Paste copy ${summarizeNames(entries.map { it.dest.fileName.toString() })}"
    }

    data class PasteCutOp(val moves: List<Pair<Path, Path>>) : Op {
        override fun undo(): UndoResult {
            val touched = mutableListOf<Path>()
            val errors = mutableListOf<String>()
            for ((origin, current) in moves) {
                if (!Files.exists(current)) {
                    errors.add("Missing pasted entry: ${current.fileName}")
                    continue
                }
                if (Files.exists(origin)) {
                    errors.add("Original location occupied: ${origin.fileName}")
                    continue
                }
                runCatching {
                    val parent = origin.parent
                    if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
                    Files.move(current, origin, StandardCopyOption.ATOMIC_MOVE)
                    touched.add(origin)
                }.recoverCatching {
                    Files.move(current, origin)
                    touched.add(origin)
                }.onFailure { e -> errors.add(e.message ?: "Failed to move back") }
            }
            return if (errors.isEmpty()) UndoResult.Ok(touched)
            else UndoResult.Err(errors.joinToString("; "))
        }
        override fun redo(): UndoResult {
            val touched = mutableListOf<Path>()
            val errors = mutableListOf<String>()
            for ((origin, current) in moves) {
                if (!Files.exists(origin)) {
                    errors.add("Missing original entry: ${origin.fileName}")
                    continue
                }
                if (Files.exists(current)) {
                    errors.add("Target location occupied: ${current.fileName}")
                    continue
                }
                runCatching {
                    val parent = current.parent
                    if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
                    Files.move(origin, current, StandardCopyOption.ATOMIC_MOVE)
                    touched.add(current)
                }.recoverCatching {
                    Files.move(origin, current)
                    touched.add(current)
                }.onFailure { e -> errors.add(e.message ?: "Failed to redo move") }
            }
            return if (errors.isEmpty()) UndoResult.Ok(touched)
            else UndoResult.Err(errors.joinToString("; "))
        }
        override fun describe(): String =
            "Paste cut ${summarizeNames(moves.map { it.second.fileName.toString() })}"
    }

    data class RewriteEntry(val path: Path, val original: String, val rewritten: String)

    data class ReferenceRewriteOp(val rewrites: List<RewriteEntry>) : Op {
        override fun undo(): UndoResult {
            val touched = mutableListOf<Path>()
            val errors = mutableListOf<String>()
            for (entry in rewrites) {
                runCatching {
                    val parent = entry.path.parent
                    if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
                    Files.writeString(entry.path, entry.original)
                    touched.add(entry.path)
                }.onFailure { e ->
                    errors.add(e.message ?: "Failed to restore ${entry.path.fileName}")
                }
            }
            return if (errors.isEmpty()) UndoResult.Ok(touched)
            else UndoResult.Err(errors.joinToString("; "))
        }
        override fun redo(): UndoResult {
            val touched = mutableListOf<Path>()
            val errors = mutableListOf<String>()
            for (entry in rewrites) {
                runCatching {
                    val parent = entry.path.parent
                    if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
                    Files.writeString(entry.path, entry.rewritten)
                    touched.add(entry.path)
                }.onFailure { e ->
                    errors.add(e.message ?: "Failed to rewrite ${entry.path.fileName}")
                }
            }
            return if (errors.isEmpty()) UndoResult.Ok(touched)
            else UndoResult.Err(errors.joinToString("; "))
        }
        override fun describe(): String = "Rewrite ${rewrites.size} reference(s)"
    }

    data class CompositeOp(val parts: List<Op>) : Op {
        override fun undo(): UndoResult {
            val touched = mutableListOf<Path>()
            val errors = mutableListOf<String>()
            for (part in parts.asReversed()) {
                when (val r = part.undo()) {
                    is UndoResult.Ok -> touched.addAll(r.touched)
                    is UndoResult.Err -> errors.add(r.message)
                }
            }
            return if (errors.isEmpty()) UndoResult.Ok(touched)
            else UndoResult.Err(errors.joinToString("; "))
        }
        override fun redo(): UndoResult {
            val touched = mutableListOf<Path>()
            val errors = mutableListOf<String>()
            for (part in parts) {
                when (val r = part.redo()) {
                    is UndoResult.Ok -> touched.addAll(r.touched)
                    is UndoResult.Err -> errors.add(r.message)
                }
            }
            return if (errors.isEmpty()) UndoResult.Ok(touched)
            else UndoResult.Err(errors.joinToString("; "))
        }
        override fun describe(): String =
            if (parts.isEmpty()) "Composite (empty)" else parts.joinToString(" + ") { it.describe() }
    }

    private const val MAX_HISTORY = 64

    class Stack {
        private val undoStack = ArrayDeque<Op>()
        private val redoStack = ArrayDeque<Op>()

        val size: Int get() = undoStack.size
        val redoSize: Int get() = redoStack.size

        fun push(op: Op) {
            undoStack.addLast(op)
            while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
            redoStack.clear()
        }

        fun peek(): Op? = undoStack.peekLast()
        fun peekRedo(): Op? = redoStack.peekLast()

        fun pop(): Op? = if (undoStack.isEmpty()) null else undoStack.removeLast()

        fun popForUndo(): Op? {
            if (undoStack.isEmpty()) return null
            val op = undoStack.removeLast()
            redoStack.addLast(op)
            while (redoStack.size > MAX_HISTORY) redoStack.removeFirst()
            return op
        }

        fun popForRedo(): Op? {
            if (redoStack.isEmpty()) return null
            val op = redoStack.removeLast()
            undoStack.addLast(op)
            while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
            return op
        }

        fun clear() {
            undoStack.clear()
            redoStack.clear()
        }
    }
}
