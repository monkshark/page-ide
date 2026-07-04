package page.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.shared.md.BlockQuote
import page.shared.md.BulletList
import page.shared.md.Callout
import page.shared.md.CalloutKind
import page.shared.md.CodeBlock
import page.shared.md.CodeSpan
import page.shared.md.Document
import page.shared.md.Emphasis
import page.shared.md.Heading
import page.shared.md.Inline
import page.shared.md.Link
import page.shared.md.MdNode
import page.shared.md.Paragraph
import page.shared.md.Strikethrough
import page.shared.md.Table
import page.shared.md.TaskList
import page.shared.md.Text as MdText
import page.shared.md.WidgetRef
import page.docs.widgets.PageWidgets

@Composable
fun Article(doc: Document, onHeadingPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }) {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp).padding(horizontal = 48.dp, vertical = 56.dp),
    ) {
        for (node in doc.children) Node(node, onHeadingPositioned)
    }
}

@Composable
private fun Node(node: MdNode, onHeadingPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }) {
    when (node) {
        is Heading -> HeadingView(node, onHeadingPositioned)
        is Paragraph -> Text(
            inlineString(node.inlines),
            color = DocsTheme.text,
            fontSize = 15.sp,
            lineHeight = 25.sp,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        is CodeBlock -> CodeBlockView(node)
        is BulletList -> BulletListView(node)
        is TaskList -> Column(modifier = Modifier.padding(bottom = 14.dp)) {
            for (item in node.items) Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(if (item.checked) "☑  " else "☐  ", color = DocsTheme.accent, fontSize = 15.sp)
                Text(inlineString(item.inlines), color = DocsTheme.text, fontSize = 15.sp, lineHeight = 25.sp)
            }
        }
        is Table -> TableView(node)
        is Callout -> CalloutView(node)
        is BlockQuote -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                .border(0.dp, DocsTheme.outline)
                .background(DocsTheme.surface, RoundedCornerShape(10.dp))
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 14.dp),
        ) { Column { for (c in node.children) Node(c) } }
        is WidgetRef -> if (PageWidgets.has(node.name)) {
            PageWidgets.Render(node)
        } else Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                .border(1.dp, DocsTheme.outline, RoundedCornerShape(12.dp))
                .background(DocsTheme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) { Text("⟨ widget: ${node.name} ⟩", color = DocsTheme.muted, fontSize = 13.sp) }
        is Document -> for (c in node.children) Node(c, onHeadingPositioned)
    }
}

@Composable
private fun HeadingView(h: Heading, onHeadingPositioned: (String, LayoutCoordinates) -> Unit) {
    val size = when (h.level) {
        1 -> 32.sp
        2 -> 23.sp
        3 -> 18.sp
        else -> 16.sp
    }
    val top = if (h.level <= 1) 4.dp else 28.dp
    Text(
        inlineString(h.text),
        color = DocsTheme.text,
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        lineHeight = size * 1.3f,
        modifier = Modifier.onGloballyPositioned { onHeadingPositioned(h.slug, it) }
            .padding(top = top, bottom = 10.dp),
    )
}

@Composable
private fun CodeBlockView(c: CodeBlock) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DocsTheme.outline, RoundedCornerShape(12.dp))
            .background(DocsTheme.surface),
    ) {
        val lang = c.lang
        if (lang != null) Text(
            lang.uppercase(),
            color = DocsTheme.muted,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().background(DocsTheme.background).padding(horizontal = 14.dp, vertical = 7.dp),
        )
        Text(
            c.code,
            color = DocsTheme.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
        )
    }
}

@Composable
private fun BulletListView(list: BulletList) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        list.items.forEachIndexed { idx, item ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    if (list.ordered) "${idx + 1}.  " else "•  ",
                    color = DocsTheme.muted,
                    fontSize = 15.sp,
                )
                Column {
                    for (n in item) {
                        if (n is Paragraph) Text(inlineString(n.inlines), color = DocsTheme.text, fontSize = 15.sp, lineHeight = 25.sp)
                        else Node(n)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableView(t: Table) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, DocsTheme.outline, RoundedCornerShape(10.dp)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().background(DocsTheme.background)) {
            for (cell in t.header) Text(
                inlineString(cell),
                color = DocsTheme.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(10.dp),
            )
        }
        for (row in t.rows) Row(modifier = Modifier.fillMaxWidth()) {
            for (cell in row) Text(
                inlineString(cell),
                color = DocsTheme.muted,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f).padding(10.dp),
            )
        }
    }
}

@Composable
private fun CalloutView(callout: Callout) {
    val edge = when (callout.kind) {
        CalloutKind.WARNING -> DocsTheme.warn
        CalloutKind.DANGER -> DocsTheme.danger
        CalloutKind.TIP -> DocsTheme.success
        else -> DocsTheme.primary
    }
    val icon = when (callout.kind) {
        CalloutKind.WARNING, CalloutKind.DANGER -> "▲"
        else -> "◆"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DocsTheme.outline, RoundedCornerShape(12.dp))
            .background(DocsTheme.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(icon, color = edge, fontSize = 15.sp)
        Column { for (c in callout.children) Node(c) }
    }
}

private fun inlineString(inlines: List<Inline>): AnnotatedString = buildAnnotatedString {
    fun render(list: List<Inline>) {
        for (n in list) when (n) {
            is MdText -> append(n.value)
            is CodeSpan -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = DocsTheme.accent, fontSize = 13.sp)) { append(n.value) }
            is Link -> withStyle(SpanStyle(color = DocsTheme.primary)) {
                pushStringAnnotation("URL", n.href); append(n.text); pop()
            }
            is Emphasis -> withStyle(
                SpanStyle(
                    fontWeight = if (n.strong) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (n.strong) FontStyle.Normal else FontStyle.Italic,
                ),
            ) { render(n.inlines) }
            is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { render(n.inlines) }
        }
    }
    render(inlines)
}
