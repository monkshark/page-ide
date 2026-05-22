package page.app

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

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

    private fun writeCsZip(target: Path) {
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            zip.putNextEntry(ZipEntry("cs-x86_64-pc-win32.exe"))
            zip.write("fake cs.exe".toByteArray())
            zip.closeEntry()
        }
    }

    private fun winInstaller(
        processRunner: ProcessRunner,
        downloader: (String, Path, (Long, Long) -> Unit) -> Unit = { _, target, onProgress ->
            writeCsZip(target)
            onProgress(1, 1)
        },
        versionsFetcher: () -> List<String> = { listOf("1.3.5", "1.3.4") },
    ): MetalsInstaller = MetalsInstaller(
        processRunner = processRunner,
        osKey = "windows",
        archKey = "amd64",
        isWindows = true,
        versionsFetcher = versionsFetcher,
        downloader = downloader,
    )

    @Test
    fun coursierUrlForLinuxAmd64() {
        val installer = MetalsInstaller(osKey = "linux", archKey = "amd64", isWindows = false)
        assertTrue(installer.coursierUrl().contains("cs-x86_64-pc-linux.gz"), installer.coursierUrl())
    }

    @Test
    fun coursierUrlForLinuxArm64() {
        val installer = MetalsInstaller(osKey = "linux", archKey = "arm64", isWindows = false)
        assertTrue(installer.coursierUrl().contains("cs-aarch64-pc-linux.gz"), installer.coursierUrl())
    }

    @Test
    fun coursierUrlForMacosArm64() {
        val installer = MetalsInstaller(osKey = "macos", archKey = "arm64", isWindows = false)
        assertTrue(installer.coursierUrl().contains("cs-aarch64-apple-darwin.gz"), installer.coursierUrl())
    }

    @Test
    fun coursierUrlForWindowsAmd64() {
        val installer = MetalsInstaller(osKey = "windows", archKey = "amd64", isWindows = true)
        assertTrue(installer.coursierUrl().endsWith("cs-x86_64-pc-win32.zip"), installer.coursierUrl())
    }

    @Test
    fun executableNullWhenNotInstalled() {
        useTempHome()
        val runner = object : ProcessRunner {
            override fun runStreaming(c: List<String>, o: (String) -> Unit): Int = fail("not called")
            override fun runStreaming(c: List<String>, e: Map<String, String>, o: (String) -> Unit): Int = fail("not called")
            override fun captureOutput(c: List<String>): String = ""
        }
        assertNull(winInstaller(runner).executable())
    }

    @Test
    fun installLatestUsesPlainSpec() {
        useTempHome()
        val capturedCommands = mutableListOf<List<String>>()
        val runner = object : ProcessRunner {
            override fun runStreaming(c: List<String>, o: (String) -> Unit): Int = fail("env-aware")
            override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int {
                capturedCommands += command
                val installDir = Path.of(command[command.indexOf("--install-dir") + 1])
                val metals = installDir.resolve("metals.bat")
                metals.parent.createDirectories()
                Files.writeString(metals, "@echo fake metals shim")
                return 0
            }
            override fun captureOutput(c: List<String>): String = ""
        }
        val installer = winInstaller(processRunner = runner)
        installer.install(null) { }
        val cmd = capturedCommands.single()
        assertEquals("metals", cmd.last(), "latest should use bare 'metals' spec, got $cmd")
    }

    @Test
    fun installPinnedVersionUsesColonSpec() {
        useTempHome()
        val capturedCommands = mutableListOf<List<String>>()
        val runner = object : ProcessRunner {
            override fun runStreaming(c: List<String>, o: (String) -> Unit): Int = fail("env-aware")
            override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int {
                capturedCommands += command
                val installDir = Path.of(command[command.indexOf("--install-dir") + 1])
                val metals = installDir.resolve("metals.bat")
                metals.parent.createDirectories()
                Files.writeString(metals, "shim")
                return 0
            }
            override fun captureOutput(c: List<String>): String = ""
        }
        val installer = winInstaller(processRunner = runner)
        installer.install("1.3.5") { }
        val cmd = capturedCommands.single()
        assertEquals("metals:1.3.5", cmd.last(), "pinned version should use 'metals:VER' spec, got $cmd")
    }

    @Test
    fun installEndToEndWritesPointerAndBinary() {
        useTempHome()
        val runner = object : ProcessRunner {
            override fun runStreaming(c: List<String>, o: (String) -> Unit): Int = fail("env-aware")
            override fun runStreaming(c: List<String>, env: Map<String, String>, o: (String) -> Unit): Int {
                val installDir = Path.of(c[c.indexOf("--install-dir") + 1])
                val metals = installDir.resolve("metals.bat")
                metals.parent.createDirectories()
                Files.writeString(metals, "shim")
                return 0
            }
            override fun captureOutput(c: List<String>): String = ""
        }
        val installer = winInstaller(processRunner = runner)
        var lastEvent: LspInstaller.Progress? = null
        installer.install("1.3.5") { lastEvent = it }
        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertEquals("1.3.5", installer.installedVersion())
        assertNotNull(installer.executable())
        assertTrue(installer.isInstalled())
    }

    @Test
    fun installFailsWhenCsExitNonZero() {
        useTempHome()
        val runner = object : ProcessRunner {
            override fun runStreaming(c: List<String>, o: (String) -> Unit): Int = fail("env-aware")
            override fun runStreaming(c: List<String>, e: Map<String, String>, o: (String) -> Unit): Int = 3
            override fun captureOutput(c: List<String>): String = ""
        }
        val installer = winInstaller(processRunner = runner)
        var failed: Throwable? = null
        installer.install("1.3.5") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
    }
}
