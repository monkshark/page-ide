package page.atlas.render

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import page.atlas.graph.GraphInsights
import page.atlas.graph.GraphSlice
import page.atlas.graph.Neighbor

private const val NEIGHBOR_LIMIT = 12

@Composable
internal fun FileInspector(
    slice: GraphSlice,
    fileId: String,
    onSelectFile: (String) -> Unit,
    onOpenFile: (FilePath) -> Unit,
    modifier: Modifier = Modifier,
) {
    val neighborhood = remember(slice, fileId) { GraphInsights.neighborhood(slice, fileId, limit = NEIGHBOR_LIMIT) }
    val focus = neighborhood.focus ?: return
    val roles = atlasRoleColors()
    val dir = focus.path?.parent?.toString()

    Column(
        modifier = modifier
            .width(220.dp)
            .heightIn(max = 360.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = "FILE",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Text(
            text = focus.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = focus.path != null) { focus.path?.let(onOpenFile) }
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

        NeighborSection("Imports", neighborhood.outgoing, neighborhood.outgoingTotal, roles.dependency, onSelectFile)
        NeighborSection("Used by", neighborhood.incoming, neighborhood.incomingTotal, roles.usedBy, onSelectFile)

        if (neighborhood.incomingTotal == 0 && neighborhood.outgoingTotal == 0) {
            SectionDivider()
            Text(
                text = "No import relationships in view",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
            )
        }

        SectionDivider()
        Text(
            text = "click a file to focus it · click the name to open",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun NeighborSection(
    title: String,
    neighbors: List<Neighbor>,
    total: Int,
    accent: Color,
    onSelectFile: (String) -> Unit,
) {
    if (total == 0) return
    SectionDivider()
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
        Text(
            text = neighbor.node.label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectFile(neighbor.node.id) }
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
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
