package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinLexerTest {

    private fun tokens(src: String) = KotlinLexer.tokenize(src)

    private fun kindsAndText(src: String): List<Pair<TokenKind, String>> =
        tokens(src).map { it.kind to src.substring(it.range.first, it.range.last + 1) }

    @Test
    fun `keywords are detected`() {
        val src = "fun foo() = val"
        val tokens = tokens(src)
        val keywords = tokens.filter { it.kind == TokenKind.KEYWORD }
        assertEquals(2, keywords.size)
        assertEquals("fun", src.substring(keywords[0].range.first, keywords[0].range.last + 1))
        assertEquals("val", src.substring(keywords[1].range.first, keywords[1].range.last + 1))
    }

    @Test
    fun `line comment goes to end of line`() {
        val src = "val x = 1 // hello\nval y = 2"
        val comments = tokens(src).filter { it.kind == TokenKind.COMMENT }
        assertEquals(1, comments.size)
        assertEquals("// hello", src.substring(comments[0].range.first, comments[0].range.last + 1))
    }

    @Test
    fun `block comment is captured`() {
        val src = "/* a\nb */fun f(){}"
        val comments = tokens(src).filter { it.kind == TokenKind.COMMENT }
        assertEquals(1, comments.size)
        assertEquals("/* a\nb */", src.substring(comments[0].range.first, comments[0].range.last + 1))
    }

    @Test
    fun `unterminated block comment captures rest`() {
        val src = "/* never ends"
        val comments = tokens(src).filter { it.kind == TokenKind.COMMENT }
        assertEquals(1, comments.size)
        assertEquals(src, src.substring(comments[0].range.first, comments[0].range.last + 1))
    }

    @Test
    fun `double quoted string with escape`() {
        val src = """val s = "he said \"hi\""  """
        val strings = tokens(src).filter { it.kind == TokenKind.STRING }
        assertEquals(1, strings.size)
        assertEquals("\"he said \\\"hi\\\"\"", src.substring(strings[0].range.first, strings[0].range.last + 1))
    }

    @Test
    fun `triple quoted string is captured`() {
        val src = "val s = \"\"\"a\nb\"\"\" + 1"
        val strings = tokens(src).filter { it.kind == TokenKind.STRING }
        assertEquals(1, strings.size)
        assertEquals("\"\"\"a\nb\"\"\"", src.substring(strings[0].range.first, strings[0].range.last + 1))
    }

    @Test
    fun `numbers including hex and underscore`() {
        val src = "1 0xFF 1_000 3.14 2e10 5L 1.5f"
        val numbers = tokens(src).filter { it.kind == TokenKind.NUMBER }
        val texts = numbers.map { src.substring(it.range.first, it.range.last + 1) }
        assertEquals(listOf("1", "0xFF", "1_000", "3.14", "2e10", "5L", "1.5f"), texts)
    }

    @Test
    fun `annotation is detected`() {
        val src = "@Composable fun foo() {}"
        val annotations = tokens(src).filter { it.kind == TokenKind.ANNOTATION }
        assertEquals(1, annotations.size)
        assertEquals("@Composable", src.substring(annotations[0].range.first, annotations[0].range.last + 1))
    }

    @Test
    fun `capitalized identifier marked as type`() {
        val src = "val x: String = Foo()"
        val types = tokens(src).filter { it.kind == TokenKind.TYPE }
        val texts = types.map { src.substring(it.range.first, it.range.last + 1) }
        assertEquals(listOf("String", "Foo"), texts)
    }

    @Test
    fun `keyword in identifier is not a keyword`() {
        val src = "valuable funny"
        val keywords = tokens(src).filter { it.kind == TokenKind.KEYWORD }
        assertTrue(keywords.isEmpty(), "expected no keywords, got $keywords")
    }

    @Test
    fun `tokens are non overlapping and in order`() {
        val src = "fun f() { val s = \"hi\" /* c */ ; val n = 42 }"
        val toks = tokens(src)
        var prevEnd = -1
        for (t in toks) {
            assertTrue(t.range.first >= prevEnd, "token at ${t.range} overlaps prev end $prevEnd: $toks")
            prevEnd = t.range.last + 1
        }
    }

    @Test
    fun `kdoc tag emitted as annotation overlay inside comment`() {
        val src = "/** @param a foo */fun f(a: Int) {}"
        val toks = tokens(src)
        val docs = toks.filter { it.kind == TokenKind.DOC_COMMENT }
        val annotations = toks.filter { it.kind == TokenKind.ANNOTATION }
        assertEquals(1, docs.size)
        assertEquals("/** @param a foo */", src.substring(docs[0].range.first, docs[0].range.last + 1))
        assertEquals(1, annotations.size)
        assertEquals("@param", src.substring(annotations[0].range.first, annotations[0].range.last + 1))
        assertTrue(annotations[0].range.first in docs[0].range)
    }

    @Test
    fun `kdoc multiple tags`() {
        val src = """
            /**
             * Sum two ints.
             *
             * @param a first
             * @param b second
             * @return the sum
             */
            fun add(a: Int, b: Int) = a + b
        """.trimIndent()
        val annotations = tokens(src).filter { it.kind == TokenKind.ANNOTATION }
        val texts = annotations.map { src.substring(it.range.first, it.range.last + 1) }
        assertEquals(listOf("@param", "@param", "@return"), texts)
    }

    @Test
    fun `TODO with colon extends highlight to end of line`() {
        val src = "// TODO: refactor later\nval x = 1"
        val todos = tokens(src).filter { it.kind == TokenKind.TODO_TAG }
        assertEquals(1, todos.size)
        assertEquals("TODO: refactor later", src.substring(todos[0].range.first, todos[0].range.last + 1))
    }

    @Test
    fun `bare TODO without colon highlights only keyword`() {
        val src = "// TODO is enough\nval x = 1"
        val todos = tokens(src).filter { it.kind == TokenKind.TODO_TAG }
        assertEquals(1, todos.size)
        assertEquals("TODO", src.substring(todos[0].range.first, todos[0].range.last + 1))
    }

    @Test
    fun `NOTE with colon extends highlight to end of line`() {
        val src = "// NOTE: 캐시 정책 참고\nval x = 1"
        val todos = tokens(src).filter { it.kind == TokenKind.TODO_TAG }
        assertEquals(1, todos.size)
        assertEquals("NOTE: 캐시 정책 참고", src.substring(todos[0].range.first, todos[0].range.last + 1))
    }

    @Test
    fun `FIXME with colon extends highlight to end of line`() {
        val src = "// FIXME: 빈 케이스\nval x = 1"
        val todos = tokens(src).filter { it.kind == TokenKind.TODO_TAG }
        assertEquals(1, todos.size)
        assertEquals("FIXME: 빈 케이스", src.substring(todos[0].range.first, todos[0].range.last + 1))
    }

    @Test
    fun `TODO inside KDoc with colon extends within doc body`() {
        val src = "/** TODO: in doc */fun f() {}"
        val toks = tokens(src)
        val docs = toks.filter { it.kind == TokenKind.DOC_COMMENT }
        val todos = toks.filter { it.kind == TokenKind.TODO_TAG }
        assertEquals(1, docs.size)
        assertEquals(1, todos.size)
        assertEquals("TODO: in doc ", src.substring(todos[0].range.first, todos[0].range.last + 1))
        assertTrue(todos[0].range.first in docs[0].range)
    }

    @Test
    fun `TODO colon does not cross newline boundary`() {
        val src = "// TODO: one line\nval next = 2"
        val todos = tokens(src).filter { it.kind == TokenKind.TODO_TAG }
        assertEquals(1, todos.size)
        val text = src.substring(todos[0].range.first, todos[0].range.last + 1)
        assertTrue(!text.contains('\n'), "TODO span leaked past newline: '$text'")
    }

    @Test
    fun `non-kdoc block comment does not emit tags`() {
        val src = "/* @param not a tag */fun f() {}"
        val toks = tokens(src)
        assertTrue(toks.none { it.kind == TokenKind.ANNOTATION }, "got ${toks.filter { it.kind == TokenKind.ANNOTATION }}")
    }

    @Test
    fun `kdoc tag mid-text is not highlighted`() {
        val src = "/** see foo@bar.com for help */fun f() {}"
        val toks = tokens(src)
        assertTrue(toks.none { it.kind == TokenKind.ANNOTATION }, "got ${toks.filter { it.kind == TokenKind.ANNOTATION }}")
    }

    @Test
    fun `kdoc tag at very start of single-line is highlighted`() {
        val src = "/**@return x*/fun f(): Int = 0"
        val annotations = tokens(src).filter { it.kind == TokenKind.ANNOTATION }
        assertEquals(1, annotations.size)
        assertEquals("@return", src.substring(annotations[0].range.first, annotations[0].range.last + 1))
    }

    @Test
    fun `empty kdoc emits no tags`() {
        val src = "/**/fun f() {}"
        val toks = tokens(src)
        assertTrue(toks.none { it.kind == TokenKind.ANNOTATION })
    }

    @Test
    fun `empty input produces empty tokens`() {
        assertEquals(emptyList(), tokens(""))
    }

    @Test
    fun `interpolated variable splits string into STRING+IDENTIFIER+STRING`() {
        val src = "\"hi \$name!\""
        val parts = kindsAndText(src)
        assertEquals(
            listOf(
                TokenKind.STRING to "\"hi ",
                TokenKind.IDENTIFIER to "\$name",
                TokenKind.STRING to "!\"",
            ),
            parts,
        )
    }

    @Test
    fun `interpolated braced expression is one IDENTIFIER token`() {
        val src = "\"sum=\${a + b}!\""
        val parts = kindsAndText(src)
        assertEquals(
            listOf(
                TokenKind.STRING to "\"sum=",
                TokenKind.IDENTIFIER to "\${a + b}",
                TokenKind.STRING to "!\"",
            ),
            parts,
        )
    }

    @Test
    fun `nested string inside braced interpolation is handled`() {
        val src = "\"v=\${wrap(\"x\")}\""
        val ident = tokens(src).single { it.kind == TokenKind.IDENTIFIER }
        assertEquals("\${wrap(\"x\")}", src.substring(ident.range.first, ident.range.last + 1))
    }

    @Test
    fun `dollar without identifier remains plain string`() {
        val src = "\"price: \$ only\""
        val parts = kindsAndText(src)
        assertEquals(listOf(TokenKind.STRING to "\"price: \$ only\""), parts)
    }

    @Test
    fun `escaped dollar is plain string`() {
        val src = "\"raw \\\$name\""
        val identifiers = tokens(src).filter { it.kind == TokenKind.IDENTIFIER }
        assertEquals(emptyList(), identifiers)
    }

    @Test
    fun `triple-quoted interpolation splits tokens`() {
        val src = "\"\"\"a\$x b\"\"\""
        val parts = kindsAndText(src)
        assertEquals(
            listOf(
                TokenKind.STRING to "\"\"\"a",
                TokenKind.IDENTIFIER to "\$x",
                TokenKind.STRING to " b\"\"\"",
            ),
            parts,
        )
    }
}
