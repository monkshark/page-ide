package page.runtime

import java.io.IOException
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
import kotlin.test.fail

class KlsInstallerTest {

    private val tempDirs = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        for (dir in tempDirs) {
            if (Files.exists(dir)) {
                Files.walk(dir).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { p ->
                        runCatching { Files.delete(p) }
                    }
                }
            }
        }
    }

    private fun newTempDir(prefix: String): Path {
        val p = Files.createTempDirectory(prefix)
        tempDirs.add(p)
        return p
    }

    private fun newTempFile(prefix: String, suffix: String): Path {
        val p = Files.createTempFile(prefix, suffix)
        tempDirs.add(p)
        return p
    }

    @Test
    fun installRootUsesPageIdeUnderHome() {
        val home = newTempDir("kls-home-")
        val root = KlsInstaller.installRoot(home)
        assertEquals(
            home.resolve(".page-ide").resolve("lsp").resolve("kotlin-language-server-${KlsInstaller.VERSION}"),
            root,
        )
    }

    @Test
    fun executableNameMatchesPlatform() {
        assertEquals("kotlin-language-server.bat", KlsInstaller.executableName(windows = true))
        assertEquals("kotlin-language-server", KlsInstaller.executableName(windows = false))
    }

    @Test
    fun isInstalledFalseWhenBinMissing() {
        val home = newTempDir("kls-home-")
        val root = KlsInstaller.installRoot(home)
        Files.createDirectories(root)
        assertFalse(KlsInstaller.isInstalled(root))
    }

    @Test
    fun isInstalledFalseWhenBinIsEmpty() {
        val home = newTempDir("kls-home-")
        val root = KlsInstaller.installRoot(home)
        Files.createDirectories(root.resolve("bin"))
        assertFalse(KlsInstaller.isInstalled(root))
    }

    @Test
    fun isInstalledTrueWhenBatPresent() {
        val home = newTempDir("kls-home-")
        val root = KlsInstaller.installRoot(home)
        val bin = root.resolve("bin")
        Files.createDirectories(bin)
        Files.writeString(bin.resolve("kotlin-language-server.bat"), "@echo off\n")
        assertTrue(KlsInstaller.isInstalled(root))
    }

    @Test
    fun isInstalledTrueWhenShellPresent() {
        val home = newTempDir("kls-home-")
        val root = KlsInstaller.installRoot(home)
        val bin = root.resolve("bin")
        Files.createDirectories(bin)
        Files.writeString(bin.resolve("kotlin-language-server"), "#!/usr/bin/env bash\n")
        assertTrue(KlsInstaller.isInstalled(root))
    }

    @Test
    fun executableReturnsNullBeforeInstall() {
        val home = newTempDir("kls-home-")
        val root = KlsInstaller.installRoot(home)
        assertNull(KlsInstaller.executable(root))
    }

    @Test
    fun installFromZipFlattensFirstSegmentAndPlacesBin() {
        val home = newTempDir("kls-home-")
        val target = KlsInstaller.installRoot(home)
        val zip = newTempFile("kls-zip-", ".zip")
        writeSampleZip(zip, prefix = "server")

        KlsInstaller.installFromZip(zip, target)

        assertTrue(Files.exists(target.resolve("bin").resolve("kotlin-language-server.bat")))
        assertTrue(Files.exists(target.resolve("bin").resolve("kotlin-language-server")))
        assertTrue(Files.exists(target.resolve("lib").resolve("server.jar")))
        assertTrue(KlsInstaller.isInstalled(target))
    }

    @Test
    fun installFromZipReplacesExistingTarget() {
        val home = newTempDir("kls-home-")
        val target = KlsInstaller.installRoot(home)
        Files.createDirectories(target)
        Files.writeString(target.resolve("STALE"), "stale")

        val zip = newTempFile("kls-zip-", ".zip")
        writeSampleZip(zip, prefix = "server")

        KlsInstaller.installFromZip(zip, target)

        assertFalse(Files.exists(target.resolve("STALE")), "old contents must be wiped before move")
        assertTrue(Files.exists(target.resolve("bin").resolve("kotlin-language-server.bat")))
    }

    @Test
    fun installFromZipRejectsZipSlip() {
        val home = newTempDir("kls-home-")
        val target = KlsInstaller.installRoot(home)
        val zip = newTempFile("kls-zip-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            val malicious = ZipEntry("server/../../evil.txt")
            out.putNextEntry(malicious)
            out.write("evil".toByteArray())
            out.closeEntry()
        }

        try {
            KlsInstaller.installFromZip(zip, target)
            fail("expected zip-slip rejection")
        } catch (io: IOException) {
            assertTrue(io.message?.contains("zip slip", ignoreCase = true) == true, "unexpected: ${io.message}")
        }
        val sibling = target.parent.resolve("evil.txt")
        assertFalse(Files.exists(sibling), "evil sibling must not exist")
    }

    @Test
    fun installFromZipSkipsTopLevelOnlyEntries() {
        val home = newTempDir("kls-home-")
        val target = KlsInstaller.installRoot(home)
        val zip = newTempFile("kls-zip-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("server/"))
            out.closeEntry()
            out.putNextEntry(ZipEntry("server/bin/kotlin-language-server.bat"))
            out.write("@echo".toByteArray())
            out.closeEntry()
        }
        KlsInstaller.installFromZip(zip, target)
        assertTrue(Files.exists(target.resolve("bin").resolve("kotlin-language-server.bat")))
    }

    @Test
    fun sanitizeLabelCollapsesSpacesAndParens() {
        assertEquals("1.3.13-page-1_fork", KlsInstaller.sanitizeLabel("1.3.13-page-1 (fork)"))
        assertEquals("1.3.12_upstream", KlsInstaller.sanitizeLabel("1.3.12 (upstream)"))
    }

    @Test
    fun installLabeledWritesLabelAndCurrentPointer() {
        val home = newTempDir("kls-home-")
        val label = "1.3.13-page-1 (fork)"
        val target = KlsInstaller.installRootFor(label, home)
        val zip = newTempFile("kls-zip-", ".zip")
        writeSampleZip(zip, prefix = "server")

        KlsInstaller.installFromZip(zip, target)
        Files.writeString(target.resolve("LABEL"), label)
        KlsInstaller.setActiveLabel(label, home)

        assertEquals(label, KlsInstaller.readInstalledLabel(target))
        assertEquals(label, KlsInstaller.activeLabel(home))
        assertEquals(target, KlsInstaller.installRootForLabel(label, home))
    }

    @Test
    fun installedLabelsListsEveryInstalledLabel() {
        val home = newTempDir("kls-home-")
        val labelA = "1.3.13-page-1 (fork)"
        val labelB = "1.3.12 (upstream)"

        for (label in listOf(labelA, labelB)) {
            val target = KlsInstaller.installRootFor(label, home)
            val zip = newTempFile("kls-zip-", ".zip")
            writeSampleZip(zip, prefix = "server")
            KlsInstaller.installFromZip(zip, target)
            Files.writeString(target.resolve("LABEL"), label)
        }

        val installed = KlsInstaller.installedLabels(home).toSet()
        assertEquals(setOf(labelA, labelB), installed)
    }

    @Test
    fun setActiveLabelSwapsPointerWithoutReinstall() {
        val home = newTempDir("kls-home-")
        val labelA = "1.3.13-page-1 (fork)"
        val labelB = "1.3.12 (upstream)"

        for (label in listOf(labelA, labelB)) {
            val target = KlsInstaller.installRootFor(label, home)
            val zip = newTempFile("kls-zip-", ".zip")
            writeSampleZip(zip, prefix = "server")
            KlsInstaller.installFromZip(zip, target)
            Files.writeString(target.resolve("LABEL"), label)
        }

        KlsInstaller.setActiveLabel(labelA, home)
        assertEquals(labelA, KlsInstaller.activeLabel(home))
        KlsInstaller.setActiveLabel(labelB, home)
        assertEquals(labelB, KlsInstaller.activeLabel(home))
    }

    @Test
    fun uninstallLabelRemovesRootAndClearsActive() {
        val home = newTempDir("kls-home-")
        val labelA = "1.3.13-page-1 (fork)"
        val labelB = "1.3.12 (upstream)"

        for (label in listOf(labelA, labelB)) {
            val target = KlsInstaller.installRootFor(label, home)
            val zip = newTempFile("kls-zip-", ".zip")
            writeSampleZip(zip, prefix = "server")
            KlsInstaller.installFromZip(zip, target)
            Files.writeString(target.resolve("LABEL"), label)
        }
        KlsInstaller.setActiveLabel(labelA, home)
        val rootA = KlsInstaller.installRootFor(labelA, home)

        KlsInstaller.uninstallLabel(labelA, home)

        assertFalse(Files.exists(rootA), "label dir must be gone")
        assertEquals(setOf(labelB), KlsInstaller.installedLabels(home).toSet())
        assertNull(KlsInstaller.activeLabel(home), "CURRENT must be cleared when active label removed")
    }

    @Test
    fun uninstallLabelKeepsCurrentWhenRemovingInactive() {
        val home = newTempDir("kls-home-")
        val labelA = "1.3.13-page-1 (fork)"
        val labelB = "1.3.12 (upstream)"

        for (label in listOf(labelA, labelB)) {
            val target = KlsInstaller.installRootFor(label, home)
            val zip = newTempFile("kls-zip-", ".zip")
            writeSampleZip(zip, prefix = "server")
            KlsInstaller.installFromZip(zip, target)
            Files.writeString(target.resolve("LABEL"), label)
        }
        KlsInstaller.setActiveLabel(labelA, home)

        KlsInstaller.uninstallLabel(labelB, home)

        assertEquals(setOf(labelA), KlsInstaller.installedLabels(home).toSet())
        assertEquals(labelA, KlsInstaller.activeLabel(home), "active label must survive inactive removal")
    }

    @Test
    fun uninstallLabelFallsBackToLegacyInstall() {
        val home = newTempDir("kls-home-")
        val legacyRoot = KlsInstaller.installRoot(home)
        val zip = newTempFile("kls-zip-", ".zip")
        writeSampleZip(zip, prefix = "server")
        KlsInstaller.installFromZip(zip, legacyRoot)
        assertTrue(KlsInstaller.isInstalled(legacyRoot))

        KlsInstaller.uninstallLabel("1.3.13-page-3 (fork)", home)

        assertFalse(Files.exists(legacyRoot), "legacy install must be removed when no labeled dir matches")
    }

    @Test
    fun executableAfterInstallResolvesToCorrectBinary() {
        val home = newTempDir("kls-home-")
        val target = KlsInstaller.installRoot(home)
        val zip = newTempFile("kls-zip-", ".zip")
        writeSampleZip(zip, prefix = "server")

        KlsInstaller.installFromZip(zip, target)

        val exe = KlsInstaller.executable(target)
        assertNotNull(exe, "executable must resolve after install")
        assertTrue(
            exe.fileName.toString() == "kotlin-language-server" ||
                exe.fileName.toString() == "kotlin-language-server.bat",
            "unexpected exe name: ${exe.fileName}",
        )
    }

    private fun writeSampleZip(zip: Path, prefix: String) {
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            putEntry(out, "$prefix/bin/kotlin-language-server.bat", "@echo off\n")
            putEntry(out, "$prefix/bin/kotlin-language-server", "#!/usr/bin/env bash\n")
            putEntry(out, "$prefix/lib/server.jar", "JAR")
        }
    }

    private fun putEntry(out: ZipOutputStream, name: String, body: String) {
        out.putNextEntry(ZipEntry(name))
        out.write(body.toByteArray())
        out.closeEntry()
    }
}
