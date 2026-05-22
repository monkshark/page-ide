package page.app

object ArchDetect {

    fun archKey(osArch: String = System.getProperty("os.arch") ?: ""): String {
        val lower = osArch.lowercase()
        return when {
            lower.contains("aarch64") || lower.contains("arm64") -> "arm64"
            lower.contains("amd64") || lower.contains("x86_64") || lower.contains("x64") -> "amd64"
            lower.contains("386") || lower == "x86" || lower.contains("i386") || lower.contains("i686") -> "386"
            else -> "amd64"
        }
    }
}
