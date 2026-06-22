package page.atlas.analyzer

import java.nio.file.Files
import java.nio.file.Path

object GoModResolver {

    private data class Cached(val mtime: Long, val module: String?)

    private val cache = HashMap<String, Cached>()

    fun resolve(activeFile: Path, target: String, index: WorkspaceIndex): Path? {
        if (target.isEmpty()) return null
        val goMod = findGoMod(activeFile) ?: return null
        val module = moduleOf(goMod) ?: return null
        val moduleDir = goMod.parent ?: return null
        val rel = when {
            target == module -> ""
            target.startsWith("$module/") -> target.substring(module.length + 1)
            else -> return null
        }
        val dir = if (rel.isEmpty()) moduleDir else moduleDir.resolve(rel).normalize()
        if (!dir.startsWith(moduleDir)) return null
        return index.files().firstOrNull { file ->
            file.fileName.toString().endsWith(".go") && file.parent?.normalize() == dir
        }
    }

    private fun findGoMod(activeFile: Path): Path? {
        var dir = activeFile.parent
        var hops = 0
        while (dir != null && hops < 40) {
            val candidate = dir.resolve("go.mod")
            if (Files.isRegularFile(candidate)) return candidate
            dir = dir.parent
            hops++
        }
        return null
    }

    private fun moduleOf(goMod: Path): String? {
        val mtime = try {
            Files.getLastModifiedTime(goMod).toMillis()
        } catch (e: Exception) {
            return null
        }
        cache[goMod.toString()]?.let { if (it.mtime == mtime) return it.module }
        val module = parseModule(goMod)
        cache[goMod.toString()] = Cached(mtime, module)
        return module
    }

    private fun parseModule(goMod: Path): String? {
        val lines = try {
            Files.readAllLines(goMod)
        } catch (e: Exception) {
            return null
        }
        for (raw in lines) {
            val line = raw.substringBefore("//").trim()
            if (line.startsWith("module ")) {
                return line.removePrefix("module").trim().trim('"').ifEmpty { null }
            }
        }
        return null
    }
}
