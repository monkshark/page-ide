package page.lsp

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticTest {

    @Test
    fun `severity mapping covers all values including null`() {
        assertEquals(DiagnosticSeverity.ERROR, DiagnosticSeverity.fromLsp(org.eclipse.lsp4j.DiagnosticSeverity.Error))
        assertEquals(DiagnosticSeverity.WARNING, DiagnosticSeverity.fromLsp(org.eclipse.lsp4j.DiagnosticSeverity.Warning))
        assertEquals(DiagnosticSeverity.INFO, DiagnosticSeverity.fromLsp(org.eclipse.lsp4j.DiagnosticSeverity.Information))
        assertEquals(DiagnosticSeverity.HINT, DiagnosticSeverity.fromLsp(org.eclipse.lsp4j.DiagnosticSeverity.Hint))
        assertEquals(DiagnosticSeverity.ERROR, DiagnosticSeverity.fromLsp(null))
    }

    @Test
    fun `fromLsp copies position and message`() {
        val src = org.eclipse.lsp4j.Diagnostic(
            Range(Position(2, 4), Position(2, 9)),
            "Unresolved reference: foo",
        )
        src.severity = org.eclipse.lsp4j.DiagnosticSeverity.Error
        src.source = "kotlin"

        val mapped = Diagnostic.fromLsp(src)
        assertEquals(2, mapped.start.line)
        assertEquals(4, mapped.start.character)
        assertEquals(2, mapped.end.line)
        assertEquals(9, mapped.end.character)
        assertEquals(DiagnosticSeverity.ERROR, mapped.severity)
        assertEquals("Unresolved reference: foo", mapped.message)
        assertEquals("kotlin", mapped.source)
    }

    @Test
    fun `fromLsp marks Unnecessary tag so unused code can be greyed out`() {
        val src = org.eclipse.lsp4j.Diagnostic(
            Range(Position(0, 0), Position(0, 20)),
            "The import java.util.Map is never used",
        )
        src.severity = org.eclipse.lsp4j.DiagnosticSeverity.Warning
        src.tags = listOf(org.eclipse.lsp4j.DiagnosticTag.Unnecessary)

        val mapped = Diagnostic.fromLsp(src)
        assertTrue(mapped.unnecessary)
        assertFalse(mapped.deprecated)
    }

    @Test
    fun `fromLsp marks Deprecated tag and leaves unnecessary false`() {
        val src = org.eclipse.lsp4j.Diagnostic(
            Range(Position(1, 0), Position(1, 5)),
            "'foo' is deprecated",
        )
        src.tags = listOf(org.eclipse.lsp4j.DiagnosticTag.Deprecated)

        val mapped = Diagnostic.fromLsp(src)
        assertTrue(mapped.deprecated)
        assertFalse(mapped.unnecessary)
    }

    @Test
    fun `fromLsp without tags leaves unnecessary and deprecated false`() {
        val src = org.eclipse.lsp4j.Diagnostic(
            Range(Position(0, 0), Position(0, 1)),
            "boom",
        )
        val mapped = Diagnostic.fromLsp(src)
        assertFalse(mapped.unnecessary)
        assertFalse(mapped.deprecated)
    }
}
