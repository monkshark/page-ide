package page.app

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdkInstallerTest {

    private var savedHome: String? = null
    private lateinit var tempHome: Path

    private fun useTempHome(): Path {
        savedHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("page-jdk-test-home-")
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

    private fun writeJdkZip(target: Path) {
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            zip.putNextEntry(ZipEntry("bin/java.exe"))
            zip.write("fake java.exe".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("lib/modules"))
            zip.write("fake modules blob".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("page-bundle-manifest.json"))
            zip.write("""{"jdk_major":21}""".toByteArray())
            zip.closeEntry()
        }
    }

    private fun writeJdkTarGz(target: Path) {
        val raw = ByteArrayOutputStream()
        GzipCompressorOutputStream(raw).use { gz ->
            TarArchiveOutputStream(gz).use { tar ->
                fun put(name: String, body: ByteArray) {
                    val entry = TarArchiveEntry(name)
                    entry.size = body.size.toLong()
                    tar.putArchiveEntry(entry)
                    tar.write(body)
                    tar.closeArchiveEntry()
                }
                put("bin/java", "fake java".toByteArray())
                put("lib/modules", "fake modules blob".toByteArray())
                put("page-bundle-manifest.json", """{"jdk_major":21}""".toByteArray())
            }
        }
        Files.write(target, raw.toByteArray())
    }

    private fun winInstaller(
        versions: List<String> = listOf("page-java-temurin-windows-x86_64-21.0.5-11.zip"),
    ): JdkInstaller = JdkInstaller(
        osKey = "windows",
        archKey = "amd64",
        isWindows = true,
        downloader = { _, target, onProgress ->
            writeJdkZip(target)
            onProgress(1, 1)
        },
        versionsFetcher = { _, _, _ -> versions },
        manifestFetcher = { emptyList() },
    )

    private fun linuxInstaller(
        versions: List<String> = listOf("page-java-temurin-linux-x86_64-21.0.5-11.tar.gz"),
    ): JdkInstaller = JdkInstaller(
        osKey = "linux",
        archKey = "amd64",
        isWindows = false,
        downloader = { _, target, onProgress ->
            writeJdkTarGz(target)
            onProgress(1, 1)
        },
        versionsFetcher = { _, _, _ -> versions },
        manifestFetcher = { emptyList() },
    )

    @Test
    fun downloadUrlForWindowsAmd64() {
        val installer = JdkInstaller(osKey = "windows", archKey = "amd64", isWindows = true)
        val url = installer.downloadUrl("21.0.5+11")
        assertTrue(url.endsWith("/page-java-temurin-windows-x86_64-21.0.5-11.zip"), url)
        assertTrue(url.contains("/jdk-bundle/"), url)
    }

    @Test
    fun downloadUrlForLinuxAarch64() {
        val installer = JdkInstaller(osKey = "linux", archKey = "arm64", isWindows = false)
        val url = installer.downloadUrl("17.0.13+11")
        assertTrue(url.endsWith("/page-java-temurin-linux-aarch64-17.0.13-11.tar.gz"), url)
    }

    @Test
    fun downloadUrlForMacosArm64() {
        val installer = JdkInstaller(osKey = "macos", archKey = "arm64", isWindows = false)
        val url = installer.downloadUrl("21.0.5+11")
        assertTrue(url.endsWith("/page-java-temurin-macos-aarch64-21.0.5-11.tar.gz"), url)
    }

    @Test
    fun executableNullWhenNotInstalled() {
        useTempHome()
        val installer = JdkInstaller(
            osKey = "windows", archKey = "amd64", isWindows = true,
            downloader = { _, _, _ -> },
            versionsFetcher = { _, _, _ -> emptyList() },
            manifestFetcher = { emptyList() },
        )
        assertNull(installer.executable())
        assertFalse(installer.isInstalled())
    }

    @Test
    fun installExtractsAndSetsPointer() {
        useTempHome()
        val installer = winInstaller()
        var lastEvent: LspInstaller.Progress? = null
        installer.install("21.0.5+11") { lastEvent = it }
        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertEquals("21.0.5-11", installer.installedVersion())
        assertNotNull(installer.executable())
        assertTrue(installer.isInstalled())
    }

    @Test
    fun installAcceptsTarGzOnLinux() {
        useTempHome()
        val installer = linuxInstaller()
        installer.install("21.0.5+11") { }
        assertEquals("21.0.5-11", installer.activeVersion())
        val java = installer.javaBinary("21.0.5-11")
        assertTrue(Files.exists(java), "java should land at $java")
    }

    @Test
    fun installSkipsRedownloadWhenAlreadyPresent() {
        useTempHome()
        var downloads = 0
        val installer = JdkInstaller(
            osKey = "windows", archKey = "amd64", isWindows = true,
            downloader = { _, target, _ ->
                downloads++
                writeJdkZip(target)
            },
            versionsFetcher = { _, _, _ -> emptyList() },
            manifestFetcher = { emptyList() },
        )
        installer.install("21.0.5+11") { }
        val firstDownloads = downloads
        installer.install("21.0.5+11") { }
        assertEquals(firstDownloads, downloads, "second install should reuse extracted root")
    }

    @Test
    fun availableVersionsParsesAssetNames() {
        useTempHome()
        val installer = JdkInstaller(
            osKey = "linux", archKey = "amd64", isWindows = false,
            downloader = { _, _, _ -> },
            versionsFetcher = { _, _, _ ->
                listOf(
                    "page-java-temurin-linux-x86_64-21.0.5-11.tar.gz",
                    "page-java-temurin-linux-x86_64-17.0.13-11.tar.gz",
                    "page-java-temurin-linux-x86_64-11.0.25-9.tar.gz",
                    "page-java-temurin-linux-aarch64-21.0.5-11.tar.gz",
                    "page-java-temurin-windows-x86_64-21.0.5-11.zip",
                    "noise-not-matching.tar.gz",
                )
            },
            manifestFetcher = { emptyList() },
        )
        val versions = installer.availableVersions()
        assertEquals(listOf("21.0.5-11", "17.0.13-11", "11.0.25-9"), versions)
        assertFalse(versions.any { it.startsWith("noise") })
    }

    @Test
    fun applyVersionTogglesCurrentPointerWithoutReinstall() {
        useTempHome()
        var downloads = 0
        val installer = JdkInstaller(
            osKey = "windows", archKey = "amd64", isWindows = true,
            downloader = { _, target, _ ->
                downloads++
                writeJdkZip(target)
            },
            versionsFetcher = { _, _, _ -> emptyList() },
            manifestFetcher = { emptyList() },
        )
        installer.install("17.0.13+11") { }
        installer.install("21.0.5+11") { }
        val downloadsAfterInstall = downloads
        assertEquals("21.0.5-11", installer.activeVersion())
        assertTrue(installer.applyVersion("17.0.13+11"))
        assertEquals("17.0.13-11", installer.activeVersion())
        assertEquals(downloadsAfterInstall, downloads, "applyVersion should not redownload")
    }

    @Test
    fun applyVersionAcceptsSanitizedForm() {
        useTempHome()
        val installer = winInstaller()
        installer.install("21.0.5+11") { }
        assertTrue(installer.applyVersion("21.0.5-11"))
        assertEquals("21.0.5-11", installer.activeVersion())
    }

    @Test
    fun applyVersionRejectsMissingInstall() {
        useTempHome()
        val installer = winInstaller()
        installer.install("21.0.5+11") { }
        assertFalse(installer.applyVersion("8.0.452+9"))
        assertEquals("21.0.5-11", installer.activeVersion())
    }

    @Test
    fun installedVersionsListsAllInstalledRoots() {
        useTempHome()
        val installer = winInstaller()
        installer.install("17.0.13+11") { }
        installer.install("21.0.5+11") { }
        val installed = installer.installedVersions()
        assertEquals(listOf("21.0.5-11", "17.0.13-11"), installed)
    }

    @Test
    fun availableVersionsIncludesInstalledEvenWhenOffline() {
        useTempHome()
        winInstaller().install("17.0.13+11") { }
        val offline = JdkInstaller(
            osKey = "windows", archKey = "amd64", isWindows = true,
            downloader = { _, _, _ -> },
            versionsFetcher = { _, _, _ -> emptyList() },
            manifestFetcher = { emptyList() },
        )
        assertTrue("17.0.13-11" in offline.availableVersions(), "installed roots must survive offline fetch")
    }

    @Test
    fun sanitizeReplacesPlusWithDash() {
        assertEquals("21.0.5-11", JdkInstaller.sanitize("21.0.5+11"))
        assertEquals("8.0.452-9", JdkInstaller.sanitize("8.0.452+9"))
    }

    @Test
    fun versionDescSortsHighestFirst() {
        val mixed = listOf("11.0.25-9", "21.0.5-11", "8.0.452-9", "17.0.13-11")
        val sorted = mixed.sortedWith(JdkInstaller.VERSION_DESC)
        assertEquals(listOf("21.0.5-11", "17.0.13-11", "11.0.25-9", "8.0.452-9"), sorted)
    }

    @Test
    fun availableVersionsIncludesManifestEntries() {
        useTempHome()
        val installer = JdkInstaller(
            osKey = "linux", archKey = "amd64", isWindows = false,
            downloader = { _, _, _ -> },
            versionsFetcher = { _, _, _ -> emptyList() },
            manifestFetcher = { listOf("21.0.7+6", "17.0.13+11", "8.0.492+9") },
        )
        val versions = installer.availableVersions()
        assertTrue("21.0.7-6" in versions, "should contain 21.0.7-6, got: $versions")
        assertTrue("17.0.13-11" in versions, "should contain 17.0.13-11, got: $versions")
        assertTrue("8.0.492-9" in versions, "should contain 8.0.492-9, got: $versions")
    }
}
