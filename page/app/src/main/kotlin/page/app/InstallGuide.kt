package page.app

import page.runtime.*

import page.lsp.LanguageDefinition

object InstallGuide {
    val OS_KEYS: List<String> = listOf(
        LanguageDefinition.OS_MACOS,
        LanguageDefinition.OS_LINUX,
        LanguageDefinition.OS_WINDOWS,
    )

    fun osLabel(key: String): String = when (key) {
        LanguageDefinition.OS_MACOS -> "macOS"
        LanguageDefinition.OS_LINUX -> "Linux"
        LanguageDefinition.OS_WINDOWS -> "Windows"
        else -> key
    }

    fun initialOsKey(
        currentOs: String = System.getProperty("os.name") ?: "",
    ): String = LanguageDefinition.detectOsKey(currentOs)

    fun expectedBinaries(def: LanguageDefinition, osKey: String): List<String> {
        val win = osKey == LanguageDefinition.OS_WINDOWS
        val list = if (win && def.lspWindowsBinaries.isNotEmpty()) def.lspWindowsBinaries
        else def.lspBinaries
        return list.distinct()
    }

    fun installText(def: LanguageDefinition, osKey: String): String =
        def.installInstructionsFor(osKey)
            ?: "No install instructions available for ${osLabel(osKey)}."

    fun formatAttempted(paths: List<String>): String {
        if (paths.isEmpty()) return ""
        return paths.joinToString(separator = "\n") { "  $it" }
    }
}
