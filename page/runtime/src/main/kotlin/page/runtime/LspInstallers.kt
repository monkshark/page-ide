package page.runtime

object LspInstallers {

    private val registry: Map<String, () -> LspInstaller> = mapOf(
        "kotlin" to { KlsLspInstaller() },
        "rust" to { RustAnalyzerInstaller() },
        "c" to ::clangdInstaller,
        "cpp" to ::clangdInstaller,
        "lua" to ::luaLanguageServerInstaller,
        "markdown" to ::marksmanInstaller,
        "zig" to ::zlsInstaller,
        "elixir" to ::elixirLsInstaller,
        "clojure" to ::clojureLspInstaller,
        "java" to ::jdtlsInstaller,
        "typescript" to ::typescriptLanguageServerInstaller,
        "javascript" to ::typescriptLanguageServerInstaller,
        "html" to ::vscodeHtmlInstaller,
        "css" to ::vscodeCssInstaller,
        "json" to ::vscodeJsonInstaller,
        "yaml" to ::yamlLanguageServerInstaller,
        "bash" to ::bashLanguageServerInstaller,
        "python" to ::pyrightInstaller,
        "dockerfile" to ::dockerLangserverInstaller,
        "vue" to ::vueLanguageServerInstaller,
        "svelte" to ::svelteLanguageServerInstaller,
        "php" to ::intelephenseInstaller,
        "sql" to ::sqlLanguageServerInstaller,
        "ruby" to ::rubyInstaller,
        "ocaml" to ::ocamlInstaller,
        "fsharp" to ::fsharpInstaller,
        "perl" to ::perlInstaller,
        "r" to ::rInstaller,
        "haskell" to ::haskellInstaller,
        "go" to ::goInstaller,
        "scala" to ::scalaInstaller,
        "dart" to ::dartInstaller,
        "flutter" to ::flutterInstaller,
        "swift" to ::swiftInstaller,
        "jdk" to { JdkInstaller() },
        "node" to { NodeInstaller() },
        "python-runtime" to { PythonInstaller() },
        "cpp-toolchain" to { CppToolchainInstaller() },
        "go-sdk" to { GoSdkInstaller() },
        "rust-runtime" to { RustToolchainInstaller() },
        "dotnet-runtime" to { DotnetSdkInstaller() },
    )

    fun forId(languageId: String): LspInstaller? = registry[languageId]?.invoke()

    fun supports(languageId: String): Boolean = registry.containsKey(languageId)

    private fun clangdInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "clangd",
            displayName = "clangd",
            owner = "clangd",
            repo = "clangd",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/clangd/clangd/releases/download/{tag}/clangd-mac-{tag}.zip",
                    executableRelative = "bin/clangd",
                    archiveType = ArchiveType.ZIP,
                    flatten = 1,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/clangd/clangd/releases/download/{tag}/clangd-linux-{tag}.zip",
                    executableRelative = "bin/clangd",
                    archiveType = ArchiveType.ZIP,
                    flatten = 1,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/clangd/clangd/releases/download/{tag}/clangd-windows-{tag}.zip",
                    executableRelative = "bin/clangd.exe",
                    archiveType = ArchiveType.ZIP,
                    flatten = 1,
                ),
            ),
        ),
    )

    private fun luaLanguageServerInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "lua",
            displayName = "lua-language-server",
            owner = "LuaLS",
            repo = "lua-language-server",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/LuaLS/lua-language-server/releases/download/{tag}/lua-language-server-{versionNoV}-darwin-arm64.tar.gz",
                    executableRelative = "bin/lua-language-server",
                    archiveType = ArchiveType.TAR_GZ,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/LuaLS/lua-language-server/releases/download/{tag}/lua-language-server-{versionNoV}-linux-x64.tar.gz",
                    executableRelative = "bin/lua-language-server",
                    archiveType = ArchiveType.TAR_GZ,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/LuaLS/lua-language-server/releases/download/{tag}/lua-language-server-{versionNoV}-win32-x64.zip",
                    executableRelative = "bin/lua-language-server.exe",
                    archiveType = ArchiveType.ZIP,
                ),
            ),
        ),
    )

    private fun marksmanInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "markdown",
            displayName = "marksman",
            owner = "artempyanykh",
            repo = "marksman",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/artempyanykh/marksman/releases/download/{tag}/marksman-macos",
                    executableRelative = "marksman",
                    archiveType = ArchiveType.RAW_BINARY,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/artempyanykh/marksman/releases/download/{tag}/marksman-linux-x64",
                    executableRelative = "marksman",
                    archiveType = ArchiveType.RAW_BINARY,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/artempyanykh/marksman/releases/download/{tag}/marksman.exe",
                    executableRelative = "marksman.exe",
                    archiveType = ArchiveType.RAW_BINARY,
                ),
            ),
        ),
    )

    private fun zlsInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "zig",
            displayName = "zls",
            owner = "zigtools",
            repo = "zls",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/zigtools/zls/releases/download/{tag}/zls-aarch64-macos.tar.xz",
                    executableRelative = "zls",
                    archiveType = ArchiveType.RAW_BINARY,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/zigtools/zls/releases/download/{tag}/zls-x86_64-linux.tar.xz",
                    executableRelative = "zls",
                    archiveType = ArchiveType.RAW_BINARY,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/zigtools/zls/releases/download/{tag}/zls-x86_64-windows.zip",
                    executableRelative = "zls.exe",
                    archiveType = ArchiveType.ZIP,
                ),
            ),
        ),
    )

    private fun elixirLsInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "elixir",
            displayName = "elixir-ls",
            owner = "elixir-lsp",
            repo = "elixir-ls",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/elixir-lsp/elixir-ls/releases/download/{tag}/elixir-ls-{tag}.zip",
                    executableRelative = "language_server.sh",
                    archiveType = ArchiveType.ZIP,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/elixir-lsp/elixir-ls/releases/download/{tag}/elixir-ls-{tag}.zip",
                    executableRelative = "language_server.sh",
                    archiveType = ArchiveType.ZIP,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/elixir-lsp/elixir-ls/releases/download/{tag}/elixir-ls-{tag}.zip",
                    executableRelative = "language_server.bat",
                    archiveType = ArchiveType.ZIP,
                ),
            ),
        ),
    )

    private fun clojureLspInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "clojure",
            displayName = "clojure-lsp",
            owner = "clojure-lsp",
            repo = "clojure-lsp",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/clojure-lsp/clojure-lsp/releases/download/{tag}/clojure-lsp-native-macos-amd64.zip",
                    executableRelative = "clojure-lsp",
                    archiveType = ArchiveType.ZIP,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/clojure-lsp/clojure-lsp/releases/download/{tag}/clojure-lsp-native-linux-amd64.zip",
                    executableRelative = "clojure-lsp",
                    archiveType = ArchiveType.ZIP,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/clojure-lsp/clojure-lsp/releases/download/{tag}/clojure-lsp-native-windows-amd64.zip",
                    executableRelative = "clojure-lsp.exe",
                    archiveType = ArchiveType.ZIP,
                ),
            ),
        ),
    )

    private fun jdtlsInstaller(): LspInstaller = JdtlsInstaller()

    private fun typescriptLanguageServerInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "typescript",
            displayName = "typescript-language-server",
            packageName = "typescript-language-server",
            binaryName = "typescript-language-server",
        ),
    )

    private fun vscodeHtmlInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "html",
            displayName = "vscode-html-language-server",
            packageName = "vscode-langservers-extracted",
            binaryName = "vscode-html-language-server",
        ),
    )

    private fun vscodeCssInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "css",
            displayName = "vscode-css-language-server",
            packageName = "vscode-langservers-extracted",
            binaryName = "vscode-css-language-server",
        ),
    )

    private fun vscodeJsonInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "json",
            displayName = "vscode-json-language-server",
            packageName = "vscode-langservers-extracted",
            binaryName = "vscode-json-language-server",
        ),
    )

    private fun yamlLanguageServerInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "yaml",
            displayName = "yaml-language-server",
            packageName = "yaml-language-server",
            binaryName = "yaml-language-server",
        ),
    )

    private fun bashLanguageServerInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "bash",
            displayName = "bash-language-server",
            packageName = "bash-language-server",
            binaryName = "bash-language-server",
        ),
    )

    private fun pyrightInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "python",
            displayName = "pyright",
            packageName = "pyright",
            binaryName = "pyright-langserver",
        ),
    )

    private fun dockerLangserverInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "dockerfile",
            displayName = "dockerfile-language-server-nodejs",
            packageName = "dockerfile-language-server-nodejs",
            binaryName = "docker-langserver",
        ),
    )

    private fun vueLanguageServerInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "vue",
            displayName = "@vue/language-server",
            packageName = "@vue/language-server",
            binaryName = "vue-language-server",
        ),
    )

    private fun svelteLanguageServerInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "svelte",
            displayName = "svelte-language-server",
            packageName = "svelte-language-server",
            binaryName = "svelteserver",
        ),
    )

    private fun intelephenseInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "php",
            displayName = "intelephense",
            packageName = "intelephense",
            binaryName = "intelephense",
        ),
    )

    private fun sqlLanguageServerInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "sql",
            displayName = "sql-language-server",
            packageName = "sql-language-server",
            binaryName = "sql-language-server",
        ),
    )

    private fun rubyInstaller(): LspInstaller = when (LspInstaller.osKey()) {
        "windows", "macos" -> RubyBootstrapInstaller()
        else -> shellRubyInstaller()
    }

    internal fun shellRubyInstaller(): LspInstaller = ShellPackageInstaller(
        ShellPackageDescriptor(
            languageId = "ruby",
            displayName = "solargraph",
            managerName = "gem",
            managerInstallUrl = "https://www.ruby-lang.org/en/downloads/",
            binaryName = "solargraph",
            packageName = "solargraph",
            heavyInstall = LspInstaller.HeavyInstallEstimate(
                sizeEstimate = "~80 MB",
                durationEstimate = "~30 sec to 2 min",
                notes = "gem downloads solargraph and its dependencies. Duration varies with network conditions.",
            ),
            buildInstallCommand = { mgr, pkg, _ -> listOf(mgr, "install", "--no-document", pkg) },
        ),
    )

    private fun ocamlInstaller(): LspInstaller = ShellPackageInstaller(
        ShellPackageDescriptor(
            languageId = "ocaml",
            displayName = "ocaml-lsp-server",
            managerName = "opam",
            managerInstallUrl = "https://opam.ocaml.org/doc/Install.html",
            binaryName = "ocamllsp",
            packageName = "ocaml-lsp-server",
            heavyInstall = LspInstaller.HeavyInstallEstimate(
                sizeEstimate = "~120 MB",
                durationEstimate = "~2 to 30 min",
                notes = "opam builds ocaml-lsp-server and its dependencies. The first run may also fetch the compiler, which can take a while.",
            ),
            buildInstallCommand = { mgr, pkg, _ -> listOf(mgr, "install", "-y", pkg) },
        ),
    )

    private fun fsharpInstaller(): LspInstaller = FsAutocompleteInstaller()

    private fun perlInstaller(): LspInstaller = ShellPackageInstaller(
        ShellPackageDescriptor(
            languageId = "perl",
            displayName = "Perl::LanguageServer",
            managerName = "cpan",
            managerInstallUrl = "https://www.perl.org/get.html",
            binaryName = "perl",
            packageName = "Perl::LanguageServer",
            heavyInstall = LspInstaller.HeavyInstallEstimate(
                sizeEstimate = "~50 MB",
                durationEstimate = "~5 to 15 min",
                notes = "cpan builds Perl::LanguageServer and its XS dependencies. The first build can be slow.",
            ),
            buildInstallCommand = { mgr, pkg, _ -> listOf(mgr, "-T", pkg) },
        ),
    )

    private fun rInstaller(): LspInstaller = ShellPackageInstaller(
        ShellPackageDescriptor(
            languageId = "r",
            displayName = "languageserver (R)",
            managerName = "Rscript",
            managerInstallUrl = "https://www.r-project.org/",
            binaryName = "Rscript",
            packageName = "languageserver",
            buildInstallCommand = { mgr, _, _ ->
                listOf(mgr, "-e", "install.packages('languageserver', repos='https://cloud.r-project.org')")
            },
        ),
    )

    private fun haskellInstaller(): LspInstaller = HaskellHlsInstaller()

    private fun goInstaller(): LspInstaller = GoplsInstaller()

    private fun scalaInstaller(): LspInstaller = MetalsInstaller()

    private fun dartInstaller(): LspInstaller = DartSdkInstaller()

    private fun flutterInstaller(): LspInstaller = FlutterSdkInstaller()

    private fun swiftInstaller(): LspInstaller = ToolchainDetectInstaller(
        languageId = "swift",
        displayName = "sourcekit-lsp (Swift toolchain)",
        managerName = "sourcekit-lsp",
        managerInstallUrl = "https://www.swift.org/install/",
    )
}
