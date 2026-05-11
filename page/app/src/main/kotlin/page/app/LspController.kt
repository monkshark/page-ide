    package page.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.PublishDiagnosticsParams
import page.lsp.CompletionEdit
import page.lsp.CompletionEnhancer
import page.lsp.CompletionItem
import page.lsp.CompletionItemKind
import page.lsp.CompletionList
import page.lsp.Diagnostic
import page.lsp.KLS_GRADLE_DEPS_KIND
import page.lsp.KLS_GRADLE_SCRIPT_DEPS_KIND
import page.lsp.KLS_LINTING_KIND
import page.lsp.KLS_SYMBOL_INDEX_KIND
import page.lsp.KlsActivity
import page.lsp.KotlinLsp
import page.lsp.LspClient
import page.lsp.LspState
import page.lsp.LspWorkspace
import page.lsp.WorkspaceSymbolEntry
import page.lsp.parseKlsActivity
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class LspController(
    private val workspaceRoot: Path?,
    private val scope: CoroutineScope,
) {

    enum class Status { IDLE, STARTING, READY, MISSING, FAILED }

    data class Activity(val kind: String, val label: String, val startedAtMs: Long)

    val status: MutableState<Status> = mutableStateOf(Status.IDLE)
    val statusDetail: MutableState<String> = mutableStateOf("")
    val activities: SnapshotStateMap<String, Activity> = androidx.compose.runtime.mutableStateMapOf()
    val diagnosticsByUri: SnapshotStateMap<String, List<Diagnostic>> = androidx.compose.runtime.mutableStateMapOf()

    private var client: LspClient? = null
    private var workspace: LspWorkspace? = null
    private val pendingChanges = ConcurrentHashMap<String, Job>()
    private var startAttempted = false

    private data class PendingOpen(val path: Path, val languageId: String, val text: String)
    private val pendingOpens = ConcurrentHashMap<String, PendingOpen>()

    private data class CompletionCacheKey(
        val uri: String,
        val line: Int,
        val character: Int,
        val prefix: String,
        val triggerCharacter: String?,
    )

    private data class LastCompletion(
        val character: Int,
        val prefix: String,
        val list: CompletionList,
    )

    private val completionCache: MutableMap<CompletionCacheKey, CompletionList> = Collections.synchronizedMap(
        object : LinkedHashMap<CompletionCacheKey, CompletionList>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CompletionCacheKey, CompletionList>): Boolean = size > COMPLETION_CACHE_MAX
        }
    )

    private val lastCompletionByLine = ConcurrentHashMap<Pair<String, Int>, LastCompletion>()

    fun ensureStarted() {
        if (startAttempted) return
        startAttempted = true
        println("[lsp] resolving kotlin-language-server (workspace=$workspaceRoot)")
        val resolution = KotlinLsp.resolveExecutable()
        if (resolution !is KotlinLsp.Resolution.Found) {
            val attempted = (resolution as KotlinLsp.Resolution.NotFound).attempted.joinToString("\n  ")
            status.value = Status.MISSING
            statusDetail.value = "kotlin-language-server 를 찾지 못했습니다. 시도한 위치:\n  $attempted"
            println("[lsp] MISSING — attempted:\n  $attempted")
            return
        }
        status.value = Status.STARTING
        statusDetail.value = "starting (${resolution.origin}: ${resolution.executable})"
        startActivity(STARTUP_KIND, "시작 중…")
        println("[lsp] STARTING — ${resolution.origin}: ${resolution.executable}")
        try {
            val c = KotlinLsp.spawn(resolution.executable, workspaceRoot)
            c.onDiagnostics(::onDiagnostics)
            c.onLogMessage { mp ->
                println("[lsp:log/${mp.type}] ${mp.message}")
                applyActivityEvent(parseKlsActivity(mp.message))
            }
            c.onShowMessage { mp -> println("[lsp:show/${mp.type}] ${mp.message}") }
            c.start().whenComplete { result, throwable ->
                endActivity(STARTUP_KIND)
                if (throwable != null) {
                    status.value = Status.FAILED
                    statusDetail.value = throwable.message ?: throwable.toString()
                    println("[lsp] FAILED on initialize: ${throwable.message}")
                    throwable.printStackTrace()
                } else {
                    status.value = Status.READY
                    statusDetail.value = "kotlin-language-server ready (capabilities=${result.capabilities != null})"
                    println("[lsp] READY — capabilities=${result.capabilities != null}")
                    flushPendingOpens()
                }
            }
            client = c
            workspace = LspWorkspace(c)
        } catch (t: Throwable) {
            endActivity(STARTUP_KIND)
            status.value = Status.FAILED
            statusDetail.value = t.message ?: t.toString()
            println("[lsp] FAILED on spawn: ${t.message}")
            t.printStackTrace()
        }
    }

    private fun startActivity(kind: String, label: String) {
        val existing = activities[kind]
        if (existing == null) {
            activities[kind] = Activity(kind, label, System.currentTimeMillis())
        } else if (existing.label != label) {
            activities[kind] = existing.copy(label = label)
        }
    }

    private fun endActivity(kind: String) {
        activities.remove(kind)
    }

    private fun applyActivityEvent(event: KlsActivity?) {
        event ?: return
        when (event) {
            is KlsActivity.Start -> startActivity(event.kind, event.label)
            is KlsActivity.End -> endActivity(event.kind)
        }
    }

    fun didOpen(path: Path, languageId: String, text: String) {
        val uri = path.toUri().toString()
        if (status.value != Status.READY) {
            pendingOpens[uri] = PendingOpen(path, languageId, text)
            println("[lsp] didOpen queued (status=${status.value}) for $uri")
            return
        }
        val ws = workspace ?: return
        if (ws.isOpen(uri)) {
            println("[lsp] didChange (already open) $uri")
            ws.didChange(uri, text)
        } else {
            println("[lsp] didOpen $uri (lang=$languageId, ${text.length} chars)")
            ws.didOpen(uri, languageId, text)
        }
    }

    fun didChange(path: Path, text: String, debounceMs: Long = 250L) {
        val uri = path.toUri().toString()
        if (status.value != Status.READY) {
            pendingOpens[uri]?.let { pendingOpens[uri] = it.copy(text = text) }
            println("[lsp] didChange skipped (status=${status.value}) for $uri")
            return
        }
        val ws = workspace
        if (ws == null) {
            println("[lsp] didChange skipped (workspace=null) for $uri")
            return
        }
        if (!ws.isOpen(uri)) {
            println("[lsp] didChange skipped (not open) for $uri — open uris: ${ws.openUris()}")
            return
        }
        pendingChanges[uri]?.cancel()
        invalidateCompletionCache(uri)
        pendingChanges[uri] = scope.launch(Dispatchers.Default) {
            delay(debounceMs)
            try {
                println("[lsp] didChange → KLS for $uri (${text.length} chars)")
                ws.didChange(uri, text)
            } catch (t: Throwable) {
                println("[lsp] didChange failed for $uri: ${t.message}")
            }
        }
    }

    private fun invalidateCompletionCache(uri: String) {
        synchronized(completionCache) {
            completionCache.entries.removeAll { it.key.uri == uri }
        }
        lastCompletionByLine.entries.removeAll { it.key.first == uri }
    }

    private fun flushPendingOpens() {
        val ws = workspace ?: return
        val snapshot = pendingOpens.toMap()
        pendingOpens.clear()
        for ((uri, p) in snapshot) {
            try {
                if (ws.isOpen(uri)) {
                    println("[lsp] flush → didChange $uri")
                    ws.didChange(uri, p.text)
                } else {
                    println("[lsp] flush → didOpen $uri (lang=${p.languageId}, ${p.text.length} chars)")
                    ws.didOpen(uri, p.languageId, p.text)
                }
            } catch (t: Throwable) {
                println("[lsp] flush failed for $uri: ${t.message}")
            }
        }
    }

    fun didClose(path: Path) {
        val uri = path.toUri().toString()
        pendingChanges.remove(uri)?.cancel()
        pendingOpens.remove(uri)
        invalidateCompletionCache(uri)
        val ws = workspace
        if (ws != null && ws.isOpen(uri)) ws.didClose(uri)
        diagnosticsByUri.remove(uri)
    }

    fun diagnosticsFor(path: Path): List<Diagnostic> {
        val uri = path.toUri().toString()
        return diagnosticsByUri[uri].orEmpty()
    }

    fun completion(
        path: Path,
        text: String,
        line: Int,
        character: Int,
        triggerCharacter: String? = null,
    ): CompletableFuture<CompletionList> {
        if (status.value != Status.READY) {
            return CompletableFuture.completedFuture(CompletionList.EMPTY)
        }
        val ws = workspace ?: return CompletableFuture.completedFuture(CompletionList.EMPTY)
        val uri = path.toUri().toString()
        pendingChanges.remove(uri)?.cancel()
        if (ws.isOpen(uri)) {
            runCatching { ws.didChange(uri, text) }
        }
        val trig = triggerCharacter ?: "<invoke>"
        val prefix = computePrefix(text, line, character)
        val tStart = System.nanoTime()
        val timings = StringBuilder()
        fun mark(label: String) {
            val elapsed = (System.nanoTime() - tStart) / 1_000_000
            if (timings.isNotEmpty()) timings.append(' ')
            timings.append(label).append('=').append(elapsed).append("ms")
        }
        println("[lsp] completion → $uri @($line,$character) trigger=$trig prefix='$prefix'")

        val cacheKey = CompletionCacheKey(uri, line, character, prefix, triggerCharacter)
        val cached = synchronized(completionCache) { completionCache[cacheKey] }
        if (cached != null) {
            println("[lsp] completion ✓ $uri — ${cached.items.size} items (incomplete=${cached.isIncomplete}) [cache=hit]")
            return CompletableFuture.completedFuture(cached)
        }

        val canExtend = triggerCharacter == null && prefix.length >= 2
        if (canExtend) {
            val last = lastCompletionByLine[uri to line]
            if (last != null
                && !last.list.isIncomplete
                && prefix.length > last.prefix.length
                && prefix.startsWith(last.prefix, ignoreCase = true)
                && character >= last.character
            ) {
                val filteredItems = CompletionEnhancer.filterByPrefix(last.list.items, prefix)
                val sorted = CompletionEnhancer.applyPrefixSort(filteredItems, prefix)
                val extended = CompletionList(isIncomplete = false, items = sorted)
                synchronized(completionCache) { completionCache[cacheKey] = extended }
                println("[lsp] completion ✓ $uri — ${extended.items.size} items (incomplete=false) [cache=extend('${last.prefix}'→'$prefix')]")
                return CompletableFuture.completedFuture(extended)
            }
        }

        val fileName = path.fileName?.toString().orEmpty()
        val isKotlin = fileName.endsWith(".kt") || fileName.endsWith(".kts")
        val canAugmentKeywords = isKotlin && prefix.isNotEmpty() && triggerCharacter == null
        val canAugmentImports = isKotlin && prefix.length >= 2 && triggerCharacter == null
        return ws.completion(uri, line, character, triggerCharacter, prefix)
            .thenApply { list ->
                mark("kls")
                if (list == null || !canAugmentKeywords) list
                else augmentKeywords(list, prefix).also { mark("kw") }
            }
            .thenCompose { list ->
                if (list == null || !canAugmentImports) CompletableFuture.completedFuture(list)
                else augmentImports(ws, list, prefix, text).thenApply { result ->
                    mark("imp")
                    result
                }
            }
            .thenApply { list ->
                val sorted = if (list == null || prefix.isEmpty()) list
                else list.copy(items = CompletionEnhancer.applyPrefixSort(list.items, prefix))
                mark("sort")
                sorted
            }
            .whenComplete { list, err ->
                if (err != null) {
                    println("[lsp] completion ✗ $uri: ${err.message} [$timings]")
                } else if (list != null) {
                    if (prefix.isNotEmpty()) {
                        synchronized(completionCache) { completionCache[cacheKey] = list }
                        if (triggerCharacter == null && !list.isIncomplete) {
                            lastCompletionByLine[uri to line] = LastCompletion(character, prefix, list)
                        }
                    }
                    println("[lsp] completion ✓ $uri — ${list.items.size} items (incomplete=${list.isIncomplete}) [$timings]")
                    val logLimit = if (triggerCharacter == ".") list.items.size else 20
                    list.items.take(logLimit).forEachIndexed { i, it ->
                        val labelPreview = it.label.take(40).replace("\n", "⏎")
                        val insertPreview = it.insertText.take(60).replace("\n", "⏎")
                        println("  [$i] kind=${it.kind} label='$labelPreview' detail='${it.detail?.take(60)}' insert='$insertPreview' snippet=${it.isSnippet}")
                    }
                    if (list.items.size > logLimit) println("  … (+${list.items.size - logLimit} more)")
                }
            }
    }

    private fun augmentImports(
        ws: LspWorkspace,
        list: CompletionList,
        prefix: String,
        text: String,
    ): CompletableFuture<CompletionList> {
        val header = parseFileHeader(text)
        val tWsStart = System.nanoTime()
        println("[lsp] augmentImports → workspaceSymbols('$prefix') sent")
        return ws.workspaceSymbols(prefix)
            .exceptionally {
                val wsMs = (System.nanoTime() - tWsStart) / 1_000_000
                println("[lsp] workspaceSymbols('$prefix') failed in ${wsMs}ms: ${it.message}")
                emptyList()
            }
            .thenApply { syms ->
                val wsMs = (System.nanoTime() - tWsStart) / 1_000_000
                println("[lsp] augmentImports ← workspaceSymbols('$prefix') returned ${syms.size} sym(s) in ${wsMs}ms")
                if (syms.isEmpty()) return@thenApply list
                val existingLabels = list.items.mapTo(HashSet()) { it.label }
                val typeKinds = setOf(
                    org.eclipse.lsp4j.SymbolKind.Class,
                    org.eclipse.lsp4j.SymbolKind.Interface,
                    org.eclipse.lsp4j.SymbolKind.Enum,
                    org.eclipse.lsp4j.SymbolKind.Struct,
                )
                val seen = HashSet<String>()
                val candidates = mutableListOf<WorkspaceSymbolEntry>()
                for (sym in syms) {
                    if (sym.kind !in typeKinds) continue
                    if (sym.name.isEmpty()) continue
                    if (!sym.name.startsWith(prefix, ignoreCase = true)) continue
                    val container = sym.containerName?.takeIf { it.isNotBlank() } ?: continue
                    if (container == header.packageName) continue
                    val fqn = "$container.${sym.name}"
                    if (fqn in header.importedFqns) continue
                    if (sym.name in existingLabels) continue
                    if (!seen.add(fqn)) continue
                    candidates += sym
                    if (candidates.size >= 20) break
                }
                if (candidates.isEmpty()) return@thenApply list
                println("[lsp] augmentImports → ${candidates.size} candidate(s) for prefix='$prefix'")
                val newItems = candidates.map { sym ->
                    val container = sym.containerName!!
                    val fqn = "$container.${sym.name}"
                    val newText = if (header.insertNeedsLeadingBlankLine) "\nimport $fqn\n" else "import $fqn\n"
                    CompletionItem(
                        label = sym.name,
                        kind = mapSymbolKind(sym.kind),
                        detail = "(import from $container)",
                        documentation = null,
                        insertText = sym.name,
                        isSnippet = false,
                        edit = null,
                        additionalEdits = listOf(
                            CompletionEdit(
                                startLine = header.insertLine,
                                startCharacter = 0,
                                endLine = header.insertLine,
                                endCharacter = 0,
                                newText = newText,
                            )
                        ),
                        filterText = sym.name,
                        sortText = sym.name,
                    )
                }
                val leadingKeywordCount = list.items.takeWhile { it.kind == CompletionItemKind.KEYWORD }.size
                val head = list.items.take(leadingKeywordCount)
                val tail = list.items.drop(leadingKeywordCount)
                list.copy(items = head + newItems + tail)
            }
    }

    private fun mapSymbolKind(kind: org.eclipse.lsp4j.SymbolKind?): CompletionItemKind = when (kind) {
        org.eclipse.lsp4j.SymbolKind.Interface -> CompletionItemKind.INTERFACE
        org.eclipse.lsp4j.SymbolKind.Enum -> CompletionItemKind.ENUM
        org.eclipse.lsp4j.SymbolKind.Struct -> CompletionItemKind.STRUCT
        else -> CompletionItemKind.CLASS
    }

    private data class FileHeader(
        val packageName: String?,
        val importedFqns: Set<String>,
        val insertLine: Int,
        val insertNeedsLeadingBlankLine: Boolean,
    )

    private fun parseFileHeader(text: String): FileHeader {
        val lines = text.split('\n')
        var pkg: String? = null
        val imports = HashSet<String>()
        var lastImportLine = -1
        var packageLine = -1
        for (i in lines.indices) {
            val stripped = lines[i].trim().removeSuffix(";")
            when {
                stripped.startsWith("package ") -> {
                    pkg = stripped.removePrefix("package").trim()
                    packageLine = i
                }
                stripped.startsWith("import ") -> {
                    val body = stripped.removePrefix("import").trim()
                    val fqn = body.substringBefore(" as ").trim()
                    imports += fqn
                    lastImportLine = i
                }
            }
        }
        val insertLine: Int
        val needsBlank: Boolean
        when {
            lastImportLine >= 0 -> { insertLine = lastImportLine + 1; needsBlank = false }
            packageLine >= 0 -> { insertLine = packageLine + 1; needsBlank = true }
            else -> { insertLine = 0; needsBlank = false }
        }
        return FileHeader(pkg, imports, insertLine, needsBlank)
    }

    private fun augmentKeywords(list: CompletionList, prefix: String): CompletionList {
        val matched = KOTLIN_KEYWORDS.filter { it.startsWith(prefix, ignoreCase = true) }
        if (matched.isEmpty()) return list
        val existing = list.items.mapTo(HashSet()) { it.label }
        val toAdd = matched.filter { it !in existing }
        if (toAdd.isEmpty()) return list
        val keywordItems = toAdd.map { kw ->
            CompletionItem(
                label = kw,
                kind = CompletionItemKind.KEYWORD,
                detail = null,
                documentation = null,
                insertText = kw,
                isSnippet = false,
                edit = null,
                additionalEdits = emptyList(),
                filterText = kw,
                sortText = kw,
            )
        }
        return list.copy(items = keywordItems + list.items)
    }

    private fun computePrefix(text: String, line: Int, character: Int): String {
        val lines = text.split('\n')
        if (line < 0 || line >= lines.size) return ""
        val raw = lines[line]
        val ln = if (raw.endsWith('\r')) raw.dropLast(1) else raw
        val col = character.coerceIn(0, ln.length)
        var start = col
        while (start > 0) {
            val c = ln[start - 1]
            if (!c.isLetterOrDigit() && c != '_') break
            start--
        }
        return ln.substring(start, col)
    }

    fun shutdown() {
        pendingChanges.values.forEach { it.cancel() }
        pendingChanges.clear()
        client?.shutdown()
        scope.cancel()
    }

    companion object {
        private const val COMPLETION_CACHE_MAX = 64
        const val STARTUP_KIND = "startup"
        const val GRADLE_DEPS_KIND = KLS_GRADLE_DEPS_KIND
        const val GRADLE_SCRIPT_DEPS_KIND = KLS_GRADLE_SCRIPT_DEPS_KIND
        const val SYMBOL_INDEX_KIND = KLS_SYMBOL_INDEX_KIND
        const val LINTING_KIND = KLS_LINTING_KIND

        private val KOTLIN_KEYWORDS = listOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
            "if", "in", "interface", "is", "null", "object", "package", "return",
            "super", "this", "throw", "true", "try", "typealias", "val", "var",
            "when", "while",
            "by", "catch", "constructor", "delegate", "dynamic", "field", "file",
            "finally", "get", "import", "init", "param", "property", "receiver",
            "set", "setparam", "value", "where",
            "abstract", "actual", "annotation", "companion", "const", "crossinline",
            "data", "enum", "expect", "external", "final", "infix", "inline",
            "inner", "internal", "lateinit", "noinline", "open", "operator", "out",
            "override", "private", "protected", "public", "reified", "sealed",
            "suspend", "tailrec", "vararg",
        )
    }

    private fun onDiagnostics(params: PublishDiagnosticsParams) {
        val uri = params.uri ?: return
        val mapped = params.diagnostics.orEmpty().map(Diagnostic::fromLsp)
        diagnosticsByUri[uri] = mapped
        println("[lsp] publishDiagnostics $uri — ${mapped.size} diagnostic(s)")
    }
}

@Composable
fun rememberLspController(workspaceRoot: Path?): LspController {
    val controller = remember(workspaceRoot) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        LspController(workspaceRoot, scope)
    }
    DisposableEffect(controller) {
        onDispose { controller.shutdown() }
    }
    return controller
}

val LspController.errorAndWarningCount: Int
    get() {
        var n = 0
        for ((_, list) in diagnosticsByUri) {
            for (d in list) {
                val s = d.severity
                if (s == page.lsp.DiagnosticSeverity.ERROR || s == page.lsp.DiagnosticSeverity.WARNING) n++
            }
        }
        return n
    }
