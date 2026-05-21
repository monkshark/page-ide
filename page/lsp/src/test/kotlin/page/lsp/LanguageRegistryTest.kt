package page.lsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanguageRegistryTest {
    @Test
    fun bundledRegistryHasThirtyLanguages() {
        val all = LanguageRegistry.all()
        assertEquals(30, all.size, "expected 30 bundled language definitions, got ${all.size}")
    }

    @Test
    fun bundledRegistryHasUniqueIds() {
        val all = LanguageRegistry.all()
        val ids = all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "language ids must be unique: $ids")
    }

    @Test
    fun kotlinDefinitionMatchesSchema() {
        val def = LanguageRegistry.byId("kotlin")
        assertNotNull(def, "kotlin definition must exist")
        assertEquals("Kotlin", def.displayName)
        assertTrue(def.extensions.containsAll(listOf("kt", "kts")), "kotlin must support kt and kts")
        assertTrue(def.lspBinaries.contains("kotlin-language-server"))
        assertTrue(def.installGuideUrl.startsWith("http"), "install guide url must be http(s)")
        assertNotNull(def.installInstructionsFor(LanguageDefinition.OS_MACOS))
        assertNotNull(def.installInstructionsFor(LanguageDefinition.OS_LINUX))
        assertNotNull(def.installInstructionsFor(LanguageDefinition.OS_WINDOWS))
    }

    @Test
    fun extensionLookupWorksForCommonLanguages() {
        assertEquals("kotlin", LanguageRegistry.byExtension("kt")?.id)
        assertEquals("kotlin", LanguageRegistry.byExtension("kts")?.id)
        assertEquals("java", LanguageRegistry.byExtension("java")?.id)
        assertEquals("python", LanguageRegistry.byExtension("py")?.id)
        assertEquals("javascript", LanguageRegistry.byExtension("js")?.id)
        assertEquals("typescript", LanguageRegistry.byExtension("ts")?.id)
        assertEquals("rust", LanguageRegistry.byExtension("rs")?.id)
        assertEquals("go", LanguageRegistry.byExtension("go")?.id)
    }

    @Test
    fun extensionLookupIsCaseInsensitive() {
        assertEquals("kotlin", LanguageRegistry.byExtension("KT")?.id)
        assertEquals("java", LanguageRegistry.byExtension("Java")?.id)
    }

    @Test
    fun unknownExtensionReturnsNull() {
        assertNull(LanguageRegistry.byExtension("nope"))
        assertNull(LanguageRegistry.byExtension(null))
    }

    @Test
    fun byIdIsCaseInsensitive() {
        assertNotNull(LanguageRegistry.byId("Kotlin"))
        assertNotNull(LanguageRegistry.byId("PYTHON"))
    }

    @Test
    fun everyDefinitionHasNonEmptyExtensionsAndInstall() {
        val all = LanguageRegistry.all()
        for (def in all) {
            assertTrue(def.extensions.isNotEmpty(), "${def.id} must list at least one extension")
            assertTrue(def.lspBinaries.isNotEmpty(), "${def.id} must list at least one lsp binary")
            assertTrue(def.installGuideUrl.startsWith("http"), "${def.id} install guide url must be http(s)")
            for (os in listOf(
                LanguageDefinition.OS_MACOS,
                LanguageDefinition.OS_LINUX,
                LanguageDefinition.OS_WINDOWS,
            )) {
                val text = def.installInstructionsFor(os)
                assertTrue(!text.isNullOrBlank(), "${def.id} must have install text for $os")
            }
        }
    }

    @Test
    fun detectOsKeyClassifiesCommonNames() {
        assertEquals(LanguageDefinition.OS_WINDOWS, LanguageDefinition.detectOsKey("Windows 11"))
        assertEquals(LanguageDefinition.OS_MACOS, LanguageDefinition.detectOsKey("Mac OS X"))
        assertEquals(LanguageDefinition.OS_MACOS, LanguageDefinition.detectOsKey("Darwin"))
        assertEquals(LanguageDefinition.OS_LINUX, LanguageDefinition.detectOsKey("Linux"))
        assertEquals(LanguageDefinition.OS_LINUX, LanguageDefinition.detectOsKey("Unknown"))
    }
}
