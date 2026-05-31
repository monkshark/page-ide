package page.runtime

import java.nio.file.Files
import java.nio.file.Path

object CargoWorkspaceDetector {

    private val skipDirs = setOf("target", "node_modules", ".git", ".page-ide", ".idea", "build")

    fun findCargoProjects(workspaceRoot: Path, maxDepth: Int = 4, limit: Int = 32): List<Path> {
        val root = runCatching { workspaceRoot.toAbsolutePath().normalize() }.getOrNull() ?: return emptyList()
        if (!Files.isDirectory(root)) return emptyList()
        val found = ArrayList<Path>()
        collect(root, 0, maxDepth, limit, found)
        return found
    }

    private fun collect(dir: Path, depth: Int, maxDepth: Int, limit: Int, out: MutableList<Path>) {
        if (out.size >= limit || depth > maxDepth) return
        val manifest = dir.resolve("Cargo.toml")
        if (Files.isRegularFile(manifest)) {
            out.add(manifest)
            return
        }
        val children = runCatching {
            Files.list(dir).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .filter { it.fileName?.toString()?.let { n -> !n.startsWith(".") && n !in skipDirs } ?: false }
                    .toList()
            }
        }.getOrDefault(emptyList())
        for (child in children) {
            if (out.size >= limit) return
            collect(child, depth + 1, maxDepth, limit, out)
        }
    }

    fun linkedProjects(workspaceRoot: Path?): Map<String, Any>? {
        val root = workspaceRoot ?: return null
        val rootManifest = root.resolve("Cargo.toml")
        if (Files.isRegularFile(rootManifest)) return null
        val projects = findCargoProjects(root)
        if (projects.isEmpty()) return null
        return mapOf("linkedProjects" to projects.map { it.toString() })
    }
}
