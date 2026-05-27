package page.runtime

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
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

class GoplsInstallerTest {

    private var savedHome: String? = null
    private lateinit var tempHome: Path

    private fun useTempHome(): Path {
        savedHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("page-gopls-test-home-")
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

    private fun writeGoplsTarGz(target: Path) {
        val raw = ByteArrayOutputStream()
        GzipCompressorOutputStream(raw).use { gz ->
            TarArchiveOutputStream(gz).use { tar ->
                val body = "fake gopls".toByteArray()
                val entry = TarArchiveEntry("gopls.exe")
                entry.size = body.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(body)
                tar.closeArchiveEntry()
            }
        }
        Files.write(target, raw.toByteArray())
    }

    private fun winInstaller(
        versions: List<String> = listOf("page-go-gopls-windows-x86_64-v0.22.0.tar.gz"),
    ): GoplsInstaller = GoplsInstaller(
        osKey = "windows",
        archKey = "amd64",
        isWindows = true,
        downloader = { _, target, onProgress ->
            writeGoplsTarGz(target)
            onProgress(1, 1)
        },
        versionsFetcher = { _, _, _ -> versions },
    )

    @Test
    fun downloadUrl() {
        val installer = GoplsInstaller(osKey = "linux", archKey = "amd64", isWindows = false)
        val url = installer.downloadUrl("v0.22.0")
        assertTrue(url.endsWith("/page-go-gopls-linux-x86_64-v0.22.0.tar.gz"), url)
    }

    @Test
    fun executableNullWhenNotInstalled() {
        useTempHome()
        val installer = GoplsInstaller(
            osKey = "windows", archKey = "amd64", isWindows = true,
            downloader = { _, _, _ -> },
            versionsFetcher = { _, _, _ -> emptyList() },
        )
        assertNull(installer.executable())
        assertFalse(installer.isInstalled())
    }

    @Test
    fun installExtractsAndSetsPointer() {
        useTempHome()
        val installer = winInstaller()
        var lastEvent: LspInstaller.Progress? = null
        installer.install("v0.22.0") { lastEvent = it }
        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertEquals("v0.22.0", installer.installedVersion())
        assertNotNull(installer.executable())
        assertTrue(installer.isInstalled())
    }

    @Test
    fun availableVersionsParsesAssetNames() {
        useTempHome()
        val installer = GoplsInstaller(
            osKey = "windows", archKey = "amd64", isWindows = true,
            downloader = { _, _, _ -> },
            versionsFetcher = { _, _, _ ->
                listOf(
                    "page-go-gopls-windows-x86_64-v0.22.0.tar.gz",
                    "page-go-gopls-windows-x86_64-v0.21.0.tar.gz",
                    "page-go-gopls-linux-x86_64-v0.22.0.tar.gz",
                )
            },
        )
        val versions = installer.availableVersions()
        assertTrue("v0.22.0" in versions)
        assertTrue("v0.21.0" in versions)
    }

    @Test
    fun applyVersionTogglesPointer() {
        useTempHome()
        val installer = winInstaller()
        installer.install("v0.22.0") { }
        assertEquals("v0.22.0", installer.activeVersion())
    }
}
