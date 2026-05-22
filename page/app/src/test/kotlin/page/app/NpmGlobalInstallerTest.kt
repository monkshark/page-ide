package page.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NpmGlobalInstallerTest {

    private var savedHome: String? = null
    private lateinit var tempHome: Path

    private fun useTempHome(): Path {
        savedHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("page-npm-test-home-")
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

    private fun ts(): NpmGlobalInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "typescript",
            displayName = "typescript-language-server",
            packageName = "typescript-language-server",
            binaryName = "typescript-language-server",
        ),
        npmFinder = { Path.of("/usr/bin/npm") },
    )

    @Test
    fun descriptorInstallKeyStripsScope() {
        val key = NpmPackageDescriptor.defaultInstallKey("@vue/language-server")
        assertEquals("vue-language-server", key)
    }

    @Test
    fun descriptorInstallKeyKeepsPlainName() {
        assertEquals("typescript-language-server", NpmPackageDescriptor.defaultInstallKey("typescript-language-server"))
    }

    @Test
    fun binaryRelativeUsesCmdOnWindows() {
        assertEquals("ts.cmd", NpmGlobalInstaller.binaryRelative("ts", "Windows 11"))
    }

    @Test
    fun binaryRelativeUsesBinDirOnUnix() {
        assertEquals("bin/ts", NpmGlobalInstaller.binaryRelative("ts", "Linux"))
    }

    @Test
    fun precheckMissingWhenNpmNotFound() {
        val installer = NpmGlobalInstaller(
            NpmPackageDescriptor("typescript", "ts", "typescript-language-server", "typescript-language-server"),
            npmFinder = { null },
        )
        val precheck = installer.precheck
        assertTrue(precheck is LspInstaller.Precheck.MissingTool, "expected MissingTool")
        assertEquals("npm", (precheck as LspInstaller.Precheck.MissingTool).tool)
    }

    @Test
    fun precheckOkWhenNpmFound() {
        assertTrue(ts().precheck is LspInstaller.Precheck.Ok)
    }

    @Test
    fun executableNullWhenInstallRootMissing() {
        useTempHome()
        assertNull(ts().executable())
    }

    @Test
    fun executableFoundWhenBinaryPlaced() {
        useTempHome()
        val installer = ts()
        val root = installer.installRoot("0.1.0")
        Files.createDirectories(root)
        val rel = NpmGlobalInstaller.binaryRelative("typescript-language-server")
        val exe = root.resolve(rel)
        exe.parent.createDirectories()
        Files.writeString(exe, "shim")
        val pointer = LspInstaller.lspHome().resolve("typescript-language-server").resolve("CURRENT")
        Files.writeString(pointer, "0.1.0")
        assertNotNull(installer.executable())
        assertTrue(installer.isInstalled())
    }

    @Test
    fun installRootSharedBetweenVscodeServers() {
        useTempHome()
        val html = NpmGlobalInstaller(
            NpmPackageDescriptor("html", "html", "vscode-langservers-extracted", "vscode-html-language-server"),
            npmFinder = { Path.of("/usr/bin/npm") },
        )
        val css = NpmGlobalInstaller(
            NpmPackageDescriptor("css", "css", "vscode-langservers-extracted", "vscode-css-language-server"),
            npmFinder = { Path.of("/usr/bin/npm") },
        )
        assertEquals(html.installRoot("4.10.0"), css.installRoot("4.10.0"))
    }

    @Test
    fun installInvokesNpmAndWritesPointer() {
        useTempHome()
        val capturedCommands = mutableListOf<List<String>>()
        val runner = object : ProcessRunner {
            override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int {
                capturedCommands += command
                onLine("added 1 package in 1s")
                val target = Path.of(command[command.indexOf("--prefix") + 1])
                val exe = target.resolve(NpmGlobalInstaller.binaryRelative("yaml-language-server"))
                exe.parent.createDirectories()
                Files.writeString(exe, "shim")
                return 0
            }

            override fun captureOutput(command: List<String>): String = ""
        }
        val installer = NpmGlobalInstaller(
            NpmPackageDescriptor("yaml", "yaml", "yaml-language-server", "yaml-language-server"),
            npmFinder = { Path.of("/usr/bin/npm") },
            processRunner = runner,
        )
        var lastEvent: LspInstaller.Progress? = null
        installer.install("1.14.0") { lastEvent = it }
        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertEquals(1, capturedCommands.size)
        val cmd = capturedCommands[0]
        assertTrue(cmd.contains("--global"), "should pass --global to isolate prefix")
        assertTrue(cmd.contains("yaml-language-server@1.14.0"), "should pin version: $cmd")
        val pointer = LspInstaller.lspHome().resolve("yaml-language-server").resolve("CURRENT")
        assertEquals("1.14.0", Files.readString(pointer).trim())
        assertEquals("1.14.0", installer.installedVersion())
    }

    @Test
    fun installFailsWhenBinaryMissingAfterRun() {
        useTempHome()
        val runner = object : ProcessRunner {
            override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = 0
            override fun captureOutput(command: List<String>): String = ""
        }
        val installer = NpmGlobalInstaller(
            NpmPackageDescriptor("yaml", "yaml", "yaml-language-server", "yaml-language-server"),
            npmFinder = { Path.of("/usr/bin/npm") },
            processRunner = runner,
        )
        var failed: Throwable? = null
        installer.install("1.14.0") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
        assertFalse(installer.isInstalled())
    }

    @Test
    fun installSpecOmitsAtSymbolForLatest() {
        useTempHome()
        val captured = mutableListOf<List<String>>()
        val runner = object : ProcessRunner {
            override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int {
                captured += command
                val target = Path.of(command[command.indexOf("--prefix") + 1])
                val exe = target.resolve(NpmGlobalInstaller.binaryRelative("typescript-language-server"))
                exe.parent.createDirectories()
                Files.writeString(exe, "shim")
                return 0
            }

            override fun captureOutput(command: List<String>): String = ""
        }
        val installer = NpmGlobalInstaller(
            NpmPackageDescriptor(
                languageId = "typescript",
                displayName = "ts",
                packageName = "typescript-language-server",
                binaryName = "typescript-language-server",
                defaultVersion = "latest",
            ),
            npmFinder = { Path.of("/usr/bin/npm") },
            processRunner = runner,
        )
        installer.install(null) { }
        val cmd = captured.single()
        assertTrue(cmd.contains("typescript-language-server"), "should pass bare package name when latest")
        assertFalse(cmd.any { it == "typescript-language-server@latest" }, "should not append @latest tag literal")
    }

    @Test
    fun installFailsCleanlyWhenNpmMissing() {
        useTempHome()
        val installer = NpmGlobalInstaller(
            NpmPackageDescriptor("yaml", "yaml", "yaml-language-server", "yaml-language-server"),
            npmFinder = { null },
        )
        var error: Throwable? = null
        installer.install("1.14.0") { p ->
            if (p is LspInstaller.Progress.Failed) error = p.error
        }
        assertNotNull(error)
    }

    @Test
    fun versionParserHandlesArray() {
        val parsed = NpmVersionParser.parseVersions("""["1.0.0", "1.1.0", "1.2.0"]""")
        assertEquals(listOf("1.2.0", "1.1.0", "1.0.0"), parsed)
    }

    @Test
    fun versionParserHandlesSingleString() {
        assertEquals(listOf("1.0.0"), NpmVersionParser.parseVersions("\"1.0.0\""))
    }

    @Test
    fun versionParserHandlesEmpty() {
        assertEquals(emptyList(), NpmVersionParser.parseVersions(""))
        assertEquals(emptyList(), NpmVersionParser.parseVersions("[]"))
    }

    @Test
    fun availableVersionsPrefersManifestOverNpmView() {
        val runner = object : ProcessRunner {
            override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = 0
            override fun captureOutput(command: List<String>): String {
                error("npm view should not be called when manifest hits")
            }
        }
        val installer = NpmGlobalInstaller(
            NpmPackageDescriptor(
                languageId = "typescript",
                displayName = "ts",
                packageName = "typescript-language-server",
                binaryName = "typescript-language-server",
            ),
            npmFinder = { Path.of("/usr/bin/npm") },
            processRunner = runner,
            manifestFetcher = { slug ->
                assertEquals("typescript-language-server", slug)
                listOf("4.4.0", "4.3.4", "4.3.3")
            },
        )
        assertEquals(listOf("4.4.0", "4.3.4", "4.3.3"), installer.availableVersions())
    }

    @Test
    fun availableVersionsFallsBackToNpmViewWhenManifestNull() {
        val captured = mutableListOf<List<String>>()
        val runner = object : ProcessRunner {
            override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = 0
            override fun captureOutput(command: List<String>): String {
                captured += command
                return """["1.0.0", "1.1.0"]"""
            }
        }
        val installer = NpmGlobalInstaller(
            NpmPackageDescriptor("yaml", "yaml", "yaml-language-server", "yaml-language-server"),
            npmFinder = { Path.of("/usr/bin/npm") },
            processRunner = runner,
            manifestFetcher = { null },
        )
        assertEquals(listOf("1.1.0", "1.0.0"), installer.availableVersions())
        assertEquals(1, captured.size)
        assertTrue(captured[0].contains("view"))
    }

    @Test
    fun availableVersionsFallsBackToNpmViewWhenManifestEmpty() {
        val runner = object : ProcessRunner {
            override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = 0
            override fun captureOutput(command: List<String>): String = """["2.0.0"]"""
        }
        val installer = NpmGlobalInstaller(
            NpmPackageDescriptor("yaml", "yaml", "yaml-language-server", "yaml-language-server"),
            npmFinder = { Path.of("/usr/bin/npm") },
            processRunner = runner,
            manifestFetcher = { emptyList() },
        )
        assertEquals(listOf("2.0.0"), installer.availableVersions())
    }

    @Test
    fun availableVersionsManifestUsesInstallKeyForScopedPackage() {
        val seenSlugs = mutableListOf<String>()
        val installer = NpmGlobalInstaller(
            NpmPackageDescriptor("vue", "vue", "@vue/language-server", "vue-language-server"),
            npmFinder = { Path.of("/usr/bin/npm") },
            manifestFetcher = { slug ->
                seenSlugs += slug
                listOf("2.1.0")
            },
        )
        assertEquals(listOf("2.1.0"), installer.availableVersions())
        assertEquals(listOf("vue-language-server"), seenSlugs)
    }
}
