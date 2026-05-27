package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetalsInstallerTest {

    private var savedHome: String? = null
    private lateinit var tempHome: Path

    private fun useTempHome(): Path {
        savedHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("page-metals-test-home-")
        System.setProperty("user.home", tempHome.toString())
        return tempHome
    }

    @AfterTest
    fun restoreHome() {
        savedHome?.let { System.setProperty("user.home", it) }
        savedHome = null
        if (::tempHome.isInitialized) {
            runCatching {
                Files.walk(tempHome).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun writeMetalsZip(target: Path) {
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            zip.putNextEntry(ZipEntry("bin/metals.bat"))
            zip.write("@echo off\r\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("jre/bin/java.exe"))
            zip.write("fake java".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("lib/metals.jar"))
            zip.write("fake jar".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("page-bundle-manifest.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
    }

    private fun winInstaller(
        assets: List<String> = listOf(
            "page-scala-metals-windows-x86_64-1.6.7.zip",
            "page-scala-metals-windows-x86_64-1.6.0.zip",
            "page-scala-metals-linux-x86_64-1.6.7.tar.gz",
            "page-scala-metals-macos-aarch64-1.6.7.tar.gz",
        ),
        staticManifest: List<String>? = null,
        downloads: MutableList<String> = mutableListOf(),
    ): MetalsInstaller = MetalsInstaller(
        osKey = "windows",
        archKey = "amd64",
        isWindows = true,
        downloader = { url, target, onProgress ->
            downloads += url
            writeMetalsZip(target)
            onProgress(1, 1)
        },
        staticManifestFetcher = { _ -> staticManifest },
        assetsFetcher = { _, _, _ -> assets },
    )

    @Test
    fun assetNameRegexExtractsVersionForWindowsX86() {
        val match = MetalsInstaller.ASSET_NAME_REGEX.matchEntire("page-scala-metals-windows-x86_64-1.6.7.zip")
        assertNotNull(match)
        assertEquals("windows", match.groupValues[1])
        assertEquals("x86_64", match.groupValues[2])
        assertEquals("1.6.7", match.groupValues[3])
    }

    @Test
    fun assetNameRegexExtractsVersionForMacArm64() {
        val match = MetalsInstaller.ASSET_NAME_REGEX.matchEntire("page-scala-metals-macos-aarch64-1.6.7.tar.gz")
        assertNotNull(match)
        assertEquals("macos", match.groupValues[1])
        assertEquals("aarch64", match.groupValues[2])
        assertEquals("1.6.7", match.groupValues[3])
    }

    @Test
    fun assetNameRegexRejectsForeignPattern() {
        assertNull(MetalsInstaller.ASSET_NAME_REGEX.matchEntire("page-dart-sdk-linux-x86_64-3.5.0.tar.gz"))
        assertNull(MetalsInstaller.ASSET_NAME_REGEX.matchEntire("metals-1.6.7.jar"))
    }

    @Test
    fun bundleUrlForWindowsAmd64() {
        val installer = MetalsInstaller(osKey = "windows", archKey = "amd64", isWindows = true)
        assertEquals(
            "https://github.com/monkshark/page-ide-assets/releases/download/metals-bundle/page-scala-metals-windows-x86_64-1.6.7.zip",
            installer.bundleUrl("1.6.7"),
        )
    }

    @Test
    fun bundleUrlForMacArm64() {
        val installer = MetalsInstaller(osKey = "macos", archKey = "arm64", isWindows = false)
        assertEquals(
            "https://github.com/monkshark/page-ide-assets/releases/download/metals-bundle/page-scala-metals-macos-aarch64-1.6.7.tar.gz",
            installer.bundleUrl("1.6.7"),
        )
    }

    @Test
    fun availableVersionsFiltersOsArchAndSortsDesc() {
        val installer = winInstaller(
            assets = listOf(
                "page-scala-metals-windows-x86_64-1.6.0.zip",
                "page-scala-metals-windows-x86_64-1.6.7.zip",
                "page-scala-metals-windows-x86_64-1.6.5.zip",
                "page-scala-metals-linux-x86_64-1.6.7.tar.gz",
                "page-scala-metals-macos-aarch64-1.6.7.tar.gz",
            ),
        )
        assertEquals(listOf("1.6.7", "1.6.5", "1.6.0"), installer.availableVersions())
    }

    @Test
    fun availableVersionsReturnsEmptyOnFetcherFailure() {
        val installer = MetalsInstaller(
            osKey = "linux", archKey = "amd64", isWindows = false,
            staticManifestFetcher = { null },
            assetsFetcher = { _, _, _ -> throw RuntimeException("boom") },
        )
        assertEquals(emptyList(), installer.availableVersions())
    }

    @Test
    fun executableNullWhenNotInstalled() {
        useTempHome()
        val installer = winInstaller(assets = emptyList())
        assertNull(installer.executable())
    }

    @Test
    fun installLatestResolvesFromAssetList() {
        useTempHome()
        val downloads = mutableListOf<String>()
        val installer = winInstaller(downloads = downloads)
        var lastEvent: LspInstaller.Progress? = null
        installer.install(null) { lastEvent = it }
        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertEquals("1.6.7", installer.installedVersion())
        assertEquals(installer.bundleUrl("1.6.7"), downloads.single())
        assertNotNull(installer.executable())
    }

    @Test
    fun installPinnedVersionUsesPinnedPath() {
        useTempHome()
        val installer = winInstaller()
        installer.install("1.6.0") { }
        assertEquals("1.6.0", installer.installedVersion())
        assertTrue(Files.exists(installer.metalsBinary("1.6.0")), "metals.bat should land in pinned 1.6.0 root")
    }

    @Test
    fun installedVersionsListsAllInstalledRoots() {
        useTempHome()
        val installer = winInstaller()
        installer.install("1.6.0") { }
        installer.install("1.6.7") { }
        assertEquals(listOf("1.6.7", "1.6.0"), installer.installedVersions())
    }

    @Test
    fun applyVersionTogglesCurrentPointerWithoutReinstall() {
        useTempHome()
        val downloads = mutableListOf<String>()
        val installer = winInstaller(downloads = downloads)
        installer.install("1.6.0") { }
        installer.install("1.6.7") { }
        assertEquals("1.6.7", installer.activeVersion())
        val downloadsAfterInstall = downloads.size

        assertTrue(installer.applyVersion("1.6.0"))
        assertEquals("1.6.0", installer.activeVersion())
        assertEquals(downloadsAfterInstall, downloads.size, "applyVersion should not redownload")
    }

    @Test
    fun applyVersionRejectsMissingInstall() {
        useTempHome()
        val installer = winInstaller()
        installer.install("1.6.7") { }
        assertFalse(installer.applyVersion("9.9.9"))
        assertEquals("1.6.7", installer.activeVersion())
    }

    @Test
    fun availableVersionsIncludesInstalledEvenWhenOffline() {
        useTempHome()
        val installer = winInstaller()
        installer.install("1.6.0") { }
        val offline = MetalsInstaller(
            osKey = "windows", archKey = "amd64", isWindows = true,
            staticManifestFetcher = { null },
            assetsFetcher = { _, _, _ -> emptyList() },
        )
        assertTrue("1.6.0" in offline.availableVersions(), "installed roots must survive offline fetch")
    }

    @Test
    fun installFailsWhenAssetsEmpty() {
        useTempHome()
        val installer = winInstaller(assets = emptyList())
        var failed: Throwable? = null
        installer.install("latest") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
        assertTrue(failed!!.message!!.contains("metals-bundle"))
    }

    @Test
    fun availableVersionsPrefersStaticManifestOverApiFetcher() {
        val installer = winInstaller(
            assets = listOf("page-scala-metals-windows-x86_64-9.9.9.zip"),
            staticManifest = listOf(
                "page-scala-metals-windows-x86_64-1.6.7.zip",
                "page-scala-metals-windows-x86_64-1.6.0.zip",
            ),
        )
        assertEquals(listOf("1.6.7", "1.6.0"), installer.availableVersions())
    }

    @Test
    fun availableVersionsFallsBackToFetcherWhenManifestEmpty() {
        val installer = winInstaller(
            assets = listOf("page-scala-metals-windows-x86_64-1.6.7.zip"),
            staticManifest = emptyList(),
        )
        assertEquals(listOf("1.6.7"), installer.availableVersions())
    }

    @Test
    fun availableVersionsFallsBackToFetcherWhenManifestNull() {
        val installer = winInstaller(
            assets = listOf("page-scala-metals-windows-x86_64-1.6.7.zip"),
            staticManifest = null,
        )
        assertEquals(listOf("1.6.7"), installer.availableVersions())
    }

    @Test
    fun parseAssetNamesManifestExtractsAssetEntries() {
        val body = """
            {
              "updatedAt": "2026-05-23T00:00:00Z",
              "tag": "metals-bundle",
              "assets": [
                "page-scala-metals-windows-x86_64-1.6.7.zip",
                "page-scala-metals-linux-x86_64-1.6.7.tar.gz"
              ]
            }
        """.trimIndent()
        assertEquals(
            listOf(
                "page-scala-metals-windows-x86_64-1.6.7.zip",
                "page-scala-metals-linux-x86_64-1.6.7.tar.gz",
            ),
            LspStaticManifest.parseAssetNames(body),
        )
    }

    @Test
    fun versionDescSortsHighestFirst() {
        val mixed = listOf("1.6.0", "1.6.7", "1.5.2", "1.6.10", "1.6.5")
        val sorted = mixed.sortedWith(MetalsInstaller.VERSION_DESC)
        assertEquals(listOf("1.6.10", "1.6.7", "1.6.5", "1.6.0", "1.5.2"), sorted)
    }
}
