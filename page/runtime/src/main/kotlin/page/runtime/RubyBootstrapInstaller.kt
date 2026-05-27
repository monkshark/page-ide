package page.runtime

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class RubyBootstrapInstaller(
    private val processRunner: ProcessRunner = DefaultProcessRunner,
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val zipExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractZip(src, dst, flatten) },
    private val bundleOverridePath: () -> String? = { System.getenv("PAGE_RUBY_BUNDLE_OVERRIDE") },
    private val defaultRubyVersion: String = DEFAULT_RUBY_VERSION,
    private val macBottleSlug: String = DEFAULT_MAC_BOTTLE_SLUG,
    private val solargraphPackage: String = "solargraph",
    private val solargraphVersion: String = DEFAULT_SOLARGRAPH_VERSION,
    private val rubyBundleRelease: String = DEFAULT_RUBY_BUNDLE_RELEASE,
    private val rubyBundleRepo: String = DEFAULT_RUBY_BUNDLE_REPO,
    private val versionsFetcher: (String, String, String) -> List<String> = { owner, repo, tag ->
        GitHubReleases.listAssetNames(owner, repo, tag)
    },
    private val macVersionsFetcher: () -> List<String> = {
        runCatching {
            GitHubReleases.listReleases("Homebrew", "homebrew-portable-ruby", limit = 30)
                .map { it.tagName }
                .filter { CLEAN_VERSION_REGEX.matches(it) }
        }.getOrDefault(emptyList())
    },
) : LspInstaller {

    override val languageId: String = "ruby"
    override val displayName: String = "solargraph"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate? = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = if (isWindows) "~700 MB to 1 GB (Ruby + MinGW UCRT64 + solargraph all-in-one bundle)" else "~25 MB",
        durationEstimate = "~3 to 7 min",
        notes = if (isWindows)
            "PAGE downloads a prebuilt Ruby + MSYS2 + solargraph all-in-one zip bundle and extracts it into an isolated directory. " +
                "No gem install or MSYS2 toolchain bootstrap runs on your machine, so ASR/Defender process-fork blocking has no effect."
        else
            "PAGE downloads a portable Ruby runtime and uses its bundled gem to install solargraph.",
    )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return findInstalledSolargraph(ver)
    }

    override fun defaultVersion(): String? = defaultRubyVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = rubyBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .map { it.fileName.toString() }
                    .filter { findInstalledSolargraph(it) != null }
                    .toList()
                    .sortedWith(VERSION_DESC)
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (findInstalledSolargraph(version) == null) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val discovered = when (osKey) {
            "windows" -> discoverWindowsBundleVersions()
            "macos" -> discoverMacPortableVersions()
            else -> emptyList()
        }
        val installed = installedVersions()
        return (discovered + defaultRubyVersion + installed).distinct().sortedWith(VERSION_DESC)
    }

    private fun discoverWindowsBundleVersions(): List<String> {
        val (owner, repo) = parseRepo(rubyBundleRepo) ?: return emptyList()
        return runCatching {
            versionsFetcher(owner, repo, rubyBundleRelease)
                .mapNotNull { WINDOWS_BUNDLE_NAME.find(it)?.groupValues?.get(1) }
                .filter { CLEAN_VERSION_REGEX.matches(it) }
        }.getOrDefault(emptyList())
    }

    private fun discoverMacPortableVersions(): List<String> =
        runCatching { macVersionsFetcher() }.getOrDefault(emptyList())

    private fun parseRepo(slashSlug: String): Pair<String, String>? {
        val parts = slashSlug.split('/')
        if (parts.size != 2 || parts.any { it.isBlank() }) return null
        return parts[0] to parts[1]
    }

    override fun install(version: String?, rawProgress: (LspInstaller.Progress) -> Unit) {
        val logFile = LspInstaller.lspHome().resolve("ruby-bootstrap").resolve("install.log")
        runCatching {
            Files.createDirectories(logFile.parent)
            Files.writeString(logFile, "=== Ruby bootstrap install @ ${java.time.LocalDateTime.now()} ===\n")
        }
        val onProgress: (LspInstaller.Progress) -> Unit = { p ->
            rawProgress(p)
            when (p) {
                is LspInstaller.Progress.CommandOutput -> runCatching { Files.writeString(logFile, p.line + "\n", java.nio.file.StandardOpenOption.APPEND) }
                is LspInstaller.Progress.Failed -> runCatching { Files.writeString(logFile, "FAILED: ${p.error.javaClass.simpleName}: ${p.error.message}\n", java.nio.file.StandardOpenOption.APPEND) }
                is LspInstaller.Progress.Done -> runCatching { Files.writeString(logFile, "DONE: ${p.executable}\n", java.nio.file.StandardOpenOption.APPEND) }
                else -> Unit
            }
        }
        try {
            val resolved = version ?: defaultRubyVersion
            if (isWindows) installFromPrebuiltBundle(resolved, onProgress)
            else installFromMacBottle(resolved, onProgress)

            val solargraph = findInstalledSolargraph(resolved)
                ?: throw IOException(
                    "solargraph binary missing after install: none of " +
                        solargraphCandidateNames().joinToString("/") + " found under ${gemHomeFor(resolved).resolve("bin")}",
                )
            runCatching { solargraph.toFile().setExecutable(true, false) }

            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(solargraph))
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    private fun installFromPrebuiltBundle(version: String, onProgress: (LspInstaller.Progress) -> Unit) {
        val target = rubyRoot(version)
        Files.createDirectories(target.parent)

        val solargraph = solargraphBinary(version)
        val ruby = rubyBinary(version)
        if (Files.exists(solargraph) && Files.exists(ruby)) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[info] existing install detected (solargraph.bat + ruby.exe) — skipping bundle download: $target",
            ))
            return
        }
        if (Files.exists(target)) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[info] partial install detected at $target (solargraph.bat=${Files.exists(solargraph)}, ruby.exe=${Files.exists(ruby)}) — removing and re-extracting",
            ))
            ArchiveExtractors.deleteRecursively(target)
        }
        Files.createDirectories(target)

        detectThirdPartyAntivirus(target, onProgress)
        requestDefenderExclusion(target, onProgress)

        val bundle = obtainBundleZip(version, onProgress)
        onProgress(LspInstaller.Progress.Extracting("Extracting Ruby + solargraph bundle to $target …"))
        try {
            zipExtractor(bundle.path, target, 0)
        } finally {
            if (bundle.deleteAfterExtraction) runCatching { Files.deleteIfExists(bundle.path) }
        }

        if (!Files.exists(solargraph)) {
            throw IOException(
                "solargraph.bat missing after bundle extraction: $solargraph — zip layout differs from expected " +
                    "(zip root must be <install_dir>, with gemhome/bin/solargraph.bat).",
            )
        }
        onProgress(LspInstaller.Progress.CommandOutput("[info] all-in-one bundle extracted: $solargraph"))
    }

    private fun installFromMacBottle(version: String, onProgress: (LspInstaller.Progress) -> Unit) {
        val url = downloadUrl(version)
        val target = rubyRoot(version)
        val tmp = Files.createTempFile("page-ruby-", ".tar.gz")
        try {
            downloader(url, tmp) { read, total ->
                onProgress(LspInstaller.Progress.Downloading(read, total))
            }
            onProgress(LspInstaller.Progress.Extracting("Extracting Ruby runtime…"))
            tarGzExtractor(tmp, target, 2)
        } finally {
            runCatching { Files.deleteIfExists(tmp) }
        }

        val gemBin = gemBinary(version)
        if (!Files.exists(gemBin)) throw IOException("gem binary missing after Ruby bootstrap: $gemBin")
        runCatching { gemBin.toFile().setExecutable(true, false) }
        val rubyBin = rubyBinary(version)
        runCatching { rubyBin.toFile().setExecutable(true, false) }

        val gemHome = gemHomeFor(version)
        Files.createDirectories(gemHome)

        val env = buildInstallEnv(version, gemHome)
        onProgress(LspInstaller.Progress.CommandOutput("[debug] GEM_HOME  = ${env["GEM_HOME"]}"))
        onProgress(LspInstaller.Progress.CommandOutput("[debug] PATH(head)= ${env["PATH"]?.take(300)}…"))

        val rbsCmd = gemInvocation(gemBin, listOf("install", "--no-document", "--version", PRISM_FREE_RBS_VERSION, "rbs"))
        onProgress(LspInstaller.Progress.CommandOutput("> gem install --no-document --version $PRISM_FREE_RBS_VERSION rbs (prism-free pin)"))
        val rbsExit = processRunner.runStreaming(rbsCmd, env) { line ->
            onProgress(LspInstaller.Progress.CommandOutput(line))
        }
        if (rbsExit != 0) throw IOException("gem install rbs exited with code $rbsExit")

        val installCmd = gemInvocation(
            gemBin,
            listOf(
                "install", "--no-document", "--conservative",
                "--version", solargraphVersion,
                solargraphPackage,
            ),
        )
        onProgress(LspInstaller.Progress.CommandOutput("> gem install --no-document --conservative --version $solargraphVersion $solargraphPackage"))
        val exit = processRunner.runStreaming(installCmd, env) { line ->
            onProgress(LspInstaller.Progress.CommandOutput(line))
        }
        if (exit != 0) throw IOException("gem install solargraph exited with code $exit")
    }

    private data class BundleZip(val path: Path, val deleteAfterExtraction: Boolean)

    private fun obtainBundleZip(version: String, onProgress: (LspInstaller.Progress) -> Unit): BundleZip {
        val overrideRaw = bundleOverridePath()?.trim().orEmpty()
        if (overrideRaw.isNotEmpty()) {
            val overridePath = runCatching { Path.of(overrideRaw) }.getOrNull()
            when {
                overridePath == null -> onProgress(LspInstaller.Progress.CommandOutput(
                    "[warning] PAGE_RUBY_BUNDLE_OVERRIDE is not a valid path ('$overrideRaw') — ignoring and falling back to download",
                ))
                !Files.isRegularFile(overridePath) -> onProgress(LspInstaller.Progress.CommandOutput(
                    "[warning] PAGE_RUBY_BUNDLE_OVERRIDE points to a zip that does not exist ('$overridePath') — ignoring and falling back to download",
                ))
                else -> {
                    onProgress(LspInstaller.Progress.CommandOutput(
                        "[info] PAGE_RUBY_BUNDLE_OVERRIDE → using $overridePath (skipping download)",
                    ))
                    return BundleZip(overridePath, deleteAfterExtraction = false)
                }
            }
        }

        val url = rubyBundleUrl(version)
        onProgress(LspInstaller.Progress.Extracting("Downloading PAGE Ruby + solargraph bundle…"))
        onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
        val tmp = Files.createTempFile("page-ruby-bundle-", ".zip")
        try {
            downloader(url, tmp) { read, total ->
                onProgress(LspInstaller.Progress.Downloading(read, total))
            }
            return BundleZip(tmp, deleteAfterExtraction = true)
        } catch (t: Throwable) {
            runCatching { Files.deleteIfExists(tmp) }
            throw IOException(buildBundleDownloadDiagnostic(url, t), t)
        }
    }

    private fun buildBundleDownloadDiagnostic(url: String, cause: Throwable): String =
        "PAGE Ruby + solargraph bundle download failed ($url): ${cause.javaClass.simpleName}: ${cause.message}\n" +
            "Recovery steps:\n" +
            "  1. Check network / corporate proxy, then retry install in PAGE\n" +
            "  2. Download the zip from the URL above on another machine, copy it over, then\n" +
            "     set the PAGE_RUBY_BUNDLE_OVERRIDE environment variable to the zip's absolute path and retry install\n" +
            "  3. Verify that the '$rubyBundleRelease' asset is published on GitHub releases"

    private fun detectThirdPartyAntivirus(target: Path, onProgress: (LspInstaller.Progress) -> Unit) {
        val script = "Get-CimInstance -Namespace root/SecurityCenter2 -ClassName AntiVirusProduct " +
            "-ErrorAction SilentlyContinue | Select-Object -ExpandProperty displayName"
        val cmd = listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script)
        val output = try {
            processRunner.captureOutput(cmd)
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[info] AV detection PowerShell call failed (${t.javaClass.simpleName}) — skipping",
            ))
            return
        }
        val products = output.lines().map(String::trim).filter(String::isNotEmpty)
        val nonDefender = products.filter { !it.equals("Windows Defender", ignoreCase = true) }
        if (nonDefender.isEmpty()) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[info] AV detection: no active AV other than Defender — proceeding with Defender exclusion only",
            ))
            return
        }
        onProgress(LspInstaller.Progress.CommandOutput(
            "[warning] Non-Defender AV detected: ${nonDefender.joinToString(", ")}.\n" +
                "  PAGE's Defender ExclusionPath registration does not apply to this AV.\n" +
                "  Files extracted from the zip may be quarantined during install.\n" +
                "  Recommended — add the following path as an exclusion in that AV, then retry install: $target",
        ))
    }

    private fun requestDefenderExclusion(target: Path, onProgress: (LspInstaller.Progress) -> Unit): Boolean {
        onProgress(LspInstaller.Progress.Extracting("Requesting Windows Defender exclusion (UAC prompt)…"))
        val pathLiteral = target.toString().replace("'", "''")
        val innerCommand = "try { Add-MpPreference -ExclusionPath ''" + pathLiteral +
            "''; exit 0 } catch { exit 2 }"
        val outerCommand = "Start-Process powershell -Verb RunAs -WindowStyle Hidden -Wait " +
            "-ArgumentList '-NoProfile -ExecutionPolicy Bypass -Command \"" + innerCommand + "\"'"
        val cmd = listOf(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-Command",
            outerCommand,
        )
        onProgress(LspInstaller.Progress.CommandOutput("> [UAC] $outerCommand"))
        val exit = try {
            processRunner.runStreaming(cmd) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[warning] UAC PowerShell invocation failed (${t.javaClass.simpleName}: ${t.message}).\n" +
                    "  Install will continue, but Defender RTP may quarantine files during zip extraction.\n" +
                    "  Manual workaround — run the following in an elevated PowerShell, then retry install:\n" +
                    "    Add-MpPreference -ExclusionPath '$target'",
            ))
            return false
        }
        if (exit != 0) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[warning] Defender exclusion request failed (exit=$exit — UAC denied or blocked by GPO / enterprise Defender policy).\n" +
                    "  Install will continue, but Defender RTP may quarantine files during zip extraction.\n" +
                    "  Manual workaround — run the following in an elevated PowerShell, then retry install:\n" +
                    "    Add-MpPreference -ExclusionPath '$target'",
            ))
            return false
        }
        onProgress(LspInstaller.Progress.CommandOutput("[info] Defender exclusion registration completed (exit=0)"))
        return true
    }

    internal fun rubyBundleUrl(version: String): String =
        "https://github.com/$rubyBundleRepo/releases/download/$rubyBundleRelease/page-ruby-solargraph-windows-x86_64-$version.zip"

    internal fun downloadUrl(version: String): String = when (osKey) {
        "macos" -> {
            val slug = if (archKey == "arm64") "arm64_big_sur" else macBottleSlug
            "https://github.com/Homebrew/homebrew-portable-ruby/releases/download/$version/portable-ruby-$version.$slug.bottle.tar.gz"
        }
        "windows" -> rubyBundleUrl(version)
        else -> throw IOException("RubyBootstrapInstaller does not support Linux. (osKey=$osKey)")
    }

    fun gemBinary(version: String): Path =
        rubyRoot(version).resolve("bin").resolve(if (isWindows) "gem.cmd" else "gem")

    private fun gemInvocation(gemBin: Path, args: List<String>): List<String> =
        if (isWindows) listOf("cmd.exe", "/c", gemBin.toString()) + args
        else listOf(gemBin.toString()) + args

    fun rubyBinary(version: String): Path =
        rubyRoot(version).resolve("bin").resolve(if (isWindows) "ruby.exe" else "ruby")

    fun solargraphBinary(version: String): Path =
        gemHomeFor(version).resolve("bin").resolve(if (isWindows) "solargraph.bat" else "solargraph")

    fun findInstalledSolargraph(version: String): Path? {
        val binDir = gemHomeFor(version).resolve("bin")
        return solargraphCandidateNames()
            .map { binDir.resolve(it) }
            .firstOrNull { Files.exists(it) }
    }

    private fun solargraphCandidateNames(): List<String> =
        if (isWindows) listOf("solargraph.bat", "solargraph.cmd", "solargraph") else listOf("solargraph")

    fun rubyRoot(version: String): Path = rubyBase().resolve(version)

    fun gemHomeFor(version: String): Path = rubyRoot(version).resolve("gemhome")

    private fun rubyBase(): Path = LspInstaller.lspHome().resolve("ruby-bootstrap")

    override fun installDir(version: String?): Path {
        val v = version ?: defaultRubyVersion
        return rubyRoot(v)
    }

    private fun pathEnv(version: String): String {
        val sep = if (isWindows) ";" else ":"
        val rubyBin = rubyRoot(version).resolve("bin").toString()
        val gemBin = gemHomeFor(version).resolve("bin").toString()
        val current = System.getenv("PATH") ?: ""
        if (!isWindows) return rubyBin + sep + gemBin + sep + current
        val msys2Ucrt = rubyRoot(version).resolve("msys64").resolve("ucrt64").resolve("bin").toString()
        val msys2Usr = rubyRoot(version).resolve("msys64").resolve("usr").resolve("bin").toString()
        return listOf(msys2Ucrt, msys2Usr, rubyBin, gemBin, current).joinToString(sep)
    }

    private fun buildInstallEnv(version: String, gemHome: Path): Map<String, String> {
        val base = mutableMapOf(
            "GEM_HOME" to gemHome.toString(),
            "GEM_PATH" to gemHome.toString(),
            "PATH" to pathEnv(version),
        )
        if (!isWindows) return base
        val msys2Root = rubyRoot(version).resolve("msys64").toString()
        base["MSYSTEM"] = "UCRT64"
        base["MSYSTEM_PREFIX"] = "/ucrt64"
        base["MSYSTEM_CARCH"] = "x86_64"
        base["MSYSTEM_CHOST"] = "x86_64-w64-mingw32"
        base["MINGW_CHOST"] = "x86_64-w64-mingw32"
        base["MINGW_PREFIX"] = "/ucrt64"
        base["MINGW_PACKAGE_PREFIX"] = "mingw-w64-ucrt-x86_64"
        base["RI_DEVKIT"] = msys2Root
        base["ACLOCAL_PATH"] = "/ucrt64/share/aclocal:/usr/share/aclocal"
        base["MANPATH"] = "/ucrt64/share/man"
        base["PKG_CONFIG_PATH"] = "/ucrt64/lib/pkgconfig:/ucrt64/share/pkgconfig"
        return base
    }

    fun currentInstalledVersion(): String? {
        val pointer = rubyBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = rubyBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    companion object {
        const val DEFAULT_RUBY_VERSION = "3.4.6"
        const val DEFAULT_MAC_BOTTLE_SLUG = "el_capitan"
        const val DEFAULT_SOLARGRAPH_VERSION = "0.55.4"
        const val PRISM_FREE_RBS_VERSION = "3.3.0"
        const val DEFAULT_RUBY_BUNDLE_RELEASE = "ruby-bundle"
        const val DEFAULT_RUBY_BUNDLE_REPO = "monkshark/page-ide-assets"

        internal val CLEAN_VERSION_REGEX = Regex("^\\d+(?:\\.\\d+){1,3}$")
        internal val WINDOWS_BUNDLE_NAME = Regex("^page-ruby-solargraph-windows-x86_64-(.+?)\\.zip$")

        internal val VERSION_DESC: Comparator<String> = Comparator { a, b ->
            val pa = versionTokens(a)
            val pb = versionTokens(b)
            val len = maxOf(pa.size, pb.size)
            for (i in 0 until len) {
                val va = pa.getOrElse(i) { 0 }
                val vb = pb.getOrElse(i) { 0 }
                if (va != vb) return@Comparator vb.compareTo(va)
            }
            0
        }

        private fun versionTokens(s: String): IntArray = s.split('.', '_', '-')
            .mapNotNull { it.toIntOrNull() }
            .toIntArray()
            .let { if (it.isEmpty()) intArrayOf(-1) else it }
    }
}
