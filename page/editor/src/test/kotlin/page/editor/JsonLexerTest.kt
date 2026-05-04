package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonLexerTest {
    private fun tokens(src: String) = JsonLexer.tokenize(src)

    @Test
    fun `json scalars are classified`() {
        val src = """{"name": "alice", "age": 30, "active": true, "tag": null}"""
        val tokens = tokens(src)
        val byKind = tokens.groupBy { it.kind }
        assertEquals(5, byKind[TokenKind.STRING]?.size)
        assertEquals(1, byKind[TokenKind.NUMBER]?.size)
        assertEquals(2, byKind[TokenKind.KEYWORD]?.size)
    }

    @Test
    fun `negative and decimal numbers`() {
        val src = "[-1, 3.14, 1.5e2]"
        val numbers = tokens(src).filter { it.kind == TokenKind.NUMBER }
        val texts = numbers.map { src.substring(it.range.first, it.range.last + 1) }
        assertEquals(listOf("-1", "3.14", "1.5e2"), texts)
    }

    @Test
    fun `unterminated string captures rest of line`() {
        val src = "\"oops"
        val strings = tokens(src).filter { it.kind == TokenKind.STRING }
        assertEquals(1, strings.size)
    }
}
