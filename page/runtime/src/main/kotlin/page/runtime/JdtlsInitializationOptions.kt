package page.runtime

object JdtlsInitializationOptions {

    val importExclusions: List<String> = listOf(
        "**/node_modules/**",
        "**/.metadata/**",
        "**/archetype-resources/**",
        "**/META-INF/maven/**",
        "**/.page-ide/**",
    )

    val resourceFilters: List<String> = listOf(
        "node_modules",
        ".git",
        ".page-ide",
    )

    const val codeGenerationInsertionLocation: String = "lastMember"

    fun forWorkspace(): Map<String, Any> = mapOf(
        "settings" to mapOf(
            "java" to mapOf(
                "import" to mapOf(
                    "exclusions" to importExclusions,
                ),
                "project" to mapOf(
                    "resourceFilters" to resourceFilters,
                ),
                "codeGeneration" to mapOf(
                    "insertionLocation" to codeGenerationInsertionLocation,
                ),
            ),
        ),
    )
}
