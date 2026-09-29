package page.atlas.analyzer

import java.nio.file.Files
import java.nio.file.Path

object ModuleLinks {

    private val PROJECT_REF = Regex("""project\(\s*(?:path\s*=\s*)?["'](:[\w.:-]*)["']\s*\)""")
    private val INCLUDE_CALL = Regex("""include\s*\(?([^)\n]*)\)?""")
    private val QUOTED = Regex("""["'](:?[\w.:-]+)["']""")

    fun read(root: Path): List<Pair<Path, Path>> = gradle(root.toAbsolutePath().normalize())

    fun gradle(root: Path): List<Pair<Path, Path>> {
        val settings = listOf("settings.gradle.kts", "settings.gradle").map { root.resolve(it) }.firstOrNull { Files.isRegularFile(it) }
            ?: return emptyList()
        val included = includedProjects(runCatching { Files.readString(settings) }.getOrDefault(""))
        val links = ArrayList<Pair<Path, Path>>()
        for (path in included) {
            val dir = dirOf(root, path)
            val build = listOf("build.gradle.kts", "build.gradle").map { dir.resolve(it) }.firstOrNull { Files.isRegularFile(it) } ?: continue
            val text = runCatching { Files.readString(build) }.getOrDefault("")
            for (target in referencedProjects(text)) {
                val targetDir = dirOf(root, target)
                if (targetDir != dir) links.add(dir to targetDir)
            }
        }
        return links.distinct()
    }

    internal fun includedProjects(settings: String): List<String> =
        settings.lineSequence()
            .map { it.substringBefore("//").trim() }
            .filter { it.startsWith("include") }
            .flatMap { line -> INCLUDE_CALL.find(line)?.groupValues?.get(1)?.let { QUOTED.findAll(it).map { m -> m.groupValues[1] } } ?: emptySequence() }
            .map { if (it.startsWith(":")) it else ":$it" }
            .distinct()
            .toList()

    internal fun referencedProjects(build: String): List<String> =
        PROJECT_REF.findAll(build).map { it.groupValues[1] }.distinct().toList()

    private fun dirOf(root: Path, gradlePath: String): Path =
        gradlePath.trim(':').split(':').filter { it.isNotEmpty() }.fold(root) { acc, seg -> acc.resolve(seg) }.normalize()
}
