package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.ui.CompactContextMenuRepresentation
import page.ui.CompactDropdown
import page.ui.CompactMenuItem
import java.awt.Cursor

@Composable
fun TerminalPanel(
    manager: TerminalManager,
    onPanelClose: () -> Unit,
    height: Dp,
    onResizeDelta: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = manager.activeTab
    Surface(
        modifier = modifier.fillMaxWidth().height(height),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BottomPanelResizeBar(onResizeDelta = onResizeDelta)
            TerminalTabBar(
                tabs = manager.tabs,
                activeId = manager.activeId,
                onSelect = { manager.selectTab(it) },
                onClose = { manager.closeTab(it) },
                onRename = { id, name -> manager.renameTab(id, name) },
                onNewTab = { manager.newTab() },
                onPanelClose = onPanelClose,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            if (active != null) {
                TerminalHeader(
                    alive = active.controller.alive,
                    shells = manager.availableShells,
                    currentShell = active.controller.shell,
                    elevated = active.controller.elevated,
                    onShellSelected = { active.controller.selectShell(it) },
                    onElevatedToggle = { active.controller.toggleElevated(it) },
                    onRestart = { active.controller.start() },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                key(active.id) {
                    TerminalBody(
                        lines = active.controller.lines,
                        alive = active.controller.alive,
                        cursorRow = active.controller.cursorRow,
                        cursorCol = active.controller.cursorCol,
                        cursorVisible = active.controller.cursorVisible,
                        scrollbackSize = active.controller.scrollbackSize,
                        onRawInput = { active.controller.sendRaw(it) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No terminal open — click + above to start one",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalHeader(
    alive: Boolean,
    shells: List<ShellOption>,
    currentShell: ShellOption,
    elevated: Boolean,
    onShellSelected: (ShellOption) -> Unit,
    onElevatedToggle: (Boolean) -> Unit,
    onRestart: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShellSelector(
            shells = shells,
            current = currentShell,
            onSelected = onShellSelected,
        )
        ToggleChip(
            label = "Admin",
            selected = elevated,
            onClick = { onElevatedToggle(!elevated) },
        )
        Text(
            text = if (alive) "running" else "stopped",
            style = MaterialTheme.typography.labelSmall,
            color = if (alive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.weight(1f))
        if (!alive) {
            ClickableLabel(text = "Restart", onClick = onRestart)
        }
    }
}

@Composable
private fun ShellSelector(
    shells: List<ShellOption>,
    current: ShellOption,
    onSelected: (ShellOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { expanded = true },
        ) {
            Text(
                text = current.kind.display + " ▾",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        CompactDropdown(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in shells) {
                CompactMenuItem(
                    label = option.kind.display,
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
            if (shells.isEmpty()) {
                CompactMenuItem(
                    label = "No shells available",
                    enabled = false,
                    onClick = { expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bg,
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ClickableLabel(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp),
    )
}

@Composable
private fun TerminalTabBar(
    tabs: List<TerminalTab>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onNewTab: () -> Unit,
    onPanelClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "TERMINAL",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (tab in tabs) {
                key(tab.id) {
                    TerminalTabChip(
                        tab = tab,
                        active = tab.id == activeId,
                        onSelect = { onSelect(tab.id) },
                        onClose = { onClose(tab.id) },
                        onRename = { newName -> onRename(tab.id, newName) },
                    )
                }
            }
            ClickableLabel(text = "+", onClick = onNewTab)
        }
        ClickableLabel(text = "Close", onClick = onPanelClose)
    }
}

@Composable
private fun TerminalTabChip(
    tab: TerminalTab,
    active: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onRename: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(TextFieldValue(tab.name)) }
    var hadFocus by remember { mutableStateOf(false) }
    val editFocus = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) {
            editValue = TextFieldValue(tab.name, selection = TextRange(0, tab.name.length))
            hadFocus = false
            repeat(8) {
                delay(25)
                if (runCatching { editFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        } else {
            hadFocus = false
        }
    }

    val bg = when {
        editing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        active -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val fg = if (active || editing) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (editing) {
        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    } else null

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.LocalContextMenuRepresentation provides CompactContextMenuRepresentation,
    ) {
        androidx.compose.foundation.ContextMenuArea(
            items = {
                listOf(
                    androidx.compose.foundation.ContextMenuItem("Rename") { editing = true },
                    androidx.compose.foundation.ContextMenuItem("Close") { onClose() },
                )
            },
        ) {
            Box(
                modifier = if (editing) Modifier else Modifier.pointerInput(tab.id) {
                    detectTapGestures(
                        onTap = { onSelect() },
                        onDoubleTap = { editing = true },
                    )
                },
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = bg,
                    border = border,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (editing) {
                            BasicTextField(
                                value = editValue,
                                onValueChange = { editValue = it },
                                singleLine = true,
                                textStyle = TextStyle(color = fg, fontSize = 12.sp),
                                cursorBrush = SolidColor(fg),
                                modifier = Modifier
                                    .widthIn(min = 60.dp, max = 160.dp)
                                    .focusRequester(editFocus)
                                    .onFocusChanged { state ->
                                        if (state.isFocused) {
                                            hadFocus = true
                                        } else if (hadFocus && editing) {
                                            onRename(editValue.text)
                                            editing = false
                                        }
                                    }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (event.key) {
                                            Key.Enter -> {
                                                onRename(editValue.text)
                                                editing = false
                                                true
                                            }
                                            Key.Escape -> {
                                                editing = false
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                            )
                        } else {
                            Text(
                                text = tab.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = fg,
                            )
                        }
                        Text(
                            text = "x",
                            style = MaterialTheme.typography.labelSmall,
                            color = fg,
                            modifier = Modifier.clickable { onClose() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalBody(
    lines: List<TerminalLine>,
    alive: Boolean,
    cursorRow: Int,
    cursorCol: Int,
    cursorVisible: Boolean,
    scrollbackSize: Int,
    onRawInput: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val stickToBottom = remember { mutableStateOf(true) }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .collect { (value, max) ->
                stickToBottom.value = max == 0 || value >= max - 4
            }
    }
    LaunchedEffect(lines.size, cursorRow) {
        if (stickToBottom.value) scrollState.scrollTo(scrollState.maxValue)
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(alive) {
        if (alive) runCatching { focusRequester.requestFocus() }
    }

    var sink by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(false) }

    var caretOn by remember { mutableStateOf(true) }
    LaunchedEffect(alive, focused) {
        if (!alive) {
            caretOn = false
            return@LaunchedEffect
        }
        if (!focused) {
            caretOn = true
            return@LaunchedEffect
        }
        caretOn = true
        while (true) {
            delay(530)
            caretOn = !caretOn
        }
    }
    val caretColor = MaterialTheme.colorScheme.onSurface

    val cursorLineIndex by remember(cursorRow, scrollbackSize) {
        derivedStateOf { scrollbackSize + cursorRow }
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { runCatching { focusRequester.requestFocus() } })
        },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalContextMenuRepresentation provides CompactContextMenuRepresentation,
        ) {
        SelectionContainer(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                val displayEnd = (cursorLineIndex + 2).coerceAtMost(lines.size)
                for (idx in 0 until displayEnd) {
                    val line = lines[idx]
                    val showCaret = idx == cursorLineIndex && alive && cursorVisible && focused && caretOn
                    Text(
                        text = line.toAnnotatedString(
                            showCaret = showCaret,
                            caretColor = caretColor,
                            caretCol = if (showCaret) cursorCol else -1,
                            caretFocused = focused,
                        ),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        }
        BasicTextField(
            value = sink,
            onValueChange = { newValue ->
                if (newValue.text.isNotEmpty()) {
                    onRawInput(newValue.text)
                    sink = TextFieldValue("")
                }
            },
            singleLine = false,
            enabled = alive,
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val esc = Char(0x1B).toString()
                    when {
                        event.key == Key.Enter -> { onRawInput("\r"); true }
                        event.key == Key.Backspace -> { onRawInput("\b"); true }
                        event.key == Key.Tab -> { onRawInput("\t"); true }
                        event.key == Key.DirectionUp -> { onRawInput(esc + "[A"); true }
                        event.key == Key.DirectionDown -> { onRawInput(esc + "[B"); true }
                        event.key == Key.DirectionLeft -> { onRawInput(esc + "[D"); true }
                        event.key == Key.DirectionRight -> { onRawInput(esc + "[C"); true }
                        event.key == Key.MoveHome -> { onRawInput(esc + "[H"); true }
                        event.key == Key.MoveEnd -> { onRawInput(esc + "[F"); true }
                        event.key == Key.PageUp -> { onRawInput(esc + "[5~"); true }
                        event.key == Key.PageDown -> { onRawInput(esc + "[6~"); true }
                        event.key == Key.Delete -> { onRawInput(esc + "[3~"); true }
                        event.isCtrlPressed && event.key == Key.C -> { onRawInput(Char(0x03).toString()); true }
                        event.isCtrlPressed && event.key == Key.D -> { onRawInput(Char(0x04).toString()); true }
                        event.isCtrlPressed && event.key == Key.L -> { onRawInput(Char(0x0C).toString()); true }
                        event.isCtrlPressed && event.key == Key.A -> { onRawInput(Char(0x01).toString()); true }
                        event.isCtrlPressed && event.key == Key.E -> { onRawInput(Char(0x05).toString()); true }
                        event.isCtrlPressed && event.key == Key.K -> { onRawInput(Char(0x0B).toString()); true }
                        event.isCtrlPressed && event.key == Key.U -> { onRawInput(Char(0x15).toString()); true }
                        event.isCtrlPressed && event.key == Key.W -> { onRawInput(Char(0x17).toString()); true }
                        else -> false
                    }
                },
        )
    }
}

private fun TerminalLine.toAnnotatedString(
    showCaret: Boolean = false,
    caretColor: Color = Color.Unspecified,
    caretCol: Int = -1,
    caretFocused: Boolean = true,
): AnnotatedString = buildAnnotatedString {
    var charIndex = 0
    for (span in spans) {
        val spanStyle = SpanStyle(
            color = span.style.fg ?: Color.Unspecified,
            background = span.style.bg ?: Color.Unspecified,
            fontWeight = if (span.style.bold) FontWeight.Bold else null,
            fontStyle = if (span.style.italic) FontStyle.Italic else null,
            textDecoration = if (span.style.underline) TextDecoration.Underline else null,
        )
        if (showCaret && caretCol >= charIndex && caretCol < charIndex + span.text.length) {
            val localPos = caretCol - charIndex
            if (localPos > 0) {
                withStyle(spanStyle) { append(span.text.substring(0, localPos)) }
            }
            withStyle(SpanStyle(color = Color.Black, background = caretColor)) {
                append(span.text[localPos])
            }
            if (localPos + 1 < span.text.length) {
                withStyle(spanStyle) { append(span.text.substring(localPos + 1)) }
            }
        } else {
            withStyle(spanStyle) { append(span.text) }
        }
        charIndex += span.text.length
    }
    if (showCaret && caretCol >= charIndex) {
        while (charIndex < caretCol) {
            append(' ')
            charIndex++
        }
        withStyle(SpanStyle(color = Color.Black, background = caretColor)) { append(' ') }
    }
}

@Composable
private fun BottomPanelResizeBar(onResizeDelta: (Dp) -> Unit) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
            .hoverable(interactionSource)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dy ->
                    onResizeDelta(with(density) { (-dy).toDp() })
                }
            }
            .background(
                if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else Color.Transparent,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}
