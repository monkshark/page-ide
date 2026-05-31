package page.app

import page.lsp.CompletionItem
import page.lsp.CompletionItemKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompletionSortTest {

    private fun item(
        label: String,
        sortText: String = label,
        kind: CompletionItemKind = CompletionItemKind.CLASS,
    ): CompletionItem = CompletionItem(
        label = label,
        kind = kind,
        insertText = label,
        isSnippet = false,
        sortText = sortText,
    )

    private fun rank(prefix: String, items: List<CompletionItem>): List<String> =
        items.filter { fuzzyMatch(prefix, it.filterText.takeIf { f -> f.isNotBlank() } ?: it.label) }
            .sortedWith(
                compareByDescending<CompletionItem> { fuzzyScore(prefix, it.filterText.takeIf { f -> f.isNotBlank() } ?: it.label) }
                    .thenByDescending { it.kind == CompletionItemKind.KEYWORD }
                    .thenBy { it.sortText }
                    .thenBy { (it.filterText.takeIf { f -> f.isNotBlank() } ?: it.label).length },
            )
            .map { it.label }

    @Test
    fun `exact-case prefix outranks case-differing prefix even when longer`() {
        assertTrue(
            fuzzyScore("sta", "static") > fuzzyScore("sta", "State"),
            "lowercase 'static' must beat capitalized 'State' for lowercase query 'sta'",
        )
    }

    @Test
    fun `typing lowercase sta puts static keyword above State type`() {
        val ranked = rank(
            "sta",
            listOf(
                item("State"),
                item("static", kind = CompletionItemKind.KEYWORD),
                item("Statement"),
            ),
        )
        assertEquals("static", ranked.first())
    }

    @Test
    fun `static keyword outranks standard symbol for lowercase sta`() {
        val ranked = rank(
            "sta",
            listOf(
                item("standard"),
                item("static", kind = CompletionItemKind.KEYWORD),
            ),
        )
        assertEquals("static", ranked.first())
    }

    @Test
    fun `typing capitalized Sta prefers the type`() {
        val ranked = rank(
            "Sta",
            listOf(
                item("static", kind = CompletionItemKind.KEYWORD),
                item("State"),
            ),
        )
        assertEquals("State", ranked.first())
    }

    @Test
    fun `same-tier matches break ties by server sortText then length`() {
        val ranked = rank(
            "co",
            listOf(
                item("convert", sortText = "200"),
                item("count", sortText = "100"),
                item("connect", sortText = "100"),
            ),
        )
        assertEquals(listOf("count", "connect", "convert"), ranked)
    }

    @Test
    fun `keyword insert gets a single trailing space`() {
        assertEquals("static ", keywordInsertText(item("static", kind = CompletionItemKind.KEYWORD)))
    }

    @Test
    fun `keyword insert does not double an existing trailing space`() {
        val withSpace = CompletionItem(
            label = "static",
            kind = CompletionItemKind.KEYWORD,
            insertText = "static ",
            isSnippet = false,
        )
        assertEquals("static ", keywordInsertText(withSpace))
    }

    @Test
    fun `snippet-flagged plain keyword still gets a trailing space`() {
        val flagged = CompletionItem(
            label = "public",
            kind = CompletionItemKind.KEYWORD,
            insertText = "public",
            isSnippet = true,
        )
        assertEquals("public ", keywordInsertText(flagged))
    }

    @Test
    fun `non-keyword insert is left untouched`() {
        assertEquals("State", keywordInsertText(item("State", kind = CompletionItemKind.CLASS)))
    }

    @Test
    fun `snippet keyword insert is left untouched`() {
        val snippet = CompletionItem(
            label = "record",
            kind = CompletionItemKind.KEYWORD,
            insertText = "record \$1 {\n}",
            isSnippet = true,
        )
        assertEquals("record \$1 {\n}", keywordInsertText(snippet))
    }

    @Test
    fun `prefix matches always rank above subsequence matches`() {
        val ranked = rank(
            "sta",
            listOf(
                item("setTextarea"),
                item("start"),
            ),
        )
        assertEquals("start", ranked.first())
    }
}
