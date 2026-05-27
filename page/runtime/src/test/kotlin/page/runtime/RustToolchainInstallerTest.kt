package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RustToolchainInstallerTest {

    @Test
    fun languageIdAndDisplayName() {
        val installer = RustToolchainInstaller()
        assertEquals("rust-runtime", installer.languageId)
        assertEquals("Rust Toolchain", installer.displayName)
    }

    @Test
    fun defaultVersionIsSet() {
        val installer = RustToolchainInstaller()
        assertEquals(RustToolchainInstaller.DEFAULT_RUST_VERSION, installer.defaultVersion())
    }

    @Test
    fun precheckIsAlwaysOk() {
        val installer = RustToolchainInstaller()
        assertTrue(installer.precheck is LspInstaller.Precheck.Ok)
    }

    @Test
    fun heavyInstallEstimateProvided() {
        val installer = RustToolchainInstaller()
        val heavy = installer.heavyInstall
        assertNotNull(heavy)
        assertTrue(heavy.sizeEstimate.isNotBlank())
        assertTrue(heavy.durationEstimate.isNotBlank())
    }

    @Test
    fun downloadUrlUsesPageIdeAssets() {
        val installer = RustToolchainInstaller(osKey = "windows", archKey = "amd64", isWindows = true)
        val url = installer.downloadUrl("1.82.0")
        assertEquals(
            "https://github.com/monkshark/page-ide-assets/releases/download/rust-toolchain-bundle/page-rust-toolchain-windows-x86_64-1.82.0.tar.gz",
            url,
        )
    }

    @Test
    fun downloadUrlMacosArm64() {
        val installer = RustToolchainInstaller(osKey = "macos", archKey = "arm64", isWindows = false)
        val url = installer.downloadUrl("1.75.0")
        assertEquals(
            "https://github.com/monkshark/page-ide-assets/releases/download/rust-toolchain-bundle/page-rust-toolchain-macos-aarch64-1.75.0.tar.gz",
            url,
        )
    }

    @Test
    fun downloadUrlLinuxAmd64() {
        val installer = RustToolchainInstaller(osKey = "linux", archKey = "amd64", isWindows = false)
        val url = installer.downloadUrl("1.82.0")
        assertEquals(
            "https://github.com/monkshark/page-ide-assets/releases/download/rust-toolchain-bundle/page-rust-toolchain-linux-x86_64-1.82.0.tar.gz",
            url,
        )
    }

    @Test
    fun versionDescComparator() {
        val versions = listOf("1.75.0", "1.82.0", "1.80.1", "1.80.0")
        val sorted = versions.sortedWith(RustToolchainInstaller.VERSION_DESC)
        assertEquals(listOf("1.82.0", "1.80.1", "1.80.0", "1.75.0"), sorted)
    }

    @Test
    fun registryExposesRustToolchainInstaller() {
        assertTrue(LspInstallers.supports("rust-runtime"))
        val installer = LspInstallers.forId("rust-runtime")
        assertNotNull(installer)
        assertTrue(installer is RustToolchainInstaller)
        assertEquals("rust-runtime", installer.languageId)
    }

    @Test
    fun versionGroupsReturnsNull() {
        val installer = RustToolchainInstaller()
        val versions = listOf("1.82.0", "1.81.0", "1.80.1", "1.80.0", "1.75.0")
        kotlin.test.assertNull(installer.versionGroups(versions))
    }
}
