package page.shared.md

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MdParserTest {

    private fun nodes(src: String): List<MdNode> = MdParser.parse(src).children

    @Test
    fun heading_level_and_slug() {
        val h = nodes("## Getting Started").single()
        assertIs<Heading>(h)
        assertEquals(2, h.level)
        assertEquals("getting-started", h.slug)
        assertEquals(listOf(Text("Getting Started")), h.text)
    }

    @Test
    fun duplicate_heading_slugs_get_suffix() {
        val hs = nodes("# Setup\n\n# Setup\n\n# Setup").filterIsInstance<Heading>()
        assertEquals(listOf("setup", "setup-2", "setup-3"), hs.map { it.slug })
    }

    @Test
    fun paragraph_inline_variants() {
        val p = nodes("plain **bold** and *italic* and `code` and [d](https://x) end").single()
        assertIs<Paragraph>(p)
        assertEquals(
            listOf(
                Text("plain "),
                Emphasis(listOf(Text("bold")), strong = true),
                Text(" and "),
                Emphasis(listOf(Text("italic")), strong = false),
                Text(" and "),
                CodeSpan("code"),
                Text(" and "),
                Link("d", "https://x"),
                Text(" end"),
            ),
            p.inlines,
        )
    }

    @Test
    fun strikethrough_inline() {
        val p = nodes("a ~~gone~~ b").single()
        assertIs<Paragraph>(p)
        assertEquals(Strikethrough(listOf(Text("gone"))), p.inlines[1])
    }

    @Test
    fun snake_case_is_not_emphasis() {
        val p = nodes("call foo_bar_baz now").single()
        assertIs<Paragraph>(p)
        assertEquals(listOf(Text("call foo_bar_baz now")), p.inlines)
    }

    @Test
    fun code_fence_lang_and_body() {
        val c = nodes("```kotlin\nval x = 1\nfun y() {}\n```").single()
        assertIs<CodeBlock>(c)
        assertEquals("kotlin", c.lang)
        assertEquals("val x = 1\nfun y() {}", c.code)
    }

    @Test
    fun code_fence_without_lang() {
        val c = nodes("```\nraw\n```").single()
        assertIs<CodeBlock>(c)
        assertEquals(null, c.lang)
        assertEquals("raw", c.code)
    }

    @Test
    fun bullet_and_ordered_lists() {
        val b = nodes("- one\n- two").single()
        assertIs<BulletList>(b)
        assertFalse(b.ordered)
        assertEquals(2, b.items.size)
        assertEquals(Paragraph(listOf(Text("one"))), b.items[0].single())

        val o = nodes("1. a\n2. b").single()
        assertIs<BulletList>(o)
        assertTrue(o.ordered)
        assertEquals(2, o.items.size)
    }

    @Test
    fun task_list() {
        val tl = nodes("- [ ] todo\n- [x] done").single()
        assertIs<TaskList>(tl)
        assertEquals(TaskItem(false, listOf(Text("todo"))), tl.items[0])
        assertEquals(TaskItem(true, listOf(Text("done"))), tl.items[1])
    }

    @Test
    fun table_header_and_rows() {
        val t = nodes("| A | B |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |").single()
        assertIs<Table>(t)
        assertEquals(listOf(listOf(Text("A")), listOf(Text("B"))), t.header)
        assertEquals(2, t.rows.size)
        assertEquals(listOf(listOf(Text("1")), listOf(Text("2"))), t.rows[0])
    }

    @Test
    fun callout_kind_and_nested_markdown() {
        val warn = nodes(":::warning\nBe **careful** here\n:::").single()
        assertIs<Callout>(warn)
        assertEquals(CalloutKind.WARNING, warn.kind)
        val inner = warn.children.single()
        assertIs<Paragraph>(inner)
        assertEquals(Emphasis(listOf(Text("careful")), strong = true), inner.inlines[1])

        val info = nodes(":::info\nhi\n:::").single()
        assertIs<Callout>(info)
        assertEquals(CalloutKind.INFO, info.kind)
    }

    @Test
    fun blockquote() {
        val q = nodes("> quoted line").single()
        assertIs<BlockQuote>(q)
        assertEquals(Paragraph(listOf(Text("quoted line"))), q.children.single())
    }

    @Test
    fun widget_ref_with_args() {
        val w = nodes("@Render(AtlasDemo, id=page:atlas, depth=2)").single()
        assertIs<WidgetRef>(w)
        assertEquals("AtlasDemo", w.name)
        assertEquals(mapOf("id" to "page:atlas", "depth" to "2"), w.args)
    }

    @Test
    fun mixed_document_blocks_in_order() {
        val src = """
            # Title

            Intro paragraph.

            ## Section

            - a
            - b

            ```kotlin
            fun f() {}
            ```

            :::info
            done
            :::
        """.trimIndent()
        val ns = nodes(src)
        assertIs<Heading>(ns[0])
        assertIs<Paragraph>(ns[1])
        assertIs<Heading>(ns[2])
        assertIs<BulletList>(ns[3])
        assertIs<CodeBlock>(ns[4])
        assertIs<Callout>(ns[5])
    }
}
