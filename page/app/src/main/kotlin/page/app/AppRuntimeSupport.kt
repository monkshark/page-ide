package page.app

import page.runtime.*
import page.lsp.GenericLanguageBackend
import page.lsp.LanguageRegistry
import page.lsp.LspBackends
import page.language.LspController
import page.language.LspRouter
import java.nio.file.Path

private var backendsRegistered = false

private val nonRoutingBackendIds = setOf("flutter")

internal fun registerAllBackends() {
    if (backendsRegistered) return
    backendsRegistered = true
    for (def in LanguageRegistry.all()) {
        if (def.id in nonRoutingBackendIds) continue
        if (LspBackends.byId(def.id) != null) continue
        LspBackends.register(GenericLanguageBackend(
            definition = def,
            executableFinder = { LspInstallers.forId(def.id)?.executable() },
            userOverrideFinder = {
                AppSettings.loadLsp().serverPaths[def.id]
                    ?.trim()?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { Path.of(it) }.getOrNull() }
            },
            envSetup = { env ->
                PageRuntimeEnv.applyTo(env)
                if (def.id == "java") PageRuntimeEnv.pinJavaRuntime(env)
            },
            initializationOptionsProvider = when (def.id) {
                "rust" -> { root -> CargoWorkspaceDetector.linkedProjects(root) }
                "java" -> { _ -> JdtlsInitializationOptions.forWorkspace() }
                else -> null
            },
        ))
    }
}

internal fun resolveLanguageForPath(path: Path): page.lsp.LanguageDefinition? {
    val name = path.fileName?.toString() ?: return null
    val dot = name.lastIndexOf('.')
    val ext = if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1) else name
    return page.lsp.LanguageRegistry.byExtension(ext)
}

@androidx.compose.runtime.Composable
internal fun lspStatusLineText(lspRouter: LspRouter, activePath: Path?): String? {
    val definition = activePath?.let(::resolveLanguageForPath)
    val langId = definition?.id
    val displayName = definition?.displayName
    val isKotlin = langId == "kotlin" || (langId == null && activePath?.fileName?.toString()?.endsWith(".kt") != false)
    if (langId == null && !isKotlin) return null
    val resolvedId = langId ?: "kotlin"
    val resolvedName = displayName ?: "Kotlin"
    val ctrl = activePath?.let { lspRouter.controllerFor(it) }
    val installer = LspInstallers.forId(resolvedId) ?: return when {
        isKotlin && ctrl?.status?.value == LspController.Status.MISSING -> "LSP · kotlin-language-server missing"
        isKotlin && ctrl?.status?.value == LspController.Status.FAILED -> "LSP · failed to start"
        else -> null
    }
    val installed = installer.installedVersion()
    val suffix = when (ctrl?.status?.value) {
        LspController.Status.FAILED -> " · failed"
        LspController.Status.STARTING -> " · starting…"
        LspController.Status.MISSING -> " · not installed"
        LspController.Status.IDLE -> " · idle"
        LspController.Status.READY -> ""
        null -> if (installed != null) " · not started" else ""
    }
    val core = if (installed != null) "$resolvedName $installed" else "$resolvedName (not installed)"
    return "$core$suffix"
}

private fun detectRuntimeVersions(projectRoot: java.nio.file.Path? = null): Map<String, String> {
    val vers = mutableMapOf<String, String>()
    val jdk = runCatching { JdkInstaller().activeVersion() }.getOrNull() ?: System.getProperty("java.version")
    if (!jdk.isNullOrBlank()) vers["java"] = jdk
    val node = runCatching { NodeInstaller().activeVersion() }.getOrNull()
        ?: runCatching { captureVersion("node", "--version")?.removePrefix("v") }.getOrNull()
    if (!node.isNullOrBlank()) vers["js"] = node
    val py = runCatching { PythonInstaller().activeVersion() }.getOrNull()
        ?: runCatching { captureVersion("python", "--version")?.substringAfter("Python ")?.trim() }.getOrNull()
    if (!py.isNullOrBlank()) vers["py"] = py
    val go = runCatching { GoSdkInstaller().activeVersion() }.getOrNull()
        ?: runCatching { captureVersion("go", "version")?.let { Regex("go(\\d+\\.\\d+\\.\\d+)").find(it)?.groupValues?.get(1) } }.getOrNull()
    if (!go.isNullOrBlank()) vers["go"] = go
    val cpp = runCatching { CppToolchainInstaller().activeVersion() }.getOrNull()
        ?: runCatching { captureVersion("clang", "--version")?.let { Regex("(\\d+\\.\\d+\\.\\d+)").find(it)?.groupValues?.get(1) } }.getOrNull()
    if (!cpp.isNullOrBlank()) vers["cpp"] = cpp
    val rust = runCatching { RustToolchainInstaller().activeVersion() }.getOrNull()
        ?: runCatching { captureVersion("rustc", "--version")?.let { Regex("(\\d+\\.\\d+\\.\\d+)").find(it)?.groupValues?.get(1) } }.getOrNull()
    if (!rust.isNullOrBlank()) vers["rs"] = rust
    val dotnet = runCatching { DotnetSdkInstaller().activeVersion() }.getOrNull()
        ?: runCatching { captureVersion("dotnet", "--version") }.getOrNull()
    if (!dotnet.isNullOrBlank()) vers["cs"] = dotnet
    if (projectRoot != null) {
        var detected = runCatching { BuildFileVersionDetector.detect(projectRoot) }.getOrDefault(emptyList())
        if (detected.isEmpty()) {
            detected = runCatching {
                java.nio.file.Files.list(projectRoot).use { stream ->
                    stream.filter { java.nio.file.Files.isDirectory(it) }
                        .flatMap { BuildFileVersionDetector.detect(it).stream() }
                        .toList()
                }
            }.getOrDefault(emptyList())
        }
        for (d in detected) {
            val key = when (d.runtime) {
                "jdk" -> "java"
                "node" -> "js"
                "python-runtime" -> "py"
                "go-sdk" -> "go"
                "rust" -> "rs"
                "dotnet" -> "cs"
                else -> continue
            }
            val hasManaged = when (d.runtime) {
                "jdk" -> runCatching { JdkInstaller().activeVersion() }.getOrNull() != null
                "node" -> runCatching { NodeInstaller().activeVersion() }.getOrNull() != null
                "python-runtime" -> runCatching { PythonInstaller().activeVersion() }.getOrNull() != null
                "go-sdk" -> runCatching { GoSdkInstaller().activeVersion() }.getOrNull() != null
                else -> false
            }
            if (!hasManaged) vers[key] = d.version
        }
    }
    return vers
}

internal fun detectRuntimeVersionsWithSources(projectRoot: java.nio.file.Path? = null): Triple<Map<String, String>, Map<String, String>, Map<String, String>> {
    val vers = detectRuntimeVersions(projectRoot)
    val sources = mutableMapOf<String, String>()
    val buildVers = mutableMapOf<String, String>()
    if (projectRoot != null) {
        var detected = runCatching { BuildFileVersionDetector.detect(projectRoot) }.getOrDefault(emptyList())
        if (detected.isEmpty()) {
            detected = runCatching {
                java.nio.file.Files.list(projectRoot).use { stream ->
                    stream.filter { java.nio.file.Files.isDirectory(it) }
                        .flatMap { BuildFileVersionDetector.detect(it).stream() }
                        .toList()
                }
            }.getOrDefault(emptyList())
        }
        for (d in detected) {
            val key = when (d.runtime) {
                "jdk" -> "java"; "node" -> "js"; "python-runtime" -> "py"; "go-sdk" -> "go"; "rust" -> "rs"; "dotnet" -> "cs"; else -> continue
            }
            sources[key] = d.source
            buildVers[key] = d.version
        }
    }
    return Triple(vers, sources, buildVers)
}

private fun captureVersion(cmd: String, vararg args: String): String? {
    val p = ProcessBuilder(cmd, *args).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().use { it.readLine() }
    p.waitFor()
    return out?.trim()?.takeIf { it.isNotBlank() }
}
