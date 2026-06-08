package page.app

import page.lsp.DefinitionTarget
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefinitionTargetContainsTest {

    private fun target(
        startLine: Int,
        startCharacter: Int,
        endLine: Int,
        endCharacter: Int,
    ) = DefinitionTarget(
        uri = "file:///A.kt",
        startLine = startLine,
        startCharacter = startCharacter,
        endLine = endLine,
        endCharacter = endCharacter,
    )

    @Test
    fun `caret inside single-line target is contained`() {
        val t = target(10, 4, 10, 9)
        assertTrue(definitionTargetContains(t, 10, 4))
        assertTrue(definitionTargetContains(t, 10, 6))
        assertTrue(definitionTargetContains(t, 10, 9))
    }

    @Test
    fun `caret before or after single-line target is not contained`() {
        val t = target(10, 4, 10, 9)
        assertFalse(definitionTargetContains(t, 10, 3))
        assertFalse(definitionTargetContains(t, 10, 10))
    }

    @Test
    fun `caret on other lines is not contained`() {
        val t = target(10, 4, 10, 9)
        assertFalse(definitionTargetContains(t, 9, 6))
        assertFalse(definitionTargetContains(t, 11, 6))
    }

    @Test
    fun `multi-line target contains interior lines regardless of column`() {
        val t = target(10, 4, 12, 9)
        assertTrue(definitionTargetContains(t, 11, 0))
        assertTrue(definitionTargetContains(t, 10, 4))
        assertTrue(definitionTargetContains(t, 12, 9))
        assertFalse(definitionTargetContains(t, 10, 3))
        assertFalse(definitionTargetContains(t, 12, 10))
    }
}
