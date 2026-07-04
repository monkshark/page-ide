package page.shared.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CodeLexersTest {
    @Test
    fun resolvesKotlinAliases() {
        assertSame(KotlinLexer, CodeLexers.forLang("kotlin"))
        assertSame(KotlinLexer, CodeLexers.forLang("kt"))
        assertSame(KotlinLexer, CodeLexers.forLang("kts"))
        assertSame(KotlinLexer, CodeLexers.forLang("Kotlin"))
    }

    @Test
    fun resolvesJavaAndJson() {
        assertSame(JavaLexer, CodeLexers.forLang("java"))
        assertSame(JsonLexer, CodeLexers.forLang("JSON"))
    }

    @Test
    fun unknownOrNullLangReturnsNull() {
        assertNull(CodeLexers.forLang("python"))
        assertNull(CodeLexers.forLang(null))
        assertNull(CodeLexers.forLang(""))
    }

    @Test
    fun kotlinLexerTokenizesKeywordAndType() {
        val tokens = KotlinLexer.tokenize("val Foo = 1")
        assertTrue(tokens.any { it.kind == TokenKind.KEYWORD })
        assertTrue(tokens.any { it.kind == TokenKind.TYPE })
        assertTrue(tokens.any { it.kind == TokenKind.NUMBER })
    }

    @Test
    fun jsonLexerTokenizesStringAndLiteral() {
        val tokens = JsonLexer.tokenize("""{"k": true, "n": 42}""")
        assertEquals(1, tokens.count { it.kind == TokenKind.KEYWORD })
        assertTrue(tokens.any { it.kind == TokenKind.STRING })
        assertTrue(tokens.any { it.kind == TokenKind.NUMBER })
    }
}
