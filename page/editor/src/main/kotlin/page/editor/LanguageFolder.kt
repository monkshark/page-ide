package page.editor

interface LanguageFolder {
    fun detect(text: String): List<FoldRegions.Region>
}

object BraceFolder : LanguageFolder {
    override fun detect(text: String): List<FoldRegions.Region> = FoldRegions.detect(text)
}

object LanguageFolders {
    fun forExtension(ext: String?): LanguageFolder = when (ext?.lowercase()) {
        "kt", "kts" -> TreeSitterKotlinFolder
        "java" -> TreeSitterJavaFolder
        else -> BraceFolder
    }
}
