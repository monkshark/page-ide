package page.shared.docs

private const val EN_SUFFIX = "_en"

fun isEnVariant(path: String): Boolean = path.removeSuffix(".md").endsWith(EN_SUFFIX)

fun enVariant(path: String): String {
    if (isEnVariant(path)) return path
    val base = path.removeSuffix(".md")
    return "$base$EN_SUFFIX.md"
}

fun koVariant(path: String): String {
    if (!isEnVariant(path)) return path
    val base = path.removeSuffix(".md").removeSuffix(EN_SUFFIX)
    return "$base.md"
}

fun variantFor(path: String, english: Boolean, available: Set<String>): String {
    val target = if (english) enVariant(path) else koVariant(path)
    return if (target in available) target else path
}
