package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import page.lsp.CodeActionEntry
import page.lsp.CodeActionPreview
import page.lsp.RenameWorkspaceEdit
import page.ui.Glass

@Composable
fun CodeActionReviewPanel(
    action: CodeActionEntry,
    currentUri: String?,
    currentText: String?,
    height: Dp,
    onApply: (CodeActionEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Glass.colors
    val previews = remember(action, currentUri, currentText) {
        CodeActionPreview.build(action.edit, currentUri, currentText, contextLines = 2)
    }
    var selectedFile by remember(action) { mutableStateOf(0) }
    var excluded by remember(action) { mutableStateOf(emptySet<String>()) }
    val included = previews.filter { it.uri !in excluded }
    val editCount = included.sumOf { it.editCount }

    Surface(
        modifier = Modifier.fillMaxWidth().height(height),
        color = colors.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                title = action.title,
                editCount = editCount,
                fileCount = included.size,
                partial = excluded.isNotEmpty(),
                onApply = { onApply(action.copy(edit = filterEdit(action.edit, excluded))) },
                onDismiss = onDismiss,
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
            Row(modifier = Modifier.fillMaxSize()) {
                FileList(
                    previews = previews,
                    selected = selectedFile,
                    excluded = excluded,
                    onSelect = { selectedFile = it },
                    onToggle = { uri ->
                        excluded = if (uri in excluded) excluded - uri else excluded + uri
                    },
                )
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(colors.separator))
                Diff(preview = previews.getOrNull(selectedFile))
            }
        }
    }
}

@Composable
private fun Header(
    title: String,
    editCount: Int,
    fileCount: Int,
    partial: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Glass.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colors.text,
        )
        Text(
            text = if (fileCount == 1) "$editCount edits in 1 file" else "$editCount edits in $fileCount files",
            style = MaterialTheme.typography.labelSmall,
            color = colors.faint,
        )
        if (partial) {
            Text(
                text = "some files left out",
                style = MaterialTheme.typography.labelSmall,
                color = colors.warn,
            )
        }
        Box(modifier = Modifier.weight(1f))
        Text(
            text = "Apply",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (partial) colors.warn else colors.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(Glass.radius.xs))
                .clickable(enabled = fileCount > 0) { onApply() }
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(
            text = "Cancel · Esc",
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted,
            modifier = Modifier
                .clip(RoundedCornerShape(Glass.radius.xs))
                .clickable { onDismiss() }
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun FileList(
    previews: List<CodeActionPreview.FilePreview>,
    selected: Int,
    excluded: Set<String>,
    onSelect: (Int) -> Unit,
    onToggle: (String) -> Unit,
) {
    val colors = Glass.colors
    LazyColumn(modifier = Modifier.width(200.dp).fillMaxHeight().padding(vertical = 4.dp)) {
        items(previews.size) { index ->
            val preview = previews[index]
            val isOn = preview.uri !in excluded
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (index == selected) colors.primarySoft else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isOn) colors.primary else Color.Transparent)
                        .clickable { onToggle(preview.uri) },
                )
                Text(
                    text = preview.basename,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOn) colors.text else colors.faint,
                )
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = preview.editCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = colors.faint,
                )
            }
        }
    }
}

@Composable
private fun Diff(preview: CodeActionPreview.FilePreview?) {
    val colors = Glass.colors
    if (preview == null) return
    LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        items(preview.lines.size) { index ->
            val line = preview.lines[index]
            val (sign, tint, wash) = when (line.kind) {
                CodeActionPreview.LineKind.ADDED -> Triple("+", colors.success, colors.success.copy(alpha = 0.10f))
                CodeActionPreview.LineKind.REMOVED -> Triple("−", colors.danger, colors.danger.copy(alpha = 0.10f))
                CodeActionPreview.LineKind.OMITTED -> Triple("⋯", colors.faint, Color.Transparent)
                else -> Triple(" ", colors.muted, Color.Transparent)
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(wash).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = (line.newLineNo ?: line.oldLineNo)?.plus(1)?.toString().orEmpty().padStart(4),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = colors.faint,
                )
                Text(
                    text = sign,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = tint,
                )
                Text(
                    text = line.text.trimEnd(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = tint,
                    maxLines = 1,
                )
            }
        }
    }
}

internal fun filterEdit(edit: RenameWorkspaceEdit, excluded: Set<String>): RenameWorkspaceEdit =
    if (excluded.isEmpty()) edit
    else RenameWorkspaceEdit(edit.changes.filter { it.uri !in excluded })
