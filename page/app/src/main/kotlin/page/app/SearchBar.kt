package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.editor.SearchState

@Composable
fun SearchBar(
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onToggleCase: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    onWindowShortcut: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val queryFocus = remember { FocusRequester() }
    val replaceFocus = remember { FocusRequester() }
    LaunchedEffect(state.replaceVisible) {
        if (state.replaceVisible) replaceFocus.requestFocus() else queryFocus.requestFocus()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InputBox {
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onNext() }),
                        textStyle = inputTextStyle(),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(queryFocus)
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.Enter -> {
                                        if (e.isShiftPressed) onPrev() else onNext(); true
                                    }
                                    Key.Escape -> { onClose(); true }
                                    else -> onWindowShortcut(e)
                                }
                            },
                    )
                }
                Spacer(Modifier.width(10.dp))
                CounterLabel(state)
                Spacer(Modifier.width(12.dp))
                ToggleChip(
                    label = "Aa",
                    active = state.caseSensitive,
                    onClick = onToggleCase,
                )
                Spacer(Modifier.width(8.dp))
                IconChip(label = "<", onClick = onPrev, enabled = state.matches.isNotEmpty())
                Spacer(Modifier.width(4.dp))
                IconChip(label = ">", onClick = onNext, enabled = state.matches.isNotEmpty())
                Spacer(Modifier.width(8.dp))
                IconChip(label = "×", onClick = onClose, enabled = true)
            }
            if (state.replaceVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InputBox {
                        BasicTextField(
                            value = state.replace,
                            onValueChange = onReplaceChange,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onReplace() }),
                            textStyle = inputTextStyle(),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(replaceFocus)
                                .onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key) {
                                        Key.Enter -> { onReplace(); true }
                                        Key.Escape -> { onClose(); true }
                                        else -> onWindowShortcut(e)
                                    }
                                },
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    TextChip(
                        label = "바꾸기",
                        onClick = onReplace,
                        enabled = state.matches.isNotEmpty(),
                    )
                    Spacer(Modifier.width(6.dp))
                    TextChip(
                        label = "전부 바꾸기",
                        onClick = onReplaceAll,
                        enabled = state.matches.isNotEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
private fun InputBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .width(220.dp)
            .background(MaterialTheme.colorScheme.background)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun inputTextStyle() = TextStyle(
    color = MaterialTheme.colorScheme.onBackground,
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
)

@Composable
private fun CounterLabel(state: SearchState) {
    val text = when {
        state.query.isEmpty() -> ""
        state.matches.isEmpty() -> "0 matches"
        else -> "${state.activeMatchIndex + 1} / ${state.matches.size}"
    }
    Text(
        text = text,
        style = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        ),
    )
}

@Composable
private fun ToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .height(22.dp)
            .width(28.dp)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LocalTextStyle.current.copy(
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun IconChip(label: String, onClick: () -> Unit, enabled: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg = when {
        !enabled -> Color.Transparent
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val fg = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(24.dp)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LocalTextStyle.current.copy(
                color = fg,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
        )
    }
}

@Composable
private fun TextChip(label: String, onClick: () -> Unit, enabled: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg = when {
        !enabled -> Color.Transparent
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val fg = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .height(22.dp)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LocalTextStyle.current.copy(
                color = fg,
                fontSize = 11.sp,
            ),
        )
    }
}
