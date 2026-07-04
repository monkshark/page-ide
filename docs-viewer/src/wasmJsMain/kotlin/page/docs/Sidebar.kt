package page.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun Sidebar(entries: List<TocEntry>, activeSlug: String?, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier.width(240.dp).fillMaxHeight()
            .background(DocsTheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 28.dp, horizontal = 18.dp),
    ) {
        Text(
            "PAGE Docs",
            color = DocsTheme.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, bottom = 18.dp),
        )
        for (e in entries) {
            val active = e.slug == activeSlug
            Text(
                e.title,
                color = if (active) DocsTheme.primary else DocsTheme.muted,
                fontSize = if (e.level == 1) 14.sp else 13.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth()
                    .clickable { onSelect(e.slug) }
                    .padding(start = (6 + (e.level - 1) * 14).dp, top = 5.dp, bottom = 5.dp, end = 6.dp),
            )
        }
    }
}
