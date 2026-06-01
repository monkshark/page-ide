package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeSitterCommentTest {

    private fun comments(lexer: SyntaxLexer, src: String): List<String> =
        lexer.tokenize(src)
            .filter { it.kind == TokenKind.COMMENT || it.kind == TokenKind.DOC_COMMENT }
            .map { src.substring(it.range.first, it.range.last + 1) }

    @Test
    fun `python trailing comment after valid code is one comment token`() {
        val src = "data.append(6)  # 예상: append(), extend()\n"
        val comments = comments(TreeSitterLexers.python, src)
        assertEquals(listOf("# 예상: append(), extend()"), comments)
    }

    @Test
    fun `python trailing comment with multibyte text keeps exact range`() {
        val src = "x = 1  # 한글주석\n"
        val tokens = TreeSitterLexers.python.tokenize(src)
        val comment = tokens.single { it.kind == TokenKind.COMMENT }
        assertEquals("# 한글주석", src.substring(comment.range.first, comment.range.last + 1))
    }

    @Test
    fun `go trailing comment after valid code is one comment token`() {
        val src = "package main\nfunc main() {\n\tx := 1 // 한글 주석 테스트\n\t_ = x\n}\n"
        val comments = comments(TreeSitterLexers.go, src)
        assertEquals(listOf("// 한글 주석 테스트"), comments)
    }

    @Test
    fun `python trailing comment after a dangling dot still highlights as comment`() {
        val src = "data.  # 예상: append()\n"
        val comments = comments(TreeSitterLexers.python, src)
        assertTrue(
            comments.any { it.startsWith("#") && it.contains("예상") },
            "trailing comment must still be classified even with a syntax error on the line: $comments",
        )
    }
}
