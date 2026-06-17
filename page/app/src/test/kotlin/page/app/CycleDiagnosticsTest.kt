package page.app

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.GraphNode
import page.atlas.graph.NodeKind
import page.lsp.Diagnostic
import page.lsp.DiagnosticPosition
import page.lsp.DiagnosticSeverity

class CycleDiagnosticsTest {

    private fun node(name: String): GraphNode =
        GraphNode(name, name, Path.of(name), NodeKind.WORKSPACE_FILE)

    private fun external(id: String): GraphNode =
        GraphNode(id, id, null, NodeKind.EXTERNAL)

    private fun uri(name: String): String = Path.of(name).toUri().toString()

    @Test
    fun `each cycle member gets one atlas warning listing every file`() {
        val out = cycleDiagnostics(listOf(listOf(node("b.kt"), node("a.kt"), node("c.kt"))))
        assertEquals(setOf(uri("a.kt"), uri("b.kt"), uri("c.kt")), out.keys)
        val diag = out.getValue(uri("a.kt")).single()
        assertEquals(DiagnosticSeverity.WARNING, diag.severity)
        assertEquals("atlas", diag.source)
        assertEquals("Dependency cycle · 3 files: a.kt, b.kt, c.kt", diag.message)
    }

    @Test
    fun `nodes without a path are ignored`() {
        val out = cycleDiagnostics(listOf(listOf(node("a.kt"), external("kotlin.collections.List"))))
        assertTrue(out.isEmpty(), "a single resolvable member is not a multi file cycle")
    }

    @Test
    fun `independent cycles each yield their own diagnostics`() {
        val out = cycleDiagnostics(
            listOf(
                listOf(node("a.kt"), node("b.kt")),
                listOf(node("c.kt"), node("d.kt")),
            ),
        )
        assertEquals(setOf(uri("a.kt"), uri("b.kt"), uri("c.kt"), uri("d.kt")), out.keys)
        assertEquals("Dependency cycle · 2 files: c.kt, d.kt", out.getValue(uri("c.kt")).single().message)
    }

    @Test
    fun `empty cycle list yields no diagnostics`() {
        assertTrue(cycleDiagnostics(emptyList()).isEmpty())
    }

    @Test
    fun `merge appends cycle diagnostics to existing lsp diagnostics`() {
        val error = Diagnostic(DiagnosticPosition(2, 0), DiagnosticPosition(2, 1), DiagnosticSeverity.ERROR, "boom")
        val base = mapOf(uri("a.kt") to listOf(error))
        val merged = mergeDiagnostics(base, cycleDiagnostics(listOf(listOf(node("a.kt"), node("b.kt")))))
        assertEquals(2, merged.getValue(uri("a.kt")).size)
        assertEquals(error, merged.getValue(uri("a.kt")).first())
        assertEquals(DiagnosticSeverity.WARNING, merged.getValue(uri("a.kt"))[1].severity)
        assertTrue(uri("b.kt") in merged.keys)
    }

    @Test
    fun `merge with empty extra returns the base unchanged`() {
        val error = Diagnostic(DiagnosticPosition(0, 0), DiagnosticPosition(0, 1), DiagnosticSeverity.ERROR, "boom")
        val base = mapOf(uri("a.kt") to listOf(error))
        assertEquals(base, mergeDiagnostics(base, emptyMap()))
    }
}
