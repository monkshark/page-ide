package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TokenShiftTest {

    private fun tokensOf(text: String, vararg words: Pair<String, TokenKind>): List<Token> =
        words.map { (word, kind) ->
            val start = text.indexOf(word)
            Token(kind, start until start + word.length)
        }

    @Test
    fun `identical text returns the same list`() {
        val text = "val x = 1"
        val tokens = tokensOf(text, "val" to TokenKind.KEYWORD)
        assertSame(tokens, TokenShift.shift(tokens, text, text))
    }

    @Test
    fun `insertion shifts tokens after the edit and keeps tokens before it`() {
        val old = "val x = name"
        val new = "val x = fullname"
        val tokens = tokensOf(old, "val" to TokenKind.KEYWORD, "name" to TokenKind.IDENTIFIER)
        val shifted = TokenShift.shift(tokens, old, new)
        assertEquals(tokensOf(new, "val" to TokenKind.KEYWORD), shifted.filter { it.kind == TokenKind.KEYWORD })
        val ident = shifted.first { it.kind == TokenKind.IDENTIFIER }
        assertEquals("name", new.substring(ident.start, ident.endExclusive))
    }

    @Test
    fun `deletion shifts following tokens back`() {
        val old = "import foo; val answer = 42"
        val new = "val answer = 42"
        val tokens = tokensOf(
            old,
            "import" to TokenKind.KEYWORD,
            "val" to TokenKind.KEYWORD,
            "42" to TokenKind.NUMBER,
        )
        val shifted = TokenShift.shift(tokens, old, new)
        val number = shifted.first { it.kind == TokenKind.NUMBER }
        assertEquals("42", new.substring(number.start, number.endExclusive))
    }

    @Test
    fun `token overlapping the edited region is dropped`() {
        val old = "val keyword = 1"
        val new = "val keyXword = 1"
        val tokens = tokensOf(old, "keyword" to TokenKind.IDENTIFIER, "1" to TokenKind.NUMBER)
        val shifted = TokenShift.shift(tokens, old, new)
        assertTrue(shifted.none { it.kind == TokenKind.IDENTIFIER })
        val number = shifted.first { it.kind == TokenKind.NUMBER }
        assertEquals("1", new.substring(number.start, number.endExclusive))
    }

    @Test
    fun `typing at the end keeps every earlier token`() {
        val old = "fun main() { print(\"hi\") }"
        val new = old + " //"
        val tokens = tokensOf(old, "fun" to TokenKind.KEYWORD, "\"hi\"" to TokenKind.STRING)
        val shifted = TokenShift.shift(tokens, old, new)
        assertEquals(tokens, shifted)
    }

    @Test
    fun `continuous typing in the middle keeps surrounding colors aligned`() {
        var text = "class A { val tail = 9 }"
        var tokens = tokensOf(text, "class" to TokenKind.KEYWORD, "9" to TokenKind.NUMBER)
        for (ch in "newField") {
            val next = text.substring(0, 10) + ch + text.substring(10)
            tokens = TokenShift.shift(tokens, text, next)
            text = next
        }
        val number = tokens.first { it.kind == TokenKind.NUMBER }
        assertEquals("9", text.substring(number.start, number.endExclusive))
        val keyword = tokens.first { it.kind == TokenKind.KEYWORD }
        assertEquals("class", text.substring(keyword.start, keyword.endExclusive))
    }

    @Test
    fun `repeated identical characters around the edit do not corrupt offsets`() {
        val old = "aaa bbb aaa"
        val new = "aaa bbbb aaa"
        val tokens = listOf(
            Token(TokenKind.IDENTIFIER, 0..2),
            Token(TokenKind.IDENTIFIER, 8..10),
        )
        val shifted = TokenShift.shift(tokens, old, new)
        assertEquals(Token(TokenKind.IDENTIFIER, 0..2), shifted.first())
        assertEquals("aaa", new.substring(shifted.last().start, shifted.last().endExclusive))
    }
}
