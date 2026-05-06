package page.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import page.editor.CursorMotion
import page.editor.EditorContent
import page.editor.Selection

@Composable
fun CodeEditor(
    content: EditorContent,
    onContentChange: (EditorContent) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = defaultEditorStyle(),
    selectionColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    caretColor: Color = MaterialTheme.colorScheme.primary,
    onKeyShortcut: (KeyEvent) -> Boolean = { false },
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var caretVisible by remember { mutableStateOf(true) }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    val latestContent by rememberUpdatedState(content)
    val latestOnChange by rememberUpdatedState(onContentChange)
    val latestShortcut by rememberUpdatedState(onKeyShortcut)

    val layout = remember(content.text, textStyle, density.density, density.fontScale) {
        measurer.measure(
            text = AnnotatedString(content.text),
            style = textStyle,
            softWrap = false,
        )
    }

    LaunchedEffect(isFocused, content.selection.caret) {
        caretVisible = true
        if (!isFocused) return@LaunchedEffect
        while (true) {
            delay(530)
            caretVisible = !caretVisible
        }
    }

    val widthDp = with(density) { layout.size.width.toDp() }
    val heightDp = with(density) { layout.size.height.toDp() }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                handleKeyEvent(event, latestContent, latestOnChange, latestShortcut)
            }
            .verticalScroll(vScroll)
            .horizontalScroll(hScroll)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Canvas(
            modifier = Modifier
                .requiredWidth(widthDp)
                .requiredHeight(heightDp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        focusRequester.requestFocus()
                    })
                }
                .pointerInput(content.text) {
                    awaitPointerEventScope {
                        var dragging = false
                        var anchor = 0
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Main)
                            val change = e.changes.firstOrNull() ?: continue
                            when (e.type) {
                                PointerEventType.Press -> {
                                    val off = layout.getOffsetForPosition(change.position)
                                    anchor = off
                                    dragging = true
                                    latestOnChange(latestContent.withSelection(Selection.at(off)))
                                    focusRequester.requestFocus()
                                    change.consume()
                                }
                                PointerEventType.Move -> {
                                    if (dragging && change.pressed) {
                                        val off = layout.getOffsetForPosition(change.position)
                                        latestOnChange(
                                            latestContent.withSelection(Selection(anchor, off)),
                                        )
                                        change.consume()
                                    }
                                }
                                PointerEventType.Release -> {
                                    dragging = false
                                }
                                else -> Unit
                            }
                        }
                    }
                },
        ) {
            if (!latestContent.selection.isCollapsed) {
                val path = layout.multiParagraph.getPathForRange(
                    latestContent.selection.start,
                    latestContent.selection.end,
                )
                drawPath(path = path, color = selectionColor)
            }
            drawText(textLayoutResult = layout)
            if (isFocused && caretVisible) {
                val caretRect = layout.getCursorRect(latestContent.selection.caret)
                drawLine(
                    color = caretColor,
                    start = Offset(caretRect.left, caretRect.top),
                    end = Offset(caretRect.left, caretRect.bottom),
                    strokeWidth = with(density) { 1.5.dp.toPx() },
                )
            }
        }
    }
}

@Composable
private fun defaultEditorStyle(): TextStyle = TextStyle(
    color = MaterialTheme.colorScheme.onBackground,
    fontFamily = EditorFontFamily,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

private fun handleKeyEvent(
    event: KeyEvent,
    content: EditorContent,
    onChange: (EditorContent) -> Unit,
    onShortcut: (KeyEvent) -> Boolean,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (onShortcut(event)) return true
    val shift = event.isShiftPressed
    val ctrl = event.isCtrlPressed
    val handled = when (event.key) {
        Key.DirectionLeft -> {
            val next = if (ctrl) CursorMotion.moveWordLeft(content, shift)
            else CursorMotion.moveLeft(content, shift)
            onChange(next); true
        }
        Key.DirectionRight -> {
            val next = if (ctrl) CursorMotion.moveWordRight(content, shift)
            else CursorMotion.moveRight(content, shift)
            onChange(next); true
        }
        Key.MoveHome -> {
            val next = if (ctrl) CursorMotion.moveDocStart(content, shift)
            else CursorMotion.moveLineHome(content, shift)
            onChange(next); true
        }
        Key.MoveEnd -> {
            val next = if (ctrl) CursorMotion.moveDocEnd(content, shift)
            else CursorMotion.moveLineEnd(content, shift)
            onChange(next); true
        }
        Key.Backspace -> {
            val next = if (ctrl) CursorMotion.deleteWordBackward(content)
            else CursorMotion.deleteBackward(content)
            onChange(next); true
        }
        Key.Delete -> {
            val next = if (ctrl) CursorMotion.deleteWordForward(content)
            else CursorMotion.deleteForward(content)
            onChange(next); true
        }
        Key.Enter, Key.NumPadEnter -> {
            onChange(CursorMotion.insert(content, "\n")); true
        }
        Key.Tab -> {
            onChange(CursorMotion.insert(content, "    ")); true
        }
        else -> {
            if (ctrl && event.key == Key.A) {
                onChange(CursorMotion.selectAll(content)); true
            } else if (!ctrl) {
                val cp = event.utf16CodePoint
                if (cp != 0 && cp >= 0x20 && cp != 0x7F) {
                    val ch = String(Character.toChars(cp))
                    onChange(CursorMotion.insert(content, ch))
                    true
                } else false
            } else false
        }
    }
    return handled
}
