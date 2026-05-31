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

    @Test
    fun `snippet expander turns method snippet into call with caret inside parens`() {
        val r = SnippetExpander.expand("length(\$0)")
        assertEquals("length()", r.text)
        assertEquals("length(".length, r.finalCaret)
    }

    @Test
    fun `snippet expander uses default text from placeholder`() {
        val r = SnippetExpander.expand("substring(\${1:start}, \${2:end})\$0")
        assertEquals("substring(start, end)", r.text)
        assertEquals(r.text.length, r.finalCaret)
    }

    @Test
    fun `snippet expander handles bare numbered placeholders`() {
        val r = SnippetExpander.expand("for (\$1 in \$2) {\n\t\$0\n}")
        assertEquals("for ( in ) {\n\t\n}", r.text)
        assertEquals("for ( in ) {\n\t".length, r.finalCaret)
    }

    @Test
    fun `reindent prepends current indent to continuation lines and shifts caret`() {
        val base = SnippetExpander.expand("if (\$1) {\n\t\$0\n}")
        val r = SnippetExpander.reindentContinuationLines(base, "    ")
        assertEquals("if () {\n    \t\n    }", r.text)
        assertEquals("if () {\n    \t".length, r.finalCaret)
        assertEquals("if (".length, r.tabstops.single().start)
    }

    @Test
    fun `reindent is a no-op for single-line snippets`() {
        val base = SnippetExpander.expand("length(\$0)")
        val r = SnippetExpander.reindentContinuationLines(base, "        ")
        assertEquals(base.text, r.text)
        assertEquals(base.finalCaret, r.finalCaret)
    }

    @Test
    fun `reindent is a no-op when indent is empty`() {
        val base = SnippetExpander.expand("a {\n\tb\n}")
        val r = SnippetExpander.reindentContinuationLines(base, "")
        assertEquals(base.text, r.text)
    }

    @Test
    fun `reindent shifts every numbered tabstop past each newline`() {
        val base = SnippetExpander.expand("try {\n\t\$1\n} catch (e) {\n\t\$2\n}")
        val r = SnippetExpander.reindentContinuationLines(base, "  ")
        assertEquals("try {\n  \t\n  } catch (e) {\n  \t\n  }", r.text)
        assertEquals(2, r.tabstops.size)
        assertEquals(r.text.indexOf('\n') + 1 + "  \t".length, r.tabstops[0].start)
    }

    @Test
    fun `snippet expander unescapes dollar`() {
        val r = SnippetExpander.expand("price = \\\$5")
        assertEquals("price = \$5", r.text)
    }

    @Test
    fun `snippet expander handles choice placeholder`() {
        val r = SnippetExpander.expand("\${1|red,green,blue|}")
        assertEquals("red", r.text)
    }

    @Test
    fun `snippet expander returns plain text when no placeholders`() {
        val r = SnippetExpander.expand("println()")
        assertEquals("println()", r.text)
        assertEquals("println()".length, r.finalCaret)
        assertTrue(r.tabstops.isEmpty())
    }

    @Test
    fun `snippet expander captures numbered tabstops in order`() {
        val r = SnippetExpander.expand("Vec2(\$1, \$2)\$0")
        assertEquals("Vec2(, )", r.text)
        assertEquals(2, r.tabstops.size)
        assertEquals(1, r.tabstops[0].number)
        assertEquals("Vec2(".length, r.tabstops[0].start)
        assertEquals("Vec2(".length, r.tabstops[0].end)
        assertEquals(2, r.tabstops[1].number)
        assertEquals("Vec2(, ".length, r.tabstops[1].start)
        assertEquals("Vec2(, ".length, r.tabstops[1].end)
    }

    @Test
    fun `enhancer synthesizes constructor snippet for class with detail params`() {
        val item = CompletionItem(
            label = "Vec2",
            kind = CompletionItemKind.CLASS,
            detail = "(x: Double, y: Double) -> Vec2",
            insertText = "Vec2",
            isSnippet = false,
        )
        val r = CompletionEnhancer.enhance(listOf(item))
        assertEquals(1, r.size)
        val out = r[0]
        assertTrue(out.isSnippet)
        assertEquals("Vec2(x, y)", out.label)
        assertEquals("Vec2(\${1:x}, \${2:y})\$0", out.insertText)
    }

    @Test
    fun `enhancer skips non-enhanceable kinds`() {
        val item = CompletionItem(
            label = "size",
            kind = CompletionItemKind.FIELD,
            detail = "val size: Int",
            insertText = "size",
            isSnippet = false,
        )
        val r = CompletionEnhancer.enhance(listOf(item))
        assertEquals(item, r[0])
    }

    @Test
    fun `enhancer synthesizes snippet for FUNCTION with params`() {
        val item = CompletionItem(
            label = "addInts",
            kind = CompletionItemKind.FUNCTION,
            detail = "fun addInts(x: Int, y: Int): Int",
            insertText = "addInts",
            isSnippet = false,
        )
        val out = CompletionEnhancer.enhance(listOf(item))[0]
        assertTrue(out.isSnippet)
        assertEquals("addInts(x, y)", out.label)
        assertEquals("addInts(\${1:x}, \${2:y})\$0", out.insertText)
    }

    @Test
    fun `enhancer synthesizes snippet for METHOD with params`() {
        val item = CompletionItem(
            label = "repeat",
            kind = CompletionItemKind.METHOD,
            detail = "fun String.repeat(n: Int): String",
            insertText = "repeat",
            isSnippet = false,
        )
        val out = CompletionEnhancer.enhance(listOf(item))[0]
        assertTrue(out.isSnippet)
        assertEquals("repeat(n)", out.label)
        assertEquals("repeat(\${1:n})\$0", out.insertText)
    }

    @Test
    fun `enhancer re-enhances snippet with insufficient tabstops for multi-param FUNCTION`() {
        val item = CompletionItem(
            label = "addInts",
            kind = CompletionItemKind.FUNCTION,
            detail = "fun addInts(x: Int, y: Int): Int",
            insertText = "addInts(\$0)",
            isSnippet = true,
        )
        val out = CompletionEnhancer.enhance(listOf(item))[0]
        assertTrue(out.isSnippet)
        assertEquals("addInts(x, y)", out.label)
        assertEquals("addInts(\${1:x}, \${2:y})\$0", out.insertText)
    }

    @Test
    fun `enhancer keeps snippet when KLS supplies enough tabstops`() {
        val item = CompletionItem(
            label = "addInts",
            kind = CompletionItemKind.FUNCTION,
            detail = "fun addInts(x: Int, y: Int): Int",
            insertText = "addInts(\${1:x}, \${2:y})",
            isSnippet = true,
        )
        val out = CompletionEnhancer.enhance(listOf(item))[0]
        assertEquals("addInts", out.label)
        assertEquals("addInts(\${1:x}, \${2:y})", out.insertText)
    }

    @Test
    fun `enhancer leaves zero-param FUNCTION untouched`() {
        val item = CompletionItem(
            label = "println",
            kind = CompletionItemKind.FUNCTION,
            detail = "fun println()",
            insertText = "println",
            isSnippet = false,
        )
        val r = CompletionEnhancer.enhance(listOf(item))
        assertEquals(item, r[0])
    }

    @Test
    fun `enhancer does not synthesize snippet from import-from detail on CLASS items`() {
        val item = CompletionItem(
            label = "ArrayList",
            kind = CompletionItemKind.CLASS,
            detail = "(import from java.util)",
            insertText = "ArrayList",
            isSnippet = false,
        )
        val r = CompletionEnhancer.enhance(listOf(item))
        assertEquals(1, r.size)
        assertEquals("ArrayList", r[0].label, "label must not be polluted with import-from text")
        assertEquals("ArrayList", r[0].insertText)
        assertFalse(r[0].isSnippet)
    }

    @Test
    fun `enhancer skips class without param info`() {
        val item = CompletionItem(
            label = "Singleton",
            kind = CompletionItemKind.CLASS,
            detail = null,
            insertText = "Singleton",
            isSnippet = false,
        )
        val r = CompletionEnhancer.enhance(listOf(item))
        assertEquals(item, r[0])
    }

    @Test
    fun `enhancer does not duplicate when constructor item already present with same label`() {
        val cls = CompletionItem(
            label = "Vec2",
            kind = CompletionItemKind.CLASS,
            detail = "(x: Double, y: Double) -> Vec2",
            insertText = "Vec2",
            isSnippet = false,
        )
        val ctor = CompletionItem(
            label = "Vec2(x, y)",
            kind = CompletionItemKind.CONSTRUCTOR,
            detail = "(x: Double, y: Double)",
            insertText = "Vec2(\${1:x}, \${2:y})",
            isSnippet = true,
        )
        val r = CompletionEnhancer.enhance(listOf(cls, ctor))
        assertEquals(2, r.size)
        assertEquals("Vec2", r[0].label)
        assertEquals("Vec2(x, y)", r[1].label)
    }

    @Test
    fun `enhancer with dot trigger drops KEYWORD kind`() {
        val items = listOf(
            CompletionItem("size", CompletionItemKind.FIELD, detail = "val size: Int", insertText = "size", isSnippet = false),
            CompletionItem("file", CompletionItemKind.KEYWORD, detail = null, insertText = "file", isSnippet = false),
            CompletionItem("filter", CompletionItemKind.FUNCTION, detail = "fun List<T>.filter(predicate: ...)", insertText = "filter", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = ".")
        assertEquals(listOf("size", "filter(predicate)"), r.map { it.label })
    }

    @Test
    fun `enhancer with dot trigger drops CLASS items with import-from detail`() {
        val items = listOf(
            CompletionItem("size", CompletionItemKind.FIELD, detail = "val size: Int", insertText = "size", isSnippet = false),
            CompletionItem("AgentLoadException", CompletionItemKind.CLASS, detail = "(import from com.sun.tools.attach)", insertText = "AgentLoadException", isSnippet = false),
            CompletionItem("VirtualMachine", CompletionItemKind.CLASS, detail = "(import from com.sun.tools.attach)", insertText = "VirtualMachine", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = ".")
        assertEquals(listOf("size"), r.map { it.label })
    }

    @Test
    fun `enhancer with dot trigger keeps non-import CLASS items`() {
        val items = listOf(
            CompletionItem("size", CompletionItemKind.FIELD, detail = "val size: Int", insertText = "size", isSnippet = false),
            CompletionItem("Companion", CompletionItemKind.CLASS, detail = "companion object Companion", insertText = "Companion", isSnippet = false),
            CompletionItem("Inner", CompletionItemKind.CLASS, detail = "class Inner", insertText = "Inner", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = ".")
        assertEquals(setOf("size", "Companion", "Inner"), r.map { it.label }.toSet())
    }

    @Test
    fun `enhancer with dot trigger 4-tier sort direct then type-specific then generic then java`() {
        val items = listOf(
            CompletionItem("chars", CompletionItemKind.FUNCTION, detail = "fun chars(): IntStream!", insertText = "chars()", isSnippet = true),
            CompletionItem("also", CompletionItemKind.FUNCTION, detail = "inline fun <T> T.also(block: (T) -> Unit): T", insertText = "also { \${1:block} }", isSnippet = true),
            CompletionItem("uppercase", CompletionItemKind.FUNCTION, detail = "fun String.uppercase(): String", insertText = "uppercase()", isSnippet = true),
            CompletionItem("length", CompletionItemKind.FIELD, detail = "val length: Int", insertText = "length", isSnippet = false),
            CompletionItem("compareTo", CompletionItemKind.FUNCTION, detail = "fun compareTo(other: String): Int", insertText = "compareTo(\${1:other})", isSnippet = true),
            CompletionItem("hashCode", CompletionItemKind.FUNCTION, detail = "inline fun Any?.hashCode(): Int", insertText = "hashCode()", isSnippet = true),
            CompletionItem("substring", CompletionItemKind.FUNCTION, detail = "inline fun String.substring(startIndex: Int): String", insertText = "substring(\${1:startIndex})", isSnippet = true),
            CompletionItem("to", CompletionItemKind.FUNCTION, detail = "infix fun <A, B> A.to(that: B): Pair<A, B>", insertText = "to(\${1:that})", isSnippet = true),
            CompletionItem("formatted", CompletionItemKind.FUNCTION, detail = "fun formatted(vararg Any!): String!", insertText = "formatted(\${1:p0})", isSnippet = true),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = ".")
        assertEquals(
            listOf("length", "compareTo", "uppercase", "substring", "also", "hashCode", "to", "chars", "formatted"),
            r.map { it.label },
        )
    }

    @Test
    fun `enhancer with dot trigger ranks Comparable interface receiver as type-specific`() {
        val items = listOf(
            CompletionItem("also", CompletionItemKind.FUNCTION, detail = "inline fun <T> T.also(block: (T) -> Unit): T", insertText = "also", isSnippet = true),
            CompletionItem("compareTo", CompletionItemKind.FUNCTION, detail = "inline infix fun <T> Comparable<T>.compareTo(other: T): Int", insertText = "compareTo", isSnippet = true),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = ".")
        assertEquals(listOf("compareTo(other)", "also(block)"), r.map { it.label })
    }

    @Test
    fun `enhancer without dot trigger keeps order and CLASS items`() {
        val items = listOf(
            CompletionItem("Vec2", CompletionItemKind.CLASS, detail = "class Vec2", insertText = "Vec2", isSnippet = false),
            CompletionItem("file", CompletionItemKind.KEYWORD, detail = null, insertText = "file", isSnippet = false),
            CompletionItem("println", CompletionItemKind.FUNCTION, detail = "fun println()", insertText = "println()", isSnippet = true),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = null)
        assertEquals(listOf("Vec2", "file", "println"), r.map { it.label })
    }

    @Test
    fun `additionalTextEdits map into additionalEdits with line and char ranges`() {
        val src = org.eclipse.lsp4j.CompletionItem("Path").apply {
            kind = org.eclipse.lsp4j.CompletionItemKind.Class
            additionalTextEdits = listOf(
                TextEdit(Range(Position(2, 0), Position(2, 0)), "import java.nio.file.Path\n"),
            )
        }
        val mapped = CompletionItem.fromLsp(src)
        assertEquals(1, mapped.additionalEdits.size)
        val ed = mapped.additionalEdits[0]
        assertEquals(2, ed.startLine)
        assertEquals(0, ed.startCharacter)
        assertEquals(2, ed.endLine)
        assertEquals(0, ed.endCharacter)
        assertEquals("import java.nio.file.Path\n", ed.newText)
    }

    @Test
    fun `enhancer dedups exact (label, detail, kind) duplicates keeping first`() {
        val items = listOf(
            CompletionItem("Short", CompletionItemKind.CLASS, detail = "class Short : Number", insertText = "Short", isSnippet = false),
            CompletionItem("ShortArray", CompletionItemKind.CLASS, detail = "class ShortArray : Any", insertText = "ShortArray", isSnippet = false),
            CompletionItem("Short", CompletionItemKind.CLASS, detail = "class Short : Number", insertText = "Short", isSnippet = false),
            CompletionItem("Suppress", CompletionItemKind.CLASS, detail = "annotation class Suppress", insertText = "Suppress", isSnippet = false),
            CompletionItem("Short", CompletionItemKind.CLASS, detail = "class Short : Number", insertText = "Short", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = null)
        assertEquals(listOf("Short", "ShortArray", "Suppress"), r.map { it.label })
    }

    @Test
    fun `enhancer keeps overloads when detail differs`() {
        val items = listOf(
            CompletionItem("plus", CompletionItemKind.FUNCTION, detail = "operator fun plus(other: Any?): String", insertText = "plus", isSnippet = false),
            CompletionItem("plus", CompletionItemKind.FUNCTION, detail = "operator fun String?.plus(other: Any?): String", insertText = "plus", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = null)
        assertEquals(2, r.size)
    }

    @Test
    fun `dot trigger restores raw items when CLASS filter would empty result`() {
        val items = listOf(
            CompletionItem("Foo", CompletionItemKind.INTERFACE, detail = "interface Foo (import from com.example)", insertText = "Foo", isSnippet = false),
            CompletionItem("Bar", CompletionItemKind.INTERFACE, detail = "interface Bar (import from com.example)", insertText = "Bar", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = ".")
        assertEquals(2, r.size, "filter would drop both — fallback should restore them")
        assertEquals("Foo", r[0].label)
    }

    @Test
    fun `prefix sort lifts case-sensitive matches above fuzzy ones`() {
        val items = listOf(
            CompletionItem("DeprecationLevel", CompletionItemKind.ENUM, detail = "enum DeprecationLevel", insertText = "DeprecationLevel", isSnippet = false),
            CompletionItem("UnsafeVariance", CompletionItemKind.CLASS, detail = "class UnsafeVariance", insertText = "UnsafeVariance", isSnippet = false),
            CompletionItem("Volatile", CompletionItemKind.CLASS, detail = "class Volatile", insertText = "Volatile", isSnippet = false),
            CompletionItem("vararg", CompletionItemKind.KEYWORD, detail = null, insertText = "vararg", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = null, prefix = "V")
        assertEquals("Volatile", r[0].label, "exact-case prefix match wins")
        assertEquals("DeprecationLevel", r[1].label, "uppercase prefix demotes KEYWORD below type-kind fuzzy matches")
        assertEquals("UnsafeVariance", r[2].label)
        assertEquals("vararg", r[3].label, "KEYWORD shifted +1 prefix-rank under uppercase prefix")
    }

    @Test
    fun `uppercase prefix shifts KEYWORD prefix-rank below stricter matches`() {
        val items = listOf(
            CompletionItem("file", CompletionItemKind.KEYWORD, detail = null, insertText = "file", isSnippet = false),
            CompletionItem("filter", CompletionItemKind.FUNCTION, detail = "fun filter()", insertText = "filter", isSnippet = false),
            CompletionItem("File", CompletionItemKind.CLASS, detail = "class File", insertText = "File", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = null, prefix = "File")
        assertEquals("File", r[0].label, "exact-case prefix CLASS wins")
        assertEquals("filter", r[1].label, "fuzzy FUNCTION beats shifted KEYWORD")
        assertEquals("file", r[2].label, "KEYWORD demoted from rank 1 to rank 2 by shift")
    }

    @Test
    fun `prefix sort is stable within same rank preserving prior order`() {
        val items = listOf(
            CompletionItem("printStackTrace", CompletionItemKind.FUNCTION, detail = "fun printStackTrace()", insertText = "printStackTrace", isSnippet = false),
            CompletionItem("plus", CompletionItemKind.FUNCTION, detail = "operator fun BigDecimal.plus(other: BigDecimal): BigDecimal", insertText = "plus", isSnippet = false),
            CompletionItem("println", CompletionItemKind.FUNCTION, detail = "fun println()", insertText = "println", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = null, prefix = "p")
        assertEquals("printStackTrace", r[0].label)
        assertEquals("plus(other)", r[1].label)
        assertEquals("println", r[2].label)
    }

    @Test
    fun `uppercase prefix lifts type kinds above functions within same prefix rank`() {
        val items = listOf(
            CompletionItem("autoTriggerPlayground", CompletionItemKind.FUNCTION, detail = "fun autoTriggerPlayground()", insertText = "autoTriggerPlayground", isSnippet = false),
            CompletionItem("substring", CompletionItemKind.FUNCTION, detail = "fun substring(): String", insertText = "substring", isSnippet = false),
            CompletionItem("URI", CompletionItemKind.CLASS, detail = "class URI", insertText = "URI", isSnippet = false),
            CompletionItem("UriBuilder", CompletionItemKind.CLASS, detail = "class UriBuilder", insertText = "UriBuilder", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = null, prefix = "URI")
        assertEquals("URI", r[0].label, "exact-case prefix match still wins overall")
        assertEquals("UriBuilder", r[1].label, "case-insensitive type beats fuzzy lowercase functions")
        assertEquals("autoTriggerPlayground", r[2].label)
        assertEquals("substring", r[3].label)
    }

    @Test
    fun `uppercase prefix demotes KEYWORD below other lowercase items`() {
        val items = listOf(
            CompletionItem("file", CompletionItemKind.KEYWORD, detail = null, insertText = "file", isSnippet = false),
            CompletionItem("fill", CompletionItemKind.FUNCTION, detail = "fun fill()", insertText = "fill", isSnippet = false),
            CompletionItem("filter", CompletionItemKind.FUNCTION, detail = "fun filter()", insertText = "filter", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = null, prefix = "Fil")
        assertEquals("fill", r[0].label, "lowercase functions beat keyword when uppercase prefix")
        assertEquals("filter", r[1].label)
        assertEquals("file", r[2].label)
    }

    @Test
    fun `lowercase prefix keeps keyword in original position`() {
        val items = listOf(
            CompletionItem("file", CompletionItemKind.KEYWORD, detail = null, insertText = "file", isSnippet = false),
            CompletionItem("fill", CompletionItemKind.FUNCTION, detail = "fun fill()", insertText = "fill", isSnippet = false),
            CompletionItem("filter", CompletionItemKind.FUNCTION, detail = "fun filter()", insertText = "filter", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = null, prefix = "fil")
        assertEquals("file", r[0].label, "lowercase prefix should not penalize keyword")
        assertEquals("fill", r[1].label)
        assertEquals("filter", r[2].label)
    }

    @Test
    fun `lowercase prefix preserves original ordering within rank without case bias`() {
        val items = listOf(
            CompletionItem("println", CompletionItemKind.FUNCTION, detail = "fun println()", insertText = "println", isSnippet = false),
            CompletionItem("Path", CompletionItemKind.CLASS, detail = "class Path", insertText = "Path", isSnippet = false),
            CompletionItem("plus", CompletionItemKind.FUNCTION, detail = "fun plus()", insertText = "plus", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = null, prefix = "p")
        assertEquals("println", r[0].label)
        assertEquals("plus", r[1].label)
        assertEquals("Path", r[2].label)
    }

    @Test
    fun `empty prefix leaves order untouched`() {
        val items = listOf(
            CompletionItem("zebra", CompletionItemKind.FUNCTION, detail = "fun zebra()", insertText = "zebra", isSnippet = false),
            CompletionItem("apple", CompletionItemKind.FUNCTION, detail = "fun apple()", insertText = "apple", isSnippet = false),
        )
        val r = CompletionEnhancer.enhance(items, triggerCharacter = null, prefix = "")
        assertEquals("zebra", r[0].label)
        assertEquals("apple", r[1].label)
    }

    @Test
    fun `filterByPrefix keeps items whose label starts with prefix case-insensitively`() {
        val items = listOf(
            CompletionItem("Path", CompletionItemKind.CLASS, detail = "interface Path", insertText = "Path", isSnippet = false),
            CompletionItem("Paths", CompletionItemKind.CLASS, detail = "class Paths", insertText = "Paths", isSnippet = false),
            CompletionItem("PathMatcher", CompletionItemKind.INTERFACE, detail = "interface PathMatcher", insertText = "PathMatcher", isSnippet = false),
            CompletionItem("println", CompletionItemKind.FUNCTION, detail = "fun println()", insertText = "println", isSnippet = false),
            CompletionItem("Pair", CompletionItemKind.CLASS, detail = "class Pair", insertText = "Pair", isSnippet = false),
        )
        val r = CompletionEnhancer.filterByPrefix(items, "Path")
        assertEquals(3, r.size)
        assertEquals(setOf("Path", "Paths", "PathMatcher"), r.map { it.label }.toSet())
    }

    @Test
    fun `filterByPrefix narrows when prefix grows from extension`() {
        val items = listOf(
            CompletionItem("Path", CompletionItemKind.CLASS, detail = "interface Path", insertText = "Path", isSnippet = false),
            CompletionItem("Paths", CompletionItemKind.CLASS, detail = "class Paths", insertText = "Paths", isSnippet = false),
            CompletionItem("PathMatcher", CompletionItemKind.INTERFACE, detail = "interface PathMatcher", insertText = "PathMatcher", isSnippet = false),
        )
        val r = CompletionEnhancer.filterByPrefix(items, "Paths")
        assertEquals(1, r.size)
        assertEquals("Paths", r[0].label)
    }

    @Test
    fun `filterByPrefix returns input unchanged for empty prefix`() {
        val items = listOf(
            CompletionItem("a", CompletionItemKind.FUNCTION, detail = null, insertText = "a", isSnippet = false),
            CompletionItem("b", CompletionItemKind.FUNCTION, detail = null, insertText = "b", isSnippet = false),
        )
        val r = CompletionEnhancer.filterByPrefix(items, "")
        assertEquals(2, r.size)
    }

    @Test
    fun `filterByPrefix matches via insertText when label differs`() {
        val items = listOf(
            CompletionItem(
                label = "ParameterName(name: String)",
                kind = CompletionItemKind.CONSTRUCTOR,
                detail = "constructor ParameterName(name: String)",
                insertText = "ParameterName(\${1:name})",
                isSnippet = true,
            ),
            CompletionItem(
                label = "Pair",
                kind = CompletionItemKind.CLASS,
                detail = null,
                insertText = "Pair",
                isSnippet = false,
            ),
        )
        val r = CompletionEnhancer.filterByPrefix(items, "Para")
        assertEquals(1, r.size)
        assertEquals("ParameterName(name: String)", r[0].label)
    }

    @Test
    fun `snippet expander captures tabstops with default placeholders`() {
        val r = SnippetExpander.expand("substring(\${1:start}, \${2:end})\$0")
        assertEquals("substring(start, end)", r.text)
        assertEquals(2, r.tabstops.size)
        assertEquals("substring(".length, r.tabstops[0].start)
        assertEquals("substring(start".length, r.tabstops[0].end)
        assertEquals("substring(start, ".length, r.tabstops[1].start)
        assertEquals("substring(start, end".length, r.tabstops[1].end)
    }

    @Test
    fun `C dot trigger keeps struct fields and methods, drops keywords`() {
        val items = listOf(
            CompletionItem(label = "size", kind = CompletionItemKind.FIELD, insertText = "size", isSnippet = false),
            CompletionItem(label = "name", kind = CompletionItemKind.FIELD, insertText = "name", isSnippet = false),
            CompletionItem(label = "init", kind = CompletionItemKind.METHOD, insertText = "init", isSnippet = false),
            CompletionItem(label = "struct", kind = CompletionItemKind.KEYWORD, insertText = "struct", isSnippet = false),
            CompletionItem(label = "static", kind = CompletionItemKind.KEYWORD, insertText = "static", isSnippet = false),
        )
        val out = CompletionEnhancer.enhance(items, triggerCharacter = ".")
        val labels = out.map { it.label }
        assertTrue("size" in labels)
        assertTrue("name" in labels)
        assertTrue("init" in labels)
        assertFalse("struct" in labels)
        assertFalse("static" in labels)
    }

    @Test
    fun `C invoke trigger keeps keywords alongside functions`() {
        val items = listOf(
            CompletionItem(label = "strcmp", kind = CompletionItemKind.FUNCTION, detail = "int (const char *, const char *)", insertText = "strcmp", isSnippet = false),
            CompletionItem(label = "struct", kind = CompletionItemKind.KEYWORD, insertText = "struct", isSnippet = false),
        )
        val out = CompletionEnhancer.enhance(items, triggerCharacter = null)
        val labels = out.map { it.label }
        assertTrue("struct" in labels, "keyword 'struct' must remain when not dot-triggered")
        assertTrue(labels.any { it.startsWith("strcmp(") }, "strcmp should be present (possibly enhanced with params)")
    }

    @Test
    fun `C dot trigger with empty prefix preserves clangd member ordering`() {
        val list = org.eclipse.lsp4j.CompletionList(
            false,
            listOf(
                org.eclipse.lsp4j.CompletionItem("len").apply { kind = org.eclipse.lsp4j.CompletionItemKind.Field },
                org.eclipse.lsp4j.CompletionItem("data").apply { kind = org.eclipse.lsp4j.CompletionItemKind.Field },
                org.eclipse.lsp4j.CompletionItem("clone").apply { kind = org.eclipse.lsp4j.CompletionItemKind.Method },
                org.eclipse.lsp4j.CompletionItem("if").apply { kind = org.eclipse.lsp4j.CompletionItemKind.Keyword },
            ),
        )
        val mapped = CompletionList.fromLsp(list, triggerCharacter = ".", prefix = "")
        val labels = mapped.items.map { it.label }
        assertTrue("len" in labels)
        assertTrue("data" in labels)
        assertTrue("clone" in labels)
        assertFalse("if" in labels)
    }

    @Test
    fun `C completion prefix middle-of-word matches struct field names`() {
        val items = listOf(
            CompletionItem(label = "buffer", kind = CompletionItemKind.FIELD, insertText = "buffer", isSnippet = false),
            CompletionItem(label = "buf_size", kind = CompletionItemKind.FIELD, insertText = "buf_size", isSnippet = false),
            CompletionItem(label = "count", kind = CompletionItemKind.FIELD, insertText = "count", isSnippet = false),
        )
        val filtered = CompletionEnhancer.filterByPrefix(items, "buf")
        val labels = filtered.map { it.label }
        assertTrue("buffer" in labels)
        assertTrue("buf_size" in labels)
        assertFalse("count" in labels)
    }

    @Test
    fun `C completion textEdit range from clangd member completion is preserved`() {
        val src = org.eclipse.lsp4j.CompletionItem("length").apply {
            kind = org.eclipse.lsp4j.CompletionItemKind.Field
            textEdit = Either.forLeft(
                TextEdit(Range(Position(11, 6), Position(11, 6)), "length"),
            )
        }
        val mapped = CompletionItem.fromLsp(src)
        assertEquals("length", mapped.label)
        assertEquals(CompletionItemKind.FIELD, mapped.kind)
        assertNotNull(mapped.edit)
        assertEquals(11, mapped.edit!!.startLine)
        assertEquals(6, mapped.edit!!.startCharacter)
        assertEquals(11, mapped.edit!!.endLine)
        assertEquals(6, mapped.edit!!.endCharacter)
    }
}
