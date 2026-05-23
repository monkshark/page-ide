package page.app

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JdtlsInstallerTest {

    private var savedHome: String? = null
    private var tempHome: Path? = null

    private fun useTempHome(): Path {
        savedHome = System.getProperty("user.home")
        val dir = Files.createTempDirectory("page-jdtls-test-home-")
        tempHome = dir
        System.setProperty("user.home", dir.toString())
        return dir
    }

    @AfterTest
    fun restoreHome() {
        savedHome?.let { System.setProperty("user.home", it) }
        savedHome = null
        tempHome?.let { root ->
            runCatching {
                Files.walk(root).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
        tempHome = null
    }

    private fun seedInstalledRoot(version: String) {
        val launcherName = if (LspInstaller.isWindows()) "jdtls.bat" else "jdtls.sh"
        val root = LspInstaller.lspHome().resolve("jdtls").resolve(version)
        Files.createDirectories(root)
        Files.writeString(root.resolve(launcherName), "fake-launcher")
    }

    private fun writePointer(version: String) {
        val pointer = LspInstaller.lspHome().resolve("jdtls").resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }


    @Test
    fun parseMilestoneVersionsExtractsSemverDirs() {
        val html = """
            <html><body>
            <a href="../">Parent Directory</a>
            <a href="1.59.0/">1.59.0/</a>
            <a href="1.58.0/">1.58.0/</a>
            <a href="1.43.1/">1.43.1/</a>
            <a href="latest.txt">latest.txt</a>
            <a href="some-other-link/">some-other-link/</a>
            </body></html>
        """.trimIndent()
        val out = JdtlsInstaller.parseMilestoneVersions(html)
        assertEquals(listOf("1.59.0", "1.58.0", "1.43.1"), out)
    }

    @Test
    fun parseMilestoneVersionsDedupesAndSorts() {
        val html = """
            <a href="1.43.1/">x</a>
            <a href="1.43.1/">y</a>
            <a href="1.43.0/">z</a>
            <a href="1.59.0/">a</a>
        """.trimIndent()
        assertEquals(listOf("1.59.0", "1.43.1", "1.43.0"), JdtlsInstaller.parseMilestoneVersions(html))
    }

    @Test
    fun versionDescOrdersHighestFirst() {
        val mixed = listOf("1.43.0", "1.59.0", "1.58.10", "1.58.2", "2.0.0")
        val sorted = mixed.sortedWith(JdtlsInstaller.VERSION_DESC)
        assertEquals(listOf("2.0.0", "1.59.0", "1.58.10", "1.58.2", "1.43.0"), sorted)
    }

    @Test
    fun availableVersionsPrependsSnapshotLatest() {
        useTempHome()
        val installer = JdtlsInstaller(
            baseUrl = "https://example.com/jdtls",
            versionsFetcher = { _ -> listOf("1.59.0", "1.58.0") },
            snapshotFileFetcher = { _ -> "jdt-language-server-latest.tar.gz" },
            milestoneFileFetcher = { _, v -> "jdt-language-server-$v-202605111959.tar.gz" },
        )
        assertEquals(listOf("snapshot-latest", "1.59.0", "1.58.0"), installer.availableVersions())
    }

    @Test
    fun availableVersionsFallsBackToSnapshotOnlyOnFetcherFailure() {
        useTempHome()
        val installer = JdtlsInstaller(
            baseUrl = "https://example.com/jdtls",
            versionsFetcher = { _ -> throw IOException("boom") },
        )
        assertEquals(listOf("snapshot-latest"), installer.availableVersions())
    }

    @Test
    fun installComposesMilestoneDownloadUrl() {
        val urls = mutableListOf<String>()
        val installer = JdtlsInstaller(
            baseUrl = "https://example.com/jdtls",
            versionsFetcher = { _ -> listOf("1.59.0") },
            snapshotFileFetcher = { _ -> "snap.tar.gz" },
            milestoneFileFetcher = { _, v -> "jdt-language-server-$v-202605111959.tar.gz" },
            downloader = { url, _, _ ->
                urls += url
                throw IOException("stop here — URL capture only")
            },
        )
        installer.install("1.59.0") { }
        assertEquals(1, urls.size)
        assertEquals(
            "https://example.com/jdtls/milestones/1.59.0/jdt-language-server-1.59.0-202605111959.tar.gz",
            urls.single(),
        )
    }

    @Test
    fun installComposesSnapshotDownloadUrlForSnapshotLatest() {
        val urls = mutableListOf<String>()
        val installer = JdtlsInstaller(
            baseUrl = "https://example.com/jdtls",
            versionsFetcher = { _ -> emptyList() },
            snapshotFileFetcher = { _ -> "jdt-language-server-latest.tar.gz" },
            milestoneFileFetcher = { _, _ -> null },
            downloader = { url, _, _ ->
                urls += url
                throw IOException("stop here — URL capture only")
            },
        )
        installer.install("snapshot-latest") { }
        assertEquals(1, urls.size)
        assertEquals(
            "https://example.com/jdtls/snapshots/jdt-language-server-latest.tar.gz",
            urls.single(),
        )
    }

    @Test
    fun installEmitsFailedWhenSnapshotFileMissing() {
        val installer = JdtlsInstaller(
            baseUrl = "https://example.com/jdtls",
            versionsFetcher = { _ -> emptyList() },
            snapshotFileFetcher = { _ -> null },
            milestoneFileFetcher = { _, _ -> null },
        )
        var failed: Throwable? = null
        installer.install("snapshot-latest") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
        assertTrue(failed!!.message!!.contains("파일명 조회 실패"))
    }

    @Test
    fun installedVersionsListsAllRootsWithLauncher() {
        useTempHome()
        seedInstalledRoot("1.58.0")
        seedInstalledRoot("1.59.0")
        seedInstalledRoot("snapshot-latest")
        val installer = JdtlsInstaller(versionsFetcher = { _ -> emptyList() })
        assertEquals(listOf("snapshot-latest", "1.59.0", "1.58.0"), installer.installedVersions())
    }

    @Test
    fun installedVersionsIgnoresRootsWithoutLauncher() {
        useTempHome()
        val root = LspInstaller.lspHome().resolve("jdtls").resolve("1.59.0")
        Files.createDirectories(root)
        val installer = JdtlsInstaller(versionsFetcher = { _ -> emptyList() })
        assertTrue(installer.installedVersions().isEmpty())
    }

    @Test
    fun applyVersionTogglesCurrentPointerWhenLauncherPresent() {
        useTempHome()
        seedInstalledRoot("1.58.0")
        seedInstalledRoot("1.59.0")
        writePointer("1.59.0")
        val installer = JdtlsInstaller(versionsFetcher = { _ -> emptyList() })
        assertEquals("1.59.0", installer.activeVersion())
        assertTrue(installer.applyVersion("1.58.0"))
        assertEquals("1.58.0", installer.activeVersion())
    }

    @Test
    fun applyVersionRejectsMissingInstall() {
        useTempHome()
        seedInstalledRoot("1.59.0")
        writePointer("1.59.0")
        val installer = JdtlsInstaller(versionsFetcher = { _ -> emptyList() })
        assertFalse(installer.applyVersion("9.9.9"))
        assertEquals("1.59.0", installer.activeVersion())
    }

    @Test
    fun availableVersionsIncludesInstalledEvenWhenOffline() {
        useTempHome()
        seedInstalledRoot("1.58.0")
        val installer = JdtlsInstaller(versionsFetcher = { _ -> throw IOException("boom") })
        val versions = installer.availableVersions()
        assertTrue("snapshot-latest" in versions)
        assertTrue("1.58.0" in versions, "installed roots must survive offline fetch")
    }

    @Test
    fun availableVersionsDoesNotDuplicateInstalledAndDiscovered() {
        useTempHome()
        seedInstalledRoot("1.59.0")
        val installer = JdtlsInstaller(versionsFetcher = { _ -> listOf("1.59.0", "1.58.0") })
        val versions = installer.availableVersions()
        assertEquals(versions.size, versions.distinct().size, "no duplicates")
        assertEquals(listOf("snapshot-latest", "1.59.0", "1.58.0"), versions)
    }
}
