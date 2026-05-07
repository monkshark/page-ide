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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import page.editor.EditHistory
import page.editor.EditSnapshot
import page.editor.PageScroll

@OptIn(ExperimentalFoundationApi::class)
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
    manageHistory: Boolean = true,
    viewportHeightProvider: (() -> Float)? = null,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val bringIntoView = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var caretVisible by remember { mutableStateOf(true) }
    var menuExpanded by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }

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
    val latestViewportHeight by rememberUpdatedState(viewportHeightProvider)
    val preferredX = remember { mutableStateOf<Float?>(null) }
    val dragMoveTarget = remember { mutableStateOf<Int?>(null) }

    val performUndo: () -> Boolean
    val performRedo: () -> Boolean
    if (manageHistory) {
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

        performUndo = lambda@{
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

        performRedo = lambda@{
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
    } else {
        performUndo = { false }
        performRedo = { false }
    }

    val caretRectProvider: () -> androidx.compose.ui.geometry.Rect = {
        val sel = latestValue.selection
        val caretTrans = latestMapping.originalToTransformed(sel.end)
        latestLayout.getCursorRect(caretTrans)
    }

    LaunchedEffect(value.selection.end, layout) {
        val caretTrans = mapping.originalToTransformed(value.selection.end)
        if (caretTrans !in 0..displayText.length) return@LaunchedEffect
        val rect = layout.getCursorRect(caretTrans)
        val marginPx = with(density) { 24.dp.toPx() }
        val expanded = androidx.compose.ui.geometry.Rect(
            left = (rect.left - marginPx).coerceAtLeast(0f),
            top = (rect.top - marginPx).coerceAtLeast(0f),
            right = rect.right + marginPx,
            bottom = rect.bottom + marginPx,
        )
        runCatching { bringIntoView.bringIntoView(expanded) }
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
                    preferredX = preferredX,
                    viewportHeightPx = latestViewportHeight?.invoke() ?: 0f,
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
                .bringIntoViewRequester(bringIntoView)
                .pointerInput(value.text) {
                    awaitPointerEventScope {
                        var anchor = 0
                        var dragging = false
                        var clickCount = 0
                        var lastClickTime = 0L
                        var lastClickPos = Offset.Zero
                        var moveSourceOffset = -1
                        var movePressPos = Offset.Zero
                        var moveActive = false
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Main)
                            val change = e.changes.firstOrNull() ?: continue
                            when (e.type) {
                                PointerEventType.Press -> {
                                    preferredX.value = null
                                    val transOff = latestLayout.getOffsetForPosition(change.position)
                                    if (latestPointerPress?.invoke(transOff) == true) {
                                        focusRequester.requestFocus()
                                        change.consume()
                                        clickCount = 0
                                        continue
                                    }
                                    val origOff = latestMapping.transformedToOriginal(transOff)
                                    if (e.buttons.isSecondaryPressed) {
                                        val sel = latestValue.selection
                                        if (sel.collapsed || origOff < sel.min || origOff > sel.max) {
                                            latestOnChange(latestValue.copy(selection = TextRange(origOff)))
                                        }
                                        menuOffset = DpOffset(change.position.x.toDp(), change.position.y.toDp())
                                        menuExpanded = true
                                        focusRequester.requestFocus()
                                        change.consume()
                                        clickCount = 0
                                        continue
                                    }
                                    if (e.keyboardModifiers.isShiftPressed) {
                                        val anchorPos = latestValue.selection.start
                                        anchor = anchorPos
                                        dragging = true
                                        latestOnChange(
                                            latestValue.copy(selection = TextRange(anchorPos, origOff)),
                                        )
                                        focusRequester.requestFocus()
                                        change.consume()
                                        clickCount = 0
                                        continue
                                    }
                                    val curSel = latestValue.selection
                                    val now = System.currentTimeMillis()
                                    val close = (change.position - lastClickPos).getDistance() < 8f
                                    val nextClickCount = when {
                                        !close -> 1
                                        clickCount == 1 && now - lastClickTime < 400 -> 2
                                        clickCount == 2 && now - lastClickTime < 400 -> 3
                                        else -> 1
                                    }
                                    val insideSelection = !curSel.collapsed &&
                                        origOff > curSel.min && origOff < curSel.max
                                    if (nextClickCount == 1 && insideSelection) {
                                        moveSourceOffset = origOff
                                        movePressPos = change.position
                                        moveActive = false
                                        dragging = false
                                        clickCount = nextClickCount
                                        lastClickTime = now
                                        lastClickPos = change.position
                                        focusRequester.requestFocus()
                                        change.consume()
                                        continue
                                    }
                                    clickCount = nextClickCount
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
                                    if (moveSourceOffset >= 0 && change.pressed) {
                                        if (!moveActive) {
                                            val moved = (change.position - movePressPos).getDistance()
                                            if (moved >= 4f) moveActive = true
                                        }
                                        if (moveActive) {
                                            val transOff = latestLayout.getOffsetForPosition(change.position)
                                            val origOff = latestMapping.transformedToOriginal(transOff)
                                            dragMoveTarget.value = origOff
                                            change.consume()
                                        }
                                    } else if (dragging && change.pressed) {
                                        val transOff = latestLayout.getOffsetForPosition(change.position)
                                        val origOff = latestMapping.transformedToOriginal(transOff)
                                        latestOnChange(
                                            latestValue.copy(selection = TextRange(anchor, origOff)),
                                        )
                                        change.consume()
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (moveSourceOffset >= 0) {
                                        if (moveActive) {
                                            val target = dragMoveTarget.value
                                            if (target != null) {
                                                val copy = e.keyboardModifiers.isCtrlPressed
                                                CodeEditorActions
                                                    .applyDragMove(latestValue, target, copy)
                                                    ?.let(latestOnChange)
                                            }
                                        } else {
                                            latestOnChange(
                                                latestValue.copy(selection = TextRange(moveSourceOffset)),
                                            )
                                        }
                                        moveSourceOffset = -1
                                        moveActive = false
                                        dragMoveTarget.value = null
                                    }
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
            val ghostTarget = dragMoveTarget.value
            if (ghostTarget != null) {
                val ghostTrans = latestMapping.originalToTransformed(ghostTarget)
                val ghostRect = layout.getCursorRect(ghostTrans)
                val ghostWidth = with(density) { 2.dp.toPx() }
                drawRect(
                    brush = cursorBrush,
                    topLeft = Offset(ghostRect.left, ghostRect.top),
                    size = Size(ghostWidth, ghostRect.bottom - ghostRect.top),
                    alpha = 0.55f,
                )
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            offset = menuOffset,
        ) {
            val sel = value.selection
            val hasSelection = !sel.collapsed
            DropdownMenuItem(
                text = { Text("잘라내기") },
                enabled = hasSelection,
                onClick = {
                    if (!sel.collapsed) {
                        clipboard.setText(AnnotatedString(value.text.substring(sel.min, sel.max)))
                        val newText = value.text.removeRange(sel.min, sel.max)
                        onValueChange(value.copy(text = newText, selection = TextRange(sel.min)))
                    }
                    menuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("복사") },
                enabled = hasSelection,
                onClick = {
                    if (!sel.collapsed) {
                        clipboard.setText(AnnotatedString(value.text.substring(sel.min, sel.max)))
                    }
                    menuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("붙여넣기") },
                onClick = {
                    val pasted = clipboard.getText()?.text.orEmpty()
                    if (pasted.isNotEmpty()) {
                        val newText = value.text.substring(0, sel.min) + pasted + value.text.substring(sel.max)
                        val caret = sel.min + pasted.length
                        onValueChange(value.copy(text = newText, selection = TextRange(caret)))
                    }
                    menuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("전체 선택") },
                onClick = {
                    onValueChange(value.copy(selection = TextRange(0, value.text.length)))
                    menuExpanded = false
                },
            )
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
    preferredX: androidx.compose.runtime.MutableState<Float?>,
    viewportHeightPx: Float,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val text = value.text
    val sel = value.selection
    val shift = event.isShiftPressed
    val ctrl = event.isCtrlPressed
    val alt = event.isAltPressed
    val isVerticalMove = !ctrl && !alt && (
        event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
        event.key == Key.PageUp || event.key == Key.PageDown
    )
    if (!isVerticalMove) preferredX.value = null

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
            val targetLine = layout.getLineForOffset(sel.end) - 1
            if (targetLine < 0) return true
            val x = preferredX.value ?: layout.getCursorRect(sel.end).left
            preferredX.value = x
            val newCaret = caretAt(layout, targetLine, x)
            onChange(value.copy(selection = newSelection(sel, newCaret, shift)))
            true
        }
        Key.DirectionDown -> {
            val targetLine = layout.getLineForOffset(sel.end) + 1
            if (targetLine >= layout.lineCount) return true
            val x = preferredX.value ?: layout.getCursorRect(sel.end).left
            preferredX.value = x
            val newCaret = caretAt(layout, targetLine, x)
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
            val lineHeightPx = if (layout.lineCount > 0) {
                layout.getLineBottom(0) - layout.getLineTop(0)
            } else 0f
            val step = PageScroll.linesPerPage(viewportHeightPx, lineHeightPx)
            val currentLine = layout.getLineForOffset(sel.end)
            val targetLine = (currentLine + if (up) -step else step)
                .coerceIn(0, layout.lineCount - 1)
            val x = preferredX.value ?: layout.getCursorRect(sel.end).left
            preferredX.value = x
            val newCaret = caretAt(layout, targetLine, x)
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

private fun caretAt(layout: TextLayoutResult, targetLine: Int, x: Float): Int {
    val y = layout.getLineTop(targetLine) + 1f
    return layout.getOffsetForPosition(Offset(x, y))
}

private fun OffsetMapping.safeOriginalToTransformed(offset: Int): Int = originalToTransformed(offset)
