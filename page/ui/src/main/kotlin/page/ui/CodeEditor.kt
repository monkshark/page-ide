package page.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import page.editor.EditHistory
import page.editor.EditSnapshot

@Composable
fun CodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = defaultEditorStyle(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    cursorBrush: Brush = SolidColor(MaterialTheme.colorScheme.primary),
    selectionColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onPointerPress: ((transformedOffset: Int) -> Boolean)? = null,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    var isFocused by remember { mutableStateOf(false) }
    var caretVisible by remember { mutableStateOf(true) }

    val transformed = remember(value.text, visualTransformation) {
        visualTransformation.filter(AnnotatedString(value.text))
    }
    val displayText = transformed.text
    val mapping = transformed.offsetMapping

    val layout = remember(displayText, textStyle, density.density, density.fontScale) {
        measurer.measure(text = displayText, style = textStyle, softWrap = false)
    }

    LaunchedEffect(layout) { onTextLayout(layout) }

    LaunchedEffect(focusRequester) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(isFocused, value.selection) {
        caretVisible = true
        if (!isFocused) return@LaunchedEffect
        while (true) {
            delay(530)
            caretVisible = !caretVisible
        }
    }

    val widthDp = with(density) { layout.size.width.toDp() }
    val heightDp = with(density) { layout.size.height.toDp() }

    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestPreview by rememberUpdatedState(onPreviewKeyEvent)
    val latestPointerPress by rememberUpdatedState(onPointerPress)
    val latestMapping by rememberUpdatedState(mapping)
    val latestLayout by rememberUpdatedState(layout)

    var history by remember { mutableStateOf(EditHistory()) }
    val lastRecorded = remember { mutableStateOf<EditSnapshot?>(null) }
    var skipRecord by remember { mutableStateOf(false) }

    LaunchedEffect(value.text, value.selection.end) {
        val current = EditSnapshot(value.text, value.selection.end)
        val prev = lastRecorded.value
        if (skipRecord) {
            skipRecord = false
        } else if (prev != null && prev.text != current.text) {
            history = history.pushBeforeChange(prev)
        }
        lastRecorded.value = current
    }

    val performUndo: () -> Boolean = lambda@{
        val current = EditSnapshot(latestValue.text, latestValue.selection.end)
        val r = history.undo(current) ?: return@lambda false
        history = r.first
        skipRecord = true
        lastRecorded.value = r.second
        latestOnChange(
            latestValue.copy(text = r.second.text, selection = TextRange(r.second.caret)),
        )
        true
    }

    val performRedo: () -> Boolean = lambda@{
        val current = EditSnapshot(latestValue.text, latestValue.selection.end)
        val r = history.redo(current) ?: return@lambda false
        history = r.first
        skipRecord = true
        lastRecorded.value = r.second
        latestOnChange(
            latestValue.copy(text = r.second.text, selection = TextRange(r.second.caret)),
        )
        true
    }

    val caretRectProvider: () -> androidx.compose.ui.geometry.Rect = {
        val sel = latestValue.selection
        val caretTrans = latestMapping.originalToTransformed(sel.end)
        latestLayout.getCursorRect(caretTrans)
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .onFocusChanged { isFocused = it.isFocused }
            .focusRequester(focusRequester)
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .onPreviewKeyEvent { event ->
                if (latestPreview(event)) return@onPreviewKeyEvent true
                handleDefaultKey(
                    event = event,
                    value = latestValue,
                    onChange = latestOnChange,
                    layout = latestLayout,
                    clipboard = clipboard,
                    onUndo = performUndo,
                    onRedo = performRedo,
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Press) {
                            focusRequester.requestFocus()
                        }
                    }
                }
            }
            .padding(contentPadding)
            .imeInput(value, onValueChange, caretRectProvider),
    ) {
        Canvas(
            modifier = Modifier
                .requiredWidth(widthDp)
                .requiredHeight(heightDp)
                .pointerInput(value.text) {
                    awaitPointerEventScope {
                        var anchor = 0
                        var dragging = false
                        var clickCount = 0
                        var lastClickTime = 0L
                        var lastClickPos = Offset.Zero
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Main)
                            val change = e.changes.firstOrNull() ?: continue
                            when (e.type) {
                                PointerEventType.Press -> {
                                    val transOff = latestLayout.getOffsetForPosition(change.position)
                                    if (latestPointerPress?.invoke(transOff) == true) {
                                        focusRequester.requestFocus()
                                        change.consume()
                                        clickCount = 0
                                        continue
                                    }
                                    val origOff = latestMapping.transformedToOriginal(transOff)
                                    val now = System.currentTimeMillis()
                                    val close = (change.position - lastClickPos).getDistance() < 8f
                                    clickCount = when {
                                        !close -> 1
                                        clickCount == 1 && now - lastClickTime < 400 -> 2
                                        clickCount == 2 && now - lastClickTime < 400 -> 3
                                        else -> 1
                                    }
                                    lastClickTime = now
                                    lastClickPos = change.position
                                    when (clickCount) {
                                        2 -> {
                                            latestOnChange(CodeEditorActions.selectWordAt(latestValue, origOff))
                                            dragging = false
                                        }
                                        3 -> {
                                            latestOnChange(CodeEditorActions.selectLineAt(latestValue, origOff))
                                            dragging = false
                                        }
                                        else -> {
                                            anchor = origOff
                                            dragging = true
                                            latestOnChange(latestValue.copy(selection = TextRange(origOff)))
                                        }
                                    }
                                    focusRequester.requestFocus()
                                    change.consume()
                                }
                                PointerEventType.Move -> {
                                    if (dragging && change.pressed) {
                                        val transOff = latestLayout.getOffsetForPosition(change.position)
                                        val origOff = latestMapping.transformedToOriginal(transOff)
                                        latestOnChange(
                                            latestValue.copy(selection = TextRange(anchor, origOff)),
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
            val sel = latestValue.selection
            if (!sel.collapsed) {
                val transStart = latestMapping.originalToTransformed(sel.min)
                val transEnd = latestMapping.originalToTransformed(sel.max)
                if (transStart < transEnd) {
                    val path = layout.multiParagraph.getPathForRange(transStart, transEnd)
                    drawPath(path = path, color = selectionColor)
                }
            }
            drawText(textLayoutResult = layout)
            val composition = latestValue.composition
            if (composition != null && !composition.collapsed) {
                val cStart = latestMapping.originalToTransformed(composition.min)
                val cEnd = latestMapping.originalToTransformed(composition.max)
                if (cStart < cEnd) {
                    val startRect = layout.getCursorRect(cStart)
                    val endRect = layout.getCursorRect(cEnd)
                    val underlineThickness = with(density) { 1.dp.toPx() }
                    drawRect(
                        brush = cursorBrush,
                        topLeft = Offset(startRect.left, startRect.bottom - underlineThickness),
                        size = Size(endRect.left - startRect.left, underlineThickness),
                    )
                }
            }
            if (isFocused && caretVisible) {
                val caretTrans = latestMapping.originalToTransformed(sel.end)
                val caretRect = layout.getCursorRect(caretTrans)
                val caretWidth = with(density) { 2.dp.toPx() }
                drawRect(
                    brush = cursorBrush,
                    topLeft = Offset(caretRect.left, caretRect.top),
                    size = Size(caretWidth, caretRect.bottom - caretRect.top),
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

private fun handleDefaultKey(
    event: KeyEvent,
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    layout: TextLayoutResult,
    clipboard: ClipboardManager,
    onUndo: () -> Boolean,
    onRedo: () -> Boolean,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val text = value.text
    val sel = value.selection
    val shift = event.isShiftPressed
    val ctrl = event.isCtrlPressed
    val alt = event.isAltPressed

    if (ctrl && !alt && event.key == Key.Z) return if (shift) onRedo() else onUndo()
    if (ctrl && !alt && event.key == Key.Y) return onRedo()

    if (alt && !ctrl && (event.key == Key.DirectionUp || event.key == Key.DirectionDown)) {
        val down = event.key == Key.DirectionDown
        val r = CodeEditorActions.applyLineMove(value, down = down, duplicate = shift)
        if (r != null) onChange(r)
        return true
    }

    if (ctrl && !alt && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)) {
        val r = if (event.key == Key.DirectionLeft) {
            CodeEditorActions.applyWordLeft(value, shift)
        } else {
            CodeEditorActions.applyWordRight(value, shift)
        }
        onChange(r)
        return true
    }
    if (ctrl && !alt && event.key == Key.Backspace) {
        CodeEditorActions.applyWordBackspace(value)?.let(onChange)
        return true
    }
    if (ctrl && !alt && event.key == Key.Delete) {
        CodeEditorActions.applyWordDelete(value)?.let(onChange)
        return true
    }

    if (ctrl && !alt) {
        return when (event.key) {
            Key.A -> {
                onChange(value.copy(selection = TextRange(0, text.length)))
                true
            }
            Key.C -> {
                if (!sel.collapsed) {
                    clipboard.setText(AnnotatedString(text.substring(sel.min, sel.max)))
                }
                true
            }
            Key.X -> {
                if (!sel.collapsed) {
                    clipboard.setText(AnnotatedString(text.substring(sel.min, sel.max)))
                    val newText = text.removeRange(sel.min, sel.max)
                    onChange(value.copy(text = newText, selection = TextRange(sel.min)))
                }
                true
            }
            Key.V -> {
                val pasted = clipboard.getText()?.text.orEmpty()
                onChange(insertReplacing(value, pasted))
                true
            }
            else -> false
        }
    }

    return when (event.key) {
        Key.DirectionLeft -> {
            val newCaret = if (!shift && !sel.collapsed) sel.min
            else (sel.end - 1).coerceAtLeast(0)
            onChange(value.copy(selection = newSelection(sel, newCaret, shift)))
            true
        }
        Key.DirectionRight -> {
            val newCaret = if (!shift && !sel.collapsed) sel.max
            else (sel.end + 1).coerceAtMost(text.length)
            onChange(value.copy(selection = newSelection(sel, newCaret, shift)))
            true
        }
        Key.DirectionUp -> {
            val newCaret = verticalCaret(layout, sel.end, up = true) ?: return true
            onChange(value.copy(selection = newSelection(sel, newCaret, shift)))
            true
        }
        Key.DirectionDown -> {
            val newCaret = verticalCaret(layout, sel.end, up = false) ?: return true
            onChange(value.copy(selection = newSelection(sel, newCaret, shift)))
            true
        }
        Key.MoveHome -> {
            val line = layout.getLineForOffset(sel.end)
            val newCaret = layout.getLineStart(line)
            onChange(value.copy(selection = newSelection(sel, newCaret, shift)))
            true
        }
        Key.MoveEnd -> {
            val line = layout.getLineForOffset(sel.end)
            val newCaret = layout.getLineEnd(line, visibleEnd = true)
            onChange(value.copy(selection = newSelection(sel, newCaret, shift)))
            true
        }
        Key.PageUp, Key.PageDown -> {
            val up = event.key == Key.PageUp
            val targetLine = (layout.getLineForOffset(sel.end) + if (up) -10 else 10)
                .coerceIn(0, layout.lineCount - 1)
            val x = layout.getCursorRect(sel.end).left
            val y = layout.getLineTop(targetLine) + 1
            val newCaret = layout.getOffsetForPosition(Offset(x, y))
            onChange(value.copy(selection = newSelection(sel, newCaret, shift)))
            true
        }
        Key.Backspace -> {
            CodeEditorActions.applyBackspace(value)?.let(onChange)
            true
        }
        Key.Delete -> {
            CodeEditorActions.applyDelete(value)?.let(onChange)
            true
        }
        Key.Enter, Key.NumPadEnter -> {
            onChange(CodeEditorActions.applyEnter(value))
            true
        }
        Key.Tab -> {
            onChange(CodeEditorActions.applyTab(value, shift))
            true
        }
        else -> {
            if (alt || event.isMetaPressed) return false
            val cp = event.utf16CodePoint
            if (cp == 0 || cp == 0xFFFF || cp < 0x20 || cp == 0x7F) return false
            val ch = String(Character.toChars(cp))
            onChange(CodeEditorActions.applyCharInsert(value, ch))
            true
        }
    }
}

private fun newSelection(current: TextRange, caret: Int, shift: Boolean): TextRange =
    if (shift) TextRange(current.start, caret) else TextRange(caret)

private fun insertReplacing(value: TextFieldValue, insertion: String): TextFieldValue {
    val sel = value.selection
    val newText = value.text.substring(0, sel.min) + insertion + value.text.substring(sel.max)
    val caret = sel.min + insertion.length
    return value.copy(text = newText, selection = TextRange(caret))
}

private fun verticalCaret(layout: TextLayoutResult, currentOffset: Int, up: Boolean): Int? {
    val line = layout.getLineForOffset(currentOffset)
    val targetLine = line + if (up) -1 else 1
    if (targetLine < 0 || targetLine >= layout.lineCount) return null
    val x = layout.getCursorRect(currentOffset).left
    val y = layout.getLineTop(targetLine) + 1
    return layout.getOffsetForPosition(Offset(x, y))
}

private fun OffsetMapping.safeOriginalToTransformed(offset: Int): Int = originalToTransformed(offset)
