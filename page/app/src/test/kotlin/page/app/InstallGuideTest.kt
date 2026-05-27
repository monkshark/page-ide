package page.app

import page.runtime.*

import page.lsp.LanguageDefinition
import page.lsp.LanguageRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InstallGuideTest {
    private val kotlin: LanguageDefinition by lazy {
        LanguageRegistry.byId("kotlin").also { assertNotNull(it, "kotlin definition must exist") }!!
    }

    @Test
    fun osKeysCoverAllSupportedPlatforms() {
        assertEquals(
            listOf(
                LanguageDefinition.OS_MACOS,
                LanguageDefinition.OS_LINUX,
                LanguageDefinition.OS_WINDOWS,
            ),
            InstallGuide.OS_KEYS,
        )
    }

    @Test
    fun osLabelMapsKeysToDisplayNames() {
        assertEquals("macOS", InstallGuide.osLabel(LanguageDefinition.OS_MACOS))
        assertEquals("Linux", InstallGuide.osLabel(LanguageDefinition.OS_LINUX))
        assertEquals("Windows", InstallGuide.osLabel(LanguageDefinition.OS_WINDOWS))
    }

    @Test
    fun initialOsKeyDetectsHostPlatform() {
        assertEquals(LanguageDefinition.OS_MACOS, InstallGuide.initialOsKey("Mac OS X"))
        assertEquals(LanguageDefinition.OS_LINUX, InstallGuide.initialOsKey("Linux"))
        assertEquals(LanguageDefinition.OS_WINDOWS, InstallGuide.initialOsKey("Windows 11"))
    }

    @Test
    fun expectedBinariesUsesWindowsListOnWindowsAndFallsBack() {
        val winBins = InstallGuide.expectedBinaries(kotlin, LanguageDefinition.OS_WINDOWS)
        assertTrue(winBins.contains("kotlin-language-server.bat"), "windows binaries should include .bat: $winBins")

        val linuxBins = InstallGuide.expectedBinaries(kotlin, LanguageDefinition.OS_LINUX)
        assertEquals(listOf("kotlin-language-server"), linuxBins)
    }

    @Test
    fun expectedBinariesFallsBackToUnixListWhenWindowsListEmpty() {
        val noWin = LanguageDefinition(
            id = "fake",
            displayName = "Fake",
            extensions = listOf("fk"),
            lspBinaries = listOf("fake-server"),
            lspWindowsBinaries = emptyList(),
            installGuideUrl = "https://example.com",
            install = mapOf("macos" to "brew install fake"),
            runCommand = null,
        )
        assertEquals(
            listOf("fake-server"),
            InstallGuide.expectedBinaries(noWin, LanguageDefinition.OS_WINDOWS),
        )
    }

    @Test
    fun installTextReturnsOsSpecificInstructions() {
        val mac = InstallGuide.installText(kotlin, LanguageDefinition.OS_MACOS)
        assertTrue(mac.contains("brew install"), "macOS text should mention brew: $mac")

        val win = InstallGuide.installText(kotlin, LanguageDefinition.OS_WINDOWS)
        assertTrue(win.lowercase().contains("path"), "windows text should mention PATH: $win")
    }

    @Test
    fun installTextFallsBackWhenMissing() {
        val partial = LanguageDefinition(
            id = "fake",
            displayName = "Fake",
            extensions = listOf("fk"),
            lspBinaries = listOf("fake-server"),
            lspWindowsBinaries = emptyList(),
            installGuideUrl = "https://example.com",
            install = mapOf("macos" to "brew install fake"),
            runCommand = null,
        )
        val text = InstallGuide.installText(partial, LanguageDefinition.OS_LINUX)
        assertTrue(text.startsWith("No install instructions"), "fallback text expected, got: $text")
    }

    @Test
    fun formatAttemptedReturnsBlankWhenEmptyAndIndentsOtherwise() {
        assertEquals("", InstallGuide.formatAttempted(emptyList()))
        val out = InstallGuide.formatAttempted(listOf("/a", "/b"))
        assertEquals("  /a\n  /b", out)
    }
}
