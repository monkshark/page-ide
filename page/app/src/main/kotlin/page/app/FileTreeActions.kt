package page.app

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

    fun rename(path: Path, newName: String): RenameResult {
        val validation = validateName(newName)
        if (validation != null) return RenameResult.Err(validation)
        val current = path.fileName?.toString()
        if (current == newName) return RenameResult.Err("Name unchanged")
        val parent = path.parent ?: return RenameResult.Err("No parent directory")
        val target = parent.resolve(newName)
        if (Files.exists(target)) return RenameResult.Err("'$newName' already exists")
        return runCatching {
            val moved = Files.move(path, target)
            RenameResult.Ok(moved)
        }.getOrElse { e -> RenameResult.Err(e.message ?: "Failed to rename") }
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
