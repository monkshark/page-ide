package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import page.lsp.DiagnosticSeverity
import page.ui.CompactDropdown
import page.ui.CompactMenuSlot
import page.ui.Glass

internal data class MultiKeywordChoice(
    val commentRange: IntRange,
    val chosenKeyword: String,
    val keywords: List<String>,
    val chosenColor: Color,
    val keywordColors: Map<String, Color>,
)

internal data class GutterLine(
    val originalLine: Int,
    val foldable: Boolean,
    val folded: Boolean,
    val severity: DiagnosticSeverity? = null,
    val multiKeyword: MultiKeywordChoice? = null,
)

@Composable
internal fun LineNumberGutter(
    lines: List<GutterLine>,
    currentOriginalLine: Int,
    onToggleFold: (Int) -> Unit,
    onPickKeyword: (commentRange: IntRange, oldKeyword: String, newKeyword: String) -> Unit,
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
                MultiKeywordDot(
                    choice = entry.multiKeyword,
                    onPick = { keyword ->
                        entry.multiKeyword?.let { onPickKeyword(it.commentRange, it.chosenKeyword, keyword) }
                    },
                )
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
private fun MultiKeywordDot(
    choice: MultiKeywordChoice?,
    onPick: (String) -> Unit,
) {
    if (choice == null) {
        Box(
            modifier = Modifier
                .padding(start = 2.dp, end = 2.dp)
                .width(10.dp),
        )
        return
    }
    var expanded by remember(choice.commentRange) { mutableStateOf(false) }
    Box(modifier = Modifier.padding(start = 2.dp, end = 2.dp).width(10.dp)) {
        Text(
            text = choice.chosenKeyword.firstOrNull()?.uppercase() ?: "?",
            color = choice.chosenColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable { expanded = true },
        )
        CompactDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            minWidth = 120.dp,
        ) {
            for (kw in choice.keywords) {
                val swatch = choice.keywordColors[kw] ?: MaterialTheme.colorScheme.onSurfaceVariant
                val isActive = kw == choice.chosenKeyword
                CompactMenuSlot(
                    onClick = {
                        expanded = false
                        onPick(kw)
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = kw.first().uppercase(),
                            color = swatch,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(10.dp),
                        )
                        Text(
                            text = kw,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) swatch else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
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
