package page.runtime

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object PageIdeStore {
    const val DIR_NAME = ".page-ide"

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun dirOf(workspaceRoot: Path): Path = workspaceRoot.resolve(DIR_NAME)

    fun fileOf(workspaceRoot: Path, fileName: String): Path =
        dirOf(workspaceRoot).resolve(fileName)

    fun <T> read(workspaceRoot: Path, fileName: String, type: Class<T>): T? {
        val path = fileOf(workspaceRoot, fileName)
        if (!Files.exists(path)) return null
        return runCatching {
            Files.newBufferedReader(path).use { gson.fromJson(it, type) }
        }.getOrElse {
            if (it is JsonSyntaxException) null else null
        }
    }

    fun <T> readType(workspaceRoot: Path, fileName: String, type: Type): T? {
        val path = fileOf(workspaceRoot, fileName)
        if (!Files.exists(path)) return null
        return runCatching {
            Files.newBufferedReader(path).use { gson.fromJson<T>(it, type) }
        }.getOrNull()
    }

    fun write(workspaceRoot: Path, fileName: String, value: Any) {
        runCatching {
            val dir = dirOf(workspaceRoot)
            Files.createDirectories(dir)
            ensureGitignore(workspaceRoot)
            val path = dir.resolve(fileName)
            Files.newBufferedWriter(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { gson.toJson(value, it) }
        }
    }

    fun ensureGitignore(workspaceRoot: Path) {
        runCatching {
            val gitignore = workspaceRoot.resolve(".gitignore")
            val marker = DIR_NAME
            if (!Files.exists(gitignore)) {
                if (!Files.exists(workspaceRoot.resolve(".git"))) return@runCatching
                Files.writeString(gitignore, "$marker/\n")
                return@runCatching
            }
            val lines = Files.readAllLines(gitignore)
            val already = lines.any { line ->
                val trimmed = line.trim().removePrefix("/").removeSuffix("/")
                trimmed == marker
            }
            if (already) return@runCatching
            val needsLeadingNewline = lines.isNotEmpty() && lines.last().isNotBlank()
            val appendix = buildString {
                if (needsLeadingNewline) append('\n')
                append(marker)
                append("/\n")
            }
            Files.writeString(
                gitignore,
                appendix,
                StandardOpenOption.APPEND,
            )
        }
    }
}
