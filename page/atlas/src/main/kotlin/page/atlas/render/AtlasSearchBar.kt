package page.atlas.render

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AtlasSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchIndex: Int,
    matchCount: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search files…",
                    style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter -> {
                                if (event.isShiftPressed) onPrev() else onNext()
                                true
                            }
                            Key.Escape -> {
                                onClose()
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
        Text(
            text = when {
                query.isEmpty() -> ""
                matchCount == 0 -> "No matches"
                else -> "$matchIndex/$matchCount"
            },
            style = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Text(
            text = "Close",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { onClose() }.padding(2.dp),
        )
    }
}
