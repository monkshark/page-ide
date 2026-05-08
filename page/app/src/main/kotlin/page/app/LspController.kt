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
import page.lsp.CompletionList
import page.lsp.Diagnostic
import page.lsp.KotlinLsp
import page.lsp.LspClient
import page.lsp.LspState
import page.lsp.LspWorkspace
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class LspController(
    private val workspaceRoot: Path?,
    private val scope: CoroutineScope,
) {

    enum class Status { IDLE, STARTING, READY, MISSING, FAILED }

    val status: MutableState<Status> = mutableStateOf(Status.IDLE)
    val statusDetail: MutableState<String> = mutableStateOf("")
    val currentActivity: MutableState<String> = mutableStateOf("")
    val currentActivityStartedAtMs: MutableState<Long> = mutableStateOf(0L)
    val diagnosticsByUri: SnapshotStateMap<String, List<Diagnostic>> = androidx.compose.runtime.mutableStateMapOf()

    private var client: LspClient? = null
    private var workspace: LspWorkspace? = null
    private val pendingChanges = ConcurrentHashMap<String, Job>()
    private var startAttempted = false

    private data class PendingOpen(val path: Path, val languageId: String, val text: String)
    private val pendingOpens = ConcurrentHashMap<String, PendingOpen>()

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
        println("[lsp] STARTING — ${resolution.origin}: ${resolution.executable}")
        try {
            val c = KotlinLsp.spawn(resolution.executable, workspaceRoot)
            c.onDiagnostics(::onDiagnostics)
            c.onLogMessage { mp ->
                println("[lsp:log/${mp.type}] ${mp.message}")
                parseActivity(mp.message)?.let { newActivity ->
                    if (newActivity != currentActivity.value) {
                        currentActivity.value = newActivity
                        currentActivityStartedAtMs.value =
                            if (newActivity.isBlank()) 0L else System.currentTimeMillis()
                    }
                }
            }
            c.onShowMessage { mp -> println("[lsp:show/${mp.type}] ${mp.message}") }
            c.start().whenComplete { result, throwable ->
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
            status.value = Status.FAILED
            statusDetail.value = t.message ?: t.toString()
            println("[lsp] FAILED on spawn: ${t.message}")
            t.printStackTrace()
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
        val ws = workspace
        if (ws != null && ws.isOpen(uri)) ws.didClose(uri)
        diagnosticsByUri.remove(uri)
    }

    fun diagnosticsFor(path: Path): List<Diagnostic> {
        val uri = path.toUri().toString()
        return diagnosticsByUri[uri].orEmpty()
    }

    fun completion(path: Path, line: Int, character: Int): CompletableFuture<CompletionList> {
        if (status.value != Status.READY) {
            return CompletableFuture.completedFuture(CompletionList.EMPTY)
        }
        val ws = workspace ?: return CompletableFuture.completedFuture(CompletionList.EMPTY)
        val uri = path.toUri().toString()
        return ws.completion(uri, line, character)
    }

    fun shutdown() {
        pendingChanges.values.forEach { it.cancel() }
        pendingChanges.clear()
        client?.shutdown()
        scope.cancel()
    }

    private fun parseActivity(raw: String?): String? {
        val full = raw?.trim().orEmpty()
        if (full.isEmpty()) return null
        val msg = stripKlsThreadPrefix(full)
        return when {
            msg.contains("kotlinLSPProjectDeps", ignoreCase = true) ->
                "Gradle: 프로젝트 의존성 해석 중…"
            msg.contains("kotlinLSPKotlinDSLDeps", ignoreCase = true) ->
                "Gradle: 빌드 스크립트 의존성 해석 중…"
            msg.startsWith("Successfully resolved build script dependencies", ignoreCase = true) ->
                "빌드 스크립트 classpath 적용 중…"
            msg.startsWith("Successfully resolved", ignoreCase = true) ->
                "Classpath 적용 중…"
            msg.startsWith("Adding ", ignoreCase = true) && msg.contains("class path", ignoreCase = true) ->
                "Classpath 갱신 중…"
            msg.startsWith("Reinstantiating compiler", ignoreCase = true) ->
                "Kotlin 컴파일러 초기화 중…"
            msg.startsWith("Linting", ignoreCase = true) ->
                "분석 중…"
            msg.startsWith("Updating full symbol index", ignoreCase = true) ->
                "심볼 인덱싱 중…"
            msg.startsWith("Updating symbol index", ignoreCase = true) ->
                "심볼 인덱싱 중…"
            msg.startsWith("Updated full symbol index", ignoreCase = true) -> ""
            msg.startsWith("Updated symbol index", ignoreCase = true) -> ""
            msg.startsWith("Reported", ignoreCase = true) && msg.contains("diagnostic", ignoreCase = true) -> ""
            else -> null
        }
    }

    private fun stripKlsThreadPrefix(s: String): String {
        if (s.length <= KLS_THREAD_PREFIX_WIDTH) return s
        return s.substring(KLS_THREAD_PREFIX_WIDTH).trimStart()
    }

    companion object {
        private const val KLS_THREAD_PREFIX_WIDTH = 10
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
