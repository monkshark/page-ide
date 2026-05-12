package page.lsp

import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkedString
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HoverTest {

    @Test
    fun `null hover returns null`() {
        assertNull(HoverInfo.fromLsp(null))
    }

    @Test
    fun `MarkupContent renders to markdown value`() {
        val hover = Hover().apply {
            contents = Either.forRight(MarkupContent(MarkupKind.MARKDOWN, "**fun foo(): Int**"))
            range = Range(Position(2, 4), Position(2, 7))
        }
        val info = HoverInfo.fromLsp(hover)
        assertNotNull(info)
        assertEquals("**fun foo(): Int**", info!!.markdown)
        val r = info.range
        assertNotNull(r)
        assertEquals(2, r!!.startLine)
        assertEquals(4, r.startCharacter)
        assertEquals(2, r.endLine)
        assertEquals(7, r.endCharacter)
    }

    @Test
    fun `MarkedString with language wraps in code fence`() {
        val ms = MarkedString("kotlin", "fun foo(): Int")
        val hover = Hover().apply {
            contents = Either.forLeft(mutableListOf(Either.forRight(ms)))
        }
        val info = HoverInfo.fromLsp(hover)
        assertNotNull(info)
        assertEquals("```kotlin\nfun foo(): Int\n```", info!!.markdown)
        assertNull(info.range)
    }

    @Test
    fun `plain string entry joined with double newline`() {
        val hover = Hover().apply {
            contents = Either.forLeft(
                mutableListOf(
                    Either.forLeft("first"),
                    Either.forLeft("second"),
                ),
            )
        }
        val info = HoverInfo.fromLsp(hover)
        assertNotNull(info)
        assertEquals("first\n\nsecond", info!!.markdown)
    }

    @Test
    fun `blank markdown returns null`() {
        val hover = Hover().apply {
            contents = Either.forRight(MarkupContent(MarkupKind.MARKDOWN, ""))
        }
        assertNull(HoverInfo.fromLsp(hover))
    }

    @Test
    fun `MarkedString without language emits raw value`() {
        val ms = MarkedString("", "raw text")
        val hover = Hover().apply {
            contents = Either.forLeft(mutableListOf(Either.forRight(ms)))
        }
        val info = HoverInfo.fromLsp(hover)
        assertNotNull(info)
        assertEquals("raw text", info!!.markdown)
    }

    @Test
    fun `trivial Unit type hover without range returns null`() {
        val ms = MarkedString("kotlin", "Unit")
        val hover = Hover().apply {
            contents = Either.forLeft(mutableListOf(Either.forRight(ms)))
        }
        assertNull(HoverInfo.fromLsp(hover))
    }

    @Test
    fun `trivial Nothing type hover without range returns null`() {
        val ms = MarkedString("kotlin", "Nothing")
        val hover = Hover().apply {
            contents = Either.forLeft(mutableListOf(Either.forRight(ms)))
        }
        assertNull(HoverInfo.fromLsp(hover))
    }

    @Test
    fun `trivial null type hover without range returns null`() {
        val ms = MarkedString("kotlin", "null")
        val hover = Hover().apply {
            contents = Either.forLeft(mutableListOf(Either.forRight(ms)))
        }
        assertNull(HoverInfo.fromLsp(hover))
    }

    @Test
    fun `Unit hover with range passes through`() {
        val ms = MarkedString("kotlin", "Unit")
        val hover = Hover().apply {
            contents = Either.forLeft(mutableListOf(Either.forRight(ms)))
            range = Range(Position(0, 0), Position(0, 4))
        }
        val info = HoverInfo.fromLsp(hover)
        assertNotNull(info)
        assertEquals("```kotlin\nUnit\n```", info!!.markdown)
    }

    @Test
    fun `non-trivial type hover without range passes through`() {
        val ms = MarkedString("kotlin", "List<Int>")
        val hover = Hover().apply {
            contents = Either.forLeft(mutableListOf(Either.forRight(ms)))
        }
        val info = HoverInfo.fromLsp(hover)
        assertNotNull(info)
        assertEquals("```kotlin\nList<Int>\n```", info!!.markdown)
    }

    @Test
    fun `blank entries skipped from MarkedString list`() {
        val hover = Hover().apply {
            contents = Either.forLeft(
                mutableListOf(
                    Either.forLeft(""),
                    Either.forLeft("only"),
                    Either.forLeft("  "),
                ),
            )
        }
        val info = HoverInfo.fromLsp(hover)
        assertNotNull(info)
        assertTrue(info!!.markdown == "only")
    }

    @Test
    fun `synthetic error type with kotlin fence returns null`() {
        val hover = Hover().apply {
            contents = Either.forRight(
                MarkupContent(
                    MarkupKind.MARKDOWN,
                    "```kotlin\n[Error type: Not found recorded type for unknownXyz]\n```",
                ),
            )
        }
        assertNull(HoverInfo.fromLsp(hover))
    }

    @Test
    fun `synthetic error type without fence returns null`() {
        val hover = Hover().apply {
            contents = Either.forRight(MarkupContent(MarkupKind.MARKDOWN, "[Error type: Foo]"))
        }
        assertNull(HoverInfo.fromLsp(hover))
    }

    @Test
    fun `synthetic error type with range still returns null`() {
        val hover = Hover().apply {
            contents = Either.forRight(
                MarkupContent(MarkupKind.MARKDOWN, "```kotlin\n[Error type: X]\n```"),
            )
            range = Range(Position(0, 0), Position(0, 5))
        }
        assertNull(HoverInfo.fromLsp(hover))
    }

    @Test
    fun `enrichForPropertyDecl prefixes val on bare String`() {
        val info = HoverInfo("```kotlin\nString\n```", null)
        val out = info.enrichForPropertyDecl("    val foo: String = \"hi\"", 9)
        assertEquals("```kotlin\nval foo: String\n```", out.markdown)
    }

    @Test
    fun `enrichForPropertyDecl handles var keyword`() {
        val info = HoverInfo("```kotlin\nInt\n```", null)
        val out = info.enrichForPropertyDecl("    var counter: Int = 0", 9)
        assertEquals("```kotlin\nvar counter: Int\n```", out.markdown)
    }

    @Test
    fun `enrichForPropertyDecl handles generics`() {
        val info = HoverInfo("```kotlin\nList<Int>\n```", null)
        val out = info.enrichForPropertyDecl("    val items: List<Int> = listOf()", 9)
        assertEquals("```kotlin\nval items: List<Int>\n```", out.markdown)
    }

    @Test
    fun `enrichForPropertyDecl ignores when character is in indent`() {
        val info = HoverInfo("```kotlin\nString\n```", null)
        val out = info.enrichForPropertyDecl("    val foo: String = \"hi\"", 2)
        assertEquals("```kotlin\nString\n```", out.markdown)
    }

    @Test
    fun `enrichForPropertyDecl ignores non-decl line`() {
        val info = HoverInfo("```kotlin\nString\n```", null)
        val out = info.enrichForPropertyDecl("    user.name", 10)
        assertEquals("```kotlin\nString\n```", out.markdown)
    }

    @Test
    fun `enrichForPropertyDecl skips when md already starts with val`() {
        val info = HoverInfo("```kotlin\nval foo: String\n```", null)
        val out = info.enrichForPropertyDecl("    val foo: String = \"hi\"", 9)
        assertEquals("```kotlin\nval foo: String\n```", out.markdown)
    }

    @Test
    fun `enrichForPropertyDecl skips when fence is not kotlin`() {
        val info = HoverInfo("```\nString\n```", null)
        val out = info.enrichForPropertyDecl("    val foo: String = \"hi\"", 9)
        assertEquals("```\nString\n```", out.markdown)
    }

    @Test
    fun `enrichForPropertyDecl handles modifiers before val`() {
        val info = HoverInfo("```kotlin\nString\n```", null)
        val out = info.enrichForPropertyDecl("    private val secret: String = \"x\"", 17)
        assertEquals("```kotlin\nval secret: String\n```", out.markdown)
    }

    @Test
    fun `enrichForPropertyDecl handles data class ctor param`() {
        val info = HoverInfo("```kotlin\nString\n```", null)
        val out = info.enrichForPropertyDecl("    val name: String,", 9)
        assertEquals("```kotlin\nval name: String\n```", out.markdown)
    }

    @Test
    fun `enrichWithKDocFromDefinition appends param and return tags`() {
        val md = "```kotlin\nfun addInts(a: Int, b: Int): Int\n```\n---\n두 정수의 합을 반환한다."
        val info = HoverInfo(md, null)
        val file = """
            package x

            /**
             * 두 정수의 합을 반환한다.
             *
             * @param a 첫 번째 정수
             * @param b 두 번째 정수
             * @return a + b 의 결과
             */
            fun addInts(a: Int, b: Int): Int = a + b
        """.trimIndent()
        val defLine = file.split('\n').indexOfFirst { it.startsWith("fun addInts") }
        val out = info.enrichWithKDocFromDefinition(file, defLine)
        assertTrue(out.markdown.contains("@param a 첫 번째 정수"), out.markdown)
        assertTrue(out.markdown.contains("@param b 두 번째 정수"), out.markdown)
        assertTrue(out.markdown.contains("@return a + b 의 결과"), out.markdown)
    }

    @Test
    fun `enrichWithKDocFromDefinition skips when markdown already has tags`() {
        val md = "```kotlin\nfun foo(): Int\n```\n---\n본문\n\n@return 결과"
        val info = HoverInfo(md, null)
        val file = """
            /**
             * 본문
             * @return 다른 결과
             */
            fun foo(): Int = 0
        """.trimIndent()
        val defLine = file.split('\n').indexOfFirst { it.startsWith("fun foo") }
        val out = info.enrichWithKDocFromDefinition(file, defLine)
        assertEquals(md, out.markdown)
    }

    @Test
    fun `enrichWithKDocFromDefinition returns this when no kdoc above`() {
        val md = "```kotlin\nfun foo(): Int\n```\n---\n본문"
        val info = HoverInfo(md, null)
        val file = """
            package x

            fun foo(): Int = 0
        """.trimIndent()
        val defLine = file.split('\n').indexOfFirst { it.startsWith("fun foo") }
        val out = info.enrichWithKDocFromDefinition(file, defLine)
        assertEquals(md, out.markdown)
    }

    @Test
    fun `enrichWithKDocFromDefinition skips annotation lines above def`() {
        val md = "```kotlin\nfun foo(a: Int): Int\n```\n---\n본문"
        val info = HoverInfo(md, null)
        val file = """
            /**
             * 본문
             * @param a 인자
             */
            @Composable
            @Stable
            fun foo(a: Int): Int = a
        """.trimIndent()
        val defLine = file.split('\n').indexOfFirst { it.startsWith("fun foo") }
        val out = info.enrichWithKDocFromDefinition(file, defLine)
        assertTrue(out.markdown.contains("@param a 인자"), out.markdown)
    }

    @Test
    fun `enrichWithKDocFromDefinition handles single-line kdoc`() {
        val md = "```kotlin\nfun foo(): Int\n```\n---\n본문"
        val info = HoverInfo(md, null)
        val file = """
            /** 본문 @return 결과 */
            fun foo(): Int = 0
        """.trimIndent()
        val defLine = file.split('\n').indexOfFirst { it.startsWith("fun foo") }
        val out = info.enrichWithKDocFromDefinition(file, defLine)
        assertTrue(out.markdown.contains("@return 결과"), out.markdown)
    }

    @Test
    fun `enrichWithKDocFromDefinition merges multi-line tag description`() {
        val md = "```kotlin\nfun foo(a: Int): Int\n```\n---\n본문"
        val info = HoverInfo(md, null)
        val file = """
            /**
             * 본문
             * @param a 첫 번째 줄
             *   그리고 두 번째 줄
             * @return 결과
             */
            fun foo(a: Int): Int = a
        """.trimIndent()
        val defLine = file.split('\n').indexOfFirst { it.startsWith("fun foo") }
        val out = info.enrichWithKDocFromDefinition(file, defLine)
        assertTrue(out.markdown.contains("@param a 첫 번째 줄 그리고 두 번째 줄"), out.markdown)
        assertTrue(out.markdown.contains("@return 결과"), out.markdown)
    }

    @Test
    fun `needsKdocEnrichment true for signature plus body`() {
        val info = HoverInfo("```kotlin\nfun f(): Int\n```\n---\n본문", null)
        assertTrue(info.needsKdocEnrichment())
    }

    @Test
    fun `needsKdocEnrichment false when tags already present`() {
        val info = HoverInfo("```kotlin\nfun f(): Int\n```\n---\n본문\n\n@return r", null)
        assertEquals(false, info.needsKdocEnrichment())
    }

    @Test
    fun `needsKdocEnrichment false for bare type`() {
        val info = HoverInfo("```kotlin\nString\n```", null)
        assertEquals(false, info.needsKdocEnrichment())
    }

    @Test
    fun `extractKdocTagsAbove ignores unrelated code lines`() {
        val file = """
            val before = 1

            fun foo(): Int = 0
        """.trimIndent()
        val defLine = file.split('\n').indexOfFirst { it.startsWith("fun foo") }
        assertEquals(emptyList(), extractKdocTagsAbove(file, defLine))
    }
}
