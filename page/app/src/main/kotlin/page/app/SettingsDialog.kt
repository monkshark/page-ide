package page.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import page.lsp.LspBackends
import page.ui.Glass
import page.ui.GlassPalette

data class PageSettings(
    val autoSave: AutoSaveOptions = AutoSaveOptions.DEFAULT,
    val editor: EditorOptions = EditorOptions.DEFAULT,
    val lsp: LspOptions = LspOptions.DEFAULT,
    val autoInput: AutoInputOptions = AutoInputOptions.DEFAULT,
    val ui: UiOptions = UiOptions.DEFAULT,
    val run: RunOptions = RunOptions.DEFAULT,
)

val LocalPageSettings = staticCompositionLocalOf { PageSettings() }

private val CenteredLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private enum class SettingsCategory(val label: String) {
    AUTO_SAVE("AutoSave"),
    EDITOR("Editor"),
    LSP("LSP"),
    AUTO_INPUT("AutoInput"),
    UI("UI"),
    RUN("Run"),
}

@Composable
internal fun SettingsPanel(
    settings: PageSettings,
    onApply: (PageSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var selected by remember { mutableStateOf(SettingsCategory.AUTO_SAVE) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val colors = Glass.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Glass.radius.lg))
            .background(colors.surface)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false
                else when (event.key) {
                    Key.Escape -> { onClose(); true }
                    Key.Enter, Key.NumPadEnter -> { onApply(draft); onClose(); true }
                    else -> false
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settings",
                color = colors.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = Glass.type.title,
            )
            Spacer(Modifier.weight(1f))
            CloseButton(onClose)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SettingsSidebar(
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.width(168.dp).fillMaxHeight(),
            )
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(colors.separator))
            SettingsDetailPane(
                category = selected,
                draft = draft,
                onChange = { draft = it },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(label = "Cancel", primary = false, onClick = onClose)
            Spacer(Modifier.width(8.dp))
            GlassButton(label = "Apply", primary = false, onClick = { onApply(draft) })
            Spacer(Modifier.width(8.dp))
            GlassButton(label = "Apply & Close", primary = true, onClick = { onApply(draft); onClose() })
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(Glass.radius.sm))
            .background(if (hovered) colors.surfaceL2 else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "✕", color = if (hovered) colors.text else colors.muted, fontSize = Glass.type.body)
    }
}

@Composable
private fun SettingsSidebar(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(Glass.colors.surfaceL1).padding(8.dp)) {
        for (cat in SettingsCategory.values()) {
            SidebarRow(label = cat.label, selected = cat == selected, onClick = { onSelect(cat) })
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun SidebarRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        targetValue = when {
            selected -> colors.primarySoft
            hovered -> colors.surfaceL3
            else -> colors.surfaceL3.copy(alpha = 0f)
        },
        animationSpec = tween(120),
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.muted,
        animationSpec = tween(120),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Glass.radius.sm))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = Glass.type.ui,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun SettingsDetailPane(
    category: SettingsCategory,
    draft: PageSettings,
    onChange: (PageSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            text = category.label,
            color = Glass.colors.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = Glass.type.title,
        )
        Spacer(Modifier.height(14.dp))
        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            when (category) {
                SettingsCategory.AUTO_SAVE -> AutoSaveSection(draft.autoSave) { onChange(draft.copy(autoSave = it)) }
                SettingsCategory.EDITOR -> EditorSection(draft.editor) { onChange(draft.copy(editor = it)) }
                SettingsCategory.LSP -> LspSection(draft.lsp) { onChange(draft.copy(lsp = it)) }
                SettingsCategory.AUTO_INPUT -> AutoInputSection(draft.autoInput) { onChange(draft.copy(autoInput = it)) }
                SettingsCategory.UI -> UiSection(draft.ui) { onChange(draft.copy(ui = it)) }
                SettingsCategory.RUN -> RunSection(draft.run) { onChange(draft.copy(run = it)) }
            }
        }
    }
}

@Composable
private fun AutoSaveSection(o: AutoSaveOptions, onChange: (AutoSaveOptions) -> Unit) {
    CheckRow(
        label = "Save on window deactivation",
        description = "Save all dirty tabs when PAGE loses focus.",
        checked = o.onFocusLost,
        onToggle = { onChange(o.copy(onFocusLost = !o.onFocusLost)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Save while idle",
        description = "Save after N seconds of no input. 0 = disabled.",
        checked = o.idleSeconds > 0,
        onToggle = { onChange(o.copy(idleSeconds = if (o.idleSeconds > 0) 0 else 15)) },
    )
    Spacer(Modifier.height(8.dp))
    NumberField(
        label = "Seconds",
        value = o.idleSeconds.toString(),
        onChange = { v -> onChange(o.copy(idleSeconds = v.toIntOrNull()?.coerceIn(0, 3600) ?: o.idleSeconds)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Save before Run",
        description = "Always flush to disk before running.",
        checked = o.beforeRun,
        onToggle = { onChange(o.copy(beforeRun = !o.beforeRun)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Save on tab close",
        description = "Save a dirty tab silently when closing instead of prompting.",
        checked = o.onClose,
        onToggle = { onChange(o.copy(onClose = !o.onClose)) },
    )
}

@Composable
private fun EditorSection(o: EditorOptions, onChange: (EditorOptions) -> Unit) {
    NumberField(
        label = "Font size (sp)",
        value = o.fontSize.toString(),
        onChange = { v -> onChange(o.copy(fontSize = v.toIntOrNull()?.coerceIn(8, 48) ?: o.fontSize)) },
    )
    Spacer(Modifier.height(8.dp))
    NumberField(
        label = "Tab size",
        value = o.tabSize.toString(),
        onChange = { v -> onChange(o.copy(tabSize = v.toIntOrNull()?.coerceIn(1, 16) ?: o.tabSize)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Insert spaces for Tab",
        description = "Use space characters when Tab is pressed (off = literal \\t).",
        checked = o.useSpacesForTab,
        onToggle = { onChange(o.copy(useSpacesForTab = !o.useSpacesForTab)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Show line numbers",
        description = "Display line numbers in the left gutter.",
        checked = o.showLineNumbers,
        onToggle = { onChange(o.copy(showLineNumbers = !o.showLineNumbers)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Minimap",
        description = "Show minimap on the right (applies after restart).",
        checked = o.showMinimap,
        onToggle = { onChange(o.copy(showMinimap = !o.showMinimap)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Highlight current line",
        description = "Tint the row containing the caret.",
        checked = o.highlightCurrentLine,
        onToggle = { onChange(o.copy(highlightCurrentLine = !o.highlightCurrentLine)) },
    )
}

@Composable
private fun LspSection(o: LspOptions, onChange: (LspOptions) -> Unit) {
    CheckRow(
        label = "Show inlay hints",
        description = "Inline hints from the LSP (parameter names, types).",
        checked = o.showInlayHints,
        onToggle = { onChange(o.copy(showInlayHints = !o.showInlayHints)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Trigger completion mid-word",
        description = "Show completion menu when typing inside an existing word.",
        checked = o.triggerCompletionMidWord,
        onToggle = { onChange(o.copy(triggerCompletionMidWord = !o.triggerCompletionMidWord)) },
    )
    Spacer(Modifier.height(8.dp))
    NumberField(
        label = "Hover delay (ms)",
        value = o.hoverDelayMs.toString(),
        onChange = { v -> onChange(o.copy(hoverDelayMs = v.toIntOrNull()?.coerceIn(0, 5000) ?: o.hoverDelayMs)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Inline diagnostics",
        description = "Draw errors/warnings directly in the editor (Problems panel stays available).",
        checked = o.showInlineDiagnostics,
        onToggle = { onChange(o.copy(showInlineDiagnostics = !o.showInlineDiagnostics)) },
    )
    Spacer(Modifier.height(18.dp))
    SectionLabel("Problems panel scope")
    Spacer(Modifier.height(3.dp))
    Hint("Which files' errors/warnings the Problems panel lists. Inline squiggles always follow the focused file.")
    Spacer(Modifier.height(8.dp))
    Row {
        ChoiceChip(
            label = "Current file",
            selected = o.diagnosticsScope == DiagnosticsScope.CURRENT_FILE,
            onClick = { onChange(o.copy(diagnosticsScope = DiagnosticsScope.CURRENT_FILE)) },
        )
        Spacer(Modifier.width(6.dp))
        ChoiceChip(
            label = "Open tabs",
            selected = o.diagnosticsScope == DiagnosticsScope.OPEN_TABS,
            onClick = { onChange(o.copy(diagnosticsScope = DiagnosticsScope.OPEN_TABS)) },
        )
    }
    Spacer(Modifier.height(18.dp))
    SectionLabel("Server executable overrides")
    Spacer(Modifier.height(3.dp))
    Hint("Point PAGE at a specific LSP binary. Empty = auto-detect (PAGE installer → PATH).")
    Spacer(Modifier.height(8.dp))
    val backends = remember { LspBackends.all().sortedBy { it.displayName } }
    for (backend in backends) {
        PathField(
            label = backend.displayName,
            value = o.serverPaths[backend.id].orEmpty(),
            onChange = { v ->
                val next = o.serverPaths.toMutableMap()
                if (v.isBlank()) next.remove(backend.id) else next[backend.id] = v
                onChange(o.copy(serverPaths = next))
            },
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun AutoInputSection(o: AutoInputOptions, onChange: (AutoInputOptions) -> Unit) {
    CheckRow(
        label = "Auto-pair brackets and quotes",
        description = "Insert the matching closer for ( [ { \" '.",
        checked = o.pairs,
        onToggle = { onChange(o.copy(pairs = !o.pairs)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Auto-close HTML tags",
        description = "In .html/.htm, typing <div> inserts </div>.",
        checked = o.htmlTags,
        onToggle = { onChange(o.copy(htmlTags = !o.htmlTags)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Backspace deletes empty pair",
        description = "Backspace inside (|) removes both characters.",
        checked = o.backspaceDeletesPair,
        onToggle = { onChange(o.copy(backspaceDeletesPair = !o.backspaceDeletesPair)) },
    )
}

@Composable
private fun UiSection(o: UiOptions, onChange: (UiOptions) -> Unit) {
    SectionLabel("Glass palette")
    Spacer(Modifier.height(8.dp))
    Row {
        for (p in GlassPalette.values()) {
            ChoiceChip(label = p.name, selected = p == o.palette, onClick = { onChange(o.copy(palette = p)) })
            Spacer(Modifier.width(6.dp))
        }
    }
    Spacer(Modifier.height(16.dp))
    NumberField(
        label = "Sidebar width (dp)",
        value = o.sidebarWidth.toString(),
        onChange = { v -> onChange(o.copy(sidebarWidth = v.toIntOrNull()?.coerceIn(120, 800) ?: o.sidebarWidth)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Show tab close button",
        description = "Display the × button on tabs.",
        checked = o.showTabCloseButton,
        onToggle = { onChange(o.copy(showTabCloseButton = !o.showTabCloseButton)) },
    )
}

@Composable
private fun RunSection(o: RunOptions, onChange: (RunOptions) -> Unit) {
    CheckRow(
        label = "Clear Output on Run",
        description = "Wipe previous run output before starting.",
        checked = o.clearOutputOnRun,
        onToggle = { onChange(o.copy(clearOutputOnRun = !o.clearOutputOnRun)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Open Terminal on Run",
        description = "Reveal the terminal panel when Run starts.",
        checked = o.openTerminalOnRun,
        onToggle = { onChange(o.copy(openTerminalOnRun = !o.openTerminalOnRun)) },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, color = Glass.colors.text, fontSize = Glass.type.ui, fontWeight = FontWeight.Medium)
}

@Composable
private fun Hint(text: String) {
    Text(text = text, color = Glass.colors.muted, fontSize = Glass.type.label)
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        targetValue = when {
            selected -> colors.primarySoft
            hovered -> colors.surfaceL3
            else -> colors.surfaceL2
        },
        animationSpec = tween(120),
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.outline,
        animationSpec = tween(120),
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.text,
        animationSpec = tween(120),
    )
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(Glass.radius.sm))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(Glass.radius.sm))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = Glass.type.label,
            style = TextStyle(lineHeight = Glass.type.label, lineHeightStyle = CenteredLineHeight),
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    FieldRow(label = label, fieldWidth = 96.dp) {
        BasicTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(4)) },
            singleLine = true,
            cursorBrush = SolidColor(Glass.colors.primary),
            textStyle = LocalTextStyle.current.copy(
                fontSize = Glass.type.ui,
                color = Glass.colors.text,
                lineHeight = Glass.type.ui,
                lineHeightStyle = CenteredLineHeight,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun PathField(label: String, value: String, onChange: (String) -> Unit) {
    FieldRow(label = label, fieldWidth = null) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            cursorBrush = SolidColor(Glass.colors.primary),
            textStyle = LocalTextStyle.current.copy(
                fontSize = Glass.type.ui,
                color = Glass.colors.text,
                lineHeight = Glass.type.ui,
                lineHeightStyle = CenteredLineHeight,
            ),
        )
    }
}

@Composable
private fun FieldRow(label: String, fieldWidth: androidx.compose.ui.unit.Dp?, field: @Composable () -> Unit) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val hoverBg = colors.surfaceL2.copy(alpha = colors.surfaceL2.alpha * 0.6f)
    val rowBg by animateColorAsState(
        targetValue = if (hovered) hoverBg else hoverBg.copy(alpha = 0f),
        animationSpec = tween(120),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Glass.radius.sm))
            .background(rowBg)
            .hoverable(interaction)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.muted,
            fontSize = Glass.type.label,
            style = TextStyle(lineHeight = Glass.type.label, lineHeightStyle = CenteredLineHeight),
            modifier = Modifier.width(150.dp),
        )
        Box(
            modifier = Modifier
                .let { if (fieldWidth != null) it.width(fieldWidth) else it.weight(1f) }
                .height(30.dp)
                .clip(RoundedCornerShape(Glass.radius.xs))
                .background(colors.surfaceL2)
                .border(1.dp, colors.outline, RoundedCornerShape(Glass.radius.xs))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            field()
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val checkColor = colors.onPrimary
    val hoverBg = colors.surfaceL2.copy(alpha = colors.surfaceL2.alpha * 0.6f)
    val rowBg by animateColorAsState(
        targetValue = if (hovered) hoverBg else hoverBg.copy(alpha = 0f),
        animationSpec = tween(120),
    )
    val boxBg by animateColorAsState(
        targetValue = if (checked) colors.primary else colors.surfaceL2,
        animationSpec = tween(140),
    )
    val boxBorder by animateColorAsState(
        targetValue = if (checked) colors.primary else colors.outline,
        animationSpec = tween(140),
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(160),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Glass.radius.sm))
            .background(rowBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(Glass.radius.xs))
                .background(boxBg)
                .border(1.dp, boxBorder, RoundedCornerShape(Glass.radius.xs)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(11.dp)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                        alpha = checkScale
                    },
            ) {
                val w = size.width
                val h = size.height
                val stroke = w * 0.16f
                drawLine(
                    color = checkColor,
                    start = Offset(w * 0.16f, h * 0.54f),
                    end = Offset(w * 0.40f, h * 0.78f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = checkColor,
                    start = Offset(w * 0.40f, h * 0.78f),
                    end = Offset(w * 0.84f, h * 0.24f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                color = colors.text,
                fontSize = Glass.type.ui,
                style = TextStyle(lineHeight = Glass.type.ui, lineHeightStyle = CenteredLineHeight),
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = description,
                color = colors.muted,
                fontSize = Glass.type.label,
                style = TextStyle(lineHeight = Glass.type.label, lineHeightStyle = CenteredLineHeight),
            )
        }
    }
}

@Composable
private fun GlassButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        targetValue = when {
            primary && hovered -> colors.primary.copy(alpha = colors.primary.alpha * 0.85f)
            primary -> colors.primary
            hovered -> colors.surfaceL3
            else -> colors.surfaceL2
        },
        animationSpec = tween(120),
    )
    Box(
        modifier = Modifier
            .height(30.dp)
            .defaultMinSize(minWidth = 84.dp)
            .clip(RoundedCornerShape(Glass.radius.sm))
            .background(bg)
            .border(1.dp, if (primary) Color.Transparent else colors.outline, RoundedCornerShape(Glass.radius.sm))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) colors.onPrimary else colors.text,
            fontSize = Glass.type.ui,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
            style = TextStyle(lineHeight = Glass.type.ui, lineHeightStyle = CenteredLineHeight),
        )
    }
}
