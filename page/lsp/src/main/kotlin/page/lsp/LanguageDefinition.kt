package page.lsp

data class LanguageDefinition(
    val id: String,
    val displayName: String,
    val extensions: List<String>,
    val lspBinaries: List<String>,
    val lspWindowsBinaries: List<String>,
    val installGuideUrl: String,
    val install: Map<String, String>,
    val runCommand: String?,
    val launchArgs: List<String> = listOf("--stdio"),
    val lspLanguageId: String = id,
) {
    fun supports(extension: String?): Boolean {
        if (extension == null) return false
        val lower = extension.lowercase()
        return extensions.any { it.equals(lower, ignoreCase = true) }
    }

    fun installInstructionsFor(osKey: String): String? = install[osKey]

    companion object {
        const val OS_MACOS = "macos"
        const val OS_LINUX = "linux"
        const val OS_WINDOWS = "windows"

        fun detectOsKey(osName: String = System.getProperty("os.name") ?: ""): String {
            val lower = osName.lowercase()
            return when {
                lower.contains("mac") || lower.contains("darwin") -> OS_MACOS
                lower.contains("win") -> OS_WINDOWS
                else -> OS_LINUX
            }
        }
    }
}
