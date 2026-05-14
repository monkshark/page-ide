package page.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
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
    decorations: List<EditorDecoration> = emptyList(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onPointerPress: ((transformedOffset: Int) -> Boolean)? = null,
    onHover: ((originalOffset: Int?) -> Unit)? = null,
    hoverText: String? = null,
    hoverDiagnostic: HoverDiagnostic? = null,
    completionItems: List<CompletionDisplay> = emptyList(),
    completionSelectedIndex: Int = 0,
    completionAnchorOffset: Int? = null,
    signatureHelp: SignatureHelpDisplay? = null,
    signatureHelpAnchorOffset: Int? = null,
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
    val latestOnHover by rememberUpdatedState(onHover)
    val latestMapping by rememberUpdatedState(mapping)
    val latestLayout by rememberUpdatedState(layout)
    val latestViewportHeight by rememberUpdatedState(viewportHeightProvider)
    val preferredX = remember { mutableStateOf<Float?>(null) }
    val dragMoveTarget = remember { mutableStateOf<Int?>(null) }
    var hoverPosition by remember { mutableStateOf<Offset?>(null) }
    var latchedHoverPosition by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(hoverText, hoverDiagnostic) {
        if (hoverText.isNullOrBlank() && hoverDiagnostic == null) {
            latchedHoverPosition = null
        } else {
            latchedHoverPosition = hoverPosition
        }
    }

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
                                    } else if (!change.pressed) {
                                        val transOff = latestLayout.getOffsetForPosition(change.position)
                                        val origOff = latestMapping.transformedToOriginal(transOff)
                                        hoverPosition = change.position
                                        latestOnHover?.invoke(origOff)
                                    }
                                }
                                PointerEventType.Exit -> {
                                    hoverPosition = null
                                    latestOnHover?.invoke(null)
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
            if (decorations.isNotEmpty()) {
                val pendingStrokePx = with(density) { 1.2.dp.toPx() }
                for (deco in decorations) {
                    val isActive = deco.style == EditorDecoration.Style.TABSTOP_ACTIVE
                    val isPending = deco.style == EditorDecoration.Style.TABSTOP_PENDING
                    if (!isActive && !isPending) continue
                    val transStart = latestMapping.originalToTransformed(
                        deco.startOffset.coerceIn(0, value.text.length),
                    )
                    val transEnd = latestMapping.originalToTransformed(
                        deco.endOffset.coerceIn(0, value.text.length),
                    )
                    if (transStart < 0 || transEnd > displayText.length) continue
                    val rangeStart = layout.getCursorRect(transStart)
                    val rangeEnd = layout.getCursorRect(transEnd)
                    val left = rangeStart.left
                    val right = if (transStart == transEnd) left + with(density) { 6.dp.toPx() }
                    else rangeEnd.left
                    val topLeft = Offset(left - 1f, rangeStart.top)
                    val size = Size(right - left + 2f, rangeStart.bottom - rangeStart.top)
                    if (isActive) {
                        drawRect(color = deco.color, topLeft = topLeft, size = size)
                    } else {
                        drawRect(
                            color = deco.color.copy(alpha = deco.color.alpha * 0.6f),
                            topLeft = topLeft,
                            size = size,
                        )
                        drawRect(
                            color = deco.color,
                            topLeft = topLeft,
                            size = size,
                            style = Stroke(width = pendingStrokePx),
                        )
                    }
                }
            }
            drawText(textLayoutResult = layout)
            if (decorations.isNotEmpty()) {
                val strokePx = with(density) { 1.2.dp.toPx() }
                val ampPx = with(density) { 1.5.dp.toPx() }
                for (deco in decorations) {
                    val transStart = latestMapping.originalToTransformed(
                        deco.startOffset.coerceIn(0, value.text.length),
                    )
                    val transEnd = latestMapping.originalToTransformed(
                        deco.endOffset.coerceIn(0, value.text.length),
                    )
                    if (transStart >= transEnd) continue
                    if (transStart < 0 || transEnd > displayText.length) continue
                    when (deco.style) {
                        EditorDecoration.Style.WAVY_UNDERLINE -> drawWavyUnderline(
                            layout = layout,
                            transStart = transStart,
                            transEnd = transEnd,
                            color = deco.color,
                            strokeWidth = strokePx,
                            amplitude = ampPx,
                        )
                        EditorDecoration.Style.TABSTOP_ACTIVE -> Unit
                        EditorDecoration.Style.TABSTOP_PENDING -> Unit
                    }
                }
            }
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
        val hoverTextSnapshot = hoverText
        val hoverDiagnosticSnapshot = hoverDiagnostic
        val hoverPositionSnapshot = latchedHoverPosition
        if (
            (!hoverTextSnapshot.isNullOrBlank() || hoverDiagnosticSnapshot != null) &&
            hoverPositionSnapshot != null
        ) {
            HoverPopup(
                text = hoverTextSnapshot,
                diagnostic = hoverDiagnosticSnapshot,
                position = hoverPositionSnapshot,
            )
        }
        if (completionItems.isNotEmpty()) {
            val anchorOffset = completionAnchorOffset
                ?.coerceIn(0, value.text.length)
                ?: value.selection.end.coerceIn(0, value.text.length)
            val anchorTrans = mapping.originalToTransformed(anchorOffset)
            val anchorRect = if (anchorTrans in 0..displayText.length) {
                layout.getCursorRect(anchorTrans)
            } else {
                caretRectProvider()
            }
            val padStartPx = with(density) {
                contentPadding.calculateStartPadding(LayoutDirection.Ltr).toPx()
            }
            val padTopPx = with(density) { contentPadding.calculateTopPadding().toPx() }
            CompletionPopup(
                anchor = Offset(anchorRect.left + padStartPx, anchorRect.bottom + padTopPx),
                items = completionItems,
                selectedIndex = completionSelectedIndex.coerceIn(0, completionItems.size - 1),
                textStyle = textStyle,
            )
        }
        val sigSnapshot = signatureHelp
        if (sigSnapshot != null && completionItems.isEmpty()) {
            val anchorOffset = signatureHelpAnchorOffset
                ?.coerceIn(0, value.text.length)
                ?: value.selection.end.coerceIn(0, value.text.length)
            val anchorTrans = mapping.originalToTransformed(anchorOffset)
            val anchorRect = if (anchorTrans in 0..displayText.length) {
                layout.getCursorRect(anchorTrans)
            } else {
                caretRectProvider()
            }
            val padStartPx = with(density) {
                contentPadding.calculateStartPadding(LayoutDirection.Ltr).toPx()
            }
            val padTopPx = with(density) { contentPadding.calculateTopPadding().toPx() }
            val lineHeightPx = with(density) { textStyle.lineHeight.toPx() }
            SignatureHelpPopup(
                anchor = Offset(
                    anchorRect.left + padStartPx,
                    anchorRect.top + padTopPx - lineHeightPx,
                ),
                display = sigSnapshot,
                textStyle = textStyle,
            )
        }
    }
}

data class CompletionDisplay(
    val label: String,
    val kindHint: String,
    val detail: String? = null,
)

data class SignatureHelpDisplay(
    val label: String,
    val activeParamRange: IntRange?,
    val documentation: String?,
    val activeParamDoc: String?,
    val signatureIndex: Int,
    val signatureCount: Int,
)

@Composable
private fun SignatureHelpPopup(
    anchor: Offset,
    display: SignatureHelpDisplay,
    textStyle: TextStyle,
) {
    val label = display.label
    val activeRange = display.activeParamRange
    val annotated = buildAnnotatedString {
        if (activeRange == null || activeRange.first >= label.length) {
            append(label)
        } else {
            val s = activeRange.first.coerceIn(0, label.length)
            val e = (activeRange.last + 1).coerceIn(s, label.length)
            if (s > 0) append(label, 0, s)
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                ),
            ) { append(label, s, e) }
            if (e < label.length) append(label, e, label.length)
        }
    }
    Popup(
        offset = IntOffset(anchor.x.toInt(), anchor.y.toInt() - 8),
        focusable = false,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(6.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (display.signatureCount > 1) {
                        Text(
                            text = "${display.signatureIndex + 1}/${display.signatureCount}",
                            style = textStyle.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = annotated,
                        style = TextStyle(
                            fontFamily = EditorFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                val paramDoc = display.activeParamDoc?.takeIf { it.isNotBlank() }
                if (paramDoc != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = paramDoc,
                            style = textStyle.copy(
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
                val docs = display.documentation?.takeIf { it.isNotBlank() }
                if (docs != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = docs,
                            style = textStyle.copy(
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionPopup(
    anchor: Offset,
    items: List<CompletionDisplay>,
    selectedIndex: Int,
    textStyle: TextStyle,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        val target = selectedIndex.coerceIn(0, items.size - 1)
        val visible = listState.layoutInfo.visibleItemsInfo
        val firstVisible = visible.firstOrNull()?.index ?: -1
        val lastVisible = visible.lastOrNull()?.index ?: -1
        if (firstVisible < 0 || target < firstVisible || target > lastVisible) {
            listState.scrollToItem(target)
        }
    }
    Popup(
        offset = IntOffset(
            x = anchor.x.toInt(),
            y = anchor.y.toInt() + 4,
        ),
        focusable = false,
    ) {
        Surface(
            modifier = Modifier.requiredWidth(360.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            tonalElevation = 4.dp,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .requiredHeight(minOf(items.size, 10).times(28).dp),
            ) {
                itemsIndexed(items) { idx, item ->
                    val selected = idx == selectedIndex
                    val rowBg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else Color.Transparent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.kindHint,
                            style = textStyle.copy(
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.requiredWidth(20.dp),
                        )
                        Text(
                            text = item.label,
                            style = textStyle.copy(
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp),
                        )
                        if (!item.detail.isNullOrBlank()) {
                            Text(
                                text = item.detail,
                                style = textStyle.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .widthIn(max = 140.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HoverPopup(text: String?, diagnostic: HoverDiagnostic?, position: Offset) {
    val segments = remember(text) {
        if (text.isNullOrBlank()) emptyList() else parseHoverMarkdown(text)
    }
    Popup(
        offset = IntOffset(position.x.toInt() + 12, position.y.toInt() + 18),
        focusable = false,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 8.dp,
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(6.dp),
        ) {
            Column {
                if (diagnostic != null) {
                    DiagnosticHeader(diagnostic)
                }
                segments.forEach { seg ->
                    when (seg.kind) {
                        HoverSegmentKind.CODE -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = seg.text,
                                    style = TextStyle(
                                        fontFamily = EditorFontFamily,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        HoverSegmentKind.TEXT -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = seg.text,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        HoverSegmentKind.RULE -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .height(1.dp)
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    ),
                            )
                        }
                        HoverSegmentKind.TAGS -> {
                            KdocTagList(seg.tags)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticHeader(d: HoverDiagnostic) {
    val color = when (d.severity) {
        HoverDiagnosticSeverity.ERROR -> Glass.colors.error
        HoverDiagnosticSeverity.WARNING -> Glass.colors.warn
        HoverDiagnosticSeverity.INFO, HoverDiagnosticSeverity.HINT ->
            MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = d.message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun KdocTagList(tags: List<KdocTag>) {
    val sectionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val nameColor = MaterialTheme.colorScheme.onSurface
    val descColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
    val sections = remember(tags) { groupKdocTags(tags) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = sectionColor,
                )
                section.entries.forEach { e ->
                    val line = buildAnnotatedString {
                        append("    ")
                        if (e.name != null) {
                            withStyle(
                                SpanStyle(
                                    color = nameColor,
                                    fontFamily = EditorFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            ) {
                                append(e.name)
                            }
                            if (e.description.isNotEmpty()) {
                                withStyle(SpanStyle(color = descColor)) {
                                    append(" – ")
                                    append(e.description)
                                }
                            }
                        } else {
                            withStyle(SpanStyle(color = descColor)) {
                                append(e.description)
                            }
                        }
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                    )
                }
            }
        }
    }
}

private data class KdocSection(val label: String, val entries: List<KdocTag>)

private val KDOC_SECTION_OF = mapOf(
    "param" to "Params",
    "property" to "Properties",
    "return" to "Returns",
    "throws" to "Throws",
    "exception" to "Throws",
    "see" to "See Also",
    "since" to "Since",
    "author" to "Author",
    "sample" to "Samples",
    "receiver" to "Receiver",
    "constructor" to "Constructor",
    "suppress" to "Suppress",
)

private val KDOC_SECTION_ORDER = listOf(
    "Params", "Properties", "Receiver", "Returns", "Throws",
    "Constructor", "Samples", "See Also", "Since", "Author", "Suppress",
)

private fun groupKdocTags(tags: List<KdocTag>): List<KdocSection> {
    val byCanonical = LinkedHashMap<String, MutableList<KdocTag>>()
    for (t in tags) {
        val key = t.tag.lowercase()
        val canonical = KDOC_SECTION_OF[key] ?: key.replaceFirstChar { it.uppercase() }
        byCanonical.getOrPut(canonical) { mutableListOf() } += t
    }
    val sections = mutableListOf<KdocSection>()
    for (label in KDOC_SECTION_ORDER) {
        byCanonical.remove(label)?.let { sections += KdocSection("$label:", it) }
    }
    for ((label, ts) in byCanonical) {
        sections += KdocSection("$label:", ts)
    }
    return sections
}

data class HoverDiagnostic(
    val severity: HoverDiagnosticSeverity,
    val message: String,
)

enum class HoverDiagnosticSeverity { ERROR, WARNING, INFO, HINT }

private data class HoverSegment(
    val kind: HoverSegmentKind,
    val lang: String?,
    val text: String,
    val tags: List<KdocTag> = emptyList(),
)

private data class KdocTag(val tag: String, val name: String?, val description: String)

private enum class HoverSegmentKind { TEXT, CODE, RULE, TAGS }

private val KDOC_TAG_LINE = Regex("""^\s*@(\w+)(?:\s+(.*))?$""")
private val KDOC_TAGS_WITH_NAME = setOf("param", "property", "throws", "exception")

private fun parseHoverMarkdown(md: String): List<HoverSegment> {
    val lines = md.split('\n')
    val out = mutableListOf<HoverSegment>()
    val textBuf = StringBuilder()
    fun flushText() {
        val cleaned = cleanKdocArtifacts(textBuf.toString())
        textBuf.setLength(0)
        if (cleaned.isEmpty()) return
        val ls = cleaned.split('\n')
        var tagStart = ls.size
        for (idx in ls.indices.reversed()) {
            val l = ls[idx]
            if (l.isBlank()) continue
            if (KDOC_TAG_LINE.matches(l)) {
                tagStart = idx
            } else {
                break
            }
        }
        val body = ls.subList(0, tagStart).joinToString("\n").trim()
        if (body.isNotEmpty()) out += HoverSegment(HoverSegmentKind.TEXT, null, body)
        if (tagStart < ls.size) {
            val tags = ls.subList(tagStart, ls.size).mapNotNull { parseKdocTag(it) }
            if (tags.isNotEmpty()) out += HoverSegment(HoverSegmentKind.TAGS, null, "", tags)
        }
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            flushText()
            val lang = trimmed.removePrefix("```").trim().ifEmpty { null }
            i++
            val codeBuf = StringBuilder()
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                if (codeBuf.isNotEmpty()) codeBuf.append('\n')
                codeBuf.append(lines[i])
                i++
            }
            if (i < lines.size) i++
            val codeText = codeBuf.toString().trimEnd()
            if (codeText.isNotEmpty()) out += HoverSegment(HoverSegmentKind.CODE, lang, codeText)
        } else if (isHorizontalRule(trimmed)) {
            flushText()
            out += HoverSegment(HoverSegmentKind.RULE, null, "")
            i++
        } else {
            if (textBuf.isNotEmpty()) textBuf.append('\n')
            textBuf.append(line)
            i++
        }
    }
    flushText()
    if (out.isEmpty()) out += HoverSegment(HoverSegmentKind.TEXT, null, md.trim())
    return out
}

private fun parseKdocTag(line: String): KdocTag? {
    val m = KDOC_TAG_LINE.matchEntire(line) ?: return null
    val tag = m.groupValues[1]
    val rest = m.groupValues.getOrNull(2)?.trim().orEmpty()
    return if (tag in KDOC_TAGS_WITH_NAME && rest.isNotEmpty()) {
        val parts = rest.split(Regex("""\s+"""), limit = 2)
        KdocTag(tag, parts[0], parts.getOrNull(1)?.trim().orEmpty())
    } else {
        KdocTag(tag, null, rest)
    }
}

private fun cleanKdocArtifacts(raw: String): String {
    var t = raw.trim()
    if (t.startsWith("/**")) t = t.removePrefix("/**").trimStart()
    if (t.endsWith("*/")) t = t.removeSuffix("*/").trimEnd()
    val cleaned = t.split('\n').joinToString("\n") { line ->
        val l = line.trimStart()
        when {
            l.startsWith("* ") -> line.replaceFirst(Regex("^\\s*\\* "), "")
            l == "*" -> ""
            else -> line
        }
    }
    return cleaned.trim()
}

private fun isHorizontalRule(line: String): Boolean {
    if (line.length < 3) return false
    val ch = line[0]
    if (ch != '-' && ch != '*' && ch != '_') return false
    return line.all { it == ch }
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWavyUnderline(
    layout: TextLayoutResult,
    transStart: Int,
    transEnd: Int,
    color: Color,
    strokeWidth: Float,
    amplitude: Float,
) {
    val startLine = layout.getLineForOffset(transStart)
    val endLine = layout.getLineForOffset(transEnd)
    for (line in startLine..endLine) {
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line, visibleEnd = true)
        val from = maxOf(transStart, lineStart)
        val to = minOf(transEnd, lineEnd)
        if (from >= to) continue
        val leftRect = layout.getCursorRect(from)
        val rightRect = layout.getCursorRect(to)
        val baseline = layout.getLineBottom(line) - strokeWidth
        val left = leftRect.left
        val right = rightRect.left
        if (right <= left) continue
        val path = androidx.compose.ui.graphics.Path()
        val period = amplitude * 4f
        var x = left
        var up = true
        path.moveTo(x, baseline)
        while (x < right) {
            val nextX = (x + period / 2f).coerceAtMost(right)
            val midX = (x + nextX) / 2f
            val controlY = if (up) baseline - amplitude else baseline + amplitude
            path.quadraticTo(midX, controlY, nextX, baseline)
            x = nextX
            up = !up
        }
        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
        )
    }
}
