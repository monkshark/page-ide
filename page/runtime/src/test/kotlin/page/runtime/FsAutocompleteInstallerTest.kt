package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class FsAutocompleteInstallerTest {

    private var savedHome: String? = null
    private lateinit var tempHome: Path

    private fun useTempHome(): Path {
        savedHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("page-fsac-test-home-")
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

    private fun fakeDotnetInstaller(): FsAutocompleteInstaller {
        val runner = StagedRunner()
        return FsAutocompleteInstaller(
            processRunner = runner,
            osKey = "windows",
            isWindows = true,
            downloader = { _, target, onProgress ->
                Files.writeString(target, "# fake dotnet-install.ps1")
                onProgress(1, 1)
            },
            versionsFetcher = { listOf("0.69.1", "0.69.0") },
            dotnetExeName = "dotnet.exe",
        ).also { it.attachedRunner = runner }
    }

    private var FsAutocompleteInstaller.attachedRunner: StagedRunner
        get() = throw UnsupportedOperationException()
        set(value) {
            runnerByInstaller[this] = value
        }

    private val runnerByInstaller = mutableMapOf<FsAutocompleteInstaller, StagedRunner>()

    private inner class StagedRunner : ProcessRunner {
        val commands = mutableListOf<List<String>>()
        val envs = mutableListOf<Map<String, String>>()

        override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int {
            commands += command
            envs += emptyMap()
            val installDirIdx = command.indexOf("-InstallDir")
            if (installDirIdx >= 0 && installDirIdx + 1 < command.size) {
                val sdkDir = Path.of(command[installDirIdx + 1])
                sdkDir.createDirectories()
                Files.writeString(sdkDir.resolve("dotnet.exe"), "fake dotnet")
            }
            return 0
        }

        override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int {
            commands += command
            envs += env
            val toolPathIdx = command.indexOf("--tool-path")
            if (toolPathIdx >= 0 && toolPathIdx + 1 < command.size) {
                val bin = Path.of(command[toolPathIdx + 1])
                bin.createDirectories()
                Files.writeString(bin.resolve("fsautocomplete.exe"), "fake fsac")
            }
            return 0
        }

        override fun captureOutput(command: List<String>): String = ""
    }

    @Test
    fun executableNullWhenNotInstalled() {
        useTempHome()
        val installer = FsAutocompleteInstaller(
            processRunner = StagedRunner(),
            osKey = "windows", isWindows = true,
            downloader = { _, _, _ -> },
            versionsFetcher = { emptyList() },
        )
        assertNull(installer.executable())
    }

    @Test
    fun installLatestUsesPlainSpec() {
        useTempHome()
        val installer = fakeDotnetInstaller()
        installer.install(null) { }
        val runner = runnerByInstaller[installer]!!
        val toolCmd = runner.commands.last()
        assertTrue(toolCmd.last() == "fsautocomplete", "latest should use bare fsautocomplete: $toolCmd")
        assertTrue(!toolCmd.contains("--version"), "latest should not pass --version: $toolCmd")
    }

    @Test
    fun installPinnedVersionPassesVersionFlag() {
        useTempHome()
        val installer = fakeDotnetInstaller()
        installer.install("0.69.0") { }
        val runner = runnerByInstaller[installer]!!
        val toolCmd = runner.commands.last()
        val versionIdx = toolCmd.indexOf("--version")
        assertTrue(versionIdx >= 0, "pinned version should pass --version: $toolCmd")
        assertEquals("0.69.0", toolCmd[versionIdx + 1])
    }

    @Test
    fun installEndToEndWritesPointerAndBinary() {
        useTempHome()
        val installer = fakeDotnetInstaller()
        var lastEvent: LspInstaller.Progress? = null
        installer.install("0.69.0") { lastEvent = it }
        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertEquals("0.69.0", installer.installedVersion())
        assertNotNull(installer.executable())
        assertTrue(installer.isInstalled())
    }

    @Test
    fun installUsesWindowsPowershellCommand() {
        useTempHome()
        val installer = fakeDotnetInstaller()
        installer.install("0.69.0") { }
        val runner = runnerByInstaller[installer]!!
        val sdkCmd = runner.commands.first()
        assertEquals("powershell", sdkCmd[0])
        assertTrue(sdkCmd.contains("-File"))
        assertTrue(sdkCmd.contains("-Channel"))
        assertTrue(sdkCmd.contains("LTS"))
    }

    @Test
    fun installUsesBashOnLinux() {
        useTempHome()
        val runner = StagedRunner()
        val installer = FsAutocompleteInstaller(
            processRunner = object : ProcessRunner {
                override fun runStreaming(c: List<String>, o: (String) -> Unit): Int {
                    runner.commands += c
                    val idx = c.indexOf("--install-dir")
                    if (idx >= 0) {
                        val sdk = Path.of(c[idx + 1])
                        sdk.createDirectories()
                        Files.writeString(sdk.resolve("dotnet"), "fake dotnet")
                    }
                    return 0
                }
                override fun runStreaming(c: List<String>, env: Map<String, String>, o: (String) -> Unit): Int {
                    runner.commands += c
                    val idx = c.indexOf("--tool-path")
                    if (idx >= 0) {
                        val bin = Path.of(c[idx + 1])
                        bin.createDirectories()
                        Files.writeString(bin.resolve("fsautocomplete"), "fake fsac")
                    }
                    return 0
                }
                override fun captureOutput(c: List<String>): String = ""
            },
            osKey = "linux",
            isWindows = false,
            downloader = { _, target, _ -> Files.writeString(target, "#!/bin/bash") },
            versionsFetcher = { emptyList() },
            dotnetExeName = "dotnet",
        )
        installer.install("0.69.0") { }
        val sdkCmd = runner.commands.first()
        assertEquals("bash", sdkCmd[0])
        assertTrue(sdkCmd.contains("--channel"))
        assertTrue(sdkCmd.contains("--install-dir"))
    }

    @Test
    fun installFailsWhenSdkExitNonZero() {
        useTempHome()
        val installer = FsAutocompleteInstaller(
            processRunner = object : ProcessRunner {
                override fun runStreaming(c: List<String>, o: (String) -> Unit): Int = 1
                override fun runStreaming(c: List<String>, e: Map<String, String>, o: (String) -> Unit): Int = 0
                override fun captureOutput(c: List<String>): String = ""
            },
            osKey = "windows", isWindows = true,
            downloader = { _, target, _ -> Files.writeString(target, "fake") },
            versionsFetcher = { emptyList() },
            dotnetExeName = "dotnet.exe",
        )
        var failed: Throwable? = null
        installer.install("0.69.0") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
    }

    @Test
    fun nugetVersionParserExtractsAndReverses() {
        val body = """{"versions": ["0.69.0", "0.69.1", "0.70.0"]}"""
        val versions = FsAutocompleteInstaller.parseNugetVersions(body)
        assertEquals(listOf("0.70.0", "0.69.1", "0.69.0"), versions)
    }

    @Test
    fun nugetVersionParserHandlesEmpty() {
        assertEquals(emptyList(), FsAutocompleteInstaller.parseNugetVersions("""{"versions": []}"""))
        assertEquals(emptyList(), FsAutocompleteInstaller.parseNugetVersions("{}"))
    }
}
