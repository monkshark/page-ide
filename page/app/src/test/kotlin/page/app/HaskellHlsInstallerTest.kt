package page.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HaskellHlsInstallerTest {

    private var savedHome: String? = null
    private lateinit var tempHome: Path

    private fun useTempHome(): Path {
        savedHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("page-hls-test-home-")
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

    private inner class StagedRunner : ProcessRunner {
        val commands = mutableListOf<List<String>>()
        val envs = mutableListOf<Map<String, String>>()
        override fun runStreaming(c: List<String>, o: (String) -> Unit): Int = error("env-aware only")
        override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int {
            commands += command
            envs += env
            val basePrefix = env["GHCUP_INSTALL_BASE_PREFIX"] ?: return 0
            val bin = Path.of(basePrefix).resolve(".ghcup").resolve("bin")
            bin.createDirectories()
            val wrapper = bin.resolve("haskell-language-server-wrapper.exe")
            Files.writeString(wrapper, "fake hls wrapper")
            return 0
        }
        override fun captureOutput(c: List<String>): String = ""
    }

    private fun winInstaller(runner: StagedRunner): HaskellHlsInstaller = HaskellHlsInstaller(
        processRunner = runner,
        osKey = "windows",
        archKey = "amd64",
        isWindows = true,
        downloader = { _, target, onProgress ->
            Files.writeString(target, "fake ghcup binary")
            onProgress(1, 1)
        },
        versionsFetcher = { listOf("2.10.0.0", "2.9.0.1") },
    )

    @Test
    fun ghcupUrlForWindowsAmd64() {
        val installer = HaskellHlsInstaller(osKey = "windows", archKey = "amd64", isWindows = true)
        assertTrue(installer.ghcupUrl().endsWith("x86_64-mingw64-ghcup-${HaskellHlsInstaller.DEFAULT_GHCUP_VERSION}.exe"))
    }

    @Test
    fun ghcupUrlForLinuxArm64() {
        val installer = HaskellHlsInstaller(osKey = "linux", archKey = "arm64", isWindows = false)
        assertTrue(installer.ghcupUrl().endsWith("aarch64-linux-ghcup-${HaskellHlsInstaller.DEFAULT_GHCUP_VERSION}"))
    }

    @Test
    fun ghcupUrlForMacosArm64() {
        val installer = HaskellHlsInstaller(osKey = "macos", archKey = "arm64", isWindows = false)
        assertTrue(installer.ghcupUrl().endsWith("aarch64-apple-darwin-ghcup-${HaskellHlsInstaller.DEFAULT_GHCUP_VERSION}"))
    }

    @Test
    fun executableNullWhenNotInstalled() {
        useTempHome()
        val installer = HaskellHlsInstaller(
            processRunner = StagedRunner(),
            osKey = "windows", isWindows = true,
            downloader = { _, _, _ -> },
            versionsFetcher = { emptyList() },
        )
        assertNull(installer.executable())
    }

    @Test
    fun installLatestPassesNoVersionArg() {
        useTempHome()
        val runner = StagedRunner()
        val installer = winInstaller(runner)
        installer.install(null) { }
        val cmd = runner.commands.last()
        assertTrue(cmd.contains("hls"), cmd.toString())
        assertTrue(cmd.contains("--set"), cmd.toString())
        val idx = cmd.indexOf("hls")
        val nextTokens = cmd.subList(idx + 1, cmd.size)
        assertTrue(nextTokens.first() == "--set", "latest should jump from 'hls' straight to '--set': $cmd")
    }

    @Test
    fun installPinnedVersionAppendsAfterHls() {
        useTempHome()
        val runner = StagedRunner()
        val installer = winInstaller(runner)
        installer.install("2.10.0.0") { }
        val cmd = runner.commands.last()
        val hlsIdx = cmd.indexOf("hls")
        assertTrue(hlsIdx >= 0)
        assertEquals("2.10.0.0", cmd[hlsIdx + 1], "pinned version should follow 'hls': $cmd")
    }

    @Test
    fun installEndToEndWritesPointerAndWrapper() {
        useTempHome()
        val runner = StagedRunner()
        val installer = winInstaller(runner)
        var lastEvent: LspInstaller.Progress? = null
        installer.install("2.10.0.0") { lastEvent = it }
        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertEquals("2.10.0.0", installer.installedVersion())
        assertNotNull(installer.executable())
        assertTrue(installer.isInstalled())
    }

    @Test
    fun installPassesGhcupIsolationEnv() {
        useTempHome()
        val runner = StagedRunner()
        val installer = winInstaller(runner)
        installer.install("2.10.0.0") { }
        val env = runner.envs.last()
        assertNotNull(env["GHCUP_INSTALL_BASE_PREFIX"], "must set GHCUP_INSTALL_BASE_PREFIX for isolation")
        assertEquals("0", env["GHCUP_TUI"])
        assertEquals("1", env["BOOTSTRAP_HASKELL_NONINTERACTIVE"])
    }

    @Test
    fun installFailsWhenGhcupExitNonZero() {
        useTempHome()
        val failingRunner = object : ProcessRunner {
            override fun runStreaming(c: List<String>, o: (String) -> Unit): Int = error("env-aware only")
            override fun runStreaming(c: List<String>, e: Map<String, String>, o: (String) -> Unit): Int = 1
            override fun captureOutput(c: List<String>): String = ""
        }
        val installer = HaskellHlsInstaller(
            processRunner = failingRunner,
            osKey = "linux", isWindows = false,
            downloader = { _, target, _ -> Files.writeString(target, "fake") },
            versionsFetcher = { emptyList() },
        )
        var failed: Throwable? = null
        installer.install("2.10.0.0") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
    }
}
