package page.atlas.render

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.toNioPath
import page.ui.EditorFontFamily

@Composable
internal fun RelationshipRow(
    edge: GraphEdge,
    nodes: Map<String, GraphNode>,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val source = nodes[edge.from]?.label ?: edge.from
    val target = nodes[edge.to]?.label ?: edge.to
    Column(modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
        .background(if (selected) colors.primary.copy(alpha = .08f) else Color.Transparent)
        .semantics {
            this.selected = selected
            contentDescription = "Inspect $source ${edge.kind.explorationLabel()} $target"
        }.clickable(role = Role.Button, onClick = onSelect).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("→", color = if (selected) colors.primary else colors.onSurfaceVariant, fontSize = 16.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("$source → $target", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${edge.kind.explorationLabel()} · ${edge.evidence?.let { "source line ${it.line + 1}" } ?: "location unavailable"}",
                fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Text(if (selected) "−" else "+", color = colors.onSurfaceVariant)
    }
    Divider()
    }
}

@Composable
internal fun RelationshipEvidence(
    edge: GraphEdge,
    source: GraphNode?,
    onOpen: (Path) -> Unit,
    onOpenLocation: (Path, Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val evidence = edge.evidence
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (evidence != null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(colors.background)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Source preview", fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("L${evidence.line + 1}", fontSize = 11.sp, color = colors.primary)
                }
                Divider()
                SelectionContainer {
                    Text(evidence.text.take(2400), fontFamily = EditorFontFamily, fontSize = 12.sp, lineHeight = 19.sp,
                        modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
            }
            if (evidence.text.length > 2400) Text("Source excerpt shortened. Open the file to read more.", fontSize = 11.sp)
            source?.path?.let { path ->
                Text(path.toString(), fontSize = 11.sp, color = colors.onSurfaceVariant)
                ExploreAction("Open source · line ${evidence.line + 1}  ↗") {
                    onOpenLocation(path.toNioPath(), evidence.line)
                }
            }
        } else {
            Text("This relationship has no source location in the current analysis.", fontSize = 11.sp,
                color = colors.onSurfaceVariant)
            source?.path?.let { path -> ExploreAction("Open source file") { onOpen(path.toNioPath()) } }
        }
    }
}
