package page.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class CodeActionNormalizeTest {

    private val uri = "file:///proj/Foo.kt"

    @Test
    fun `import insert without trailing newline gets newline appended when followed by content`() {
        val text = "// === header ===\nfun foo() = 1\n"
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(RenameEdit(0, 0, 0, 0, "\n\nimport java.time.LocalDate")),
                ),
            ),
        )
        val normalized = CodeActionNormalize.normalize(edit, uri, text)
        val nt = normalized.changes.single().edits.single().newText
        assertEquals("\n\nimport java.time.LocalDate\n", nt)
    }

    @Test
    fun `import insert already ending in newline is untouched`() {
        val text = "// header\n"
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(RenameEdit(0, 0, 0, 0, "import java.time.LocalDate\n")),
                ),
            ),
        )
        val normalized = CodeActionNormalize.normalize(edit, uri, text)
        assertSame(edit, normalized)
    }

    @Test
    fun `non-import inserts are untouched`() {
        val text = "fun foo()\n"
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(uri, listOf(RenameEdit(0, 0, 0, 0, "x"))),
            ),
        )
        val normalized = CodeActionNormalize.normalize(edit, uri, text)
        assertSame(edit, normalized)
    }

    @Test
    fun `import insert at end-of-line position stays unchanged`() {
        val text = "// header\nfun foo()\n"
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(RenameEdit(0, 9, 0, 9, "\nimport java.time.LocalDate")),
                ),
            ),
        )
        val normalized = CodeActionNormalize.normalize(edit, uri, text)
        assertSame(edit, normalized)
    }

    @Test
    fun `replacement edits are not normalized`() {
        val text = "fun foo() = bar\n"
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(RenameEdit(0, 12, 0, 15, "import java.time.LocalDate")),
                ),
            ),
        )
        val normalized = CodeActionNormalize.normalize(edit, uri, text)
        assertSame(edit, normalized)
    }
}
