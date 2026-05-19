package page.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import page.lsp.DocumentSymbolEntry
import page.lsp.ReferenceLocation
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors

data class ReferenceHit(
    val symbol: String,
    val file: Path,
    val line: Int,
    val column: Int,
    val preview: String,
)

sealed interface ImpactScanState {
    data object Idle : ImpactScanState
    data class Scanning(val done: Int, val total: Int) : ImpactScanState
    data class Done(val hits: List<ReferenceHit>, val scannedFiles: Int) : ImpactScanState
    data class Error(val message: String) : ImpactScanState
    data object Cancelled : ImpactScanState
}

class ReferenceScanner(
    private val documentSymbols: (Path) -> CompletableFuture<List<DocumentSymbolEntry>>,
    private val references: (Path, Int, Int) -> CompletableFuture<List<ReferenceLocation>>,
    private val ensureOpen: (Path) -> Unit = {},
    private val scope: CoroutineScope,
) {
    fun scan(target: Path): Pair<StateFlow<ImpactScanState>, Job> {
        val state = MutableStateFlow<ImpactScanState>(ImpactScanState.Scanning(0, 1))
        val job = scope.launch(Dispatchers.IO) {
            try {
                if (Files.isDirectory(target)) {
                    scanFolder(target, state)
                } else {
                    scanFile(target, state)
                }
            } catch (e: CancellationException) {
                state.value = ImpactScanState.Cancelled
                throw e
            } catch (e: Throwable) {
                state.value = ImpactScanState.Error(e.message ?: "Scan failed")
            }
        }
        return state.asStateFlow() to job
    }

    private suspend fun scanFile(file: Path, state: MutableStateFlow<ImpactScanState>) {
        if (!isKotlin(file)) {
            state.value = ImpactScanState.Done(emptyList(), 0)
            return
        }
        state.value = ImpactScanState.Scanning(0, 1)
        val internal = setOf(file.toAbsolutePath().normalize())
        val hits = collectHits(file, internal)
        state.value = ImpactScanState.Done(dedupe(hits), 1)
    }

    private suspend fun scanFolder(folder: Path, state: MutableStateFlow<ImpactScanState>) {
        val ktFiles = Files.walk(folder).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && isKotlin(it) }
                .map { it.toAbsolutePath().normalize() }
                .collect(Collectors.toList())
        }
        if (ktFiles.isEmpty()) {
            state.value = ImpactScanState.Done(emptyList(), 0)
            return
        }
        val internal = ktFiles.toSet()
        val all = mutableListOf<ReferenceHit>()
        state.value = ImpactScanState.Scanning(0, ktFiles.size)
        ktFiles.forEachIndexed { idx, file ->
            val hits = collectHits(file, internal)
            all.addAll(hits)
            state.value = ImpactScanState.Scanning(idx + 1, ktFiles.size)
        }
        state.value = ImpactScanState.Done(dedupe(all), ktFiles.size)
    }

    private suspend fun collectHits(file: Path, internalFiles: Set<Path>): List<ReferenceHit> {
        runCatching { ensureOpen(file) }
        val syms = runCatching { documentSymbols(file).await() }.getOrDefault(emptyList())
        if (syms.isEmpty()) return emptyList()
        val topLevel = syms.filter { it.containerName.isNullOrBlank() }
        val previewCache = HashMap<Path, List<String>>()
        val out = mutableListOf<ReferenceHit>()
        for (sym in topLevel) {
            val pos = sym.selectionRange
            val refs = runCatching {
                references(file, pos.startLine, pos.startCharacter).await()
            }.getOrDefault(emptyList())
            for (r in refs) {
                val refPath = uriToPath(r.uri) ?: continue
                val abs = refPath.toAbsolutePath().normalize()
                if (abs in internalFiles) continue
                out.add(
                    ReferenceHit(
                        symbol = sym.name,
                        file = abs,
                        line = r.startLine,
                        column = r.startCharacter,
                        preview = lineAt(abs, r.startLine, previewCache),
                    )
                )
            }
        }
        return out
    }

    private fun lineAt(path: Path, line: Int, cache: MutableMap<Path, List<String>>): String {
        val lines = cache.getOrPut(path) {
            runCatching { Files.readAllLines(path) }.getOrDefault(emptyList())
        }
        return lines.getOrNull(line)?.trim().orEmpty()
    }

    private fun dedupe(hits: List<ReferenceHit>): List<ReferenceHit> {
        val seen = LinkedHashMap<Triple<Path, Int, Int>, ReferenceHit>()
        for (h in hits) {
            val key = Triple(h.file, h.line, h.column)
            if (key !in seen) seen[key] = h
        }
        return seen.values.toList()
    }

    private fun isKotlin(path: Path): Boolean {
        val name = path.fileName?.toString().orEmpty()
        return name.endsWith(".kt") || name.endsWith(".kts")
    }

    private fun uriToPath(uri: String): Path? = runCatching {
        Paths.get(URI.create(uri))
    }.getOrNull()
}
