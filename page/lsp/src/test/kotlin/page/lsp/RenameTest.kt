package page.lsp

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenameTest {

    @Test
    fun `null edit yields empty`() {
        val r = RenameWorkspaceEdit.fromLsp(null)
        assertTrue(r.isEmpty)
        assertEquals(0, r.totalEditCount)
    }

    @Test
    fun `changes map maps to file changes`() {
        val we = WorkspaceEdit().apply {
            changes = mutableMapOf(
                "file:///A.kt" to mutableListOf(
                    TextEdit(Range(Position(2, 4), Position(2, 7)), "bar"),
                    TextEdit(Range(Position(0, 0), Position(0, 3)), "bar"),
                ),
                "file:///B.kt" to mutableListOf(
                    TextEdit(Range(Position(5, 1), Position(5, 4)), "bar"),
                ),
            )
        }
        val r = RenameWorkspaceEdit.fromLsp(we)
        assertEquals(2, r.changes.size)
        assertEquals(3, r.totalEditCount)
        val a = r.changes.first { it.uri == "file:///A.kt" }
        assertEquals(2, a.edits.first().startLine)
        assertEquals(0, a.edits.last().startLine)
    }

    @Test
    fun `documentChanges preferred over changes map`() {
        val we = WorkspaceEdit().apply {
            documentChanges = mutableListOf(
                Either.forLeft<TextDocumentEdit, ResourceOperation>(
                    TextDocumentEdit(
                        VersionedTextDocumentIdentifier("file:///A.kt", 1),
                        mutableListOf(TextEdit(Range(Position(0, 0), Position(0, 3)), "newName")),
                    )
                ),
            )
            changes = mutableMapOf(
                "file:///OLD.kt" to mutableListOf(TextEdit(Range(Position(9, 9), Position(9, 9)), "ignored")),
            )
        }
        val r = RenameWorkspaceEdit.fromLsp(we)
        assertEquals(1, r.changes.size)
        assertEquals("file:///A.kt", r.changes[0].uri)
        assertEquals("newName", r.changes[0].edits[0].newText)
    }

    @Test
    fun `applyToText applies edits in descending order without shifting`() {
        val src = "val foo = 1\nval foo2 = foo + 1\n"
        val edits = listOf(
            RenameEdit(0, 4, 0, 7, "bar"),
            RenameEdit(1, 4, 1, 8, "bar2"),
            RenameEdit(1, 11, 1, 14, "bar"),
        )
        val out = RenameApply.applyToText(src, edits)
        assertEquals("val bar = 1\nval bar2 = bar + 1\n", out)
    }

    @Test
    fun `applyToText handles out-of-order inputs`() {
        val src = "abc def"
        val edits = listOf(
            RenameEdit(0, 0, 0, 3, "X"),
            RenameEdit(0, 4, 0, 7, "Y"),
        )
        assertEquals("X Y", RenameApply.applyToText(src, edits))
    }

    @Test
    fun `applyToText is no-op on empty edits`() {
        assertEquals("hi", RenameApply.applyToText("hi", emptyList()))
    }

    @Test
    fun `applyToText preserves input order for edits at the same position`() {
        val src = "class Circle : Shape()"
        val pos = src.length
        val edits = listOf(
            RenameEdit(0, pos, 0, pos, " {"),
            RenameEdit(0, pos, 0, pos, "\n    override fun area(): Double { }"),
            RenameEdit(0, pos, 0, pos, "\n    override fun perimeter(): Double { }"),
            RenameEdit(0, pos, 0, pos, "\n}"),
        )
        val out = RenameApply.applyToText(src, edits)
        assertEquals(
            "class Circle : Shape() {\n    override fun area(): Double { }\n    override fun perimeter(): Double { }\n}",
            out,
        )
    }

    @Test
    fun `prepareRename range-only`() {
        val either: Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> =
            Either3.forFirst(Range(Position(1, 2), Position(1, 5)))
        val p = RenamePrepare.fromLsp(either)
        assertNotNull(p)
        assertEquals(1, p!!.startLine)
        assertEquals(2, p.startCharacter)
        assertEquals(5, p.endCharacter)
        assertNull(p.placeholder)
        assertFalse(p.isDefaultBehavior)
    }

    @Test
    fun `prepareRename with placeholder`() {
        val r = PrepareRenameResult(Range(Position(0, 0), Position(0, 3)), "oldName")
        val either: Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> = Either3.forSecond(r)
        val p = RenamePrepare.fromLsp(either)
        assertNotNull(p)
        assertEquals("oldName", p!!.placeholder)
    }

    @Test
    fun `prepareRename default behavior marker`() {
        val either: Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> =
            Either3.forThird(PrepareRenameDefaultBehavior(true))
        val p = RenamePrepare.fromLsp(either)
        assertNotNull(p)
        assertTrue(p!!.isDefaultBehavior)
    }

    @Test
    fun `prepareRename null when server refuses`() {
        val p = RenamePrepare.fromLsp(null)
        assertNull(p)
    }

    @Test
    fun `documentChanges merges multiple edits for same URI into one file change`() {
        val we = WorkspaceEdit().apply {
            documentChanges = mutableListOf(
                Either.forLeft<TextDocumentEdit, ResourceOperation>(
                    TextDocumentEdit(
                        VersionedTextDocumentIdentifier("file:///A.kt", 1),
                        mutableListOf(TextEdit(Range(Position(5, 0), Position(5, 3)), "New")),
                    )
                ),
                Either.forLeft<TextDocumentEdit, ResourceOperation>(
                    TextDocumentEdit(
                        VersionedTextDocumentIdentifier("file:///A.kt", 1),
                        mutableListOf(TextEdit(Range(Position(2, 4), Position(2, 7)), "New")),
                    )
                ),
                Either.forLeft<TextDocumentEdit, ResourceOperation>(
                    TextDocumentEdit(
                        VersionedTextDocumentIdentifier("file:///B.kt", 1),
                        mutableListOf(TextEdit(Range(Position(0, 0), Position(0, 3)), "New")),
                    )
                ),
            )
        }
        val r = RenameWorkspaceEdit.fromLsp(we)
        assertEquals(2, r.changes.size)
        assertEquals(3, r.totalEditCount)
        val a = r.changes.first { it.uri == "file:///A.kt" }
        assertEquals(2, a.edits.size)
        assertEquals(5, a.edits.first().startLine)
        assertEquals(2, a.edits.last().startLine)
    }

    @Test
    fun `changes map with duplicate URI keys merges edits`() {
        val we = WorkspaceEdit().apply {
            val map = LinkedHashMap<String, MutableList<TextEdit>>()
            map["file:///A.kt"] = mutableListOf(
                TextEdit(Range(Position(0, 0), Position(0, 3)), "New"),
            )
            changes = map
        }
        val r = RenameWorkspaceEdit.fromLsp(we)
        assertEquals(1, r.changes.size)
        assertEquals(1, r.totalEditCount)
    }
}
