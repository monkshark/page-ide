package page.lsp

import org.eclipse.lsp4j.InsertTextFormat as LspInsertTextFormat
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompletionTest {

    @Test
    fun `kind mapping covers common values plus null fallback`() {
        assertEquals(CompletionItemKind.METHOD, CompletionItemKind.fromLsp(org.eclipse.lsp4j.CompletionItemKind.Method))
        assertEquals(CompletionItemKind.FUNCTION, CompletionItemKind.fromLsp(org.eclipse.lsp4j.CompletionItemKind.Function))
        assertEquals(CompletionItemKind.CLASS, CompletionItemKind.fromLsp(org.eclipse.lsp4j.CompletionItemKind.Class))
        assertEquals(CompletionItemKind.VARIABLE, CompletionItemKind.fromLsp(org.eclipse.lsp4j.CompletionItemKind.Variable))
        assertEquals(CompletionItemKind.KEYWORD, CompletionItemKind.fromLsp(org.eclipse.lsp4j.CompletionItemKind.Keyword))
        assertEquals(CompletionItemKind.OTHER, CompletionItemKind.fromLsp(null))
    }

    @Test
    fun `item with textEdit prefers edit newText for insertion`() {
        val src = org.eclipse.lsp4j.CompletionItem("foo").apply {
            kind = org.eclipse.lsp4j.CompletionItemKind.Method
            detail = "fun foo(): Int"
            insertText = "should-be-overridden"
            textEdit = Either.forLeft(
                TextEdit(Range(Position(3, 4), Position(3, 7)), "foo()"),
            )
        }

        val mapped = CompletionItem.fromLsp(src)
        assertEquals("foo", mapped.label)
        assertEquals(CompletionItemKind.METHOD, mapped.kind)
        assertEquals("fun foo(): Int", mapped.detail)
        assertEquals("foo()", mapped.insertText)
        assertNotNull(mapped.edit)
        assertEquals(3, mapped.edit!!.startLine)
        assertEquals(4, mapped.edit!!.startCharacter)
        assertEquals(7, mapped.edit!!.endCharacter)
        assertFalse(mapped.isSnippet)
    }

    @Test
    fun `item without edit falls back to insertText then label`() {
        val withInsert = org.eclipse.lsp4j.CompletionItem("println").apply {
            insertText = "println(\"\")"
        }
        assertEquals("println(\"\")", CompletionItem.fromLsp(withInsert).insertText)

        val labelOnly = org.eclipse.lsp4j.CompletionItem("Foo")
        val mapped = CompletionItem.fromLsp(labelOnly)
        assertEquals("Foo", mapped.insertText)
        assertNull(mapped.edit)
    }

    @Test
    fun `snippet format flag carried through`() {
        val snippet = org.eclipse.lsp4j.CompletionItem("for").apply {
            insertText = "for (\$1 in \$2) {\n\t$0\n}"
            insertTextFormat = LspInsertTextFormat.Snippet
        }
        assertTrue(CompletionItem.fromLsp(snippet).isSnippet)
    }

    @Test
    fun `fromLsp on CompletionList preserves isIncomplete`() {
        val list = org.eclipse.lsp4j.CompletionList(
            true,
            listOf(org.eclipse.lsp4j.CompletionItem("a"), org.eclipse.lsp4j.CompletionItem("b")),
        )
        val mapped = CompletionList.fromLsp(list)
        assertTrue(mapped.isIncomplete)
        assertEquals(listOf("a", "b"), mapped.items.map { it.label })
    }

    @Test
    fun `EMPTY constant has no items`() {
        assertEquals(0, CompletionList.EMPTY.items.size)
        assertFalse(CompletionList.EMPTY.isIncomplete)
    }
}
