package page.app

import page.lsp.Diagnostic
import page.lsp.DiagnosticPosition
import page.lsp.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsScopeFilterTest {

    private lateinit var dir: Path
    private lateinit var pA: Path
    private lateinit var pB: Path
    private lateinit var pC: Path
    private lateinit var all: Map<String, List<Diagnostic>>

    private fun diag() = Diagnostic(
        start = DiagnosticPosition(0, 0),
        end = DiagnosticPosition(0, 1),
        severity = DiagnosticSeverity.ERROR,
        message = "boom",
    )

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("page-diag-scope-")
        pA = dir.resolve("A.java")
        pB = dir.resolve("B.java")
        pC = dir.resolve("C.java")
        all = mapOf(
            pA.toUri().toString() to listOf(diag()),
            pB.toUri().toString() to listOf(diag()),
            pC.toUri().toString() to listOf(diag()),
        )
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun currentFileKeepsOnlyFocusedUri() {
        val out = diagnosticsInScope(all, DiagnosticsScope.CURRENT_FILE, focusedPath = pA, openPaths = setOf(pA, pB, pC))
        assertEquals(setOf(pA.toUri().toString()), out.keys)
    }

    @Test
    fun currentFileWithNoFocusYieldsEmpty() {
        val out = diagnosticsInScope(all, DiagnosticsScope.CURRENT_FILE, focusedPath = null, openPaths = setOf(pA, pB))
        assertTrue(out.isEmpty())
    }

    @Test
    fun openTabsKeepsOnlyOpenPaths() {
        val out = diagnosticsInScope(all, DiagnosticsScope.OPEN_TABS, focusedPath = pA, openPaths = setOf(pA, pB))
        assertEquals(setOf(pA.toUri().toString(), pB.toUri().toString()), out.keys)
    }

    @Test
    fun openTabsWithNoTabsYieldsEmpty() {
        val out = diagnosticsInScope(all, DiagnosticsScope.OPEN_TABS, focusedPath = null, openPaths = emptySet())
        assertTrue(out.isEmpty())
    }

    @Test
    fun openTabsIgnoresClosedFileDiagnostics() {
        val out = diagnosticsInScope(all, DiagnosticsScope.OPEN_TABS, focusedPath = pC, openPaths = setOf(pC))
        assertEquals(setOf(pC.toUri().toString()), out.keys)
        assertTrue(out.none { it.key == pA.toUri().toString() })
    }
}
