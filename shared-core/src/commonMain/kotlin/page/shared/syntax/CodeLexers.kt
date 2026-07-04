package page.shared.syntax

object CodeLexers {
    fun forLang(lang: String?): SyntaxLexer? = when (lang?.trim()?.lowercase()) {
        "kotlin", "kt", "kts" -> KotlinLexer
        "java" -> JavaLexer
        "json" -> JsonLexer
        else -> null
    }
}
