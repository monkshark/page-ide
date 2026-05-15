package page.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageQuickFixesTest {

    private val uri = "file:///proj/Foo.kt"

    @Test
    fun `UNUSED_VARIABLE synthesizes delete-line action`() {
        val text = "fun foo() {\n  val unused = 42\n}\n"
        val diag = diag(1, 6, 1, 12, "UNUSED_VARIABLE")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val a = actions.single()
        assertEquals("quickfix.page.unusedVariable", a.kind)
        assertTrue(a.title.contains("unused"))
        assertTrue(a.title.contains("삭제"))
        val applied = RenameApply.applyToText(text, a.edit.changes.single().edits)
        assertFalse(applied.contains("val unused"))
        assertEquals("fun foo() {\n}\n", applied)
    }

    @Test
    fun `UNUSED_PARAMETER synthesizes Suppress annotation action`() {
        val text = "fun greet(name: String, ignored: Int) = name\n"
        val diag = diag(0, 24, 0, 31, "UNUSED_PARAMETER")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val a = actions.single()
        assertEquals("quickfix.page.unusedParameter", a.kind)
        assertTrue(a.isPreferred)
        assertTrue(a.title.contains("@Suppress"))
        val applied = RenameApply.applyToText(text, a.edit.changes.single().edits)
        assertTrue(applied.startsWith("@Suppress(\"UNUSED_PARAMETER\")\nfun greet"))
    }

    @Test
    fun `UNUSED_PARAMETER Suppress preserves fun line indentation`() {
        val text = "class Foo {\n    fun greet(ignored: Int) = 1\n}\n"
        val diag = diag(1, 14, 1, 21, "UNUSED_PARAMETER")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val applied = RenameApply.applyToText(text, actions.single().edit.changes.single().edits)
        assertTrue(applied.contains("    @Suppress(\"UNUSED_PARAMETER\")\n    fun greet"))
    }

    @Test
    fun `UNUSED_PARAMETER walks back to fun on multi-line signature`() {
        val text = "fun greet(\n    name: String,\n    ignored: Int,\n): String = name\n"
        val diag = diag(2, 4, 2, 11, "UNUSED_PARAMETER")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val applied = RenameApply.applyToText(text, actions.single().edit.changes.single().edits)
        assertTrue(applied.startsWith("@Suppress(\"UNUSED_PARAMETER\")\nfun greet(\n"))
    }

    @Test
    fun `unrelated diagnostic code yields no synthesized action`() {
        val text = "fun foo() = bar\n"
        val diag = diag(0, 12, 0, 15, "UNRESOLVED_REFERENCE")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `multi-line diagnostic range is skipped`() {
        val text = "val unused =\n  42\n"
        val diag = diag(0, 4, 1, 4, "UNUSED_VARIABLE")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `function already annotated with Suppress UNUSED_PARAMETER is skipped`() {
        val text = "@Suppress(\"UNUSED_PARAMETER\")\nfun greet(ignored: Int) = 1\n"
        val diag = diag(1, 10, 1, 17, "UNUSED_PARAMETER")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `comment mentioning UNUSED_PARAMETER does not block synthesis`() {
        val text = "// 이 함수는 @Suppress(\"UNUSED_PARAMETER\") 후보\nfun greet(ignored: Int) = 1\n"
        val diag = diag(1, 10, 1, 17, "UNUSED_PARAMETER")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val applied = RenameApply.applyToText(text, actions.single().edit.changes.single().edits)
        assertTrue(applied.contains("@Suppress(\"UNUSED_PARAMETER\")\nfun greet"))
    }

    @Test
    fun `Suppress with unrelated key does not block UNUSED_PARAMETER synthesis`() {
        val text = "@Suppress(\"UNUSED_VARIABLE\")\nfun greet(ignored: Int) = 1\n"
        val diag = diag(1, 10, 1, 17, "UNUSED_PARAMETER")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
    }

    @Test
    fun `NO_ELSE_IN_WHEN synthesizes else TODO branch`() {
        val text = """
            |fun useTone(tone: Tone): String {
            |    return when (tone) {
            |        Tone.Polite -> "polite"
            |        Tone.Casual -> "casual"
            |    }
            |}
            |""".trimMargin()
        val diag = diag(1, 11, 1, 15, "NO_ELSE_IN_WHEN")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val a = actions.single()
        assertEquals("quickfix.page.noElseInWhen", a.kind)
        assertTrue(a.isPreferred)
        assertTrue(a.title.contains("else"))
        val applied = RenameApply.applyToText(text, a.edit.changes.single().edits)
        val expected = """
            |fun useTone(tone: Tone): String {
            |    return when (tone) {
            |        Tone.Polite -> "polite"
            |        Tone.Casual -> "casual"
            |        else -> TODO()
            |    }
            |}
            |""".trimMargin()
        assertEquals(expected, applied)
    }

    @Test
    fun `NO_ELSE_IN_WHEN preserves tab indentation`() {
        val text = "fun f(x: Int): Int {\n\treturn when (x) {\n\t\t1 -> 1\n\t}\n}\n"
        val diag = diag(1, 8, 1, 12, "NO_ELSE_IN_WHEN")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val applied = RenameApply.applyToText(text, actions.single().edit.changes.single().edits)
        assertTrue(applied.contains("\n\t\telse -> TODO()\n\t}"))
    }

    @Test
    fun `NO_ELSE_IN_WHEN skips when single-line block`() {
        val text = "fun f(x: Int) = when (x) { 1 -> 1 }\n"
        val diag = diag(0, 16, 0, 20, "NO_ELSE_IN_WHEN")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `NO_ELSE_IN_WHEN ignores braces inside strings`() {
        val text = """
            |fun f(x: Int): String {
            |    return when (x) {
            |        1 -> "{ noise }"
            |    }
            |}
            |""".trimMargin()
        val diag = diag(1, 11, 1, 15, "NO_ELSE_IN_WHEN")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val applied = RenameApply.applyToText(text, actions.single().edit.changes.single().edits)
        val expected = """
            |fun f(x: Int): String {
            |    return when (x) {
            |        1 -> "{ noise }"
            |        else -> TODO()
            |    }
            |}
            |""".trimMargin()
        assertEquals(expected, applied)
    }

    @Test
    fun `NO_ELSE_IN_WHEN ignores braces inside line comments`() {
        val text = """
            |fun f(x: Int): Int {
            |    return when (x) {
            |        1 -> 1 // no { fake } here
            |    }
            |}
            |""".trimMargin()
        val diag = diag(1, 11, 1, 15, "NO_ELSE_IN_WHEN")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val applied = RenameApply.applyToText(text, actions.single().edit.changes.single().edits)
        assertTrue(applied.contains("        else -> TODO()\n    }"))
    }

    @Test
    fun `multiple diagnostics produce multiple actions in order`() {
        val text = "fun greet(name: String, ignored: Int) {\n  val unused = 1\n}\n"
        val diagParam = diag(0, 24, 0, 31, "UNUSED_PARAMETER")
        val diagVar = diag(1, 6, 1, 12, "UNUSED_VARIABLE")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diagParam, diagVar))
        assertEquals(2, actions.size)
        assertEquals("quickfix.page.unusedParameter", actions[0].kind)
        assertEquals("quickfix.page.unusedVariable", actions[1].kind)
        assertTrue(actions[0].title.contains("@Suppress"))
        assertTrue(actions[1].title.contains("삭제"))
    }

    private fun diag(sl: Int, sc: Int, el: Int, ec: Int, code: String): Diagnostic =
        Diagnostic(
            start = DiagnosticPosition(sl, sc),
            end = DiagnosticPosition(el, ec),
            severity = DiagnosticSeverity.WARNING,
            message = "test",
            source = null,
            code = code,
        )
}
