package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object JavaRuntimeProbe {

    fun systemJavaHomeAtLeast(minMajor: Int): Path? {
        val output = runCatching { runJavaSettings() }.getOrNull() ?: return null
        val major = parseSpecMajor(output) ?: return null
        if (major < minMajor) return null
        val home = parseJavaHome(output) ?: return null
        val path = runCatching { Paths.get(home) }.getOrNull() ?: return null
        return path.takeIf { Files.isDirectory(it.resolve("bin")) }
    }

    private fun runJavaSettings(): String {
        val proc = ProcessBuilder("java", "-XshowSettings:properties", "-version")
            .redirectErrorStream(true)
            .start()
        val text = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor()
        return text
    }

    internal fun parseSpecMajor(output: String): Int? {
        val line = output.lineSequence().firstOrNull { it.contains("java.specification.version") } ?: return null
        return majorFromVersionString(line.substringAfter('=').trim())
    }

    internal fun majorFromVersionString(raw: String): Int? {
        val v = raw.trim()
        if (v.startsWith("1.")) return v.removePrefix("1.").substringBefore('.').toIntOrNull()
        return v.substringBefore('.').substringBefore('-').substringBefore('+').toIntOrNull()
    }

    internal fun parseJavaHome(output: String): String? {
        val line = output.lineSequence().firstOrNull { it.trimStart().startsWith("java.home") } ?: return null
        return line.substringAfter('=').trim().takeIf { it.isNotEmpty() }
    }
}
