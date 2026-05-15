package page.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeActionPreviewTest {

    private val uri = "file:///proj/Foo.kt"

    @Test
    fun `empty edit returns empty list`() {
        val previews = CodeActionPreview.build(RenameWorkspaceEdit.EMPTY, uri, "src")
        assertTrue(previews.isEmpty())
    }

    @Test
    fun `insertion at top renders ADDED line with context below`() {
        val text = "package x\n\nfun foo() = 1\n"
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(RenameEdit(0, 0, 0, 0, "import java.time.LocalDate\n")),
                ),
            ),
        )
        val previews = CodeActionPreview.build(edit, uri, text)
        assertEquals(1, previews.size)
        val p = previews[0]
        assertTrue(p.isCurrent)
        assertEquals(1, p.editCount)
        val added = p.lines.filter { it.kind == CodeActionPreview.LineKind.ADDED }
        assertEquals(1, added.size)
        assertEquals("import java.time.LocalDate", added[0].text)
        val context = p.lines.filter { it.kind == CodeActionPreview.LineKind.CONTEXT }
        assertTrue(context.any { it.text == "package x" })
    }

    @Test
    fun `replacement renders REMOVED and ADDED full lines`() {
        val text = "fun foo() {\n  return bar\n}\n"
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(RenameEdit(1, 9, 1, 12, "baz")),
                ),
            ),
        )
        val previews = CodeActionPreview.build(edit, uri, text)
        val lines = previews.single().lines
        val removed = lines.filter { it.kind == CodeActionPreview.LineKind.REMOVED }
        val added = lines.filter { it.kind == CodeActionPreview.LineKind.ADDED }
        assertEquals(1, removed.size)
        assertEquals("  return bar", removed[0].text)
        assertEquals(1, added.size)
        assertEquals("  return baz", added[0].text)
    }

    @Test
    fun `import insert without trailing newline preview shows merged line`() {
        val text = "// header\nfun foo() = 1\n"
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(RenameEdit(0, 0, 0, 0, "\n\nimport java.time.LocalDate")),
                ),
            ),
        )
        val previews = CodeActionPreview.build(edit, uri, text)
        val lines = previews.single().lines
        val removed = lines.filter { it.kind == CodeActionPreview.LineKind.REMOVED }
        val added = lines.filter { it.kind == CodeActionPreview.LineKind.ADDED }
        assertTrue(removed.any { it.text == "// header" })
        assertTrue(added.any { it.text == "import java.time.LocalDate// header" })
    }

    @Test
    fun `other-file change reports summary without lines`() {
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    "file:///proj/Bar.kt",
                    listOf(RenameEdit(0, 0, 0, 0, "x")),
                ),
            ),
        )
        val previews = CodeActionPreview.build(edit, uri, "code")
        val p = previews.single()
        assertFalse(p.isCurrent)
        assertEquals("Bar.kt", p.basename)
        assertTrue(p.lines.isEmpty())
        assertEquals(1, p.editCount)
    }

    @Test
    fun `descending sorted edits are reordered for preview`() {
        val text = "line0\nline1\nline2\nline3\n"
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(
                        RenameEdit(3, 0, 3, 5, "LINE3"),
                        RenameEdit(0, 0, 0, 5, "LINE0"),
                    ),
                ),
            ),
        )
        val previews = CodeActionPreview.build(edit, uri, text)
        val removed = previews.single().lines.filter { it.kind == CodeActionPreview.LineKind.REMOVED }
        assertEquals(listOf("line0", "line3"), removed.map { it.text })
    }

    @Test
    fun `multiple distant edits produce OMITTED separator`() {
        val text = (0..10).joinToString("\n") { "line$it" }
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    uri,
                    listOf(
                        RenameEdit(0, 0, 0, 5, "LINE0"),
                        RenameEdit(8, 0, 8, 5, "LINE8"),
                    ),
                ),
            ),
        )
        val previews = CodeActionPreview.build(edit, uri, text)
        val omitted = previews.single().lines.filter { it.kind == CodeActionPreview.LineKind.OMITTED }
        assertEquals(1, omitted.size)
    }

    @Test
    fun `basename strips uri to last segment`() {
        val edit = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    "file:///c%3A/Users/x/Desktop/Sample.kt",
                    listOf(RenameEdit(0, 0, 0, 0, "x")),
                ),
            ),
        )
        val previews = CodeActionPreview.build(edit, "file:///elsewhere.kt", "x")
        assertEquals("Sample.kt", previews.single().basename)
    }
}
