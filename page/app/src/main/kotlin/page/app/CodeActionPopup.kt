package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import page.lsp.CodeActionEntry
import page.lsp.CodeActionPreview
import page.ui.Glass
import page.ui.GlassPopup

private const val MAX_DIFF_LINES = 6

@Composable
fun CodeActionPopup(
    actions: List<CodeActionEntry>,
    selected: Int,
    pending: Boolean,
    currentUri: String?,
    currentText: String?,
    onSelectedChange: (Int) -> Unit,
    onApply: (CodeActionEntry) -> Unit,
    onOpenInPanel: () -> Unit,
) {
    val colors = Glass.colors
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        if (selected in actions.indices) listState.animateScrollToItem(selected)
    }
    val current = actions.getOrNull(selected)
    val previews = remember(current, currentUri, currentText) {
        current?.let { CodeActionPreview.build(it.edit, currentUri, currentText, contextLines = 1) }.orEmpty()
    }
    val spansOtherFiles = previews.count { !it.isCurrent } > 0

    GlassPopup(modifier = Modifier.width(380.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Code actions",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                )
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = if (pending) "searching…" else "${actions.size} available",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pending) colors.warn else colors.faint,
                )
            }
            Divider()
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 168.dp)) {
                items(actions.size) { index ->
                    val action = actions[index]
                    ActionRow(
                        action = action,
                        selected = index == selected,
                        onClick = {
                            if (index == selected) onApply(action) else onSelectedChange(index)
                        },
                    )
                }
            }
            if (previews.isNotEmpty()) {
                Divider()
                DiffPreview(previews)
            }
            Divider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (hint in listOf("↑↓ choose", "Enter apply", "Esc close")) {
                    Text(text = hint, style = MaterialTheme.typography.labelSmall, color = colors.faint)
                }
                if (spansOtherFiles) {
                    Box(modifier = Modifier.weight(1f))
                    Text(
                        text = "Open in panel",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                        modifier = Modifier.clickable { onOpenInPanel() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(action: CodeActionEntry, selected: Boolean, onClick: () -> Unit) {
    val colors = Glass.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(Glass.radius.xs))
            .background(if (selected) colors.primarySoft else Color.Transparent)
            .clickable { onClick() }
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = action.title,
            style = MaterialTheme.typography.labelSmall,
            color = if (action.isExecutable) colors.text else colors.faint,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(modifier = Modifier.weight(1f))
        val kind = kindLabel(action.kind)
        if (kind != null) {
            Text(
                text = kind,
                style = MaterialTheme.typography.labelSmall,
                color = colors.faint,
            )
        }
    }
}

@Composable
private fun DiffPreview(previews: List<CodeActionPreview.FilePreview>) {
    val colors = Glass.colors
    val lines = previews.flatMap { preview -> preview.lines.map { preview to it } }
    val shown = lines.take(MAX_DIFF_LINES)
    val hidden = lines.size - shown.size
    Column(modifier = Modifier.fillMaxWidth().background(colors.background.copy(alpha = 0.5f))) {
        if (previews.size > 1 || previews.any { !it.isCurrent }) {
            Text(
                text = previews.joinToString(" · ") { "${it.basename} (${it.editCount})" },
                style = MaterialTheme.typography.labelSmall,
                color = colors.faint,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
            )
        }
        for ((_, line) in shown) {
            val (sign, tint, wash) = when (line.kind) {
                CodeActionPreview.LineKind.ADDED -> Triple("+", colors.success, colors.success.copy(alpha = 0.10f))
                CodeActionPreview.LineKind.REMOVED -> Triple("−", colors.danger, colors.danger.copy(alpha = 0.10f))
                CodeActionPreview.LineKind.OMITTED -> Triple("⋯", colors.faint, Color.Transparent)
                else -> Triple(" ", colors.faint, Color.Transparent)
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(wash).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = sign,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = tint,
                )
                Text(
                    text = line.text.trimEnd().take(120),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = tint,
                    maxLines = 1,
                )
            }
        }
        if (hidden > 0) {
            Text(
                text = "+$hidden more lines",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = colors.faint,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Glass.colors.separator))
}

private fun kindLabel(kind: String?): String? = when {
    kind == null -> null
    kind.startsWith("quickfix") -> "quick fix"
    kind.startsWith("refactor") -> "refactor"
    kind.startsWith("source") -> "source"
    else -> null
}
