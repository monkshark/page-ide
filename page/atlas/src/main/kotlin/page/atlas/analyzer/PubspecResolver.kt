package page.atlas.analyzer

import java.nio.file.Files
import java.nio.file.Path

object PubspecResolver {

    private data class Cached(val mtime: Long, val name: String?)

    private val cache = HashMap<String, Cached>()

    fun resolve(target: String, index: WorkspaceIndex): Path? {
        if (!target.startsWith("package:")) return null
        val spec = target.removePrefix("package:")
        val pkg = spec.substringBefore('/')
        val rest = spec.substringAfter('/', "")
        if (pkg.isEmpty() || rest.isEmpty()) return null
        val suffix = "/lib/$rest"
        return index.files().firstOrNull { file ->
            val s = normalized(file)
            if (!s.endsWith(suffix)) return@firstOrNull false
            val pkgDir = s.substring(0, s.length - suffix.length)
            nameOf(Path.of(pkgDir, "pubspec.yaml")) == pkg
        }
    }

    private fun nameOf(pubspec: Path): String? {
        val mtime = try {
            Files.getLastModifiedTime(pubspec).toMillis()
        } catch (e: Exception) {
            return null
        }
        cache[pubspec.toString()]?.let { if (it.mtime == mtime) return it.name }
        val name = parseName(pubspec)
        cache[pubspec.toString()] = Cached(mtime, name)
        return name
    }

    private fun parseName(pubspec: Path): String? {
        val lines = try {
            Files.readAllLines(pubspec)
        } catch (e: Exception) {
            return null
        }
        for (raw in lines) {
            val line = raw.substringBefore('#').trim()
            if (line.startsWith("name:")) {
                return line.removePrefix("name:").trim().trim('"', '\'').ifEmpty { null }
            }
        }
        return null
    }

    private fun normalized(path: Path): String = path.toString().replace('\\', '/')
}
