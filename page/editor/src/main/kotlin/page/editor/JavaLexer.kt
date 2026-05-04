package page.editor

object JavaLexer : SyntaxLexer by JavaLexerImpl

private object JavaLexerImpl : JvmLexer(
    keywords = JAVA_KEYWORDS,
    supportTripleQuoted = true,
)

private val JAVA_KEYWORDS: Set<String> = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "false", "final", "finally", "float", "for", "goto", "if",
    "implements", "import", "instanceof", "int", "interface", "long", "native",
    "new", "non-sealed", "null", "package", "permits", "private", "protected",
    "public", "record", "return", "sealed", "short", "static", "strictfp",
    "super", "switch", "synchronized", "this", "throw", "throws", "transient",
    "true", "try", "var", "void", "volatile", "while", "yield",
)
