package page.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RustAnalyzerInstallerTest {

    @Test
    fun languageIdAndDisplayName() {
        val installer = RustAnalyzerInstaller()
        assertEquals("rust", installer.languageId)
        assertEquals("rust-analyzer", installer.displayName)
    }

    @Test
    fun defaultVersionIsSet() {
        val installer = RustAnalyzerInstaller()
        assertEquals(RustAnalyzerInstaller.DEFAULT_VERSION, installer.defaultVersion())
    }

    @Test
    fun downloadUrlUsesPageIdeAssets() {
        val installer = RustAnalyzerInstaller(osKey = "windows", archKey = "amd64", isWindows = true)
        val url = installer.downloadUrl("2025-05-26")
        assertEquals(
            "https://github.com/monkshark/page-ide-assets/releases/download/rust-analyzer-bundle/page-rust-analyzer-windows-x86_64-2025-05-26.gz",
            url,
        )
    }

    @Test
    fun downloadUrlMacosArm64() {
        val installer = RustAnalyzerInstaller(osKey = "macos", archKey = "arm64", isWindows = false)
        val url = installer.downloadUrl("2025-05-26")
        assertEquals(
            "https://github.com/monkshark/page-ide-assets/releases/download/rust-analyzer-bundle/page-rust-analyzer-macos-aarch64-2025-05-26.gz",
            url,
        )
    }

    @Test
    fun downloadUrlLinuxAmd64() {
        val installer = RustAnalyzerInstaller(osKey = "linux", archKey = "amd64", isWindows = false)
        val url = installer.downloadUrl("2025-05-26")
        assertEquals(
            "https://github.com/monkshark/page-ide-assets/releases/download/rust-analyzer-bundle/page-rust-analyzer-linux-x86_64-2025-05-26.gz",
            url,
        )
    }

    @Test
    fun registryExposesRustAnalyzerInstaller() {
        assertTrue(LspInstallers.supports("rust"))
        val installer = LspInstallers.forId("rust")
        assertNotNull(installer)
        assertTrue(installer is RustAnalyzerInstaller)
        assertEquals("rust", installer.languageId)
    }

    @Test
    fun precheckIsOk() {
        val installer = RustAnalyzerInstaller()
        assertTrue(installer.precheck is LspInstaller.Precheck.Ok)
    }
}
