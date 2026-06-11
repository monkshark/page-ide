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
        val a = actions.first { it.kind == "quickfix.page.unusedVariable" }
        assertTrue(a.title.contains("unused"))
        assertTrue(a.title.contains("Delete"))
        val applied = RenameApply.applyToText(text, a.edit.changes.single().edits)
        assertFalse(applied.contains("val unused"))
        assertEquals("fun foo() {\n}\n", applied)
    }

    @Test
    fun `UNUSED_PARAMETER synthesizes Suppress annotation action`() {
        val text = "fun greet(name: String, ignored: Int) = name\n"
        val diag = diag(0, 24, 0, 31, "UNUSED_PARAMETER")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        val a = actions.first { it.kind == "quickfix.page.unusedParameter" }
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
        val a = actions.first { it.kind == "quickfix.page.unusedParameter" }
        val applied = RenameApply.applyToText(text, a.edit.changes.single().edits)
        assertTrue(applied.contains("    @Suppress(\"UNUSED_PARAMETER\")\n    fun greet"))
    }

    @Test
    fun `UNUSED_PARAMETER walks back to fun on multi-line signature`() {
        val text = "fun greet(\n    name: String,\n    ignored: Int,\n): String = name\n"
        val diag = diag(2, 4, 2, 11, "UNUSED_PARAMETER")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        val a = actions.first { it.kind == "quickfix.page.unusedParameter" }
        val applied = RenameApply.applyToText(text, a.edit.changes.single().edits)
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
    fun `function already annotated with Suppress UNUSED_PARAMETER skips Suppress action`() {
        val text = "@Suppress(\"UNUSED_PARAMETER\")\nfun greet(ignored: Int) = 1\n"
        val diag = diag(1, 10, 1, 17, "UNUSED_PARAMETER")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertTrue(actions.none { it.kind == "quickfix.page.unusedParameter" })
        assertTrue(actions.any { it.kind == "quickfix.page.unusedParameter.renameUnderscore" })
    }

    @Test
    fun `comment mentioning UNUSED_PARAMETER does not block synthesis`() {
        val text = "// 이 함수는 @Suppress(\"UNUSED_PARAMETER\") 후보\nfun greet(ignored: Int) = 1\n"
        val diag = diag(1, 10, 1, 17, "UNUSED_PARAMETER")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        val suppress = actions.first { it.kind == "quickfix.page.unusedParameter" }
        val applied = RenameApply.applyToText(text, suppress.edit.changes.single().edits)
        assertTrue(applied.contains("@Suppress(\"UNUSED_PARAMETER\")\nfun greet"))
    }

    @Test
    fun `Suppress with unrelated key does not block UNUSED_PARAMETER synthesis`() {
        val text = "@Suppress(\"UNUSED_VARIABLE\")\nfun greet(ignored: Int) = 1\n"
        val diag = diag(1, 10, 1, 17, "UNUSED_PARAMETER")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertTrue(actions.any { it.kind == "quickfix.page.unusedParameter" })
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
    fun `UNUSED_VARIABLE also offers underscore-prefix action`() {
        val text = "fun foo() {\n  val unused = 42\n}\n"
        val diag = diag(1, 6, 1, 12, "UNUSED_VARIABLE")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(2, actions.size)
        val rename = actions.first { it.kind == "quickfix.page.unusedVariable.renameUnderscore" }
        assertFalse(rename.isPreferred)
        assertTrue(rename.title.contains("'_unused'"))
        val applied = RenameApply.applyToText(text, rename.edit.changes.single().edits)
        assertEquals("fun foo() {\n  val _unused = 42\n}\n", applied)
    }

    @Test
    fun `UNUSED_PARAMETER also offers underscore-prefix action`() {
        val text = "fun greet(name: String, ignored: Int) = name\n"
        val diag = diag(0, 24, 0, 31, "UNUSED_PARAMETER")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(2, actions.size)
        val rename = actions.first { it.kind == "quickfix.page.unusedParameter.renameUnderscore" }
        val applied = RenameApply.applyToText(text, rename.edit.changes.single().edits)
        assertEquals("fun greet(name: String, _ignored: Int) = name\n", applied)
    }

    @Test
    fun `underscore-prefix skipped when identifier already starts with underscore`() {
        val text = "fun foo() {\n  val _unused = 42\n}\n"
        val diag = diag(1, 6, 1, 13, "UNUSED_VARIABLE")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertTrue(actions.none { it.kind == "quickfix.page.unusedVariable.renameUnderscore" })
    }

    @Test
    fun `UNNECESSARY_NOT_NULL_ASSERTION removes the trailing !!`() {
        val text = "fun f(x: Int): Int = x!!\n"
        val diag = diag(0, 22, 0, 24, "UNNECESSARY_NOT_NULL_ASSERTION")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val a = actions.single()
        assertEquals("quickfix.page.unnecessaryNotNullAssertion", a.kind)
        assertTrue(a.isPreferred)
        val applied = RenameApply.applyToText(text, a.edit.changes.single().edits)
        assertEquals("fun f(x: Int): Int = x\n", applied)
    }

    @Test
    fun `UNNECESSARY_NOT_NULL_ASSERTION handles wider range covering expr`() {
        val text = "val y = obj.value!!\n"
        val diag = diag(0, 8, 0, 19, "UNNECESSARY_NOT_NULL_ASSERTION")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val applied = RenameApply.applyToText(text, actions.single().edit.changes.single().edits)
        assertEquals("val y = obj.value\n", applied)
    }

    @Test
    fun `UNNECESSARY_SAFE_CALL replaces question-dot with dot`() {
        val text = "val n = obj?.name\n"
        val diag = diag(0, 11, 0, 13, "UNNECESSARY_SAFE_CALL")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val a = actions.single()
        assertEquals("quickfix.page.unnecessarySafeCall", a.kind)
        val applied = RenameApply.applyToText(text, a.edit.changes.single().edits)
        assertEquals("val n = obj.name\n", applied)
    }

    @Test
    fun `WRONG_LONG_SUFFIX capitalizes trailing l`() {
        val text = "val big = 42l\n"
        val diag = diag(0, 10, 0, 13, "WRONG_LONG_SUFFIX")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val a = actions.single()
        assertEquals("quickfix.page.wrongLongSuffix", a.kind)
        val applied = RenameApply.applyToText(text, a.edit.changes.single().edits)
        assertEquals("val big = 42L\n", applied)
    }

    @Test
    fun `WRONG_LONG_SUFFIX skipped when range does not end with l`() {
        val text = "val big = 42L\n"
        val diag = diag(0, 10, 0, 13, "WRONG_LONG_SUFFIX")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `USELESS_CAST removes the as Type tail`() {
        val text = "val s: String = obj as String\n"
        val diag = diag(0, 16, 0, 29, "USELESS_CAST")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val a = actions.single()
        assertEquals("quickfix.page.uselessCast", a.kind)
        val applied = RenameApply.applyToText(text, a.edit.changes.single().edits)
        assertEquals("val s: String = obj\n", applied)
    }

    @Test
    fun `USELESS_CAST ignores as inside string literal`() {
        val text = "val s = \" as Int\" as String\n"
        val diag = diag(0, 8, 0, 27, "USELESS_CAST")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val applied = RenameApply.applyToText(text, actions.single().edit.changes.single().edits)
        assertEquals("val s = \" as Int\"\n", applied)
    }

    @Test
    fun `REDUNDANT_ELSE_IN_WHEN deletes the else line`() {
        val text = """
            |fun f(b: Boolean): Int {
            |    return when (b) {
            |        true -> 1
            |        false -> 0
            |        else -> -1
            |    }
            |}
            |""".trimMargin()
        val diag = diag(4, 8, 4, 18, "REDUNDANT_ELSE_IN_WHEN")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val a = actions.single()
        assertEquals("quickfix.page.redundantElseInWhen", a.kind)
        val applied = RenameApply.applyToText(text, a.edit.changes.single().edits)
        val expected = """
            |fun f(b: Boolean): Int {
            |    return when (b) {
            |        true -> 1
            |        false -> 0
            |    }
            |}
            |""".trimMargin()
        assertEquals(expected, applied)
    }

    @Test
    fun `REDUNDANT_ELSE_IN_WHEN deletes whole line even when range covers only the else keyword`() {
        val text = """
            |fun f(b: Boolean): Int {
            |    return when (b) {
            |        true -> 1
            |        false -> 0
            |        else -> -1
            |    }
            |}
            |""".trimMargin()
        val diag = diag(4, 8, 4, 12, "REDUNDANT_ELSE_IN_WHEN")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val applied = RenameApply.applyToText(text, actions.single().edit.changes.single().edits)
        val expected = """
            |fun f(b: Boolean): Int {
            |    return when (b) {
            |        true -> 1
            |        false -> 0
            |    }
            |}
            |""".trimMargin()
        assertEquals(expected, applied)
    }

    @Test
    fun `USELESS_ELVIS removes the elvis tail`() {
        val text = "fun f(n: Int): Int {\n    return n ?: 0\n}\n"
        val diag = diag(1, 11, 1, 17, "USELESS_ELVIS")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val a = actions.single()
        assertEquals("quickfix.page.uselessElvis", a.kind)
        val applied = RenameApply.applyToText(text, a.edit.changes.single().edits)
        assertEquals("fun f(n: Int): Int {\n    return n\n}\n", applied)
    }

    @Test
    fun `USELESS_ELVIS strips whitespace before the question-colon`() {
        val text = "val x = a   ?:   fallback()\n"
        val diag = diag(0, 8, 0, 27, "USELESS_ELVIS")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val applied = RenameApply.applyToText(text, actions.single().edit.changes.single().edits)
        assertEquals("val x = a\n", applied)
    }

    @Test
    fun `USELESS_ELVIS targets the last operator when chained`() {
        val text = "val x = a ?: b ?: c\n"
        val diag = diag(0, 8, 0, 19, "USELESS_ELVIS")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diag))
        assertEquals(1, actions.size)
        val applied = RenameApply.applyToText(text, actions.single().edit.changes.single().edits)
        assertEquals("val x = a ?: b\n", applied)
    }

    @Test
    fun `multiple diagnostics produce multiple actions in order`() {
        val text = "fun greet(name: String, ignored: Int) {\n  val unused = 1\n}\n"
        val diagParam = diag(0, 24, 0, 31, "UNUSED_PARAMETER")
        val diagVar = diag(1, 6, 1, 12, "UNUSED_VARIABLE")
        val actions = PageQuickFixes.synthesize(uri, text, listOf(diagParam, diagVar))
        val kinds = actions.map { it.kind }
        assertEquals(
            listOf(
                "quickfix.page.unusedParameter",
                "quickfix.page.unusedParameter.renameUnderscore",
                "quickfix.page.unusedVariable",
                "quickfix.page.unusedVariable.renameUnderscore",
            ),
            kinds,
        )
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
