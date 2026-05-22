package page.app

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DartSdkInstallerTest {

    private var savedHome: String? = null
    private lateinit var tempHome: Path

    private fun useTempHome(): Path {
        savedHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("page-dart-test-home-")
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

    private fun writeDartSdkZip(target: Path, exeName: String) {
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            zip.putNextEntry(ZipEntry("dart-sdk/bin/$exeName"))
            zip.write("fake dart binary".toByteArray())
            zip.closeEntry()
        }
    }

    private fun winInstaller(latest: String = "3.5.0"): DartSdkInstaller = DartSdkInstaller(
        osKey = "windows",
        archKey = "amd64",
        isWindows = true,
        downloader = { _, target, onProgress ->
            writeDartSdkZip(target, "dart.exe")
            onProgress(1, 1)
        },
        versionsFetcher = { listOf(latest) },
        latestResolver = { latest },
    )

    @Test
    fun downloadUrlForWindowsAmd64() {
        val installer = DartSdkInstaller(osKey = "windows", archKey = "amd64", isWindows = true)
        assertTrue(installer.downloadUrl("3.5.0").contains("dartsdk-windows-x64-release.zip"), installer.downloadUrl("3.5.0"))
    }

    @Test
    fun downloadUrlForMacosArm64() {
        val installer = DartSdkInstaller(osKey = "macos", archKey = "arm64", isWindows = false)
        assertTrue(installer.downloadUrl("3.5.0").contains("dartsdk-macos-arm64-release.zip"), installer.downloadUrl("3.5.0"))
    }

    @Test
    fun downloadUrlForLinuxAmd64() {
        val installer = DartSdkInstaller(osKey = "linux", archKey = "amd64", isWindows = false)
        assertTrue(installer.downloadUrl("3.5.0").contains("dartsdk-linux-x64-release.zip"), installer.downloadUrl("3.5.0"))
    }

    @Test
    fun executableNullWhenNotInstalled() {
        useTempHome()
        val installer = DartSdkInstaller(
            osKey = "windows", archKey = "amd64", isWindows = true,
            downloader = { _, _, _ -> },
            versionsFetcher = { emptyList() },
            latestResolver = { null },
        )
        assertNull(installer.executable())
    }

    @Test
    fun installLatestResolvesVersionThenExtracts() {
        useTempHome()
        val installer = winInstaller(latest = "3.5.0")
        var lastEvent: LspInstaller.Progress? = null
        installer.install(null) { lastEvent = it }
        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertEquals("3.5.0", installer.installedVersion())
        assertNotNull(installer.executable())
        assertTrue(installer.isInstalled())
    }

    @Test
    fun installPinnedVersionUsesPinnedPath() {
        useTempHome()
        val installer = winInstaller()
        installer.install("3.4.0") { }
        assertEquals("3.4.0", installer.installedVersion())
        assertTrue(Files.exists(installer.dartBinary("3.4.0")), "dart.exe should land in pinned 3.4.0 root")
    }

    @Test
    fun installWritesLspWrapperScript() {
        useTempHome()
        val installer = winInstaller()
        installer.install("3.5.0") { }
        val wrapper = installer.lspWrapper("3.5.0")
        assertTrue(Files.exists(wrapper), "lsp wrapper must exist at $wrapper")
        val body = Files.readString(wrapper)
        assertTrue(body.contains("language-server"), "wrapper should invoke 'dart language-server': $body")
    }

    @Test
    fun installFailsWhenLatestResolverReturnsNull() {
        useTempHome()
        val installer = DartSdkInstaller(
            osKey = "linux", archKey = "amd64", isWindows = false,
            downloader = { _, target, _ -> writeDartSdkZip(target, "dart") },
            versionsFetcher = { emptyList() },
            latestResolver = { null },
        )
        var failed: Throwable? = null
        installer.install("latest") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
    }

    @Test
    fun latestVersionParserExtractsField() {
        val body = """{"date":"2025-05-01","version":"3.5.0","channel":"stable"}"""
        assertEquals("3.5.0", DartSdkInstaller.parseLatestVersion(body))
    }

    @Test
    fun latestVersionParserHandlesMissingField() {
        assertNull(DartSdkInstaller.parseLatestVersion("""{"date":"2025-05-01"}"""))
        assertNull(DartSdkInstaller.parseLatestVersion("{}"))
    }
}
