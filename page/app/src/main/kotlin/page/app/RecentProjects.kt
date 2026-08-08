package page.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object RecentProjects {
    private const val FILE_NAME = "recent-projects.txt"
    private const val DIR_NAME = ".page"
    const val MAX = 8

    private fun storePath(): Path {
        System.getProperty("page.settings.dir")?.takeIf { it.isNotBlank() }?.let {
            return Path.of(it).resolve(FILE_NAME)
        }
        val home = System.getProperty("user.home")?.let(Path::of) ?: Path.of(".")
        return home.resolve(DIR_NAME).resolve(FILE_NAME)
    }

    fun load(): List<Path> {
        val path = storePath()
        if (!Files.exists(path)) return emptyList()
        return runCatching {
            Files.readAllLines(path)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { Path.of(it) }
                .filter { Files.isDirectory(it) }
                .distinct()
                .take(MAX)
        }.getOrDefault(emptyList())
    }

    fun push(project: Path) {
        runCatching {
            val normalized = project.toAbsolutePath().normalize()
            if (!Files.isDirectory(normalized)) return
            write((listOf(normalized) + load().filter { it != normalized }).take(MAX))
        }
    }

    fun remove(project: Path) {
        runCatching {
            val normalized = project.toAbsolutePath().normalize()
            write(load().filter { it != normalized })
        }
    }

    fun clear() {
        runCatching { write(emptyList()) }
    }

    private fun write(projects: List<Path>) {
        val path = storePath()
        Files.createDirectories(path.parent)
        Files.write(
            path,
            projects.map { it.toString() },
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }
}
