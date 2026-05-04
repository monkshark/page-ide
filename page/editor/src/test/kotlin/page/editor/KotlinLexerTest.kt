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
    fun `empty input produces empty tokens`() {
        assertEquals(emptyList(), tokens(""))
    }
}
