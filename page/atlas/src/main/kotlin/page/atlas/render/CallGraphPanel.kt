package page.atlas.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.atlas.graph.GraphSlice

@Composable
fun CallGraphPanel(
    slice: GraphSlice,
    width: Dp,
    onClose: () -> Unit,
    callsView: AtlasViewState = remember { AtlasViewState() },
    onSelect: (String) -> Unit = {},
    onOpen: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(slice, callsView.pendingFocusId) { callsView.onSliceChanged(slice) }
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
                    text = "CALL GRAPH",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(modifier = Modifier.weight(1f))
                HeaderAction("Close", accent = true, onClick = onClose)
            }
            Divider()
            if (slice.nodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No call relationships found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                CallsView(
                    slice = slice,
                    focusId = callsView.selectedId,
                    onSelect = { id ->
                        callsView.selectedId = id
                        onSelect(id)
                    },
                    onOpen = onOpen,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}
