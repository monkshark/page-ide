package page.app

import page.runtime.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import page.ui.GlassTheme
import java.nio.file.Path

internal fun initialNameFieldValue(text: String): TextFieldValue {
    val lastDot = text.lastIndexOf('.')
    val selEnd = if (lastDot > 0) lastDot else text.length
    return TextFieldValue(text, TextRange(0, selEnd))
}

@Composable
private fun NameInputChip(label: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val bg = if (hovered)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .hoverable(src)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = LocalTextStyle.current.copy(
                fontSize = 11.sp,
                lineHeight = 11.sp,
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

@Composable
internal fun NameInputDialog(
    title: String,
    label: String,
    initial: String = "",
    error: String?,
    impact: ImpactScanState? = null,
    rootDir: Path? = null,
    onJumpToHit: ((ReferenceHit) -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onSkipRemaining: (() -> Unit)? = null,
    onOverwrite: ((String) -> Unit)? = null,
    onOverwriteAll: ((String) -> Unit)? = null,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val valueState = remember { mutableStateOf(initialNameFieldValue(initial)) }
    var value by valueState
    val focus = remember { FocusRequester() }
    LaunchedEffect(initial) {
        valueState.value = initialNameFieldValue(initial)
        focus.requestFocus()
    }

    val hasHits = (impact as? ImpactScanState.Done)?.hits?.isNotEmpty() == true
    val showHits = hasHits && onJumpToHit != null
    val targetWidth = if (showHits) 520.dp else 460.dp
    val errorLineCount = error?.let { msg ->
        val charsPerLine = (targetWidth.value.toInt() - 36) / 6
        val approx = (msg.length + charsPerLine - 1) / charsPerLine.coerceAtLeast(1)
        approx.coerceIn(1, 6)
    } ?: 1
    val errorExtra = ((errorLineCount - 1) * 14).dp
    val canOverwrite = error?.contains("already exists") == true &&
        (onOverwrite != null || onOverwriteAll != null)
    val skipBarExtra = if (onSkip != null || onSkipRemaining != null || canOverwrite) 36.dp else 0.dp
    val targetHeight = when {
        showHits -> 340.dp + errorExtra + skipBarExtra
        impact == null || impact is ImpactScanState.Idle -> 140.dp + errorExtra + skipBarExtra
        else -> 168.dp + errorExtra + skipBarExtra
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
        alwaysOnTop = true,
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) false
            else when (event.key) {
                Key.Escape -> { onDismiss(); true }
                Key.Enter, Key.NumPadEnter -> {
                    val name = value.text.trim()
                    if (name.isNotEmpty()) onSubmit(name)
                    true
                }
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
                        text = label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = value,
                            onValueChange = { value = it },
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val status = error
                        ?: if (showHits) "Click a row to jump  ·  Enter to rename  ·  Esc to cancel"
                        else "Enter to create · Esc to cancel"
                    val color = if (error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = status,
                        color = color,
                        style = LocalTextStyle.current.copy(fontSize = 11.sp),
                        softWrap = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (impact != null && impact !is ImpactScanState.Idle) {
                        Spacer(Modifier.height(4.dp))
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
                    }
                    if (onSkip != null || onSkipRemaining != null || canOverwrite) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (canOverwrite && onOverwrite != null) {
                                NameInputChip("Overwrite") { onOverwrite(value.text.trim()) }
                                Spacer(Modifier.width(8.dp))
                            }
                            if (canOverwrite && onOverwriteAll != null) {
                                NameInputChip("Overwrite all") { onOverwriteAll(value.text.trim()) }
                                Spacer(Modifier.width(8.dp))
                            }
                            if (onSkip != null) NameInputChip("Skip this", onClick = onSkip)
                            if (onSkip != null && onSkipRemaining != null) Spacer(Modifier.width(8.dp))
                            if (onSkipRemaining != null) NameInputChip("Skip all", onClick = onSkipRemaining)
                        }
                    }
                }
            }
        }
    }
}
