package page.app

import java.nio.file.Path
import java.nio.file.Paths

interface LspInstaller {
    val languageId: String
    val displayName: String
    val precheck: Precheck
    val heavyInstall: HeavyInstallEstimate? get() = null

    fun isInstalled(): Boolean
    fun executable(): Path?
    fun install(version: String?, onProgress: (Progress) -> Unit)
    fun availableVersions(): List<String> = emptyList()
    fun defaultVersion(): String? = null
    fun installedVersion(): String? = null

    sealed class Progress {
        data class Downloading(val bytesRead: Long, val total: Long) : Progress()
        data class Extracting(val message: String = "Extracting…") : Progress()
        data class CommandOutput(val line: String) : Progress()
        data class Done(val executable: Path) : Progress()
        data class Failed(val error: Throwable) : Progress()
    }

    sealed class Precheck {
        object Ok : Precheck()
        data class MissingTool(val tool: String, val installUrl: String, val message: String) : Precheck()
    }

    data class HeavyInstallEstimate(
        val sizeEstimate: String,
        val durationEstimate: String,
        val notes: String,
    )

    companion object {
        fun osKey(osName: String = System.getProperty("os.name") ?: ""): String {
            val lower = osName.lowercase()
            return when {
                lower.contains("mac") || lower.contains("darwin") -> "macos"
                lower.contains("win") -> "windows"
                else -> "linux"
            }
        }

        fun isWindows(osName: String = System.getProperty("os.name") ?: ""): Boolean =
            osName.lowercase().contains("win")

        fun userHome(): Path = Paths.get(System.getProperty("user.home") ?: ".")

        fun lspHome(): Path = userHome().resolve(".page-ide").resolve("lsp")
    }
}
