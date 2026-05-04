package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavaLexerTest {
    private fun tokens(src: String) = JavaLexer.tokenize(src)

    @Test
    fun `java keywords are detected`() {
        val src = "public class Foo extends Bar {}"
        val keywords = tokens(src).filter { it.kind == TokenKind.KEYWORD }
        val texts = keywords.map { src.substring(it.range.first, it.range.last + 1) }
        assertEquals(listOf("public", "class", "extends"), texts)
    }

    @Test
    fun `kotlin specific keywords are not java keywords`() {
        val src = "val x = 1 fun foo() {}"
        val keywords = tokens(src).filter { it.kind == TokenKind.KEYWORD }
        assertTrue(keywords.isEmpty(), "Java should not treat val/fun as keywords: $keywords")
    }

    @Test
    fun `string and number and comment`() {
        val src = "int x = 42; String s = \"hi\"; // tail"
        val byKind = tokens(src).groupBy { it.kind }
        assertTrue(byKind[TokenKind.NUMBER]?.size == 1)
        assertTrue(byKind[TokenKind.STRING]?.size == 1)
        assertTrue(byKind[TokenKind.COMMENT]?.size == 1)
    }

    @Test
    fun `annotation is detected`() {
        val src = "@Override public void f() {}"
        val ann = tokens(src).first { it.kind == TokenKind.ANNOTATION }
        assertEquals("@Override", src.substring(ann.range.first, ann.range.last + 1))
    }
}
