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

    private fun writeGoSdkZip(target: Path) {
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            zip.putNextEntry(ZipEntry("go/bin/go.exe"))
            zip.write("fake go shim".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("go/src/runtime/runtime.go"))
            zip.write("package runtime".toByteArray())
            zip.closeEntry()
        }
    }

    private fun winInstaller(
        processRunner: ProcessRunner = neverCalledRunner(),
        downloader: (String, Path, (Long, Long) -> Unit) -> Unit = { _, target, onProgress ->
            writeGoSdkZip(target)
            onProgress(100, 100)
        },
        versionsFetcher: () -> List<String> = { listOf("1.22.5", "1.22.4") },
    ): GoplsInstaller = GoplsInstaller(
        processRunner = processRunner,
        osKey = "windows",
        archKey = "amd64",
        isWindows = true,
        versionsFetcher = versionsFetcher,
        downloader = downloader,
    )

    private fun neverCalledRunner(): ProcessRunner = object : ProcessRunner {
        override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = fail("runStreaming without env should not be called")
        override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int = fail("process runner should not be called in this test")
        override fun captureOutput(command: List<String>): String = fail("captureOutput should not be called")
    }

    @Test
    fun downloadUrlForLinuxAmd64() {
        val installer = GoplsInstaller(osKey = "linux", archKey = "amd64", isWindows = false)
        assertEquals("https://go.dev/dl/go1.22.5.linux-amd64.tar.gz", installer.downloadUrl("go1.22.5"))
    }

    @Test
    fun downloadUrlForMacosArm64() {
        val installer = GoplsInstaller(osKey = "macos", archKey = "arm64", isWindows = false)
        assertEquals("https://go.dev/dl/go1.22.5.darwin-arm64.tar.gz", installer.downloadUrl("go1.22.5"))
    }

    @Test
    fun downloadUrlForWindowsAmd64() {
        val installer = GoplsInstaller(osKey = "windows", archKey = "amd64", isWindows = true)
        assertEquals("https://go.dev/dl/go1.22.5.windows-amd64.zip", installer.downloadUrl("go1.22.5"))
    }

    @Test
    fun executableNullWhenNotInstalled() {
        useTempHome()
        assertNull(winInstaller().executable())
    }

    @Test
    fun availableVersionsUsesInjectedFetcher() {
        val installer = winInstaller(versionsFetcher = { listOf("1.22.5", "1.22.4", "1.21.13") })
        assertEquals(listOf("1.22.5", "1.22.4", "1.21.13"), installer.availableVersions())
    }

    @Test
    fun installEndToEndWritesGoplsBinaryAndPointer() {
        useTempHome()
        val executedCommands = mutableListOf<List<String>>()
        val capturedEnv = mutableListOf<Map<String, String>>()
        val runner = object : ProcessRunner {
            override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = fail("env-aware overload expected")
            override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int {
                executedCommands += command
                capturedEnv += env
                onLine("downloading golang.org/x/tools/gopls")
                val gopath = Path.of(env["GOPATH"]!!)
                val gopls = gopath.resolve("bin").resolve("gopls.exe")
                gopls.parent.createDirectories()
                Files.writeString(gopls, "fake gopls binary")
                return 0
            }
            override fun captureOutput(command: List<String>): String = ""
        }
        val installer = winInstaller(processRunner = runner)

        var lastEvent: LspInstaller.Progress? = null
        installer.install("1.22.5") { lastEvent = it }

        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        val installedExe = installer.executable()
        assertNotNull(installedExe)
        assertTrue(installer.isInstalled())
        assertEquals("go1.22.5", installer.installedVersion())

        assertEquals(1, executedCommands.size)
        val cmd = executedCommands.single()
        assertTrue(cmd.last() == "golang.org/x/tools/gopls@latest", "should invoke go install gopls: $cmd")
        assertTrue(cmd[0].endsWith("go.exe"), "should call downloaded go binary: $cmd")
        val env = capturedEnv.single()
        assertNotNull(env["GOPATH"])
        assertNotNull(env["GOROOT"])
        assertEquals("local", env["GOTOOLCHAIN"])
    }

    @Test
    fun installPrefixesGoVersionTagAutomatically() {
        useTempHome()
        val urls = mutableListOf<String>()
        val installer = winInstaller(
            downloader = { url, target, onProgress ->
                urls += url
                writeGoSdkZip(target)
                onProgress(1, 1)
            },
            processRunner = object : ProcessRunner {
                override fun runStreaming(c: List<String>, onLine: (String) -> Unit): Int = fail("env-aware")
                override fun runStreaming(c: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int {
                    val gopath = Path.of(env["GOPATH"]!!)
                    val exe = gopath.resolve("bin").resolve("gopls.exe")
                    exe.parent.createDirectories()
                    Files.writeString(exe, "shim")
                    return 0
                }
                override fun captureOutput(c: List<String>): String = ""
            },
        )
        installer.install("1.22.5") { }
        assertEquals(listOf("https://go.dev/dl/go1.22.5.windows-amd64.zip"), urls)
    }

    @Test
    fun installFailsWhenGoplsNotProducedByPostInstall() {
        useTempHome()
        val runner = object : ProcessRunner {
            override fun runStreaming(c: List<String>, onLine: (String) -> Unit): Int = fail("env-aware")
            override fun runStreaming(c: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int = 0
            override fun captureOutput(c: List<String>): String = ""
        }
        val installer = winInstaller(processRunner = runner)
        var failed: Throwable? = null
        installer.install("1.22.5") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
    }

    @Test
    fun installFailsWhenPostInstallExitNonZero() {
        useTempHome()
        val runner = object : ProcessRunner {
            override fun runStreaming(c: List<String>, onLine: (String) -> Unit): Int = fail("env-aware")
            override fun runStreaming(c: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int = 7
            override fun captureOutput(c: List<String>): String = ""
        }
        val installer = winInstaller(processRunner = runner)
        var failed: Throwable? = null
        installer.install("1.22.5") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
    }
}
