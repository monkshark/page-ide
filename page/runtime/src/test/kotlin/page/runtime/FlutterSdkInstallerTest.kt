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

class FlutterSdkInstallerTest {

    private var savedHome: String? = null
    private lateinit var tempHome: Path

    private fun useTempHome(): Path {
        savedHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("page-flutter-test-home-")
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

    private fun writeFlutterZip(target: Path, dartExeName: String) {
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            zip.putNextEntry(ZipEntry("flutter/bin/flutter.bat"))
            zip.write("@echo off\r\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("flutter/bin/cache/dart-sdk/bin/$dartExeName"))
            zip.write("fake dart".toByteArray())
            zip.closeEntry()
        }
    }

    private fun fakeReleases(): FlutterSdkInstaller.ReleasesDoc = FlutterSdkInstaller.ReleasesDoc(
        base_url = "https://example.com/releases",
        releases = listOf(
            FlutterSdkInstaller.Release(channel = "stable", version = "3.24.5", dart_sdk_arch = "x64", archive = "stable/windows/flutter_windows_3.24.5-stable.zip"),
            FlutterSdkInstaller.Release(channel = "beta", version = "3.25.0", dart_sdk_arch = "x64", archive = "beta/windows/flutter_windows_3.25.0-beta.zip"),
            FlutterSdkInstaller.Release(channel = "stable", version = "3.22.0", dart_sdk_arch = "x64", archive = "stable/windows/flutter_windows_3.22.0-stable.zip"),
            FlutterSdkInstaller.Release(channel = "stable", version = "3.24.5", dart_sdk_arch = "arm64", archive = "stable/macos/flutter_macos_arm64_3.24.5-stable.zip"),
        ),
    )

    private fun winInstaller(): FlutterSdkInstaller = FlutterSdkInstaller(
        osKey = "windows",
        archKey = "amd64",
        isWindows = true,
        downloader = { _, target, onProgress ->
            writeFlutterZip(target, "dart.exe")
            onProgress(1, 1)
        },
        releasesFetcher = { _ -> fakeReleases() },
        processRunner = object : ProcessRunner {
            override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = 0
            override fun captureOutput(command: List<String>): String = ""
        },
    )

    @Test
    fun availableVersionsListsStableOnlyForWindowsX64() {
        useTempHome()
        val installer = winInstaller()
        assertEquals(listOf("3.24.5", "3.22.0"), installer.availableVersions())
    }

    @Test
    fun availableVersionsForMacArm64FiltersArm64() {
        useTempHome()
        val installer = FlutterSdkInstaller(
            osKey = "macos",
            archKey = "arm64",
            isWindows = false,
            releasesFetcher = { _ -> fakeReleases() },
        )
        assertEquals(listOf("3.24.5"), installer.availableVersions())
    }

    @Test
    fun availableVersionsReturnsEmptyOnFetcherFailure() {
        useTempHome()
        val installer = FlutterSdkInstaller(
            osKey = "linux", archKey = "amd64", isWindows = false,
            releasesFetcher = { _ -> null },
        )
        assertEquals(emptyList(), installer.availableVersions())
    }

    @Test
    fun installLatestExtractsAndWritesWrapper() {
        useTempHome()
        val installer = winInstaller()
        var lastEvent: LspInstaller.Progress? = null
        installer.install(null) { lastEvent = it }
        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertEquals("3.24.5", installer.installedVersion())
        assertNotNull(installer.executable())
        val wrapper = installer.lspWrapper("3.24.5")
        val body = Files.readString(wrapper)
        assertTrue(body.contains("language-server"), "wrapper should invoke dart language-server: $body")
    }

    @Test
    fun installPinnedVersionUsesPinnedPath() {
        useTempHome()
        val installer = winInstaller()
        installer.install("3.22.0") { }
        assertEquals("3.22.0", installer.installedVersion())
        assertTrue(Files.exists(installer.dartBinary("3.22.0")), "dart should land in 3.22.0 root")
    }

    @Test
    fun installFailsWhenRequestedVersionMissing() {
        useTempHome()
        val installer = winInstaller()
        var failed: Throwable? = null
        installer.install("9.9.9") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
    }

    @Test
    fun parseReleasesAcceptsMinimalDoc() {
        val json = """
            {"base_url":"https://x","releases":[{"channel":"stable","version":"3.24.5","archive":"stable/x.zip","dart_sdk_arch":"x64"}]}
        """.trimIndent()
        val doc = FlutterSdkInstaller.parseReleases(json)
        assertNotNull(doc)
        assertEquals(1, doc!!.releases?.size)
        assertEquals("stable", doc.releases?.first()?.channel)
    }

    @Test
    fun manifestUrlByOsKey() {
        assertTrue(FlutterSdkInstaller.manifestUrl("windows").endsWith("releases_windows.json"))
        assertTrue(FlutterSdkInstaller.manifestUrl("macos").endsWith("releases_macos.json"))
        assertTrue(FlutterSdkInstaller.manifestUrl("linux").endsWith("releases_linux.json"))
    }

    @Test
    fun executableNullWhenNotInstalled() {
        useTempHome()
        val installer = FlutterSdkInstaller(
            osKey = "windows", archKey = "amd64", isWindows = true,
            releasesFetcher = { _ -> null },
        )
        assertNull(installer.executable())
    }

    @Test
    fun installedVersionsListsAllInstalledRoots() {
        useTempHome()
        val installer = winInstaller()
        installer.install("3.22.0") { }
        installer.install("3.24.5") { }
        assertEquals(listOf("3.24.5", "3.22.0"), installer.installedVersions())
    }

    @Test
    fun applyVersionTogglesCurrentPointerWithoutReinstall() {
        useTempHome()
        var downloadCalls = 0
        val installer = FlutterSdkInstaller(
            osKey = "windows", archKey = "amd64", isWindows = true,
            downloader = { _, target, onProgress ->
                downloadCalls++
                writeFlutterZip(target, "dart.exe")
                onProgress(1, 1)
            },
            releasesFetcher = { _ -> fakeReleases() },
            processRunner = object : ProcessRunner {
                override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = 0
                override fun captureOutput(command: List<String>): String = ""
            },
        )
        installer.install("3.22.0") { }
        installer.install("3.24.5") { }
        val downloadsAfterInstall = downloadCalls
        assertEquals("3.24.5", installer.activeVersion())
        assertTrue(installer.applyVersion("3.22.0"))
        assertEquals("3.22.0", installer.activeVersion())
        assertEquals(downloadsAfterInstall, downloadCalls, "applyVersion should not redownload")
    }

    @Test
    fun applyVersionRejectsMissingInstall() {
        useTempHome()
        val installer = winInstaller()
        installer.install("3.24.5") { }
        assertFalse(installer.applyVersion("9.9.9"))
        assertEquals("3.24.5", installer.activeVersion())
    }

    @Test
    fun availableVersionsIncludesInstalledEvenWhenOffline() {
        useTempHome()
        val installer = winInstaller()
        installer.install("3.22.0") { }
        val offline = FlutterSdkInstaller(
            osKey = "windows", archKey = "amd64", isWindows = true,
            releasesFetcher = { _ -> null },
        )
        assertTrue("3.22.0" in offline.availableVersions(), "installed roots must survive offline fetch")
    }
}
