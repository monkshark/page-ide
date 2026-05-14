package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import page.lsp.Diagnostic
import page.lsp.DiagnosticSeverity
import page.ui.Glass
import java.net.URI
import java.nio.file.Path

data class ProblemEntry(
    val path: Path,
    val diagnostic: Diagnostic,
)

@Composable
fun ProblemsPanel(
    diagnostics: Map<String, List<Diagnostic>>,
    onJump: (Path, Int, Int) -> Unit,
    onClose: () -> Unit,
    height: Dp,
    onResizeDelta: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = flattenAndSort(diagnostics)
    val errorCount = entries.count { it.diagnostic.severity == DiagnosticSeverity.ERROR }
    val warningCount = entries.count { it.diagnostic.severity == DiagnosticSeverity.WARNING }

    Surface(
        modifier = modifier.fillMaxWidth().height(height),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            VerticalResizeHandle(onResizeDelta = onResizeDelta)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Problems",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (errorCount > 0) ProblemBadge(errorCount, "errors", Glass.colors.error)
                if (warningCount > 0) ProblemBadge(warningCount, "warnings", Glass.colors.warn)
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = "닫기",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onClose() }.padding(4.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "현재 보고된 문제가 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val groups: List<Pair<Path, List<ProblemEntry>>> = run {
                    val byPath = LinkedHashMap<Path, MutableList<ProblemEntry>>()
                    for (entry in entries) {
                        byPath.getOrPut(entry.path) { mutableListOf() }.add(entry)
                    }
                    byPath.map { (k, v) -> k to v.toList() }
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    for ((path, list) in groups) {
                        item { ProblemFileHeader(path, list) }
                        items(list) { entry -> ProblemRow(entry, onJump) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProblemFileHeader(path: Path, list: List<ProblemEntry>) {
    val errorCount = list.count { it.diagnostic.severity == DiagnosticSeverity.ERROR }
    val warningCount = list.count { it.diagnostic.severity == DiagnosticSeverity.WARNING }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = path.fileName?.toString() ?: path.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val parent = path.parent?.toString().orEmpty()
        if (parent.isNotEmpty()) {
            Text(
                text = parent,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(modifier = Modifier.weight(1f))
        if (errorCount > 0) ProblemBadge(errorCount, "errors", Glass.colors.error)
        if (warningCount > 0) ProblemBadge(warningCount, "warnings", Glass.colors.warn)
    }
}

@Composable
private fun ProblemRow(entry: ProblemEntry, onJump: (Path, Int, Int) -> Unit) {
    val color = when (entry.diagnostic.severity) {
        DiagnosticSeverity.ERROR -> Glass.colors.error
        DiagnosticSeverity.WARNING -> Glass.colors.warn
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onJump(entry.path, entry.diagnostic.start.line, entry.diagnostic.start.character)
            }
            .padding(start = 28.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = "${entry.diagnostic.start.line + 1}:${entry.diagnostic.start.character + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.diagnostic.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProblemBadge(count: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun flattenAndSort(diagnostics: Map<String, List<Diagnostic>>): List<ProblemEntry> {
    val acc = mutableListOf<ProblemEntry>()
    for ((uri, list) in diagnostics) {
        val path = runCatching { Path.of(URI(uri)) }.getOrNull() ?: continue
        for (d in list) acc.add(ProblemEntry(path, d))
    }
    acc.sortWith(
        compareBy(
            { severityOrder(it.diagnostic.severity) },
            { it.path.fileName?.toString().orEmpty() },
            { it.diagnostic.start.line },
            { it.diagnostic.start.character },
        ),
    )
    return acc
}

private fun severityOrder(s: DiagnosticSeverity): Int = when (s) {
    DiagnosticSeverity.ERROR -> 0
    DiagnosticSeverity.WARNING -> 1
    DiagnosticSeverity.INFO -> 2
    DiagnosticSeverity.HINT -> 3
}

@Composable
private fun VerticalResizeHandle(onResizeDelta: (Dp) -> Unit) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
            .hoverable(interactionSource)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dy ->
                    onResizeDelta(with(density) { (-dy).toDp() })
                }
            }
            .background(
                if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else Color.Transparent,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}
