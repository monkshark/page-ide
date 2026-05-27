package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellPackageInstallerTest {

    private var savedHome: String? = null
    private lateinit var tempHome: Path

    private fun useTempHome(): Path {
        savedHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("page-shell-test-home-")
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

    private fun gemSolargraph(
        managerFinder: () -> Path? = { Path.of("/usr/bin/gem") },
        binaryFinder: () -> Path? = { null },
        processRunner: ProcessRunner = NoopRunner,
    ): ShellPackageInstaller = ShellPackageInstaller(
        ShellPackageDescriptor(
            languageId = "ruby",
            displayName = "solargraph",
            managerName = "gem",
            managerInstallUrl = "https://www.ruby-lang.org/",
            binaryName = "solargraph",
            packageName = "solargraph",
            buildInstallCommand = { mgr, pkg, _ -> listOf(mgr, "install", "--no-document", pkg) },
        ),
        managerFinder = managerFinder,
        binaryFinder = binaryFinder,
        processRunner = processRunner,
    )

    @Test
    fun heavyInstallEstimateFlowsFromDescriptor() {
        val heavy = LspInstaller.HeavyInstallEstimate(
            sizeEstimate = "약 12 MB",
            durationEstimate = "약 5분",
            notes = "테스트용 견적",
        )
        val installer = ShellPackageInstaller(
            ShellPackageDescriptor(
                languageId = "ruby",
                displayName = "solargraph",
                managerName = "gem",
                managerInstallUrl = "https://example.invalid/",
                binaryName = "solargraph",
                packageName = "solargraph",
                heavyInstall = heavy,
                buildInstallCommand = { mgr, pkg, _ -> listOf(mgr, "install", pkg) },
            ),
            managerFinder = { Path.of("/usr/bin/gem") },
            binaryFinder = { null },
            processRunner = NoopRunner,
        )
        assertEquals(heavy, installer.heavyInstall)
    }

    @Test
    fun heavyInstallNullByDefault() {
        assertNull(gemSolargraph().heavyInstall)
    }

    @Test
    fun precheckMissingWhenManagerNotFound() {
        val precheck = gemSolargraph(managerFinder = { null }).precheck
        assertTrue(precheck is LspInstaller.Precheck.MissingTool)
        assertEquals("gem", (precheck as LspInstaller.Precheck.MissingTool).tool)
    }

    @Test
    fun precheckOkWhenManagerFound() {
        assertTrue(gemSolargraph().precheck is LspInstaller.Precheck.Ok)
    }

    @Test
    fun executableNullBeforeInstall() {
        useTempHome()
        assertNull(gemSolargraph(binaryFinder = { Path.of("/usr/local/bin/solargraph") }).executable())
    }

    @Test
    fun executableReturnsBinaryAfterMarkerWritten() {
        useTempHome()
        val capturedCommands = mutableListOf<List<String>>()
        val fakeBinary = Files.createTempFile("solargraph-fake-", "")
        try {
            val runner = object : ProcessRunner {
                override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int {
                    capturedCommands += command
                    onLine("Successfully installed solargraph-0.50.0")
                    return 0
                }

                override fun captureOutput(command: List<String>): String = ""
            }
            val installer = gemSolargraph(binaryFinder = { fakeBinary }, processRunner = runner)
            var done: Path? = null
            installer.install("0.50.0") { p ->
                if (p is LspInstaller.Progress.Done) done = p.executable
            }
            assertEquals(fakeBinary, done)
            assertTrue(installer.isInstalled())
            assertEquals("0.50.0", installer.installedVersion())
            val cmd = capturedCommands.single()
            assertTrue(cmd.contains("install"))
            assertTrue(cmd.contains("solargraph"))
            assertTrue(cmd.contains("--no-document"), "gem install should be non-interactive: $cmd")
        } finally {
            Files.deleteIfExists(fakeBinary)
        }
    }

    @Test
    fun installFailsWhenBinaryNotFoundAfterRun() {
        useTempHome()
        val runner = object : ProcessRunner {
            override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = 0
            override fun captureOutput(command: List<String>): String = ""
        }
        val installer = gemSolargraph(binaryFinder = { null }, processRunner = runner)
        var error: Throwable? = null
        installer.install(null) { p ->
            if (p is LspInstaller.Progress.Failed) error = p.error
        }
        assertNotNull(error)
        assertFalse(installer.isInstalled())
    }

    @Test
    fun installFailsWhenManagerMissing() {
        useTempHome()
        val installer = gemSolargraph(managerFinder = { null })
        var error: Throwable? = null
        installer.install(null) { p ->
            if (p is LspInstaller.Progress.Failed) error = p.error
        }
        assertNotNull(error)
    }

    @Test
    fun installFailsWhenNonZeroExit() {
        useTempHome()
        val fakeBinary = Files.createTempFile("solargraph-fake-", "")
        try {
            val runner = object : ProcessRunner {
                override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = 7
                override fun captureOutput(command: List<String>): String = ""
            }
            val installer = gemSolargraph(binaryFinder = { fakeBinary }, processRunner = runner)
            var error: Throwable? = null
            installer.install(null) { p ->
                if (p is LspInstaller.Progress.Failed) error = p.error
            }
            assertNotNull(error)
            assertTrue(error!!.message!!.contains("7"))
        } finally {
            Files.deleteIfExists(fakeBinary)
        }
    }

    @Test
    fun rscriptCommandUsesRepos() {
        val descriptor = ShellPackageDescriptor(
            languageId = "r",
            displayName = "r",
            managerName = "Rscript",
            managerInstallUrl = "https://www.r-project.org/",
            binaryName = "Rscript",
            packageName = "languageserver",
            buildInstallCommand = { mgr, _, _ ->
                listOf(mgr, "-e", "install.packages('languageserver', repos='https://cloud.r-project.org')")
            },
        )
        val cmd = descriptor.buildInstallCommand("Rscript", "languageserver", "latest")
        assertTrue(cmd.last().contains("install.packages"))
        assertTrue(cmd.last().contains("cloud.r-project.org"), "should pin a CRAN mirror: $cmd")
    }

    @Test
    fun toolchainDetectorOkWhenBinaryPresent() {
        val fake = Files.createTempFile("dart-fake-", "")
        try {
            val detector = ToolchainDetectInstaller(
                languageId = "dart",
                displayName = "Dart SDK",
                managerName = "dart",
                managerInstallUrl = "https://dart.dev/",
                binaryFinder = { fake },
            )
            assertTrue(detector.precheck is LspInstaller.Precheck.Ok)
            assertEquals(fake, detector.executable())
            assertEquals("system", detector.installedVersion())
        } finally {
            Files.deleteIfExists(fake)
        }
    }

    @Test
    fun toolchainDetectorMissingWhenBinaryAbsent() {
        val detector = ToolchainDetectInstaller(
            languageId = "swift",
            displayName = "swift",
            managerName = "sourcekit-lsp",
            managerInstallUrl = "https://www.swift.org/",
            binaryFinder = { null },
        )
        val precheck = detector.precheck
        assertTrue(precheck is LspInstaller.Precheck.MissingTool)
        assertEquals("sourcekit-lsp", (precheck as LspInstaller.Precheck.MissingTool).tool)
        assertNull(detector.executable())
    }

    @Test
    fun toolchainDetectorInstallSucceedsIfFoundLater() {
        val fake = Files.createTempFile("swift-fake-", "")
        try {
            val detector = ToolchainDetectInstaller(
                languageId = "swift",
                displayName = "swift",
                managerName = "sourcekit-lsp",
                managerInstallUrl = "https://www.swift.org/",
                binaryFinder = { fake },
            )
            var done: Path? = null
            detector.install(null) { p ->
                if (p is LspInstaller.Progress.Done) done = p.executable
            }
            assertEquals(fake, done)
        } finally {
            Files.deleteIfExists(fake)
        }
    }

    @Test
    fun toolchainDetectorInstallFailsWhenAbsent() {
        val detector = ToolchainDetectInstaller(
            languageId = "swift",
            displayName = "swift",
            managerName = "sourcekit-lsp",
            managerInstallUrl = "https://www.swift.org/",
            binaryFinder = { null },
        )
        var error: Throwable? = null
        detector.install(null) { p ->
            if (p is LspInstaller.Progress.Failed) error = p.error
        }
        assertNotNull(error)
    }

    private object NoopRunner : ProcessRunner {
        override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = 0
        override fun captureOutput(command: List<String>): String = ""
    }
}
