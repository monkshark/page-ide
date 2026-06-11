package page.language

import page.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.PublishDiagnosticsParams
import page.lsp.CompletionAugmentor
import page.lsp.CompletionEnhancer
import page.lsp.CompletionList
import page.lsp.CompletionProfile
import page.lsp.DefinitionTarget
import page.lsp.Diagnostic
import page.lsp.HoverInfo
import page.lsp.InlayHintItem
import page.lsp.KLS_GRADLE_DEPS_KIND
import page.lsp.KLS_GRADLE_SCRIPT_DEPS_KIND
import page.lsp.KLS_LINTING_KIND
import page.lsp.KLS_SYMBOL_INDEX_KIND
import page.lsp.KlsActivity
import page.lsp.LspProgress
import page.lsp.LanguageBackend
import page.lsp.LanguageDefinition
import page.lsp.LanguageRegistry
import page.lsp.LspBackends
import page.lsp.LspClient
import page.lsp.LspState
import page.lsp.LspWorkspace
import page.lsp.CallHierarchyCall
import page.lsp.CallHierarchyItemInfo
import page.lsp.CodeActionEntry
import page.lsp.ReferenceLocation
import page.lsp.RenameEdit
import page.lsp.RenamePrepare
import page.lsp.RenameWorkspaceEdit
import page.lsp.SignatureHelpInfo
import page.lsp.DocumentSymbolEntry
import page.lsp.WorkspaceSymbolLocated
import page.lsp.parseKlsActivity
import page.editor.SyntaxLexers
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class LspController(
    private val workspaceRoot: Path?,
    private val scope: CoroutineScope,
) {

    enum class Status { IDLE, STARTING, READY, MISSING, FAILED }

    data class Activity(
        val kind: String,
        val label: String,
        val startedAtMs: Long,
        val progress: Float? = null,
        val installerId: String? = null,
    )

    val status: MutableState<Status> = mutableStateOf(Status.IDLE)
    var startedAtMs: Long = System.currentTimeMillis()
        private set
    private var readyAtMs = 0L
    private var firstServerFeedbackLogged = false
    val statusDetail: MutableState<String> = mutableStateOf("")
    val missingDefinition: MutableState<LanguageDefinition?> = mutableStateOf(null)
    val missingAttempted: MutableState<List<String>> = mutableStateOf(emptyList())
    private val _installGuideOpen = MutableStateFlow(false)
    val installGuideOpen: StateFlow<Boolean> = _installGuideOpen

    fun openInstallGuide() { _installGuideOpen.value = true }
    fun closeInstallGuide() { _installGuideOpen.value = false }
    val activities: SnapshotStateMap<String, Activity> = androidx.compose.runtime.mutableStateMapOf()
    val diagnosticsByUri: SnapshotStateMap<String, List<Diagnostic>> = androidx.compose.runtime.mutableStateMapOf()

    private var client: LspClient? = null
    private var workspace: LspWorkspace? = null
    private val pendingChanges = ConcurrentHashMap<String, Job>()
    private var startAttempted = false
    private var activeBackend: LanguageBackend? = null
    val backendId: String? get() = activeBackend?.id

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

    private data class InlayHintCacheKey(
        val uri: String,
        val version: Int,
        val startLine: Int,
        val startCharacter: Int,
        val endLine: Int,
        val endCharacter: Int,
    )

    private val inlayHintCache: MutableMap<InlayHintCacheKey, List<InlayHintItem>> = Collections.synchronizedMap(
        object : LinkedHashMap<InlayHintCacheKey, List<InlayHintItem>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<InlayHintCacheKey, List<InlayHintItem>>): Boolean = size > INLAY_HINT_CACHE_MAX
        }
    )

    @Volatile private var prepareRenameSupported: Boolean = false
    @Volatile private var inlayHintSupported: Boolean = false
    @Volatile private var completionResolveSupported: Boolean = false
    @Volatile private var executeCommandSupported: Boolean = false
    @Volatile private var callHierarchySupported: Boolean = false

    val supportsCallHierarchy: Boolean get() = status.value == Status.READY && callHierarchySupported

    @Volatile var applyEditHandler: ((RenameWorkspaceEdit) -> Boolean)? = null

    @Volatile private var clientGeneration: Long = 0L

    fun ensureStarted(backend: LanguageBackend) {
        if (startAttempted) return
        startAttempted = true
        activeBackend = backend
        startActivityJanitor()
        val resolveStartedMs = System.currentTimeMillis()
        println("[lsp] resolving ${backend.displayName} (workspace=$workspaceRoot)")
        val env = HashMap(System.getenv())
        PageRuntimeEnv.applyTo(env)
        val resolution = backend.resolveExecutable(env)
        if (resolution !is LanguageBackend.Resolution.Found) {
            val notFound = resolution as LanguageBackend.Resolution.NotFound
            val joined = notFound.attempted.joinToString("\n  ")
            markMissing(
                backendId = backend.id,
                attempted = notFound.attempted,
                detail = "${backend.displayName} not found. Tried:\n  $joined",
            )
            return
        }
        status.value = Status.STARTING
        startedAtMs = System.currentTimeMillis()
        statusDetail.value = "starting (${resolution.origin}: ${resolution.executable})"
        startActivity(STARTUP_KIND, "Starting…")
        println("[lsp] STARTING — ${resolution.origin}: ${resolution.executable} (resolve=${startedAtMs - resolveStartedMs}ms)")
        val myGeneration = ++clientGeneration
        try {
            val spawnStartedMs = System.currentTimeMillis()
            val c = backend.spawn(resolution.executable, workspaceRoot, onStderrLine = ::onLspStderr, env = env)
            val spawnMs = System.currentTimeMillis() - spawnStartedMs
            c.onDiagnostics { params -> if (myGeneration == clientGeneration) onDiagnostics(params) }
            c.onLogMessage { mp ->
                val rendered = if (mp.type == org.eclipse.lsp4j.MessageType.Error) condenseStackTrace(mp.message ?: "") else mp.message
                println("[lsp:log/${mp.type}] $rendered")
                applyActivityEvent(parseKlsActivity(mp.message))
            }
            c.onShowMessage { mp -> println("[lsp:show/${mp.type}] ${mp.message}") }
            c.onProgress { event -> if (myGeneration == clientGeneration) applyProgressEvent(event) }
            c.onApplyEdit { lspEdit ->
                if (myGeneration != clientGeneration) return@onApplyEdit false
                val edit = RenameWorkspaceEdit.fromLsp(lspEdit)
                if (edit.isEmpty) return@onApplyEdit false
                val handler = applyEditHandler ?: return@onApplyEdit false
                val applied = runCatching { handler(edit) }.getOrDefault(false)
                println("[lsp] applyEdit ◀ server — ${edit.changes.sumOf { it.edits.size }} edit(s), applied=$applied")
                applied
            }
            val initializeStartedMs = System.currentTimeMillis()
            val startFuture = c.start()
            scope.launch {
                kotlinx.coroutines.delay(60_000)
                if (myGeneration == clientGeneration && status.value == Status.STARTING) {
                    println("[lsp] STARTING timeout (60s) — marking FAILED")
                    startFuture.cancel(true)
                    endActivity(STARTUP_KIND)
                    clearActivities("initialize timeout")
                    status.value = Status.FAILED
                    statusDetail.value = "initialize did not respond within 60s"
                }
            }
            startFuture.whenComplete { result, throwable ->
                endActivity(STARTUP_KIND)
                if (throwable != null) {
                    clearActivities("initialize failed")
                    status.value = Status.FAILED
                    statusDetail.value = throwable.message ?: throwable.toString()
                    println("[lsp] FAILED on initialize: ${throwable.message}")
                    throwable.printStackTrace()
                } else {
                    status.value = Status.READY
                    readyAtMs = System.currentTimeMillis()
                    firstServerFeedbackLogged = false
                    statusDetail.value = "${backend.displayName} ready (capabilities=${result.capabilities != null})"
                    println("[lsp] READY — capabilities=${result.capabilities != null} (spawn=${spawnMs}ms initialize=${readyAtMs - initializeStartedMs}ms)")
                    prepareRenameSupported = detectPrepareRenameSupport(result.capabilities)
                    println("[lsp] prepareRename support = $prepareRenameSupported")
                    inlayHintSupported = detectInlayHintSupport(result.capabilities)
                    println("[lsp] inlayHint support = $inlayHintSupported")
                    completionResolveSupported = detectCompletionResolveSupport(result.capabilities)
                    println("[lsp] completion resolve support = $completionResolveSupported")
                    executeCommandSupported = detectExecuteCommandSupport(result.capabilities)
                    println("[lsp] executeCommand support = $executeCommandSupported")
                    callHierarchySupported = detectCallHierarchySupport(result.capabilities)
                    println("[lsp] callHierarchy support = $callHierarchySupported")
                    flushPendingOpens()
                    openWorkspaceFiles()
                    applyJavaCompilerPolicies(backend)
                    if (!backend.workspaceAutoOpen) {
                        startActivity(ANALYSIS_KIND, "Analyzing project…")
                    }
                }
            }
            client = c
            workspace = LspWorkspace(c)
        } catch (t: Throwable) {
            endActivity(STARTUP_KIND)
            clearActivities("spawn failed")
            status.value = Status.FAILED
            statusDetail.value = t.message ?: t.toString()
            println("[lsp] FAILED on spawn: ${t.message}")
            t.printStackTrace()
        }
    }

    internal fun startActivity(kind: String, label: String, progress: Float? = null) {
        val now = System.currentTimeMillis()
        val existing = activities[kind]
        if (existing == null) {
            activities[kind] = Activity(kind, label, now, progress = progress)
        } else {
            activities[kind] = existing.copy(label = label, startedAtMs = now, progress = progress)
        }
    }

    private fun endActivity(kind: String) {
        activities.remove(kind)
    }

    private fun logFirstServerFeedback(what: String) {
        if (firstServerFeedbackLogged || readyAtMs == 0L) return
        firstServerFeedbackLogged = true
        println("[lsp] first server feedback ($what) +${System.currentTimeMillis() - readyAtMs}ms after READY")
    }

    internal fun markMissing(backendId: String, attempted: List<String>, detail: String) {
        missingDefinition.value = LanguageRegistry.byId(backendId)
        missingAttempted.value = attempted
        status.value = Status.MISSING
        statusDetail.value = detail
        println("[lsp] MISSING — $detail")
    }

    fun retry() {
        if (status.value != Status.MISSING && status.value != Status.FAILED) return
        println("[lsp] retry requested (previous status=${status.value})")
        startAttempted = false
        missingDefinition.value = null
        missingAttempted.value = emptyList()
        status.value = Status.IDLE
        statusDetail.value = ""
        _installGuideOpen.value = false
        val backend = activeBackend ?: return
        ensureStarted(backend)
    }

    private fun applyActivityEvent(event: KlsActivity?) {
        event ?: return
        when (event) {
            is KlsActivity.Start -> startActivity(event.kind, event.label)
            is KlsActivity.End -> endActivity(event.kind)
        }
    }

    private val progressTitles = java.util.concurrent.ConcurrentHashMap<String, String>()

    internal fun applyProgressEvent(event: LspProgress) {
        val kind = "progress:${event.token}"
        when (event) {
            is LspProgress.Begin -> {
                println("[lsp] progress ◀ Begin token=${event.token} title='${event.title}' message='${event.message ?: ""}'")
                logFirstServerFeedback("progress")
                endActivity(ANALYSIS_KIND)
                progressTitles[event.token] = event.title
                startActivity(kind, progressLabel(event.title, event.message), progressFraction(event.percentage))
            }
            is LspProgress.Report -> {
                val title = progressTitles[event.token].orEmpty()
                startActivity(kind, progressLabel(title, event.message), progressFraction(event.percentage))
            }
            is LspProgress.End -> {
                println("[lsp] progress ◀ End token=${event.token}")
                progressTitles.remove(event.token)
                endActivity(kind)
            }
        }
    }

    private fun progressFraction(percentage: Int?): Float? =
        percentage?.let { (it / 100f).coerceIn(0f, 1f) }

    private fun progressLabel(title: String, message: String?): String = when {
        title.isNotBlank() && !message.isNullOrBlank() -> "$title — $message"
        title.isNotBlank() -> title
        !message.isNullOrBlank() -> message
        else -> "Working…"
    }

    internal fun activityTimeoutMs(kind: String): Long = when {
        kind == STARTUP_KIND -> 120_000L
        kind == GRADLE_DEPS_KIND || kind == GRADLE_SCRIPT_DEPS_KIND -> 600_000L
        kind == SYMBOL_INDEX_KIND -> 180_000L
        kind == LINTING_KIND -> 30_000L
        kind == ANALYSIS_KIND || kind.startsWith("progress:") -> 600_000L
        else -> 60_000L
    }

    private fun pruneStaleActivities() {
        if (activities.isEmpty()) return
        val now = System.currentTimeMillis()
        val expired = activities.entries.filter { (kind, act) ->
            now - act.startedAtMs > activityTimeoutMs(kind)
        }.map { it.key }
        if (expired.isNotEmpty()) {
            println("[lsp] pruning stale activities (no End received): $expired")
            expired.forEach { activities.remove(it) }
        }
    }

    private fun clearActivities(reason: String) {
        if (activities.isEmpty()) return
        val kinds = activities.keys.toList()
        println("[lsp] clearing activities ($reason): $kinds")
        activities.clear()
        progressTitles.clear()
    }

    fun didOpen(path: Path, languageId: String, text: String) {
        val uri = path.toUri().toString()
        if (status.value != Status.READY) {
            pendingOpens[uri] = PendingOpen(path, languageId, text)
            println("[lsp] didOpen queued (status=${status.value}) for $uri")
            return
        }
        val ws = workspace ?: return
        scope.launch(Dispatchers.Default) {
            try {
                if (ws.isOpen(uri)) {
                    println("[lsp] didChange (already open) $uri")
                    ws.didChange(uri, text)
                } else {
                    println("[lsp] didOpen $uri (lang=$languageId, ${text.length} chars)")
                    ws.didOpen(uri, languageId, text)
                }
            } catch (t: Throwable) {
                println("[lsp] didOpen failed for $uri: ${t.message}")
            }
        }
    }

    fun isOpenAt(path: Path): Boolean {
        if (status.value != Status.READY) return false
        val ws = workspace ?: return false
        return ws.isOpen(path.toUri().toString())
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
                println("[lsp] didChange → ${activeBackend?.id ?: "lsp"} for $uri (${text.length} chars)")
                ws.didChange(uri, text)
            } catch (t: Throwable) {
                println("[lsp] didChange failed for $uri: ${t.message}")
            }
        }
    }

    fun didSave(path: Path, text: String) {
        val uri = path.toUri().toString()
        if (status.value != Status.READY) return
        val ws = workspace ?: return
        if (!ws.isOpen(uri)) return
        pendingChanges[uri]?.cancel()
        invalidateCompletionCache(uri)
        scope.launch(Dispatchers.Default) {
            try {
                ws.didChange(uri, text)
                ws.didSave(uri)
                println("[lsp] didSave → ${activeBackend?.id ?: "lsp"} for $uri")
            } catch (t: Throwable) {
                println("[lsp] didSave failed for $uri: ${t.message}")
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
                val text = p.text.ifEmpty {
                    runCatching { java.nio.file.Files.readString(p.path) }
                        .onSuccess { println("[lsp] flush text was empty — loaded ${it.length} chars from disk for $uri") }
                        .getOrDefault("")
                }
                if (ws.isOpen(uri)) {
                    println("[lsp] flush → didChange $uri (${text.length} chars)")
                    ws.didChange(uri, text)
                } else {
                    println("[lsp] flush → didOpen $uri (lang=${p.languageId}, ${text.length} chars)")
                    ws.didOpen(uri, p.languageId, text)
                }
            } catch (t: Throwable) {
                println("[lsp] flush failed for $uri: ${t.message}")
            }
        }
    }

    private fun applyJavaCompilerPolicies(backend: LanguageBackend) {
        if (backend.id != "java" || !executeCommandSupported) return
        val myGeneration = clientGeneration
        val rootFallback = workspaceRoot?.toUri()?.toString()?.removeSuffix("/")
        scope.launch {
            kotlinx.coroutines.delay(2_000)
            if (myGeneration != clientGeneration || status.value != Status.READY) return@launch
            val ws = workspace ?: return@launch
            ws.executeCommandForResult(
                "java.project.getAll",
                emptyList(),
            ).thenAccept { result ->
                if (myGeneration != clientGeneration) return@thenAccept
                val targets = extractProjectUris(result).ifEmpty { listOfNotNull(rootFallback) }
                if (targets.isEmpty()) {
                    println("[lsp] java compiler policies → no java project resolved")
                    return@thenAccept
                }
                targets.forEach { projectUri ->
                    ws.executeCommand(
                        "java.project.updateSettings",
                        listOf(projectUri, page.runtime.JdtlsInitializationOptions.compilerProblemSeverities),
                    ).thenAccept { ok ->
                        println("[lsp] java compiler policies → unusedImport=warning ${if (ok) "✓" else "✗"} ($projectUri)")
                    }
                }
            }
        }
    }

    fun didClose(path: Path) {
        val uri = path.toUri().toString()
        pendingChanges.remove(uri)?.cancel()
        pendingOpens.remove(uri)
        invalidateCompletionCache(uri)
        invalidateInlayHintCache(uri)
        val ws = workspace
        if (ws != null && ws.isOpen(uri)) ws.didClose(uri)
        diagnosticsByUri.remove(canonicalUri(uri))
        pendingDiagnostics.remove(canonicalUri(uri))
        unnecessaryByContent.remove(canonicalUri(uri))
    }

    private fun invalidateInlayHintCache(uri: String) {
        synchronized(inlayHintCache) {
            val it = inlayHintCache.keys.iterator()
            while (it.hasNext()) if (it.next().uri == uri) it.remove()
        }
    }

    fun diagnosticsFor(path: Path): List<Diagnostic> {
        val uri = path.toUri().toString()
        return diagnosticsByUri[canonicalUri(uri)].orEmpty()
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
                .onSuccess { println("[lsp] didChange (completion sync) → $uri (${text.length} chars)") }
                .onFailure { println("[lsp] didChange (completion sync) failed for $uri: ${it.message}") }
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

        val profile = CompletionProfile.forLanguage(activeBackend?.id)
        val canAugmentKeywords = profile.keywords.isNotEmpty() && prefix.isNotEmpty() && triggerCharacter == null &&
            !isMemberAccessContext(text, line, character, prefix.length)
        val canAugmentImports = profile.supportsAutoImport && prefix.length >= 2 && triggerCharacter == null
        return ws.completion(uri, line, character, triggerCharacter, prefix)
            .thenApply { list ->
                mark("lsp")
                if (list == null || !canAugmentKeywords) list
                else CompletionAugmentor.augmentKeywords(list, prefix, profile.keywords, profile.keywordSnippets).also { mark("kw") }
            }
            .thenCompose { list ->
                if (list == null || !canAugmentImports) CompletableFuture.completedFuture(list)
                else augmentImports(ws, list, prefix, text, profile.importStatementTerminator).thenApply { result ->
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
        importTerminator: String,
    ): CompletableFuture<CompletionList> {
        val header = CompletionAugmentor.parseFileHeader(text)
        val existingLabels = list.items.mapTo(HashSet()) { it.label }
        val tWsStart = System.nanoTime()
        println("[lsp] augmentImports → workspaceSymbols('$prefix') sent")
        return ws.workspaceSymbols(prefix)
            .orTimeout(WORKSPACE_SYMBOLS_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .exceptionally {
                val wsMs = (System.nanoTime() - tWsStart) / 1_000_000
                println("[lsp] workspaceSymbols('$prefix') failed in ${wsMs}ms: ${it.message}")
                emptyList()
            }
            .thenApply { syms ->
                val wsMs = (System.nanoTime() - tWsStart) / 1_000_000
                println("[lsp] augmentImports ← workspaceSymbols('$prefix') returned ${syms.size} sym(s) in ${wsMs}ms")
                val newItems = CompletionAugmentor.buildImportCandidates(syms, header, prefix, existingLabels, importTerminator)
                if (newItems.isEmpty()) return@thenApply list
                println("[lsp] augmentImports → ${newItems.size} candidate(s) for prefix='$prefix'")
                CompletionAugmentor.mergeImportItems(list, newItems)
            }
    }

    fun hover(path: Path, line: Int, character: Int): CompletableFuture<HoverInfo?> {
        if (status.value != Status.READY) return CompletableFuture.completedFuture(null)
        val ws = workspace ?: return CompletableFuture.completedFuture(null)
        val uri = path.toUri().toString()
        if (!ws.isOpen(uri)) return CompletableFuture.completedFuture(null)
        val tStart = System.nanoTime()
        return ws.hover(uri, line, character)
            .whenComplete { info, err ->
                val ms = (System.nanoTime() - tStart) / 1_000_000
                if (err != null) {
                    println("[lsp] hover ✗ $uri @($line,$character): ${err.message} [${ms}ms]")
                } else if (info == null) {
                    println("[lsp] hover — $uri @($line,$character) [${ms}ms] (null/blank)")
                } else {
                    val md = info.markdown
                    val preview = md.replace("\n", "⏎").take(200)
                    val tail = if (md.length > 200) "…(+${md.length - 200})" else ""
                    val rng = info.range?.let { "[(${it.startLine},${it.startCharacter})..(${it.endLine},${it.endCharacter})]" } ?: "[no-range]"
                    println("[lsp] hover ✓ $uri @($line,$character) [${ms}ms] range=$rng len=${md.length} md='$preview$tail'")
                }
            }
    }

    fun definition(path: Path, line: Int, character: Int): CompletableFuture<List<DefinitionTarget>> {
        if (status.value != Status.READY) return CompletableFuture.completedFuture(emptyList())
        val ws = workspace ?: return CompletableFuture.completedFuture(emptyList())
        val uri = path.toUri().toString()
        if (!ws.isOpen(uri)) return CompletableFuture.completedFuture(emptyList())
        val tStart = System.nanoTime()
        return ws.definition(uri, line, character)
            .whenComplete { targets, err ->
                val ms = (System.nanoTime() - tStart) / 1_000_000
                if (err != null) {
                    println("[lsp] definition ✗ $uri @($line,$character): ${err.message} [${ms}ms]")
                } else {
                    val list = targets.orEmpty()
                    println("[lsp] definition ✓ $uri @($line,$character) — ${list.size} target(s) [${ms}ms]")
                    list.take(5).forEachIndexed { i, t ->
                        println("  [$i] ${t.uri} @(${t.startLine},${t.startCharacter})..(${t.endLine},${t.endCharacter})")
                    }
                    if (list.size > 5) println("  … (+${list.size - 5} more)")
                }
            }
    }

    fun references(
        path: Path,
        line: Int,
        character: Int,
        includeDeclaration: Boolean = true,
        symbolName: String? = null,
    ): CompletableFuture<List<ReferenceLocation>> {
        if (status.value != Status.READY) return CompletableFuture.completedFuture(emptyList())
        val ws = workspace ?: return CompletableFuture.completedFuture(emptyList())
        val uri = path.toUri().toString()
        if (!ws.isOpen(uri)) return CompletableFuture.completedFuture(emptyList())
        val tStart = System.nanoTime()

        if (!symbolName.isNullOrEmpty()) {
            val scope = computeScope(uri, line, character, symbolName)
            val scanned = referencesByTextScan(symbolName, scope)
            val scopeLabel = if (scope != null) {
                val fname = scope.uri.substringAfterLast('/')
                "local in $fname offsets=${scope.range.first}..${scope.range.last}"
            } else "workspace"
            println("[lsp] references(text-scan) for '$symbolName' scope=$scopeLabel — ${scanned.size} occurrence(s)")
            scanned.take(10).forEachIndexed { i, r ->
                println("  scan[$i] ${r.uri} @(${r.startLine},${r.startCharacter})..(${r.endLine},${r.endCharacter})")
            }
            if (scanned.size > 10) println("  … (+${scanned.size - 10} more)")
            val ms = (System.nanoTime() - tStart) / 1_000_000
            println("[lsp] references ✓ $uri @($line,$character) '$symbolName' — ${scanned.size} ref(s) (text-scan) [${ms}ms]")
            return CompletableFuture.completedFuture(scanned)
        }

        val refsFuture = ws.references(uri, line, character, includeDeclaration)
            .whenComplete { raw, _ ->
                val list = raw.orEmpty()
                println("[lsp] references(raw KLS) $uri @($line,$character) — ${list.size} ref(s)")
                list.forEachIndexed { i, r ->
                    println("  raw[$i] ${r.uri} @(${r.startLine},${r.startCharacter})..(${r.endLine},${r.endCharacter})")
                }
            }
        val merged = if (includeDeclaration) {
            ws.definition(uri, line, character)
                .handle { defs, err ->
                    val list = defs.orEmpty()
                    if (err != null) {
                        println("[lsp] references(definition aux) $uri @($line,$character) failed: ${err.message}")
                    } else {
                        println("[lsp] references(definition aux) $uri @($line,$character) — ${list.size} decl(s)")
                        list.forEachIndexed { i, d ->
                            println("  def[$i] ${d.uri} @(${d.startLine},${d.startCharacter})..(${d.endLine},${d.endCharacter})")
                        }
                    }
                    list
                }
                .thenCombine(refsFuture) { defs, refs -> mergeDeclarationIntoRefs(defs, refs.orEmpty()) }
        } else {
            refsFuture.thenApply { it.orEmpty() }
        }
        val sanitized = merged.thenApply { list -> snapReferencesToIdentifier(list, symbolName) }
        return sanitized.whenComplete { refs, err ->
            val ms = (System.nanoTime() - tStart) / 1_000_000
            if (err != null) {
                println("[lsp] references ✗ $uri @($line,$character): ${err.message} [${ms}ms]")
            } else {
                val list = refs.orEmpty()
                println("[lsp] references ✓ $uri @($line,$character) — ${list.size} ref(s) [${ms}ms]")
                list.take(5).forEachIndexed { i, r ->
                    println("  [$i] ${r.uri} @(${r.startLine},${r.startCharacter})..(${r.endLine},${r.endCharacter})")
                }
                if (list.size > 5) println("  … (+${list.size - 5} more)")
            }
        }
    }

    fun documentSymbols(path: Path): CompletableFuture<List<DocumentSymbolEntry>> {
        if (status.value != Status.READY) return CompletableFuture.completedFuture(emptyList())
        val ws = workspace ?: return CompletableFuture.completedFuture(emptyList())
        val uri = path.toUri().toString()
        if (!ws.isOpen(uri)) return CompletableFuture.completedFuture(emptyList())
        val tStart = System.nanoTime()
        return ws.documentSymbols(uri)
            .whenComplete { syms, err ->
                val ms = (System.nanoTime() - tStart) / 1_000_000
                if (err != null) {
                    println("[lsp] documentSymbols ✗ $uri: ${err.message} [${ms}ms]")
                } else {
                    val list = syms.orEmpty()
                    println("[lsp] documentSymbols ✓ $uri — ${list.size} top-level sym(s) [${ms}ms]")
                }
            }
    }

    fun prepareCallHierarchy(path: Path, line: Int, character: Int): CompletableFuture<List<CallHierarchyItemInfo>> {
        if (!supportsCallHierarchy) return CompletableFuture.completedFuture(emptyList())
        val ws = workspace ?: return CompletableFuture.completedFuture(emptyList())
        val uri = path.toUri().toString()
        if (!ws.isOpen(uri)) return CompletableFuture.completedFuture(emptyList())
        val tStart = System.nanoTime()
        return ws.prepareCallHierarchy(uri, line, character)
            .whenComplete { items, err ->
                val ms = (System.nanoTime() - tStart) / 1_000_000
                if (err != null) {
                    if (isUnsupportedOperation(err)) {
                        callHierarchySupported = false
                        println("[lsp] prepareCallHierarchy unsupported by server — disabling future calls [${ms}ms]")
                    } else {
                        println("[lsp] prepareCallHierarchy ✗ $uri @($line,$character): ${err.message} [${ms}ms]")
                    }
                } else {
                    val list = items.orEmpty()
                    println("[lsp] prepareCallHierarchy ✓ $uri @($line,$character) — ${list.size} item(s) [${ms}ms]")
                    list.take(5).forEachIndexed { i, it ->
                        println("  [$i] ${it.name} kind=${it.kind} ${it.uri} @(${it.selectionRange.startLine},${it.selectionRange.startCharacter})")
                    }
                }
            }
            .exceptionally { emptyList() }
    }

    fun incomingCalls(item: CallHierarchyItemInfo): CompletableFuture<List<CallHierarchyCall>> {
        if (!supportsCallHierarchy) return CompletableFuture.completedFuture(emptyList())
        val ws = workspace ?: return CompletableFuture.completedFuture(emptyList())
        val tStart = System.nanoTime()
        return ws.incomingCalls(item)
            .whenComplete { calls, err -> logCallHierarchyCalls("incomingCalls", item, calls, err, tStart) }
            .exceptionally { emptyList() }
    }

    fun outgoingCalls(item: CallHierarchyItemInfo): CompletableFuture<List<CallHierarchyCall>> {
        if (!supportsCallHierarchy) return CompletableFuture.completedFuture(emptyList())
        val ws = workspace ?: return CompletableFuture.completedFuture(emptyList())
        val tStart = System.nanoTime()
        return ws.outgoingCalls(item)
            .whenComplete { calls, err -> logCallHierarchyCalls("outgoingCalls", item, calls, err, tStart) }
            .exceptionally { emptyList() }
    }

    private fun logCallHierarchyCalls(
        what: String,
        item: CallHierarchyItemInfo,
        calls: List<CallHierarchyCall>?,
        err: Throwable?,
        tStart: Long,
    ) {
        val ms = (System.nanoTime() - tStart) / 1_000_000
        if (err != null) {
            if (isUnsupportedOperation(err)) {
                callHierarchySupported = false
                println("[lsp] $what unsupported by server — disabling future calls [${ms}ms]")
            } else {
                println("[lsp] $what ✗ '${item.name}': ${err.message} [${ms}ms]")
            }
        } else {
            val list = calls.orEmpty()
            println("[lsp] $what ✓ '${item.name}' — ${list.size} call(s) [${ms}ms]")
            list.take(5).forEachIndexed { i, c ->
                println("  [$i] ${c.item.name} kind=${c.item.kind} ${c.item.uri} (${c.fromRanges.size} site(s))")
            }
            if (list.size > 5) println("  … (+${list.size - 5} more)")
        }
    }

    fun workspaceSymbolsLocated(query: String): CompletableFuture<List<WorkspaceSymbolLocated>> {
        if (status.value != Status.READY) return CompletableFuture.completedFuture(emptyList())
        val ws = workspace ?: return CompletableFuture.completedFuture(emptyList())
        val tStart = System.nanoTime()
        return ws.workspaceSymbolsLocated(query)
            .whenComplete { syms, err ->
                val ms = (System.nanoTime() - tStart) / 1_000_000
                if (err != null) {
                    println("[lsp] workspaceSymbolsLocated('$query') ✗ ${err.message} [${ms}ms]")
                } else {
                    val list = syms.orEmpty()
                    println("[lsp] workspaceSymbolsLocated('$query') ✓ ${list.size} sym(s) [${ms}ms]")
                }
            }
    }

    fun formatting(path: Path, tabSize: Int = 4, insertSpaces: Boolean = true): CompletableFuture<List<RenameEdit>> {
        if (status.value != Status.READY) return CompletableFuture.completedFuture(emptyList())
        val ws = workspace ?: return CompletableFuture.completedFuture(emptyList())
        val uri = path.toUri().toString()
        if (!ws.isOpen(uri)) return CompletableFuture.completedFuture(emptyList())
        val tStart = System.nanoTime()
        return ws.formatting(uri, tabSize, insertSpaces)
            .whenComplete { edits, err ->
                val ms = (System.nanoTime() - tStart) / 1_000_000
                if (err != null) {
                    println("[lsp] formatting ✗ $uri: ${err.message} [${ms}ms]")
                } else {
                    val list = edits.orEmpty()
                    println("[lsp] formatting ✓ $uri — ${list.size} edit(s) [${ms}ms]")
                }
            }
    }

    fun codeActions(
        path: Path,
        startLine: Int,
        startCharacter: Int,
        endLine: Int,
        endCharacter: Int,
    ): CompletableFuture<List<CodeActionEntry>> {
        if (status.value != Status.READY) return CompletableFuture.completedFuture(emptyList())
        val ws = workspace ?: return CompletableFuture.completedFuture(emptyList())
        val uri = path.toUri().toString()
        if (!ws.isOpen(uri)) return CompletableFuture.completedFuture(emptyList())
        val overlappingDiags = diagnosticsByUri[canonicalUri(uri)].orEmpty().filter { d ->
            rangeOverlaps(
                d.start.line, d.start.character, d.end.line, d.end.character,
                startLine, startCharacter, endLine, endCharacter,
            )
        }
        val diags = overlappingDiags.map { toLspDiagnostic(it) }
        val currentText = ws.textOf(uri)
        val synthesized = if (currentText != null) {
            page.lsp.PageQuickFixes.synthesize(uri, currentText, overlappingDiags)
        } else emptyList()
        val tStart = System.nanoTime()
        val positionFuture = ws.codeAction(uri, startLine, startCharacter, endLine, endCharacter, diags)
            .exceptionally { emptyList() }
        val sourceFuture = ws.codeAction(
            uri, startLine, startCharacter, endLine, endCharacter,
            emptyList(), only = listOf(org.eclipse.lsp4j.CodeActionKind.Source),
        ).exceptionally { emptyList() }
        return positionFuture.thenCombine(sourceFuture) { position, source ->
            val ms = (System.nanoTime() - tStart) / 1_000_000
            val server = dedupActions(position + source)
            val merged = server + synthesized
            println("[lsp] codeAction ✓ $uri @($startLine,$startCharacter) — ${merged.size} action(s) (pos=${position.size}, source=${source.size}, synth=${synthesized.size}), ctx=${diags.size} diag(s) [${ms}ms]")
            merged
        }
    }

    private fun dedupActions(actions: List<CodeActionEntry>): List<CodeActionEntry> {
        val seen = HashSet<String>()
        return actions.filter { seen.add("${it.title}|${it.command}|${it.kind}") }
    }

    fun executeCommand(command: String, arguments: List<Any?>): CompletableFuture<Boolean> {
        if (status.value != Status.READY) return CompletableFuture.completedFuture(false)
        val ws = workspace ?: return CompletableFuture.completedFuture(false)
        if (!executeCommandSupported) {
            println("[lsp] executeCommand skipped (server lacks executeCommandProvider) — \"$command\"")
            return CompletableFuture.completedFuture(false)
        }
        println("[lsp] executeCommand ▶ \"$command\" (${arguments.size} arg(s))")
        return ws.executeCommand(command, arguments)
    }

    fun inlayHints(
        path: Path,
        startLine: Int,
        startCharacter: Int,
        endLine: Int,
        endCharacter: Int,
    ): CompletableFuture<List<InlayHintItem>> {
        if (status.value != Status.READY || !inlayHintSupported) return CompletableFuture.completedFuture(emptyList())
        val ws = workspace ?: return CompletableFuture.completedFuture(emptyList())
        val uri = path.toUri().toString()
        if (!ws.isOpen(uri)) return CompletableFuture.completedFuture(emptyList())
        val version = ws.versionOf(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val syncedText = ws.textOf(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val (reqStartLine, reqStartChar, reqEndLine, reqEndChar) =
            clampInlayHintRange(syncedText, startLine, startCharacter, endLine, endCharacter)
        val key = InlayHintCacheKey(uri, version, reqStartLine, reqStartChar, reqEndLine, reqEndChar)
        inlayHintCache[key]?.let {
            println("[lsp] inlayHints ✓ $uri @($reqStartLine..$reqEndLine) — ${it.size} hint(s), v=$version [cache=hit]")
            return CompletableFuture.completedFuture(it)
        }
        val tStart = System.nanoTime()
        return ws.inlayHints(uri, reqStartLine, reqStartChar, reqEndLine, reqEndChar)
            .handle<List<InlayHintItem>> { hints, err ->
                val ms = (System.nanoTime() - tStart) / 1_000_000
                if (err != null) {
                    println("[lsp] inlayHints ✗ $uri @($reqStartLine..$reqEndLine): ${err.message} [${ms}ms]")
                    emptyList()
                } else {
                    val result = hints.orEmpty()
                    inlayHintCache[key] = result
                    println("[lsp] inlayHints ✓ $uri @($reqStartLine..$reqEndLine) — ${result.size} hint(s), v=$version [${ms}ms]")
                    result
                }
            }
    }

    private data class ReferenceScope(val uri: String, val range: IntRange)

    private fun referencesByTextScan(
        symbolName: String,
        scope: ReferenceScope?,
    ): List<ReferenceLocation> {
        if (symbolName.isEmpty()) return emptyList()
        val ws = workspace ?: return emptyList()
        val out = mutableListOf<ReferenceLocation>()
        val nameLen = symbolName.length
        val urisToScan = if (scope != null) listOf(scope.uri) else ws.openUris().toList()
        val filterNamedArgs = scope != null
        for (uri in urisToScan) {
            val text = ws.textOf(uri) ?: continue
            val path = try { java.nio.file.Paths.get(java.net.URI(uri)) } catch (_: Throwable) { continue }
            val lexer = SyntaxLexers.forPath(path) ?: continue
            val tokens = runCatching { lexer.tokenize(text) }.getOrNull() ?: continue
            val excluded = stringCommentRanges(tokens)
            val lineStarts = computeLineStarts(text)
            val searchStart = if (scope != null && scope.uri == uri) scope.range.first else 0
            val searchEnd = if (scope != null && scope.uri == uri) scope.range.last + 1 else text.length
            var searchFrom = searchStart
            while (true) {
                val idx = text.indexOf(symbolName, searchFrom)
                if (idx < 0 || idx >= searchEnd) break
                val endExclusive = idx + nameLen
                if (endExclusive > searchEnd) { searchFrom = idx + 1; continue }
                val before = if (idx == 0) ' ' else text[idx - 1]
                val after = if (endExclusive >= text.length) ' ' else text[endExclusive]
                val isWordStart = !before.isLetterOrDigit() && before != '_'
                val isWordEnd = !after.isLetterOrDigit() && after != '_'
                if (isWordStart && isWordEnd && !isInsideRange(idx, excluded)) {
                    if (filterNamedArgs && isNamedArgumentPosition(text, idx, endExclusive)) {
                        searchFrom = idx + 1
                        continue
                    }
                    val (sl, sc) = offsetToLineCol(idx, lineStarts)
                    val (el, ec) = offsetToLineCol(endExclusive, lineStarts)
                    out += ReferenceLocation(uri, sl, sc, el, ec)
                }
                searchFrom = idx + 1
            }
        }
        return out
    }

    private fun computeScope(uri: String, line: Int, character: Int, symbolName: String): ReferenceScope? {
        val ws = workspace ?: return null
        val text = ws.textOf(uri) ?: return null
        val path = try { java.nio.file.Paths.get(java.net.URI(uri)) } catch (_: Throwable) { return null }
        val lexer = SyntaxLexers.forPath(path) ?: return null
        val tokens = runCatching { lexer.tokenize(text) }.getOrNull() ?: return null
        val excluded = stringCommentRanges(tokens)
        val caret = lineColToOffset(text, line, character) ?: return null
        val funRange = findEnclosingFunctionRange(text, tokens, caret, excluded) ?: return null
        val scopeText = text.substring(funRange.first, funRange.last + 1)
        if (!hasLocalDeclaration(scopeText, symbolName, excluded, funRange.first)) return null
        return ReferenceScope(uri, funRange)
    }

    private fun snapReferencesToIdentifier(
        refs: List<ReferenceLocation>,
        symbolName: String?,
    ): List<ReferenceLocation> {
        if (symbolName.isNullOrEmpty() || refs.isEmpty()) return refs
        val byUri = HashMap<String, String?>()
        val out = mutableListOf<ReferenceLocation>()
        val droppedKeys = HashSet<Triple<String, Int, Int>>()
        for (r in refs) {
            val text = byUri.getOrPut(r.uri) {
                workspace?.textOf(r.uri) ?: readFileTextByUri(r.uri)
            }
            if (text == null) { out += r; continue }
            val lineText = lineAtIndex(text, r.startLine)
            if (lineText == null) { out += r; continue }
            val sChar = r.startCharacter.coerceIn(0, lineText.length)
            val eChar = r.endCharacter.coerceIn(sChar, lineText.length)
            val existing = lineText.substring(sChar, eChar)
            if (existing == symbolName) {
                out += r
                continue
            }
            val match = findWordBoundaryMatch(lineText, symbolName, prefer = r.startCharacter)
            if (match == null) {
                droppedKeys += Triple(r.uri, r.startLine, r.startCharacter)
                println("[lsp] references(snap) dropped spurious — ${r.uri}@(${r.startLine},${r.startCharacter}) line='$lineText' (no '$symbolName' on this line)")
                continue
            }
            val adjusted = r.copy(
                startCharacter = match.first,
                endLine = r.startLine,
                endCharacter = match.second,
            )
            println("[lsp] references(snap) ${r.uri}@(${r.startLine},${r.startCharacter}→${match.first}) for '$symbolName' (was '$existing')")
            out += adjusted
        }
        val deduped = LinkedHashMap<Triple<String, Int, Int>, ReferenceLocation>()
        for (r in out) {
            val key = Triple(r.uri, r.startLine, r.startCharacter)
            deduped.putIfAbsent(key, r)
        }
        return deduped.values.toList()
    }

    fun signatureHelp(
        path: Path,
        text: String,
        line: Int,
        character: Int,
        triggerCharacter: String? = null,
        isRetrigger: Boolean = false,
    ): CompletableFuture<SignatureHelpInfo?> {
        if (status.value != Status.READY) return CompletableFuture.completedFuture(null)
        val ws = workspace ?: return CompletableFuture.completedFuture(null)
        val uri = path.toUri().toString()
        if (!ws.isOpen(uri)) return CompletableFuture.completedFuture(null)
        pendingChanges.remove(uri)?.cancel()
        runCatching { ws.didChange(uri, text) }
        val tStart = System.nanoTime()
        val trig = triggerCharacter ?: if (isRetrigger) "<retrigger>" else "<invoke>"
        return ws.signatureHelp(uri, line, character, triggerCharacter, isRetrigger)
            .whenComplete { info, err ->
                val ms = (System.nanoTime() - tStart) / 1_000_000
                if (err != null) {
                    println("[lsp] signatureHelp ✗ $uri @($line,$character) trigger=$trig: ${err.message} [${ms}ms]")
                } else if (info == null || info.isEmpty) {
                    println("[lsp] signatureHelp — $uri @($line,$character) trigger=$trig [${ms}ms] (empty)")
                } else {
                    val sig = info.active?.label?.take(120) ?: "?"
                    println("[lsp] signatureHelp ✓ $uri @($line,$character) trigger=$trig — ${info.signatures.size} sig(s), active=${info.activeSignature}, param=${info.effectiveActiveParameter()} [${ms}ms] '$sig'")
                }
            }
    }

    fun resolveCompletion(token: Long): CompletableFuture<page.lsp.ResolvedCompletion?> {
        if (status.value != Status.READY || !completionResolveSupported) {
            return CompletableFuture.completedFuture(null)
        }
        val ws = workspace ?: return CompletableFuture.completedFuture(null)
        return ws.resolveCompletionItem(token)
            .whenComplete { resolved, err ->
                if (err != null) {
                    println("[lsp] resolve ✗ token=$token: ${err.message}")
                } else {
                    val hasDoc = !resolved?.documentation.isNullOrBlank()
                    val edits = resolved?.additionalEdits?.size ?: 0
                    println("[lsp] resolve ✓ token=$token — doc=$hasDoc, edits=$edits")
                }
            }
    }

    fun prepareRename(path: Path, line: Int, character: Int): CompletableFuture<RenamePrepare?> {
        if (status.value != Status.READY) return CompletableFuture.completedFuture(null)
        if (!prepareRenameSupported) return CompletableFuture.completedFuture(null)
        val ws = workspace ?: return CompletableFuture.completedFuture(null)
        val uri = path.toUri().toString()
        if (!ws.isOpen(uri)) return CompletableFuture.completedFuture(null)
        val tStart = System.nanoTime()
        return ws.prepareRename(uri, line, character)
            .whenComplete { p, err ->
                val ms = (System.nanoTime() - tStart) / 1_000_000
                if (err != null) {
                    if (isUnsupportedOperation(err)) {
                        prepareRenameSupported = false
                        println("[lsp] prepareRename unsupported by server — disabling future calls [${ms}ms]")
                    } else {
                        println("[lsp] prepareRename ✗ $uri @($line,$character): ${err.message} [${ms}ms]")
                    }
                } else if (p == null) {
                    println("[lsp] prepareRename — $uri @($line,$character) refused [${ms}ms]")
                } else if (p.isDefaultBehavior) {
                    println("[lsp] prepareRename ✓ $uri @($line,$character) [${ms}ms] default-behavior")
                } else {
                    println("[lsp] prepareRename ✓ $uri @($line,$character) [${ms}ms] range=(${p.startLine},${p.startCharacter})..(${p.endLine},${p.endCharacter}) placeholder='${p.placeholder}'")
                }
            }
    }

    private fun isUnsupportedOperation(err: Throwable): Boolean {
        var cur: Throwable? = err
        while (cur != null) {
            if (cur is UnsupportedOperationException) return true
            val msg = cur.message ?: ""
            if (msg.contains("UnsupportedOperationException")) return true
            cur = cur.cause
        }
        return false
    }

    fun rename(path: Path, text: String, line: Int, character: Int, newName: String): CompletableFuture<RenameWorkspaceEdit> {
        if (status.value != Status.READY) return CompletableFuture.completedFuture(RenameWorkspaceEdit.EMPTY)
        val ws = workspace ?: return CompletableFuture.completedFuture(RenameWorkspaceEdit.EMPTY)
        val uri = path.toUri().toString()
        if (!ws.isOpen(uri)) return CompletableFuture.completedFuture(RenameWorkspaceEdit.EMPTY)
        pendingChanges.remove(uri)?.cancel()
        runCatching { ws.didChange(uri, text) }
        val (lineText, marker) = lineContextAt(text, line, character)
        println("[lsp] rename → $uri @($line,$character) → '$newName' (text.len=${text.length})")
        println("  line: '$lineText'")
        println("        $marker")
        val tStart = System.nanoTime()
        return ws.definition(uri, line, character).thenCompose { defs ->
            val target = defs.firstOrNull()
            if (target != null && !isInWorkspace(target.uri)) {
                println("  refused — definition points outside workspace: ${target.uri}")
                return@thenCompose CompletableFuture.failedFuture<RenameWorkspaceEdit>(
                    RenameException(EXTERNAL_SYMBOL_MESSAGE)
                )
            }
            val resolvedTarget = if (target != null && !ws.isOpen(target.uri)) {
                if (openTargetFromDisk(ws, target.uri)) target else null
            } else target
            val (rUri, rLine, rChar) = if (resolvedTarget != null && ws.isOpen(resolvedTarget.uri)) {
                val targetText = ws.textOf(resolvedTarget.uri)
                val adjustedChar = if (targetText != null) {
                    nearestIdentifierStart(targetText, resolvedTarget.startLine, resolvedTarget.startCharacter)
                } else resolvedTarget.startCharacter
                println("  via definition → ${resolvedTarget.uri} @(${resolvedTarget.startLine},${resolvedTarget.startCharacter})${if (adjustedChar != resolvedTarget.startCharacter) " → adjusted col $adjustedChar" else ""}")
                Triple(resolvedTarget.uri, resolvedTarget.startLine, adjustedChar)
            } else {
                if (target != null) {
                    println("  definition returned ${target.uri}, fallback to caret position (auto-open failed or non-file uri)")
                }
                Triple(uri, line, character)
            }
            runCatching { ws.reopen(rUri, ws.textOf(rUri) ?: text) }
            ws.rename(rUri, rLine, rChar, newName)
        }.thenCompose { edit ->
            val externalUri = edit.changes.firstOrNull { !isInWorkspace(it.uri) }?.uri
            if (externalUri != null) {
                println("  refused after KLS — edit targets outside workspace: $externalUri")
                CompletableFuture.failedFuture(RenameException(EXTERNAL_SYMBOL_MESSAGE))
            } else {
                CompletableFuture.completedFuture(edit)
            }
        }.whenComplete { edit, err ->
            val ms = (System.nanoTime() - tStart) / 1_000_000
            if (err != null) {
                println("[lsp] rename ✗ $uri @($line,$character) → '$newName': ${err.message} [${ms}ms]")
            } else {
                println("[lsp] rename ✓ $uri @($line,$character) → '$newName' — ${edit.changes.size} file(s), ${edit.totalEditCount} edit(s) [${ms}ms]")
                edit.changes.take(5).forEach { c ->
                    println("  ${c.uri} (${c.edits.size} edit(s))")
                    c.edits.take(3).forEach { e ->
                        println("    @(${e.startLine},${e.startCharacter})-(${e.endLine},${e.endCharacter}) → '${e.newText}'")
                    }
                }
            }
        }.exceptionallyCompose { err ->
            CompletableFuture.failedFuture(RenameException(renameErrorMessage(err)))
        }
    }

    private class RenameException(message: String) : RuntimeException(message)

    private fun renameErrorMessage(err: Throwable): String {
        findRenameException(err)?.message?.let { return it }
        val raw = err.message ?: err.toString()
        return when {
            raw.contains("UnsupportedOperationException") -> "LSP server does not support rename"
            raw.contains("Internal error", ignoreCase = true) ||
                raw.contains("KotlinFrontEndException") ||
                raw.contains("NoTopLevelDescriptorProvider") -> "LSP server internal error — cannot rename at this position"
            else -> raw.lineSequence().firstOrNull()?.take(160) ?: "rename failed"
        }
    }

    private fun findRenameException(err: Throwable): RenameException? {
        var cur: Throwable? = err
        while (cur != null) {
            if (cur is RenameException) return cur
            cur = cur.cause
        }
        return null
    }

    private fun isInWorkspace(uri: String): Boolean {
        val root = workspaceRoot ?: return false
        if (!uri.startsWith("file:")) return false
        return try {
            val p = java.nio.file.Paths.get(java.net.URI(uri)).toAbsolutePath().normalize()
            val r = root.toAbsolutePath().normalize()
            p.startsWith(r)
        } catch (t: Throwable) {
            false
        }
    }

    private val stderrSuppressed = java.util.concurrent.atomic.AtomicInteger(0)

    private fun onLspStderr(line: String) {
        val trimmed = line.trimEnd()
        val ltrim = trimmed.trimStart()
        val isFrame = ltrim.startsWith("at ") || (ltrim.startsWith("...") && ltrim.endsWith(" more"))
        if (isFrame) {
            stderrSuppressed.incrementAndGet()
            return
        }
        val suppressed = stderrSuppressed.getAndSet(0)
        if (suppressed > 0) {
            System.err.println("[lsp]     (suppressed $suppressed stack frame(s))")
        }
        System.err.println("[lsp] $trimmed")
    }

    private fun condenseStackTrace(message: String): String {
        if (message.isEmpty()) return message
        val out = StringBuilder()
        var stackCount = 0
        for (raw in message.lineSequence()) {
            val line = raw.trimEnd()
            val ltrim = line.trimStart()
            val isFrame = ltrim.startsWith("at ") || (ltrim.startsWith("...") && ltrim.endsWith(" more"))
            if (isFrame) {
                stackCount++
                continue
            }
            if (stackCount > 0) {
                out.append("    (suppressed ").append(stackCount).append(" stack frame(s))\n")
                stackCount = 0
            }
            out.append(line).append('\n')
        }
        if (stackCount > 0) {
            out.append("    (suppressed ").append(stackCount).append(" stack frame(s))\n")
        }
        return out.toString().trimEnd()
    }

    private fun openTargetFromDisk(ws: LspWorkspace, targetUri: String): Boolean {
        if (ws.isOpen(targetUri)) return true
        if (!targetUri.startsWith("file:")) {
            println("  cannot auto-open non-file uri $targetUri")
            return false
        }
        return try {
            val targetPath = java.nio.file.Paths.get(java.net.URI(targetUri))
            if (!java.nio.file.Files.isRegularFile(targetPath)) {
                println("  cannot auto-open $targetUri — not a regular file")
                return false
            }
            val text = java.nio.file.Files.readString(targetPath)
            ws.didOpen(targetUri, activeBackend?.id ?: "kotlin", text)
            println("  auto-opened $targetUri (${text.length} chars) for rename")
            true
        } catch (t: Throwable) {
            println("  auto-open failed for $targetUri: ${t.message}")
            false
        }
    }

    private fun openWorkspaceFiles() {
        val root = workspaceRoot ?: return
        val backend = activeBackend ?: return
        if (!backend.workspaceAutoOpen) {
            println("[lsp] workspace auto-open skipped — ${backend.id} publishes workspace-wide diagnostics on its own")
            return
        }
        scope.launch(Dispatchers.IO) {
            val ws = workspace ?: return@launch
            var opened = 0
            try {
                val candidates = java.nio.file.Files.walk(root).use { stream ->
                    stream
                        .filter { java.nio.file.Files.isRegularFile(it) }
                        .filter { p ->
                            val name = p.fileName?.toString() ?: return@filter false
                            val dot = name.lastIndexOf('.')
                            val ext = if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1) else null
                            backend.supports(ext)
                        }
                        .filter { p ->
                            val rel = root.relativize(p)
                            rel.iterator().asSequence().none { it.toString() in WORKSPACE_AUTO_OPEN_EXCLUDES }
                        }
                        .limit(WORKSPACE_AUTO_OPEN_SCAN_LIMIT)
                        .toList()
                }
                for (p in candidates) {
                    if (opened >= MAX_AUTO_OPEN_FILES) break
                    try {
                        if (java.nio.file.Files.size(p) > MAX_AUTO_OPEN_BYTES) continue
                        val uri = p.toUri().toString()
                        if (ws.isOpen(uri)) continue
                        val text = java.nio.file.Files.readString(p)
                        ws.didOpen(uri, backend.id, text)
                        opened++
                        if (opened % AUTO_OPEN_THROTTLE_BATCH == 0) delay(AUTO_OPEN_THROTTLE_MILLIS)
                    } catch (t: Throwable) {
                        println("[lsp] workspace auto-open failed for $p: ${t.message}")
                    }
                }
                val truncated = opened >= MAX_AUTO_OPEN_FILES || candidates.size.toLong() >= WORKSPACE_AUTO_OPEN_SCAN_LIMIT
                val suffix = if (truncated) " (truncated at cap; remaining files open on demand)" else ""
                println("[lsp] workspace auto-open done — $opened ${backend.id} file(s) under $root$suffix")
            } catch (t: Throwable) {
                println("[lsp] workspace walk failed: ${t.message}")
            }
        }
    }

    fun linePreviewFor(uri: String, line: Int): String? {
        if (line < 0) return null
        val text = workspace?.textOf(uri) ?: readFileTextByUri(uri) ?: return null
        var idx = 0
        var l = 0
        while (idx < text.length && l < line) {
            if (text[idx] == '\n') l++
            idx++
        }
        if (l != line) return null
        var end = idx
        while (end < text.length && text[end] != '\n') end++
        val raw = text.substring(idx, end)
        return if (raw.endsWith('\r')) raw.dropLast(1) else raw
    }

    private fun readFileTextByUri(uri: String): String? {
        if (!uri.startsWith("file:")) return null
        return try {
            val p = java.nio.file.Paths.get(java.net.URI(uri))
            if (!java.nio.file.Files.isRegularFile(p)) return null
            if (java.nio.file.Files.size(p) > MAX_AUTO_OPEN_BYTES) return null
            java.nio.file.Files.readString(p)
        } catch (_: Throwable) {
            null
        }
    }

    fun applyExternalChange(uri: String, newText: String) {
        val ws = workspace ?: return
        pendingChanges.remove(uri)?.cancel()
        invalidateCompletionCache(uri)
        if (ws.isOpen(uri)) {
            runCatching { ws.didChange(uri, newText) }
        }
    }

    fun notifyFilesRenamed(moves: List<Pair<Path, Path>>) {
        if (moves.isEmpty()) return
        val ws = workspace ?: return
        val events = mutableListOf<Pair<String, org.eclipse.lsp4j.FileChangeType>>()
        val toOpen = mutableListOf<Pair<String, String>>()
        for ((oldPath, newPath) in moves) {
            val oldUri = oldPath.toUri().toString()
            val newUri = newPath.toUri().toString()
            pendingChanges.remove(oldUri)?.cancel()
            invalidateCompletionCache(oldUri)
            invalidateInlayHintCache(oldUri)
            if (ws.isOpen(oldUri)) runCatching { ws.didClose(oldUri) }
            diagnosticsByUri.remove(canonicalUri(oldUri))
            pendingDiagnostics.remove(canonicalUri(oldUri))
            unnecessaryByContent.remove(canonicalUri(oldUri))
            events.add(oldUri to org.eclipse.lsp4j.FileChangeType.Deleted)
            events.add(newUri to org.eclipse.lsp4j.FileChangeType.Created)
            if (!ws.isOpen(newUri)) {
                val text = runCatching { java.nio.file.Files.readString(newPath) }.getOrNull()
                if (text != null) toOpen.add(newUri to text)
            }
        }
        runCatching { ws.didChangeWatchedFiles(events) }
        val langId = activeBackend?.id ?: "kotlin"
        for ((uri, text) in toOpen) {
            runCatching { ws.didOpen(uri, langId, text) }
        }
    }

    fun restart(reason: String) {
        runWithClientDown(reason) { /* no-op between teardown and bring-up */ }
    }

    fun runWithClientDown(reason: String, releaseDelayMs: Long = 350L, block: () -> Unit) {
        println("[lsp] client down requested ($reason)")
        val ws = workspace
        val openSnapshot = mutableListOf<Triple<Path, String, String>>()
        if (ws != null) {
            for (uri in ws.openUris()) {
                val text = ws.textOf(uri) ?: continue
                val path = runCatching { java.nio.file.Paths.get(java.net.URI(uri)) }.getOrNull() ?: continue
                openSnapshot.add(Triple(path, activeBackend?.id ?: "kotlin", text))
            }
        }
        pendingChanges.values.forEach { it.cancel() }
        pendingChanges.clear()
        synchronized(completionCache) { completionCache.clear() }
        lastCompletionByLine.clear()
        synchronized(inlayHintCache) { inlayHintCache.clear() }
        diagnosticsByUri.clear()
        pendingDiagnostics.clear()
        unnecessaryByContent.clear()
        clearActivities("client down: $reason")
        val shutdownFuture = runCatching { client?.shutdown() }.getOrNull()
        if (shutdownFuture != null) {
            runCatching { shutdownFuture.get(3, java.util.concurrent.TimeUnit.SECONDS) }
        }
        client = null
        workspace = null
        status.value = Status.IDLE
        statusDetail.value = "restarting ($reason)"
        startAttempted = false
        if (releaseDelayMs > 0L) {
            runCatching { Thread.sleep(releaseDelayMs) }
        }
        runCatching { block() }
        for ((path, lang, text) in openSnapshot) {
            val uri = path.toUri().toString()
            pendingOpens[uri] = PendingOpen(path, lang, text)
        }
        val backend = activeBackend ?: return
        ensureStarted(backend)
    }

    fun shutdown() {
        pendingChanges.values.forEach { it.cancel() }
        pendingChanges.clear()
        clearActivities("shutdown")
        client?.shutdown()
        scope.cancel()
    }

    private fun startActivityJanitor() {
        scope.launch {
            while (true) {
                delay(5_000L)
                pruneStaleActivities()
            }
        }
    }

    companion object {
        private const val COMPLETION_CACHE_MAX = 64
        private const val INLAY_HINT_CACHE_MAX = 32
        private const val MAX_AUTO_OPEN_BYTES = 512L * 1024
        private const val MAX_AUTO_OPEN_FILES = 500
        private const val WORKSPACE_AUTO_OPEN_SCAN_LIMIT = 4000L
        private const val AUTO_OPEN_THROTTLE_BATCH = 25
        private const val AUTO_OPEN_THROTTLE_MILLIS = 15L
        private const val EXTERNAL_SYMBOL_MESSAGE =
            "Cannot rename external library symbols (kotlin-stdlib · dependency jars)"
        private val WORKSPACE_AUTO_OPEN_EXCLUDES = setOf(
            ".git", ".hg", ".svn", ".idea", ".idea_modules", ".vs", ".vscode",
            ".gradle", "build", "out", "bin", "target", "node_modules", ".page-ide",
            ".dart_tool", "ephemeral",
        )
        const val STARTUP_KIND = "startup"
        const val ANALYSIS_KIND = "analysis"
        const val GRADLE_DEPS_KIND = KLS_GRADLE_DEPS_KIND
        const val GRADLE_SCRIPT_DEPS_KIND = KLS_GRADLE_SCRIPT_DEPS_KIND
        const val SYMBOL_INDEX_KIND = KLS_SYMBOL_INDEX_KIND
        const val LINTING_KIND = KLS_LINTING_KIND

        private const val WORKSPACE_SYMBOLS_TIMEOUT_MS = 2_000L
        private const val DIAGNOSTICS_FLUSH_MS = 80L
    }

    private val unnecessaryByContent = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, List<Diagnostic>>>()
    private val pendingDiagnostics = java.util.concurrent.ConcurrentHashMap<String, List<Diagnostic>>()
    private val diagnosticsFlushScheduled = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun scheduleDiagnosticsFlush() {
        if (!diagnosticsFlushScheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(DIAGNOSTICS_FLUSH_MS)
            diagnosticsFlushScheduled.set(false)
            val batch = drainBatch(pendingDiagnostics)
            if (batch.isEmpty()) return@launch
            Snapshot.withMutableSnapshot {
                for ((k, v) in batch) diagnosticsByUri[k] = v
            }
        }
    }

    private fun onDiagnostics(params: PublishDiagnosticsParams) {
        val uri = params.uri ?: return
        if (isNoiseUri(uri)) return
        logFirstServerFeedback("diagnostics")
        if (activities.containsKey(ANALYSIS_KIND)) endActivity(ANALYSIS_KIND)
        val key = canonicalUri(uri)
        val mapped = params.diagnostics.orEmpty().map(Diagnostic::fromLsp)
        val incomingHasUnnecessary = mapped.any { it.unnecessary }

        val cached = unnecessaryByContent[key]
        val contentHash = if (incomingHasUnnecessary || cached != null) {
            val openKey = workspace?.openUris()?.firstOrNull { canonicalUri(it) == key }
            openKey?.let { workspace?.textOf(it)?.hashCode() }
        } else {
            null
        }

        val cachedForContent = cached != null && contentHash != null &&
            cached.first == contentHash && cached.second.isNotEmpty()
        val reinject = shouldReinjectUnnecessary(
            incomingHasUnnecessary = incomingHasUnnecessary,
            cachedForCurrentContent = cachedForContent,
        )

        val effective = if (reinject) mapped + cached!!.second else mapped
        pendingDiagnostics[key] = effective
        scheduleDiagnosticsFlush()

        val effectiveUnnecessary = effective.filter { it.unnecessary }
        if (effectiveUnnecessary.isNotEmpty() && contentHash != null) {
            unnecessaryByContent[key] = contentHash to effectiveUnnecessary
        }

        if (reinject) {
            println("[lsp] publishDiagnostics $uri — kept ${cached!!.second.size} unnecessary diag(s) for restored content")
        }
        if (mapped.isEmpty()) {
            println("[lsp] publishDiagnostics $uri — 0 diagnostic(s)")
        }
        if (mapped.isNotEmpty()) {
            println("[lsp] publishDiagnostics $uri — ${mapped.size} diagnostic(s)")
            mapped.take(5).forEach { d ->
                val msgPreview = d.message.take(60).replace('\n', ' ')
                val tags = buildString {
                    if (d.unnecessary) append(" unnecessary")
                    if (d.deprecated) append(" deprecated")
                }
                println("    · L${d.start.line}:${d.start.character}-L${d.end.line}:${d.end.character} sev=${d.severity} code='${d.code ?: ""}'$tags msg='$msgPreview'")
            }
        }
    }

    private fun isNoiseUri(uri: String): Boolean {
        return uri.contains("/.clj-kondo/.cache/") ||
            uri.contains("/.lsp/.cache/") ||
            uri.contains("/.gradle/") ||
            uri.contains("/node_modules/") ||
            uri.contains("/.scala-build/") ||
            uri.endsWith(".transit.json")
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
