package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import page.ui.GlassTheme
import java.nio.file.Path

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    detail: String? = null,
    impact: ImpactScanState? = null,
    rootDir: Path? = null,
    onJumpToHit: ((ReferenceHit) -> Unit)? = null,
    confirmLabel: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasHits = (impact as? ImpactScanState.Done)?.hits?.isNotEmpty() == true
    val showHits = hasHits && onJumpToHit != null
    val targetWidth = if (showHits) 520.dp else 400.dp
    val targetHeight = when {
        showHits -> 360.dp
        impact == null || impact is ImpactScanState.Idle -> 168.dp
        else -> 196.dp
    }
    val state = rememberDialogState(
        position = WindowPosition.Aligned(Alignment.Center),
        width = targetWidth,
        height = targetHeight,
    )
    LaunchedEffect(targetWidth, targetHeight) {
        state.size = DpSize(targetWidth, targetHeight)
    }
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = title,
        resizable = false,
        undecorated = true,
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) false
            else when (event.key) {
                Key.Escape -> { onDismiss(); true }
                Key.Enter, Key.NumPadEnter -> { onConfirm(); true }
                else -> false
            }
        },
    ) {
        GlassTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                    )
                    if (!detail.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = detail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = LocalTextStyle.current.copy(fontSize = 11.sp),
                        )
                    }
                    if (impact != null && impact !is ImpactScanState.Idle) {
                        Spacer(Modifier.height(8.dp))
                        ImpactStatusLine(impact)
                    }
                    if (showHits) {
                        Spacer(Modifier.height(6.dp))
                        val hits = (impact as ImpactScanState.Done).hits
                        ReferenceHitsList(
                            hits = hits,
                            rootDir = rootDir,
                            onJumpToHit = onJumpToHit!!,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (showHits) "Click a row to jump  ·  Enter to confirm  ·  Esc to cancel"
                            else "Enter to confirm  /  Esc to cancel",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = LocalTextStyle.current.copy(fontSize = 10.sp),
                        )
                        Spacer(Modifier.weight(1f))
                        DialogButton(label = "Cancel", primary = false, onClick = onDismiss)
                        Spacer(Modifier.width(8.dp))
                        DialogButton(label = confirmLabel, primary = true, danger = danger, onClick = onConfirm)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    primary: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        primary && danger -> MaterialTheme.colorScheme.error
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        primary && danger -> MaterialTheme.colorScheme.onError
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .height(26.dp)
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            style = LocalTextStyle.current.copy(fontSize = 11.sp),
        )
    }
}
