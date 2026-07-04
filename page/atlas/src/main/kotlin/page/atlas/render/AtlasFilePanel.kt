package page.atlas.render

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import page.atlas.graph.GraphInsights
import page.atlas.graph.GraphSlice
import page.atlas.graph.Neighbor
import page.atlas.toNioPath

private const val NEIGHBOR_LIMIT = 12

@Composable
fun AtlasFilePanel(
    slice: GraphSlice,
    fileId: String,
    width: Dp,
    onClose: () -> Unit,
    onOpenFile: (String, FilePath) -> Unit,
    modifier: Modifier = Modifier,
) {
    val neighborhood = remember(slice, fileId) { GraphInsights.neighborhood(slice, fileId, limit = NEIGHBOR_LIMIT) }
    val focus = neighborhood.focus
    val roles = atlasRoleColors()

    Surface(
        modifier = modifier.width(width).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "FILE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(modifier = Modifier.weight(1f))
                HeaderAction("Close", accent = true, onClick = onClose)
            }
            Divider()
            if (focus == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "File not in current view",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }
            val dir = focus.path?.parent?.toString()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = focus.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = focus.path != null) {
                            focus.path?.let { onOpenFile(focus.id, it.toNioPath()) }
                        }
                        .padding(horizontal = 12.dp, vertical = 1.dp),
                )
                if (dir != null) {
                    Text(
                        text = dir,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }

                NeighborSection("Imports", neighborhood.outgoing, neighborhood.outgoingTotal, roles.dependency, onOpenFile)
                NeighborSection("Used by", neighborhood.incoming, neighborhood.incomingTotal, roles.usedBy, onOpenFile)

                if (neighborhood.incomingTotal == 0 && neighborhood.outgoingTotal == 0) {
                    PanelDivider()
                    Text(
                        text = "No import relationships in view",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NeighborSection(
    title: String,
    neighbors: List<Neighbor>,
    total: Int,
    accent: Color,
    onOpenFile: (String, FilePath) -> Unit,
) {
    if (total == 0) return
    PanelDivider()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(accent))
        Text(
            text = "  $title  $total files",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
    for (neighbor in neighbors) {
        val path = neighbor.node.path
        Text(
            text = neighbor.node.label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = path != null) {
                    if (path != null) onOpenFile(neighbor.node.id, path.toNioPath())
                }
                .padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
    if (total > neighbors.size) {
        Text(
            text = "+${total - neighbors.size} more",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PanelDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
