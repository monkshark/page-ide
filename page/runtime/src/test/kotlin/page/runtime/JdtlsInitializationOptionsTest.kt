package page.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JdtlsInitializationOptionsTest {

    @Test
    fun importExclusionsContainsPageIde() {
        assertTrue("**/.page-ide/**" in JdtlsInitializationOptions.importExclusions)
    }

    @Test
    fun importExclusionsKeepsDefaultVscodeJavaGlobs() {
        val expected = listOf(
            "**/node_modules/**",
            "**/.metadata/**",
            "**/archetype-resources/**",
            "**/META-INF/maven/**",
        )
        assertTrue(JdtlsInitializationOptions.importExclusions.containsAll(expected))
    }

    @Test
    fun resourceFiltersContainsPageIdeAndKeepsDefaults() {
        val filters = JdtlsInitializationOptions.resourceFilters
        assertTrue(".page-ide" in filters)
        assertTrue("node_modules" in filters)
        assertTrue(".git" in filters)
    }

    @Test
    fun forWorkspaceNestsExclusionsUnderSettingsJavaImport() {
        val options = JdtlsInitializationOptions.forWorkspace()

        @Suppress("UNCHECKED_CAST")
        val settings = options["settings"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val java = settings["java"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val import = java["import"] as Map<String, Any>

        assertEquals(JdtlsInitializationOptions.importExclusions, import["exclusions"])
    }

    @Test
    fun forWorkspaceNestsResourceFiltersUnderSettingsJavaProject() {
        val options = JdtlsInitializationOptions.forWorkspace()

        @Suppress("UNCHECKED_CAST")
        val settings = options["settings"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val java = settings["java"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val project = java["project"] as Map<String, Any>

        assertEquals(JdtlsInitializationOptions.resourceFilters, project["resourceFilters"])
    }

    @Test
    fun compilerProblemSeveritiesMarksUnusedImportAsWarning() {
        assertEquals(
            "warning",
            JdtlsInitializationOptions.compilerProblemSeverities["org.eclipse.jdt.core.compiler.problem.unusedImport"],
        )
    }

    @Test
    fun forWorkspaceSetsCodeGenerationInsertionLocationToLastMember() {
        val options = JdtlsInitializationOptions.forWorkspace()

        @Suppress("UNCHECKED_CAST")
        val settings = options["settings"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val java = settings["java"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val codeGeneration = java["codeGeneration"] as Map<String, Any>

        assertEquals("lastMember", codeGeneration["insertionLocation"])
    }
}
