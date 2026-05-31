package page.lsp

import org.eclipse.lsp4j.SymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompletionProfileTest {

    private fun item(label: String, kind: CompletionItemKind = CompletionItemKind.METHOD): CompletionItem =
        CompletionItem(
            label = label,
            kind = kind,
            insertText = label,
            isSnippet = false,
        )

    private fun listOfItems(vararg items: CompletionItem): CompletionList =
        CompletionList(isIncomplete = false, items = items.toList())

    @Test
    fun `forLanguage returns kotlin profile with keywords and auto-import`() {
        val kotlin = CompletionProfile.forLanguage("kotlin")
        assertEquals("kotlin", kotlin.languageId)
        assertTrue(kotlin.supportsAutoImport)
        assertTrue(kotlin.keywords.contains("fun"))
        assertTrue(kotlin.keywords.contains("suspend"))
    }

    @Test
    fun `forLanguage returns java profile with keywords but native auto-import`() {
        val java = CompletionProfile.forLanguage("java")
        assertEquals("java", java.languageId)
        assertFalse(java.supportsAutoImport)
        assertEquals(";", java.importStatementTerminator)
        assertTrue(java.keywords.contains("class"))
        assertTrue(java.keywords.contains("implements"))
        assertTrue(java.keywords.contains("synchronized"))
    }

    @Test
    fun `kotlin profile has no import terminator`() {
        assertEquals("", CompletionProfile.forLanguage("kotlin").importStatementTerminator)
    }

    @Test
    fun `forLanguage falls back to empty profile for unknown language`() {
        val unknown = CompletionProfile.forLanguage("python")
        assertEquals("python", unknown.languageId)
        assertFalse(unknown.supportsAutoImport)
        assertTrue(unknown.keywords.isEmpty())
    }

    @Test
    fun `forLanguage handles null language id`() {
        val none = CompletionProfile.forLanguage(null)
        assertEquals("", none.languageId)
        assertFalse(none.supportsAutoImport)
        assertTrue(none.keywords.isEmpty())
    }

    @Test
    fun `augmentKeywords prepends matching keywords ignoring case`() {
        val list = listOfItems(item("function"))
        val result = CompletionAugmentor.augmentKeywords(list, "fu", KotlinKeywords.ALL)
        assertEquals("fun", result.items.first().label)
        assertEquals(CompletionItemKind.KEYWORD, result.items.first().kind)
        assertTrue(result.items.any { it.label == "function" })
    }

    @Test
    fun `augmentKeywords skips keywords already present in list`() {
        val list = listOfItems(item("fun", CompletionItemKind.KEYWORD))
        val result = CompletionAugmentor.augmentKeywords(list, "fun", KotlinKeywords.ALL)
        assertEquals(1, result.items.count { it.label == "fun" })
    }

    @Test
    fun `augmentKeywords returns original list when nothing matches`() {
        val list = listOfItems(item("foo"))
        val result = CompletionAugmentor.augmentKeywords(list, "zzz", KotlinKeywords.ALL)
        assertEquals(list, result)
    }

    @Test
    fun `augmentKeywords keeps plain keyword insert text verbatim`() {
        val list = listOfItems(item("function"))
        val result = CompletionAugmentor.augmentKeywords(list, "fu", KotlinKeywords.ALL)
        val fun_ = result.items.first { it.label == "fun" }
        assertEquals("fun", fun_.insertText)
        assertFalse(fun_.isSnippet)
    }

    @Test
    fun `augmentKeywords expands a configured keyword into a snippet`() {
        val list = listOfItems(item("recorder"))
        val result = CompletionAugmentor.augmentKeywords(list, "rec", JavaKeywords.ALL, JavaSnippets.ALL)
        val record = result.items.first { it.label == "record" }
        assertTrue(record.isSnippet)
        assertEquals("record \$1(\$2) {\n    \$0\n}", record.insertText)
    }

    @Test
    fun `augmentKeywords overrides a server-provided item for a snippet keyword`() {
        val list = listOfItems(item("record", CompletionItemKind.KEYWORD))
        val result = CompletionAugmentor.augmentKeywords(list, "record", JavaKeywords.ALL, JavaSnippets.ALL)
        assertEquals(1, result.items.count { it.label == "record" })
        val record = result.items.first { it.label == "record" }
        assertTrue(record.isSnippet)
        assertEquals("record", result.items.first().label)
    }

    @Test
    fun `record snippet expands to name and args tabstops with a final caret inside the braces`() {
        val expanded = SnippetExpander.expand(JavaSnippets.ALL.getValue("record"))
        assertEquals(2, expanded.tabstops.size)
        assertTrue(expanded.finalCaret > expanded.tabstops.last().end)
        val openBrace = expanded.text.indexOf('{')
        val closeBrace = expanded.text.lastIndexOf('}')
        assertTrue(expanded.finalCaret in (openBrace + 1) until closeBrace)
    }

    @Test
    fun `parseFileHeader records package and imports with insert after last import`() {
        val text = """
            package com.example

            import com.foo.Bar
            import com.baz.Qux

            class Sample
        """.trimIndent()
        val header = CompletionAugmentor.parseFileHeader(text)
        assertEquals("com.example", header.packageName)
        assertTrue(header.importedFqns.contains("com.foo.Bar"))
        assertTrue(header.importedFqns.contains("com.baz.Qux"))
        assertEquals(4, header.insertLine)
        assertFalse(header.insertNeedsLeadingBlankLine)
    }

    @Test
    fun `parseFileHeader inserts after package with leading blank when no imports`() {
        val text = """
            package com.example

            class Sample
        """.trimIndent()
        val header = CompletionAugmentor.parseFileHeader(text)
        assertEquals("com.example", header.packageName)
        assertTrue(header.importedFqns.isEmpty())
        assertEquals(1, header.insertLine)
        assertTrue(header.insertNeedsLeadingBlankLine)
    }

    @Test
    fun `parseFileHeader strips trailing semicolons and import aliases`() {
        val text = "package com.example;\nimport com.foo.Bar as B;\n"
        val header = CompletionAugmentor.parseFileHeader(text)
        assertEquals("com.example", header.packageName)
        assertTrue(header.importedFqns.contains("com.foo.Bar"))
    }

    @Test
    fun `buildImportCandidates produces import edit for unimported type`() {
        val header = CompletionAugmentor.parseFileHeader("package com.example\n\nclass Sample")
        val symbols = listOf(
            WorkspaceSymbolEntry("Widget", "com.ui", SymbolKind.Class),
        )
        val candidates = CompletionAugmentor.buildImportCandidates(symbols, header, "Wid", emptySet())
        assertEquals(1, candidates.size)
        val c = candidates.first()
        assertEquals("Widget", c.label)
        assertEquals("(import from com.ui)", c.detail)
        val edit = c.additionalEdits.single()
        assertTrue(edit.newText.contains("import com.ui.Widget"))
    }

    @Test
    fun `buildImportCandidates appends semicolon terminator for java imports`() {
        val header = CompletionAugmentor.parseFileHeader("package com.example;\n\nclass Sample {}")
        val symbols = listOf(
            WorkspaceSymbolEntry("Widget", "com.ui", SymbolKind.Class),
        )
        val candidates = CompletionAugmentor.buildImportCandidates(symbols, header, "Wid", emptySet(), ";")
        val edit = candidates.single().additionalEdits.single()
        assertTrue(edit.newText.contains("import com.ui.Widget;"))
    }

    @Test
    fun `buildImportCandidates skips already imported same-package and existing labels`() {
        val header = CompletionAugmentor.parseFileHeader(
            "package com.example\n\nimport com.ui.Widget\n\nclass Sample",
        )
        val symbols = listOf(
            WorkspaceSymbolEntry("Widget", "com.ui", SymbolKind.Class),
            WorkspaceSymbolEntry("Sample", "com.example", SymbolKind.Class),
            WorkspaceSymbolEntry("Helper", "com.util", SymbolKind.Class),
        )
        val candidates = CompletionAugmentor.buildImportCandidates(symbols, header, "", setOf("Helper"))
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `buildImportCandidates ignores non-type symbol kinds`() {
        val header = CompletionAugmentor.parseFileHeader("package com.example\n")
        val symbols = listOf(
            WorkspaceSymbolEntry("doThing", "com.ui", SymbolKind.Method),
            WorkspaceSymbolEntry("CONSTANT", "com.ui", SymbolKind.Constant),
        )
        val candidates = CompletionAugmentor.buildImportCandidates(symbols, header, "", emptySet())
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `buildImportCandidates caps at twenty`() {
        val header = CompletionAugmentor.parseFileHeader("package com.example\n")
        val symbols = (1..30).map { WorkspaceSymbolEntry("Type$it", "com.gen", SymbolKind.Class) }
        val candidates = CompletionAugmentor.buildImportCandidates(symbols, header, "Type", emptySet())
        assertEquals(20, candidates.size)
    }

    @Test
    fun `mergeImportItems inserts after leading keyword items`() {
        val list = listOfItems(
            item("fun", CompletionItemKind.KEYWORD),
            item("finally", CompletionItemKind.KEYWORD),
            item("foo"),
        )
        val imports = listOf(item("Widget", CompletionItemKind.CLASS))
        val merged = CompletionAugmentor.mergeImportItems(list, imports)
        assertEquals(listOf("fun", "finally", "Widget", "foo"), merged.items.map { it.label })
    }
}
