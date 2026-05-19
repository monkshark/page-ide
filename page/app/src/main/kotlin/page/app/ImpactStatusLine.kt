package page.app

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp

@Composable
internal fun ImpactStatusLine(state: ImpactScanState) {
    val text: String
    val color = when (state) {
        is ImpactScanState.Scanning -> {
            text = "Checking references… (${state.done}/${state.total})"
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        is ImpactScanState.Done -> {
            if (state.hits.isEmpty()) {
                text = "No external references found"
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                val files = state.hits.map { it.file }.toSet().size
                text = "⚠ ${state.hits.size} reference(s) in $files file(s)"
                MaterialTheme.colorScheme.error
            }
        }
        is ImpactScanState.Error -> {
            text = "Reference check failed: ${state.message}"
            MaterialTheme.colorScheme.error
        }
        ImpactScanState.Cancelled -> {
            text = "Reference check cancelled"
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        ImpactScanState.Idle -> {
            text = ""
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    if (text.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            color = color,
            style = LocalTextStyle.current.copy(fontSize = 11.sp),
        )
    }
}
