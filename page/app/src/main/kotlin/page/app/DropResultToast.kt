package page.app

import page.runtime.*
import page.workspace.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import page.ui.Glass
import page.ui.GlassSurface
import page.ui.GlassSurfaceLevel

enum class DropResultToastTone { Info, Warning }

data class DropResultToastState(
    val message: String,
    val visibleUntilMs: Long,
    val tone: DropResultToastTone = DropResultToastTone.Info,
    val undo: (() -> Unit)? = null,
)

@Composable
fun DropResultToast(
    state: DropResultToastState,
    onDismiss: () -> Unit,
) {
    var now by remember(state) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (System.currentTimeMillis() < state.visibleUntilMs) {
            now = System.currentTimeMillis()
            delay(80)
        }
        onDismiss()
    }
    if (now >= state.visibleUntilMs) return
    val border = when (state.tone) {
        DropResultToastTone.Warning -> MaterialTheme.colorScheme.error
        DropResultToastTone.Info -> Glass.colors.outline
    }
    val accent = when (state.tone) {
        DropResultToastTone.Warning -> MaterialTheme.colorScheme.error
        DropResultToastTone.Info -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(start = 20.dp, bottom = 60.dp, end = 20.dp, top = 20.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        GlassSurface(
            level = GlassSurfaceLevel.Raised,
            shape = RoundedCornerShape(Glass.radius.sm),
            borderColor = if (state.tone == DropResultToastTone.Warning) border else null,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = state.message,
                    color = Glass.colors.text,
                    fontSize = 12.sp,
                )
                if (state.undo != null) {
                    UndoChip(accent = accent, onClick = {
                        state.undo.invoke()
                        onDismiss()
                    })
                }
            }
        }
    }
}

@Composable
private fun UndoChip(accent: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val bg = if (hovered) accent.copy(alpha = 0.22f) else accent.copy(alpha = 0.12f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "Undo  Ctrl+Z",
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
