package page.app

import page.runtime.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import page.ui.GlassTheme

@Composable
fun LargeCopyDialog(
    sourceName: String,
    destName: String,
    totalBytes: Long,
    fileCount: Int,
    bytesCopied: Long,
    filesCopied: Int,
    onCancel: () -> Unit,
) {
    val progress = if (totalBytes <= 0L) 0f else (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    val sizeLabel = formatBytes(bytesCopied) + " / " + formatBytes(totalBytes)
    val fileLabel = if (fileCount > 1) " · $filesCopied / $fileCount files" else ""
    val targetWidth = 440.dp
    val targetHeight = 168.dp
    val state = rememberDialogState(
        position = WindowPosition.Aligned(Alignment.Center),
        width = targetWidth,
        height = targetHeight,
    )
    LaunchedEffect(targetWidth, targetHeight) {
        state.size = DpSize(targetWidth, targetHeight)
    }
    DialogWindow(
        onCloseRequest = onCancel,
        state = state,
        title = "Copying",
        resizable = false,
        undecorated = true,
        alwaysOnTop = true,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onCancel(); true
            } else false
        },
    ) {
        GlassTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Copying $sourceName  →  $destName",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = LocalTextStyle.current.copy(fontSize = 12.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Text(
                        text = sizeLabel + fileLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = LocalTextStyle.current.copy(fontSize = 11.sp),
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                .clickable(onClick = onCancel)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Cancel",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = LocalTextStyle.current.copy(fontSize = 11.sp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var idx = 0
    while (value >= 1024.0 && idx < units.size - 1) {
        value /= 1024.0
        idx++
    }
    val rounded = if (value >= 100) "%.0f".format(value)
        else if (value >= 10) "%.1f".format(value)
        else "%.2f".format(value)
    return "$rounded ${units[idx]}"
}
