package page.app

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.io.path.createDirectories
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream

class RubyBootstrapInstallerTest {

    private var savedHome: String? = null
    private lateinit var tempHome: Path

    private fun useTempHome(): Path {
        savedHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("page-ruby-test-home-")
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

    private fun writeFakeMacTarGz(target: Path) {
        GZIPOutputStream(Files.newOutputStream(target)).use { gz ->
            TarArchiveOutputStream(gz).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
                listOf(
                    "portable-ruby/3.3.7/bin/ruby" to "fake ruby",
                    "portable-ruby/3.3.7/bin/gem" to "fake gem",
                ).forEach { (name, body) ->
                    val entry = TarArchiveEntry(name)
                    val bytes = body.toByteArray()
                    entry.size = bytes.size.toLong()
                    entry.mode = "0755".toInt(8)
                    tar.putArchiveEntry(entry)
                    tar.write(bytes)
                    tar.closeArchiveEntry()
                }
            }
        }
    }

    private fun winInstaller(
        processRunner: ProcessRunner = neverCalledRunner(),
        downloader: (String, Path, (Long, Long) -> Unit) -> Unit = { _, target, onProgress ->
            Files.writeString(target, "fake all-in-one bundle zip")
            onProgress(100, 100)
        },
        zipExtractor: (Path, Path, Int) -> Unit = { _, dst, _ ->
            val rubyBin = dst.resolve("bin")
            Files.createDirectories(rubyBin)
            Files.writeString(rubyBin.resolve("ruby.exe"), "fake ruby")
            Files.writeString(rubyBin.resolve("gem.cmd"), "@ruby gem")
            val solBin = dst.resolve("gemhome").resolve("bin")
            Files.createDirectories(solBin)
            Files.writeString(solBin.resolve("solargraph.bat"), "@ruby solargraph shim")
        },
        bundleOverride: () -> String? = { null },
        versionsFetcher: (String, String, String) -> List<String> = { _, _, _ -> emptyList() },
    ): RubyBootstrapInstaller = RubyBootstrapInstaller(
        processRunner = processRunner,
        osKey = "windows",
        archKey = "amd64",
        isWindows = true,
        downloader = downloader,
        zipExtractor = zipExtractor,
        bundleOverridePath = bundleOverride,
        versionsFetcher = versionsFetcher,
        macVersionsFetcher = { emptyList() },
    )

    private fun macInstaller(
        processRunner: ProcessRunner = neverCalledRunner(),
        archKey: String = "arm64",
        downloader: (String, Path, (Long, Long) -> Unit) -> Unit = { _, target, onProgress ->
            writeFakeMacTarGz(target)
            onProgress(100, 100)
        },
        macVersionsFetcher: () -> List<String> = { emptyList() },
    ): RubyBootstrapInstaller = RubyBootstrapInstaller(
        processRunner = processRunner,
        osKey = "macos",
        archKey = archKey,
        isWindows = false,
        downloader = downloader,
        versionsFetcher = { _, _, _ -> emptyList() },
        macVersionsFetcher = macVersionsFetcher,
    )

    private fun neverCalledRunner(): ProcessRunner = object : ProcessRunner {
        override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int =
            fail("processRunner.runStreaming should not be called (no-env overload): $command")
        override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int =
            fail("processRunner.runStreaming should not be called (env overload): $command")
        override fun captureOutput(command: List<String>): String =
            fail("captureOutput should not be called: $command")
    }

    private fun winNoOpRunner(
        avProducts: List<String> = emptyList(),
        uacExit: Int = 0,
    ): ProcessRunner = object : ProcessRunner {
        override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int =
            if (isUacCommand(command)) uacExit
            else fail("unexpected no-env command: $command")
        override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int =
            fail("env overload must not be invoked on Windows all-in-one path: $command")
        override fun captureOutput(command: List<String>): String =
            if (isAvDetectCommand(command)) avProducts.joinToString("\n")
            else fail("unexpected captureOutput: $command")
    }

    private fun isUacCommand(command: List<String>): Boolean =
        command.firstOrNull()?.equals("powershell.exe", ignoreCase = true) == true &&
            command.joinToString(" ").let { joined ->
                joined.contains("Start-Process") && joined.contains("-Verb RunAs")
            }

    private fun isAvDetectCommand(command: List<String>): Boolean =
        command.firstOrNull()?.equals("powershell.exe", ignoreCase = true) == true &&
            command.joinToString(" ").contains("Win32_AntiVirusProduct".lowercase(), ignoreCase = true) ||
            command.joinToString(" ").contains("AntiVirusProduct")

    @Test
    fun heavyInstallNonNull() {
        val installer = winInstaller()
        val heavy = installer.heavyInstall
        assertNotNull(heavy)
        assertTrue(heavy.sizeEstimate.isNotBlank())
        assertTrue(heavy.durationEstimate.isNotBlank())
        assertTrue(heavy.notes.isNotBlank())
        assertTrue(
            heavy.notes.contains("all-in-one") || heavy.notes.contains("bundle"),
            "Windows notes must mention prebuilt bundle: ${heavy.notes}",
        )
    }

    @Test
    fun precheckIsOk() {
        assertTrue(winInstaller().precheck is LspInstaller.Precheck.Ok)
        assertTrue(macInstaller().precheck is LspInstaller.Precheck.Ok)
    }

    @Test
    fun rubyBundleUrlForWindows() {
        val installer = RubyBootstrapInstaller(
            osKey = "windows",
            archKey = "amd64",
            isWindows = true,
            versionsFetcher = { _, _, _ -> emptyList() },
            macVersionsFetcher = { emptyList() },
        )
        val url = installer.rubyBundleUrl("3.4.6")
        assertTrue(url.startsWith("https://github.com/monkshark/page-ide-assets/"), "bundle URL must point at page-ide-assets repo: $url")
        assertTrue(url.endsWith("-3.4.6.zip"), "bundle URL must include version suffix .zip: $url")
        assertTrue(url.contains("page-ruby-solargraph-windows-x86_64"), "bundle URL must match published asset name: $url")
        assertTrue(url.contains("/releases/download/ruby-bundle/"), "bundle URL must reference the ruby-bundle release tag: $url")
    }

    @Test
    fun downloadUrlForMacosArm64() {
        val installer = RubyBootstrapInstaller(osKey = "macos", archKey = "arm64", isWindows = false)
        assertEquals(
            "https://github.com/Homebrew/homebrew-portable-ruby/releases/download/3.4.6/portable-ruby-3.4.6.arm64_big_sur.bottle.tar.gz",
            installer.downloadUrl("3.4.6"),
        )
    }

    @Test
    fun downloadUrlForMacosX86() {
        val installer = RubyBootstrapInstaller(osKey = "macos", archKey = "amd64", isWindows = false)
        assertEquals(
            "https://github.com/Homebrew/homebrew-portable-ruby/releases/download/3.4.6/portable-ruby-3.4.6.el_capitan.bottle.tar.gz",
            installer.downloadUrl("3.4.6"),
        )
    }

    @Test
    fun executableNullBeforeInstall() {
        useTempHome()
        assertNull(winInstaller().executable())
        assertNull(macInstaller().executable())
    }

    @Test
    fun availableVersionsFallsBackToDefaultWhenFetcherEmpty() {
        useTempHome()
        val installer = winInstaller(versionsFetcher = { _, _, _ -> emptyList() })
        assertEquals(listOf(RubyBootstrapInstaller.DEFAULT_RUBY_VERSION), installer.availableVersions())
    }

    @Test
    fun availableVersionsParsesWindowsAssetFilenames() {
        useTempHome()
        val captured = mutableListOf<Triple<String, String, String>>()
        val installer = winInstaller(versionsFetcher = { owner, repo, tag ->
            captured += Triple(owner, repo, tag)
            listOf(
                "page-ruby-solargraph-windows-x86_64-3.4.6.zip",
                "page-ruby-solargraph-windows-x86_64-3.3.11.zip",
                "page-ruby-solargraph-windows-x86_64-3.2.11.zip",
                "page-ruby-solargraph-windows-x86_64-3.4.9.zip",
                "README.md",
            )
        })
        val versions = installer.availableVersions()
        assertEquals(listOf("monkshark" to "page-ide-assets"), captured.map { it.first to it.second })
        assertEquals("ruby-bundle", captured[0].third)
        assertEquals(listOf("3.4.9", "3.4.6", "3.3.11", "3.2.11"), versions)
    }

    @Test
    fun availableVersionsParsesMacPortableTags() {
        useTempHome()
        val installer = macInstaller(macVersionsFetcher = { listOf("3.4.6", "3.3.8", "3.3.7", "3.1.4") })
        val versions = installer.availableVersions()
        assertEquals(listOf("3.4.6", "3.3.8", "3.3.7", "3.1.4"), versions)
    }

    @Test
    fun availableVersionsIgnoresMalformedAssetNames() {
        useTempHome()
        val installer = winInstaller(versionsFetcher = { _, _, _ ->
            listOf(
                "page-ruby-solargraph-linux-x86_64-3.4.6.tar.gz",
                "page-gopls-windows-x86_64-0.16.0.zip",
                "page-ruby-solargraph-windows-x86_64-abc.zip",
                "page-ruby-solargraph-windows-x86_64-3.4.6.zip",
            )
        })
        assertEquals(listOf("3.4.6"), installer.availableVersions())
    }

    @Test
    fun installEndToEndOnWindowsDownloadsBundleAndExtractsSolargraph() {
        useTempHome()
        val noEnvCommands = mutableListOf<List<String>>()
        val capturedCommands = mutableListOf<List<String>>()
        val downloadedUrls = mutableListOf<String>()
        val extractedTo = mutableListOf<Path>()

        val installer = winInstaller(
            processRunner = object : ProcessRunner {
                override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int {
                    noEnvCommands += command
                    return if (isUacCommand(command)) { onLine("added"); 0 }
                    else fail("unexpected no-env command: $command")
                }
                override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int =
                    fail("env overload must not be invoked on Windows all-in-one path: $command")
                override fun captureOutput(command: List<String>): String {
                    capturedCommands += command
                    return if (isAvDetectCommand(command)) "" else fail("unexpected captureOutput: $command")
                }
            },
            downloader = { url, target, onProgress ->
                downloadedUrls += url
                Files.writeString(target, "fake all-in-one bundle zip")
                onProgress(100, 100)
            },
            zipExtractor = { _, dst, _ ->
                extractedTo.add(dst)
                val rubyBin = dst.resolve("bin")
                Files.createDirectories(rubyBin)
                Files.writeString(rubyBin.resolve("ruby.exe"), "fake ruby")
                Files.writeString(rubyBin.resolve("gem.cmd"), "@ruby gem")
                val solBin = dst.resolve("gemhome").resolve("bin")
                Files.createDirectories(solBin)
                Files.writeString(solBin.resolve("solargraph.bat"), "@ruby solargraph shim")
            },
        )

        var lastEvent: LspInstaller.Progress? = null
        installer.install("3.3.7") { lastEvent = it }

        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertNotNull(installer.executable())
        assertTrue(installer.isInstalled())
        assertEquals("3.3.7", installer.installedVersion())

        assertEquals(
            1, noEnvCommands.size,
            "expected exactly one no-env invocation (UAC Defender exclusion only — no silent install, no ridk, no gem install): $noEnvCommands",
        )
        val uac = noEnvCommands[0]
        assertEquals("powershell.exe", uac[0])
        val uacJoined = uac.joinToString(" ")
        assertTrue(uacJoined.contains("Start-Process"))
        assertTrue(uacJoined.contains("-Verb RunAs"))
        assertTrue(uacJoined.contains("Add-MpPreference"))
        assertTrue(uacJoined.contains(installer.rubyRoot("3.3.7").toString()))

        assertEquals(1, capturedCommands.size, "expected AV detection captureOutput call: $capturedCommands")
        assertTrue(isAvDetectCommand(capturedCommands[0]), "captureOutput must be the AV detection query: ${capturedCommands[0]}")

        assertEquals(1, downloadedUrls.size, "expected exactly one bundle download: $downloadedUrls")
        val bundleUrl = installer.rubyBundleUrl("3.3.7")
        assertEquals(bundleUrl, downloadedUrls[0])
        assertTrue(bundleUrl.contains("page-ruby-solargraph-windows-x86_64-3.3.7.zip"))

        assertEquals(1, extractedTo.size)
        assertEquals(installer.rubyRoot("3.3.7"), extractedTo[0], "zip must extract to rubyRoot (zip root == install_dir)")

        val solargraphBat = installer.solargraphBinary("3.3.7")
        assertTrue(Files.exists(solargraphBat), "solargraph.bat must materialise at $solargraphBat")
    }

    @Test
    fun installSkipsDownloadWhenBundleAlreadyPresent() {
        useTempHome()
        val target = Path.of(System.getProperty("user.home"))
            .resolve(".page-ide").resolve("lsp").resolve("ruby-bootstrap").resolve("3.3.7")
        Files.createDirectories(target.resolve("bin"))
        Files.writeString(target.resolve("bin").resolve("ruby.exe"), "fake ruby")
        Files.createDirectories(target.resolve("gemhome").resolve("bin"))
        Files.writeString(target.resolve("gemhome").resolve("bin").resolve("solargraph.bat"), "fake solargraph")

        val downloadCalls = mutableListOf<String>()
        val installer = winInstaller(
            processRunner = neverCalledRunner(),
            downloader = { url, _, _ -> downloadCalls += url; fail("download must not be invoked when bundle already present") },
            zipExtractor = { _, _, _ -> fail("zipExtractor must not be invoked when bundle already present") },
        )

        var lastEvent: LspInstaller.Progress? = null
        installer.install("3.3.7") { lastEvent = it }

        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertTrue(downloadCalls.isEmpty(), "no download should have been attempted: $downloadCalls")
        assertEquals("3.3.7", installer.installedVersion())
    }

    @Test
    fun installCleansPartialInstallBeforeReextraction() {
        useTempHome()
        val target = Path.of(System.getProperty("user.home"))
            .resolve(".page-ide").resolve("lsp").resolve("ruby-bootstrap").resolve("3.3.7")
        Files.createDirectories(target.resolve("bin"))
        Files.writeString(target.resolve("bin").resolve("ruby.exe"), "old fake ruby")
        Files.createDirectories(target.resolve("stale-leftover"))
        Files.writeString(target.resolve("stale-leftover").resolve("stale.txt"), "stale")

        val extractedCalls = mutableListOf<Path>()
        val installer = winInstaller(
            processRunner = winNoOpRunner(),
            zipExtractor = { _, dst, _ ->
                extractedCalls.add(dst)
                assertFalse(
                    Files.exists(dst.resolve("stale-leftover")),
                    "stale-leftover must have been deleted before re-extraction",
                )
                val rubyBin = dst.resolve("bin")
                Files.createDirectories(rubyBin)
                Files.writeString(rubyBin.resolve("ruby.exe"), "fresh ruby")
                val solBin = dst.resolve("gemhome").resolve("bin")
                Files.createDirectories(solBin)
                Files.writeString(solBin.resolve("solargraph.bat"), "fresh solargraph")
            },
        )

        var lastEvent: LspInstaller.Progress? = null
        installer.install("3.3.7") { lastEvent = it }

        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertEquals(1, extractedCalls.size, "must extract exactly once after cleanup: $extractedCalls")
    }

    @Test
    fun installContinuesWhenUacDenied() {
        useTempHome()
        val outputs = mutableListOf<String>()
        val installer = winInstaller(
            processRunner = object : ProcessRunner {
                override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int {
                    return if (isUacCommand(command)) { onLine("user denied"); 1223 }
                    else fail("unexpected no-env command: $command")
                }
                override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int =
                    fail("env overload must not be invoked: $command")
                override fun captureOutput(command: List<String>): String =
                    if (isAvDetectCommand(command)) "" else fail("unexpected captureOutput: $command")
            },
        )
        var done: LspInstaller.Progress.Done? = null
        installer.install("3.3.7") { p ->
            if (p is LspInstaller.Progress.CommandOutput) outputs += p.line
            if (p is LspInstaller.Progress.Done) done = p
        }
        assertNotNull(done, "install must complete even when UAC is denied")
        val joined = outputs.joinToString("\n")
        assertTrue(
            joined.contains("[warning]") && joined.contains("Add-MpPreference -ExclusionPath"),
            "UAC denial must surface a manual Add-MpPreference guide: $joined",
        )
    }

    @Test
    fun installEmitsAvWarningWhenNonDefenderAvDetected() {
        useTempHome()
        val outputs = mutableListOf<String>()
        val installer = winInstaller(
            processRunner = object : ProcessRunner {
                override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int =
                    if (isUacCommand(command)) 0 else fail("unexpected no-env: $command")
                override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int =
                    fail("env overload must not be invoked: $command")
                override fun captureOutput(command: List<String>): String =
                    if (isAvDetectCommand(command)) "Windows Defender\nNorton Security\n" else fail("unexpected captureOutput: $command")
            },
        )
        installer.install("3.3.7") { p -> if (p is LspInstaller.Progress.CommandOutput) outputs += p.line }
        val joined = outputs.joinToString("\n")
        assertTrue(joined.contains("[warning]"), "expected [warning] for non-Defender AV: $joined")
        assertTrue(joined.contains("Norton Security"), "must list detected non-Defender AV: $joined")
    }

    @Test
    fun installUsesEnvOverrideZipInsteadOfDownloading() {
        useTempHome()
        val overrideZip = Files.createTempFile("page-ruby-bundle-override-", ".zip")
        Files.writeString(overrideZip, "fake override zip content")
        try {
            val downloadCalls = mutableListOf<String>()
            val extractedFrom = mutableListOf<Path>()
            val installer = winInstaller(
                processRunner = winNoOpRunner(),
                downloader = { url, _, _ -> downloadCalls += url; fail("downloader must not be invoked when override is set") },
                zipExtractor = { src, dst, _ ->
                    extractedFrom.add(src)
                    val rubyBin = dst.resolve("bin")
                    Files.createDirectories(rubyBin)
                    Files.writeString(rubyBin.resolve("ruby.exe"), "fake")
                    val solBin = dst.resolve("gemhome").resolve("bin")
                    Files.createDirectories(solBin)
                    Files.writeString(solBin.resolve("solargraph.bat"), "fake")
                },
                bundleOverride = { overrideZip.toString() },
            )
            var lastEvent: LspInstaller.Progress? = null
            installer.install("3.3.7") { lastEvent = it }
            assertTrue(lastEvent is LspInstaller.Progress.Done)
            assertTrue(downloadCalls.isEmpty(), "downloader must be skipped: $downloadCalls")
            assertEquals(1, extractedFrom.size)
            assertEquals(overrideZip, extractedFrom[0], "extraction must use the override zip path")
            assertTrue(Files.exists(overrideZip), "override zip must not be deleted after extraction")
        } finally {
            runCatching { Files.deleteIfExists(overrideZip) }
        }
    }

    @Test
    fun installFallsBackToDownloadWhenOverridePathDoesNotExist() {
        useTempHome()
        val downloadCalls = mutableListOf<String>()
        val outputs = mutableListOf<String>()
        val installer = winInstaller(
            processRunner = winNoOpRunner(),
            downloader = { url, target, onProgress ->
                downloadCalls += url
                Files.writeString(target, "fake")
                onProgress(1, 1)
            },
            bundleOverride = { "C:\\does\\not\\exist\\page-bundle.zip" },
        )
        var done: LspInstaller.Progress.Done? = null
        installer.install("3.3.7") { p ->
            if (p is LspInstaller.Progress.CommandOutput) outputs += p.line
            if (p is LspInstaller.Progress.Done) done = p
        }
        assertNotNull(done, "install must complete via fallback download path")
        assertEquals(1, downloadCalls.size, "downloader must be invoked when override is invalid")
        assertTrue(outputs.any { it.contains("PAGE_RUBY_BUNDLE_OVERRIDE") && it.contains("[warning]") })
    }

    @Test
    fun installFailsWhenBundleZipDoesNotProduceSolargraph() {
        useTempHome()
        val installer = winInstaller(
            processRunner = winNoOpRunner(),
            zipExtractor = { _, _, _ -> /* extract no-op — solargraph.bat never appears */ },
        )
        var failed: Throwable? = null
        installer.install("3.3.7") { p -> if (p is LspInstaller.Progress.Failed) failed = p.error }
        assertNotNull(failed)
        assertTrue(
            failed!!.message!!.contains("solargraph.bat"),
            "diagnostic must mention solargraph.bat: ${failed!!.message}",
        )
    }

    @Test
    fun installFailsWhenBundleDownloadThrows() {
        useTempHome()
        val installer = winInstaller(
            processRunner = winNoOpRunner(),
            downloader = { _, _, _ -> throw java.io.IOException("simulated network failure") },
        )
        var failed: Throwable? = null
        installer.install("3.3.7") { p -> if (p is LspInstaller.Progress.Failed) failed = p.error }
        assertNotNull(failed)
        val msg = failed!!.message!!
        assertTrue(msg.contains("bundle"), "diagnostic must mention bundle: $msg")
        assertTrue(msg.contains(installer.rubyBundleUrl("3.3.7")), "diagnostic must echo the bundle URL: $msg")
        assertTrue(msg.contains("PAGE_RUBY_BUNDLE_OVERRIDE"), "diagnostic must mention env override recovery path: $msg")
    }

    @Test
    fun installEndToEndOnMacosWritesSolargraphAndPointer() {
        useTempHome()
        val executedCommands = mutableListOf<List<String>>()
        val runner = object : ProcessRunner {
            override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = fail("env-aware overload expected")
            override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int {
                executedCommands += command
                onLine("Successfully installed solargraph-0.50.0")
                val gemHome = Path.of(env["GEM_HOME"]!!)
                val solargraph = gemHome.resolve("bin").resolve("solargraph")
                solargraph.parent.createDirectories()
                Files.writeString(solargraph, "#!/usr/bin/env ruby")
                return 0
            }
            override fun captureOutput(command: List<String>): String = ""
        }
        val installer = macInstaller(processRunner = runner)

        var lastEvent: LspInstaller.Progress? = null
        installer.install("3.3.7") { lastEvent = it }

        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertEquals("3.3.7", installer.installedVersion())
        assertEquals(2, executedCommands.size, "expected rbs pin + solargraph: $executedCommands")
        assertEquals("rbs", executedCommands[0].last(), "first gem invocation must pin rbs (prism-free)")
        val cmd = executedCommands[1]
        assertEquals("gem", Path.of(cmd[0]).fileName.toString(), "macOS install should invoke gem directly: $cmd")
        assertTrue(cmd.contains("--conservative"), "solargraph install must be --conservative: $cmd")
        assertTrue(cmd.last() == "solargraph")
    }

    @Test
    fun installFailsWhenSolargraphBinaryMissingAfterRunMacos() {
        useTempHome()
        val runner = object : ProcessRunner {
            override fun runStreaming(c: List<String>, onLine: (String) -> Unit): Int = fail("env-aware overload expected")
            override fun runStreaming(c: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int = 0
            override fun captureOutput(c: List<String>): String = ""
        }
        val installer = macInstaller(processRunner = runner)
        var failed: Throwable? = null
        installer.install("3.3.7") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
    }

    @Test
    fun installFailsWhenGemExitNonZeroMacos() {
        useTempHome()
        val runner = object : ProcessRunner {
            override fun runStreaming(c: List<String>, onLine: (String) -> Unit): Int = fail("env-aware overload expected")
            override fun runStreaming(c: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int = 7
            override fun captureOutput(c: List<String>): String = ""
        }
        val installer = macInstaller(processRunner = runner)
        var failed: Throwable? = null
        installer.install("3.3.7") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
        assertTrue(failed!!.message!!.contains("7"))
    }

    private fun seedWindowsInstall(installer: RubyBootstrapInstaller, version: String) {
        val root = installer.rubyRoot(version)
        Files.createDirectories(root.resolve("bin"))
        Files.writeString(root.resolve("bin").resolve("ruby.exe"), "fake ruby")
        Files.createDirectories(root.resolve("gemhome").resolve("bin"))
        Files.writeString(root.resolve("gemhome").resolve("bin").resolve("solargraph.bat"), "fake solargraph")
    }

    @Test
    fun installedVersionsListsRootsWithSolargraph() {
        useTempHome()
        val installer = winInstaller()
        seedWindowsInstall(installer, "3.3.7")
        seedWindowsInstall(installer, "3.4.6")
        assertEquals(listOf("3.4.6", "3.3.7"), installer.installedVersions())
    }

    @Test
    fun installedVersionsIgnoresRootsWithoutSolargraph() {
        useTempHome()
        val installer = winInstaller()
        val orphanRoot = installer.rubyRoot("3.4.6")
        Files.createDirectories(orphanRoot.resolve("bin"))
        Files.writeString(orphanRoot.resolve("bin").resolve("ruby.exe"), "fake ruby")
        assertTrue(installer.installedVersions().isEmpty())
    }

    @Test
    fun applyVersionTogglesCurrentPointerWhenInstalled() {
        useTempHome()
        val installer = winInstaller()
        seedWindowsInstall(installer, "3.3.7")
        seedWindowsInstall(installer, "3.4.6")
        assertTrue(installer.applyVersion("3.4.6"))
        assertEquals("3.4.6", installer.activeVersion())
        assertTrue(installer.applyVersion("3.3.7"))
        assertEquals("3.3.7", installer.activeVersion())
    }

    @Test
    fun applyVersionRejectsMissingInstall() {
        useTempHome()
        val installer = winInstaller()
        seedWindowsInstall(installer, "3.4.6")
        assertTrue(installer.applyVersion("3.4.6"))
        assertFalse(installer.applyVersion("9.9.9"))
        assertEquals("3.4.6", installer.activeVersion())
    }

    @Test
    fun availableVersionsIncludesInstalledEvenWhenOffline() {
        useTempHome()
        val installer = winInstaller(versionsFetcher = { _, _, _ -> emptyList() })
        seedWindowsInstall(installer, "3.3.7")
        val versions = installer.availableVersions()
        assertTrue("3.3.7" in versions, "installed root must remain visible offline: $versions")
        assertTrue(RubyBootstrapInstaller.DEFAULT_RUBY_VERSION in versions, "default version always present: $versions")
    }
}
