package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import page.lsp.DiagnosticSeverity
import page.ui.Glass

internal data class GutterLine(
    val originalLine: Int,
    val foldable: Boolean,
    val folded: Boolean,
    val severity: DiagnosticSeverity? = null,
)

@Composable
internal fun LineNumberGutter(
    lines: List<GutterLine>,
    currentOriginalLine: Int,
    onToggleFold: (Int) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val activeColor = MaterialTheme.colorScheme.onBackground
    val toggleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .width(IntrinsicSize.Max)
            .padding(top = 16.dp, bottom = 16.dp),
    ) {
        for (entry in lines) {
            val color = if (entry.originalLine == currentOriginalLine) activeColor else mutedColor
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FoldToggle(
                    foldable = entry.foldable,
                    folded = entry.folded,
                    onClick = { onToggleFold(entry.originalLine) },
                    textStyle = textStyle.copy(color = if (entry.folded) toggleColor else mutedColor),
                )
                SeverityDot(entry.severity)
                Text(
                    text = (entry.originalLine + 1).toString(),
                    style = textStyle.copy(color = color),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, end = 12.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun SeverityDot(severity: DiagnosticSeverity?) {
    val color = when (severity) {
        DiagnosticSeverity.ERROR -> Glass.colors.error
        DiagnosticSeverity.WARNING -> Glass.colors.warn
        DiagnosticSeverity.INFO -> MaterialTheme.colorScheme.primary
        DiagnosticSeverity.HINT -> MaterialTheme.colorScheme.tertiary
        null -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .padding(start = 4.dp, end = 2.dp)
            .size(6.dp)
            .background(color, CircleShape),
    )
}

@Composable
private fun FoldToggle(
    foldable: Boolean,
    folded: Boolean,
    onClick: () -> Unit,
    textStyle: TextStyle,
) {
    val symbol = when {
        !foldable -> " "
        folded -> "▸"
        else -> "▾"
    }
    val baseModifier = Modifier
        .padding(start = 6.dp)
        .width(14.dp)
        .fillMaxHeight()
    val clickable = if (foldable) baseModifier.clickable(onClick = onClick) else baseModifier
    Box(modifier = clickable, contentAlignment = Alignment.Center) {
        Text(
            text = symbol,
            style = textStyle,
            textAlign = TextAlign.Center,
        )
    }
}
