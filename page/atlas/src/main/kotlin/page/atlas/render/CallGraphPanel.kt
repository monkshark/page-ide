package page.atlas.render

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.atlas.graph.GraphSlice

class CallGraphState {
    var selectedId by mutableStateOf<String?>(null)
    var pendingFocusId by mutableStateOf<String?>(null)
    private var sliceKey: GraphSlice? = null

    fun onSliceChanged(slice: GraphSlice) {
        val pending = pendingFocusId
        if (pending != null && slice.nodes.any { it.id == pending }) {
            pendingFocusId = null
            sliceKey = slice
            selectedId = pending
            return
        }
        if (sliceKey == slice) return
        sliceKey = slice
        selectedId = null
    }
}

@Composable
fun CallGraphPanel(
    slice: GraphSlice,
    width: Dp,
    onClose: () -> Unit,
    state: CallGraphState = remember { CallGraphState() },
    onSelect: (String) -> Unit = {},
    onOpen: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(slice, state.pendingFocusId) { state.onSliceChanged(slice) }
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
                CloseAction(onClick = onClose)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
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
                    focusId = state.selectedId,
                    onSelect = { id ->
                        state.selectedId = id
                        onSelect(id)
                    },
                    onOpen = onOpen,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CloseAction(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tint = MaterialTheme.colorScheme.error
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) tint.copy(alpha = 0.12f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = "Close",
            style = MaterialTheme.typography.labelSmall,
            color = if (hovered) tint else tint.copy(alpha = 0.78f),
        )
    }
}
