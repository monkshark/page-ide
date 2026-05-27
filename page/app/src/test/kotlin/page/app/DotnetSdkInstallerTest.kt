package page.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DotnetSdkInstallerTest {

    @Test
    fun languageIdAndDisplayName() {
        val installer = DotnetSdkInstaller()
        assertEquals("dotnet-runtime", installer.languageId)
        assertEquals(".NET SDK", installer.displayName)
    }

    @Test
    fun defaultVersionIsSet() {
        val installer = DotnetSdkInstaller()
        assertEquals(DotnetSdkInstaller.DEFAULT_DOTNET_VERSION, installer.defaultVersion())
    }

    @Test
    fun precheckIsAlwaysOk() {
        val installer = DotnetSdkInstaller()
        assertTrue(installer.precheck is LspInstaller.Precheck.Ok)
    }

    @Test
    fun heavyInstallEstimateProvided() {
        val installer = DotnetSdkInstaller()
        val heavy = installer.heavyInstall
        assertNotNull(heavy)
        assertTrue(heavy.sizeEstimate.isNotBlank())
    }

    @Test
    fun downloadUrlWindowsAmd64() {
        val installer = DotnetSdkInstaller(osKey = "windows", archKey = "amd64", isWindows = true)
        val url = installer.downloadUrl("8.0.404")
        assertEquals(
            "https://github.com/monkshark/page-ide-assets/releases/download/dotnet-bundle/page-dotnet-sdk-windows-x86_64-8.0.404.zip",
            url,
        )
    }

    @Test
    fun downloadUrlMacosArm64() {
        val installer = DotnetSdkInstaller(osKey = "macos", archKey = "arm64", isWindows = false)
        val url = installer.downloadUrl("9.0.300")
        assertEquals(
            "https://github.com/monkshark/page-ide-assets/releases/download/dotnet-bundle/page-dotnet-sdk-macos-aarch64-9.0.300.tar.gz",
            url,
        )
    }

    @Test
    fun downloadUrlLinuxAmd64() {
        val installer = DotnetSdkInstaller(osKey = "linux", archKey = "amd64", isWindows = false)
        val url = installer.downloadUrl("8.0.404")
        assertEquals(
            "https://github.com/monkshark/page-ide-assets/releases/download/dotnet-bundle/page-dotnet-sdk-linux-x86_64-8.0.404.tar.gz",
            url,
        )
    }

    @Test
    fun versionDescComparator() {
        val versions = listOf("6.0.428", "8.0.404", "9.0.300", "8.0.300")
        val sorted = versions.sortedWith(DotnetSdkInstaller.VERSION_DESC)
        assertEquals(listOf("9.0.300", "8.0.404", "8.0.300", "6.0.428"), sorted)
    }

    @Test
    fun registryExposesDotnetInstaller() {
        assertTrue(LspInstallers.supports("dotnet-runtime"))
        val installer = LspInstallers.forId("dotnet-runtime")
        assertNotNull(installer)
        assertTrue(installer is DotnetSdkInstaller)
        assertEquals("dotnet-runtime", installer.languageId)
    }

    @Test
    fun dotnetRunTemplateExists() {
        val template = LanguageRunDefaults.forExtension("cs")
        assertNotNull(template)
        assertEquals("dotnet", template.command)
        assertEquals(listOf("run"), template.argTemplate)
    }
}
