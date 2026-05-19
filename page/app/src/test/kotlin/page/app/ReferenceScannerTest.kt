package page.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.eclipse.lsp4j.SymbolKind
import page.lsp.DocumentSymbolEntry
import page.lsp.ReferenceLocation
import page.lsp.SymbolRange
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReferenceScannerTest {

    private fun tempDir(): Path {
        val dir = Files.createTempDirectory("ref-scan-test-")
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching {
                Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        })
        return dir
    }

    private fun sym(name: String, line: Int, col: Int): DocumentSymbolEntry =
        DocumentSymbolEntry(
            name = name,
            detail = null,
            kind = SymbolKind.Class,
            range = SymbolRange(line, col, line, col + name.length),
            selectionRange = SymbolRange(line, col, line, col + name.length),
            containerName = null,
            children = emptyList(),
        )

    private fun ref(uri: String, line: Int, col: Int, len: Int): ReferenceLocation =
        ReferenceLocation(uri, line, col, line, col + len)

    @Test
    fun `scanFile reports external hit excluding self`() = runBlocking {
        val root = tempDir()
        val target = Files.writeString(root.resolve("Bar.kt"), "class Bar { }\n")
        val other = Files.writeString(root.resolve("Other.kt"), "fun use() { val x = Bar() }\n")

        val symbols = mapOf(target.toAbsolutePath().normalize() to listOf(sym("Bar", 0, 6)))
        val refsByTarget = mapOf(
            target.toAbsolutePath().normalize() to listOf(
                ref(target.toUri().toString(), 0, 6, 3),
                ref(other.toUri().toString(), 0, 20, 3),
            )
        )

        val scope = CoroutineScope(SupervisorJob())
        val scanner = ReferenceScanner(
            documentSymbols = { p ->
                CompletableFuture.completedFuture(symbols[p.toAbsolutePath().normalize()] ?: emptyList())
            },
            references = { p, _, _ ->
                CompletableFuture.completedFuture(refsByTarget[p.toAbsolutePath().normalize()] ?: emptyList())
            },
            scope = scope,
        )
        val (flow, job) = scanner.scan(target)
        job.join()
        val state = assertIs<ImpactScanState.Done>(flow.value)
        assertEquals(1, state.hits.size)
        assertEquals("Bar", state.hits[0].symbol)
        assertEquals(other.toAbsolutePath().normalize(), state.hits[0].file)
        assertTrue(state.hits[0].preview.contains("Bar()"))
        scope.cancel()
    }

    @Test
    fun `scanFile excludes hits inside own file`() = runBlocking {
        val root = tempDir()
        val target = Files.writeString(root.resolve("Self.kt"), "class Self { fun a() { Self() } }\n")

        val symbols = listOf(sym("Self", 0, 6))
        val refs = listOf(
            ref(target.toUri().toString(), 0, 6, 4),
            ref(target.toUri().toString(), 0, 23, 4),
        )

        val scope = CoroutineScope(SupervisorJob())
        val scanner = ReferenceScanner(
            documentSymbols = { CompletableFuture.completedFuture(symbols) },
            references = { _, _, _ -> CompletableFuture.completedFuture(refs) },
            scope = scope,
        )
        val (flow, job) = scanner.scan(target)
        job.join()
        val state = assertIs<ImpactScanState.Done>(flow.value)
        assertEquals(0, state.hits.size)
        scope.cancel()
    }

    @Test
    fun `scanFolder excludes hits inside the folder`() = runBlocking {
        val root = tempDir()
        val pkg = Files.createDirectories(root.resolve("pkg"))
        val inA = Files.writeString(pkg.resolve("A.kt"), "class A { }\n")
        val inB = Files.writeString(pkg.resolve("B.kt"), "fun useA() { A() }\n")
        val outside = Files.writeString(root.resolve("Outside.kt"), "fun ext() { A() }\n")

        val symbols = mapOf(
            inA.toAbsolutePath().normalize() to listOf(sym("A", 0, 6)),
            inB.toAbsolutePath().normalize() to listOf(sym("useA", 0, 4)),
        )
        val refsByTarget = mapOf(
            inA.toAbsolutePath().normalize() to listOf(
                ref(inB.toUri().toString(), 0, 13, 1),
                ref(outside.toUri().toString(), 0, 12, 1),
            ),
            inB.toAbsolutePath().normalize() to emptyList(),
        )

        val scope = CoroutineScope(SupervisorJob())
        val scanner = ReferenceScanner(
            documentSymbols = { p ->
                CompletableFuture.completedFuture(symbols[p.toAbsolutePath().normalize()] ?: emptyList())
            },
            references = { p, _, _ ->
                CompletableFuture.completedFuture(refsByTarget[p.toAbsolutePath().normalize()] ?: emptyList())
            },
            scope = scope,
        )
        val (flow, job) = scanner.scan(pkg)
        job.join()
        val state = assertIs<ImpactScanState.Done>(flow.value)
        assertEquals(1, state.hits.size)
        assertEquals(outside.toAbsolutePath().normalize(), state.hits[0].file)
        assertEquals(2, state.scannedFiles)
        scope.cancel()
    }

    @Test
    fun `scanFile skips non-kotlin files`() = runBlocking {
        val root = tempDir()
        val target = Files.writeString(root.resolve("README.md"), "hello\n")
        val scope = CoroutineScope(SupervisorJob())
        val scanner = ReferenceScanner(
            documentSymbols = { CompletableFuture.completedFuture(emptyList()) },
            references = { _, _, _ -> CompletableFuture.completedFuture(emptyList()) },
            scope = scope,
        )
        val (flow, job) = scanner.scan(target)
        job.join()
        val state = assertIs<ImpactScanState.Done>(flow.value)
        assertEquals(0, state.scannedFiles)
        assertTrue(state.hits.isEmpty())
        scope.cancel()
    }

    @Test
    fun `scanner dedupes identical hit positions`() = runBlocking {
        val root = tempDir()
        val target = Files.writeString(root.resolve("Bar.kt"), "class Bar { }\n")
        val other = Files.writeString(root.resolve("Other.kt"), "fun use() { Bar() }\n")
        val symbols = listOf(sym("Bar", 0, 6), sym("Bar", 0, 6))
        val refs = listOf(
            ref(other.toUri().toString(), 0, 12, 3),
            ref(other.toUri().toString(), 0, 12, 3),
        )
        val scope = CoroutineScope(SupervisorJob())
        val scanner = ReferenceScanner(
            documentSymbols = { CompletableFuture.completedFuture(symbols) },
            references = { _, _, _ -> CompletableFuture.completedFuture(refs) },
            scope = scope,
        )
        val (flow, job) = scanner.scan(target)
        job.join()
        val state = assertIs<ImpactScanState.Done>(flow.value)
        assertEquals(1, state.hits.size)
        scope.cancel()
    }
}
