package page.editor

import java.nio.file.Path
import java.util.Locale

object SyntaxLexers {
    fun forPath(path: Path): SyntaxLexer? {
        val name = path.fileName?.toString()?.lowercase(Locale.ROOT) ?: return null
        val ext = name.substringAfterLast('.', missingDelimiterValue = "")
        return when (ext) {
            "kt", "kts" -> KotlinLexer
            "java" -> JavaLexer
            "json" -> JsonLexer
            else -> null
        }
    }
}
