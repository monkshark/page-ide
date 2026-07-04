package page.docs

import page.shared.md.CodeSpan
import page.shared.md.Document
import page.shared.md.Emphasis
import page.shared.md.Heading
import page.shared.md.Inline
import page.shared.md.Link
import page.shared.md.Strikethrough
import page.shared.md.Text as MdText

data class TocEntry(val slug: String, val title: String, val level: Int)

fun tocEntries(doc: Document): List<TocEntry> =
    doc.children.filterIsInstance<Heading>()
        .filter { it.level in 1..3 }
        .map { TocEntry(it.slug, plainText(it.text), it.level) }

private fun plainText(inlines: List<Inline>): String = buildString {
    fun walk(list: List<Inline>) {
        for (n in list) when (n) {
            is MdText -> append(n.value)
            is CodeSpan -> append(n.value)
            is Link -> append(n.text)
            is Emphasis -> walk(n.inlines)
            is Strikethrough -> walk(n.inlines)
        }
    }
    walk(inlines)
}
