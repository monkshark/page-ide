package page.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LspInstallersRegistryTest {

    @Test
    fun supportsAllStep1Languages() {
        for (id in listOf("kotlin", "rust", "c", "cpp", "lua", "markdown", "zig", "elixir", "clojure", "java")) {
            assertTrue(LspInstallers.supports(id), "expected support for $id")
            assertNotNull(LspInstallers.forId(id), "expected installer for $id")
        }
    }

    @Test
    fun unsupportedLanguageReturnsNull() {
        assertNull(LspInstallers.forId("brainfuck"))
        assertNull(LspInstallers.forId(""))
    }

    @Test
    fun kotlinInstallerKeepsLegacyLanguageId() {
        val installer = LspInstallers.forId("kotlin")
        assertNotNull(installer)
        assertEquals("kotlin", installer.languageId)
        assertEquals("kotlin-language-server", installer.displayName)
    }

    @Test
    fun javaInstallerIsJdtls() {
        val installer = LspInstallers.forId("java")
        assertNotNull(installer)
        assertTrue(installer is JdtlsInstaller, "expected JdtlsInstaller, got ${installer::class}")
    }

    @Test
    fun gitHubReleaseInstallersHaveAllThreeOsBlocks() {
        val ids = listOf("c", "cpp", "lua", "markdown", "zig", "elixir", "clojure")
        for (id in ids) {
            val installer = LspInstallers.forId(id)
            assertTrue(installer is GitHubReleaseInstaller, "$id should be GitHubReleaseInstaller")
            val perOs = installer.descriptor.perOs
            assertTrue(perOs.containsKey("macos"), "$id missing macos descriptor")
            assertTrue(perOs.containsKey("linux"), "$id missing linux descriptor")
            assertTrue(perOs.containsKey("windows"), "$id missing windows descriptor")
        }
    }

    @Test
    fun gitHubReleaseInstallersOwnerRepoNonEmpty() {
        val ids = listOf("c", "cpp", "lua", "markdown", "zig", "elixir", "clojure")
        for (id in ids) {
            val installer = LspInstallers.forId(id) as GitHubReleaseInstaller
            assertTrue(installer.descriptor.owner.isNotBlank(), "$id missing owner")
            assertTrue(installer.descriptor.repo.isNotBlank(), "$id missing repo")
        }
    }

    @Test
    fun rustUsesRustAnalyzerInstaller() {
        assertTrue(LspInstallers.supports("rust"))
        val installer = LspInstallers.forId("rust")
        assertNotNull(installer)
        assertTrue(installer is RustAnalyzerInstaller, "rust should be RustAnalyzerInstaller, got ${installer::class}")
        assertEquals("rust-analyzer", installer.displayName)
    }

    @Test
    fun kotlinAdapterReportsExpectedExecutableShape() {
        val installer = LspInstallers.forId("kotlin")
        assertNotNull(installer)
        val version = installer.defaultVersion()
        assertEquals(KlsLspInstaller.labelOf(KlsInstaller.VERSION, KlsLspInstaller.FORK), version)
    }

    @Test
    fun kotlinAdapterParsesForkAndUpstreamLabels() {
        assertEquals("1.3.13-page-1" to "fork", KlsLspInstaller.parseLabel("1.3.13-page-1 (fork)"))
        assertEquals("1.3.13" to "upstream", KlsLspInstaller.parseLabel("1.3.13 (upstream)"))
        assertEquals("1.3.13" to "fork", KlsLspInstaller.parseLabel("1.3.13"))
    }

    @Test
    fun kotlinAdapterExposesMultiInstallApis() {
        val installer = LspInstallers.forId("kotlin")
        assertNotNull(installer)
        assertTrue(installer is KlsLspInstaller)
        installer.installedVersions()
        installer.activeVersion()
    }

    @Test
    fun supportsAllStep2NpmLanguages() {
        val npmIds = listOf("typescript", "javascript", "html", "css", "json", "yaml", "bash", "python", "dockerfile", "vue", "svelte")
        for (id in npmIds) {
            assertTrue(LspInstallers.supports(id), "expected support for $id")
            val installer = LspInstallers.forId(id)
            assertNotNull(installer, "expected installer for $id")
            assertTrue(installer is NpmGlobalInstaller, "$id should be NpmGlobalInstaller, got ${installer::class}")
        }
    }

    @Test
    fun npmDescriptorsHavePackageAndBinary() {
        val npmIds = listOf("typescript", "javascript", "html", "css", "json", "yaml", "bash", "python", "dockerfile", "vue", "svelte")
        for (id in npmIds) {
            val installer = LspInstallers.forId(id) as NpmGlobalInstaller
            assertTrue(installer.descriptor.packageName.isNotBlank(), "$id missing packageName")
            assertTrue(installer.descriptor.binaryName.isNotBlank(), "$id missing binaryName")
        }
    }

    @Test
    fun typescriptAndJavascriptShareSameInstaller() {
        val ts = LspInstallers.forId("typescript") as NpmGlobalInstaller
        val js = LspInstallers.forId("javascript") as NpmGlobalInstaller
        assertEquals(ts.descriptor.packageName, js.descriptor.packageName)
        assertEquals(ts.descriptor.installKey, js.descriptor.installKey)
    }

    @Test
    fun vscodeExtractedTriadSharesInstallKey() {
        val html = LspInstallers.forId("html") as NpmGlobalInstaller
        val css = LspInstallers.forId("css") as NpmGlobalInstaller
        val json = LspInstallers.forId("json") as NpmGlobalInstaller
        assertEquals(html.descriptor.installKey, css.descriptor.installKey)
        assertEquals(html.descriptor.installKey, json.descriptor.installKey)
        assertEquals(html.installRoot("4.10.0"), css.installRoot("4.10.0"))
    }

    @Test
    fun supportsAllStep3ShellLanguages() {
        val shellIds = mapOf(
            "ocaml" to "opam",
            "perl" to "cpan",
            "r" to "Rscript",
        )
        for ((id, manager) in shellIds) {
            assertTrue(LspInstallers.supports(id), "expected support for $id")
            val installer = LspInstallers.forId(id)
            assertNotNull(installer, "expected installer for $id")
            assertTrue(installer is ShellPackageInstaller, "$id should be ShellPackageInstaller, got ${installer::class}")
            assertEquals(manager, installer.descriptor.managerName, "$id manager mismatch")
        }
    }

    @Test
    fun rubyShellFallbackKeepsGemManager() {
        val installer = LspInstallers.shellRubyInstaller()
        assertTrue(installer is ShellPackageInstaller, "shellRubyInstaller should return ShellPackageInstaller")
        assertEquals("gem", installer.descriptor.managerName)
        assertEquals("solargraph", installer.descriptor.binaryName)
    }

    @Test
    fun rubyRegistryDispatchesByOs() {
        assertTrue(LspInstallers.supports("ruby"))
        val installer = LspInstallers.forId("ruby")
        assertNotNull(installer)
        val os = LspInstaller.osKey()
        if (os == "windows" || os == "macos") {
            assertTrue(installer is RubyBootstrapInstaller, "ruby should be bootstrap on $os, got ${installer::class}")
        } else {
            assertTrue(installer is ShellPackageInstaller, "ruby should be shell fallback on $os, got ${installer::class}")
        }
    }

    @Test
    fun heavyInstallSetForRubyOcamlPerl() {
        for (id in listOf("ruby", "ocaml", "perl")) {
            val installer = LspInstallers.forId(id)
            assertNotNull(installer, "expected installer for $id")
            val heavy = installer.heavyInstall
            assertNotNull(heavy, "$id should expose heavy install estimate")
            assertTrue(heavy.sizeEstimate.isNotBlank(), "$id heavy.sizeEstimate blank")
            assertTrue(heavy.durationEstimate.isNotBlank(), "$id heavy.durationEstimate blank")
            assertTrue(heavy.notes.isNotBlank(), "$id heavy.notes blank")
        }
    }

    @Test
    fun heavyInstallNullForLightInstallers() {
        for (id in listOf("r", "typescript", "rust", "go", "scala", "haskell", "dart")) {
            val installer = LspInstallers.forId(id)
            assertNotNull(installer, "expected installer for $id")
            assertNull(installer.heavyInstall, "$id should not flag heavy install")
        }
    }

    @Test
    fun goUsesGoplsInstallerNotShellPackage() {
        assertTrue(LspInstallers.supports("go"))
        val installer = LspInstallers.forId("go")
        assertNotNull(installer)
        assertTrue(installer is GoplsInstaller, "go should be GoplsInstaller, got ${installer::class}")
        assertEquals("gopls", installer.displayName)
    }

    @Test
    fun scalaUsesMetalsInstallerNotShellPackage() {
        assertTrue(LspInstallers.supports("scala"))
        val installer = LspInstallers.forId("scala")
        assertNotNull(installer)
        assertTrue(installer is MetalsInstaller, "scala should be MetalsInstaller, got ${installer::class}")
        assertEquals("metals", installer.displayName)
    }

    @Test
    fun fsharpUsesFsAutocompleteInstallerNotShellPackage() {
        assertTrue(LspInstallers.supports("fsharp"))
        val installer = LspInstallers.forId("fsharp")
        assertNotNull(installer)
        assertTrue(installer is FsAutocompleteInstaller, "fsharp should be FsAutocompleteInstaller, got ${installer::class}")
        assertEquals("fsautocomplete", installer.displayName)
    }

    @Test
    fun supportsToolchainDetectorLanguages() {
        val installer = LspInstallers.forId("swift")
        assertNotNull(installer, "expected installer for swift")
        assertTrue(installer is ToolchainDetectInstaller, "swift should be ToolchainDetectInstaller, got ${installer::class}")
    }

    @Test
    fun haskellUsesHaskellHlsInstallerNotShellPackage() {
        assertTrue(LspInstallers.supports("haskell"))
        val installer = LspInstallers.forId("haskell")
        assertNotNull(installer)
        assertTrue(installer is HaskellHlsInstaller, "haskell should be HaskellHlsInstaller, got ${installer::class}")
        assertEquals("haskell-language-server", installer.displayName)
    }

    @Test
    fun dartUsesDartSdkInstallerNotToolchainDetect() {
        assertTrue(LspInstallers.supports("dart"))
        val installer = LspInstallers.forId("dart")
        assertNotNull(installer)
        assertTrue(installer is DartSdkInstaller, "dart should be DartSdkInstaller, got ${installer::class}")
    }

    @Test
    fun registryCoversAllThirtyJsonLanguages() {
        val expected = listOf(
            "kotlin", "java", "python", "javascript", "typescript", "go", "rust", "c", "cpp", "dart",
            "bash", "ruby", "php", "swift", "scala", "haskell", "lua", "json", "yaml", "html",
            "css", "markdown", "sql", "r", "perl", "elixir", "clojure", "fsharp", "ocaml", "zig",
        )
        for (id in expected) {
            assertTrue(LspInstallers.supports(id), "registry missing language id: $id")
        }
    }
}
