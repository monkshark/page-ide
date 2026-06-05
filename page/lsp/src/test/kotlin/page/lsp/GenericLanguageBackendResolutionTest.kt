package page.lsp

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenericLanguageBackendResolutionTest {

    private fun definition() = LanguageDefinition(
        id = "demo",
        displayName = "Demo",
        extensions = listOf("demo"),
        lspBinaries = listOf("demo-ls"),
        lspWindowsBinaries = listOf("demo-ls.exe"),
        installGuideUrl = "",
        install = emptyMap(),
        runCommand = null,
    )

    private fun tempExecutable(prefix: String): Path {
        val file = Files.createTempFile(prefix, ".bin")
        file.toFile().setExecutable(true)
        file.toFile().deleteOnExit()
        return file
    }

    @Test
    fun userOverrideWinsOverInstaller() {
        val override = tempExecutable("override-")
        val installer = tempExecutable("installer-")
        val backend = GenericLanguageBackend(
            definition = definition(),
            executableFinder = { installer },
            userOverrideFinder = { override },
        )

        val result = backend.resolveExecutable(emptyMap())

        assertTrue(result is LanguageBackend.Resolution.Found)
        assertEquals(override, result.executable)
        assertEquals("user override", result.origin)
    }

    @Test
    fun fallsBackToInstallerWhenOverrideMissing() {
        val installer = tempExecutable("installer-")
        val backend = GenericLanguageBackend(
            definition = definition(),
            executableFinder = { installer },
            userOverrideFinder = { Path.of("/definitely/does/not/exist/demo-ls") },
        )

        val result = backend.resolveExecutable(emptyMap())

        assertTrue(result is LanguageBackend.Resolution.Found)
        assertEquals(installer, result.executable)
        assertEquals("PAGE installer", result.origin)
    }

    @Test
    fun noOverrideConfiguredFallsThrough() {
        val installer = tempExecutable("installer-")
        val backend = GenericLanguageBackend(
            definition = definition(),
            executableFinder = { installer },
        )

        val result = backend.resolveExecutable(emptyMap())

        assertTrue(result is LanguageBackend.Resolution.Found)
        assertEquals(installer, result.executable)
        assertEquals("PAGE installer", result.origin)
    }

    @Test
    fun missingOverrideRecordedInAttempts() {
        val ghost = Path.of("/definitely/does/not/exist/demo-ls")
        val backend = GenericLanguageBackend(
            definition = definition(),
            userOverrideFinder = { ghost },
        )

        val result = backend.resolveExecutable(emptyMap())

        assertTrue(result is LanguageBackend.Resolution.NotFound)
        assertTrue(result.attempted.any { it == "override=$ghost" })
    }
}
