package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.lsp.LspBackends
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

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false
                else when (event.key) {
                    Key.Escape -> { onClose(); true }
                    Key.Enter, Key.NumPadEnter -> { onApply(draft); true }
                    else -> false
                }
            }
    ) {
        SettingsSidebar(
            selected = selected,
            onSelect = { selected = it },
            onClose = onClose,
            modifier = Modifier.width(180.dp).fillMaxHeight(),
        )
        Box(
            modifier = Modifier.width(1.dp).fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
        SettingsDetailPane(
            category = selected,
            draft = draft,
            onChange = { draft = it },
            onApply = { onApply(draft) },
            onCancel = onClose,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun SettingsSidebar(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settings",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                style = LocalTextStyle.current.copy(
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "×",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                style = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    lineHeight = 16.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
                modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 6.dp),
            )
        }
        for (cat in SettingsCategory.values()) {
            SidebarRow(label = cat.label, selected = cat == selected, onClick = { onSelect(cat) })
        }
    }
}

@Composable
private fun SidebarRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = fg,
            style = LocalTextStyle.current.copy(
                fontSize = 12.sp,
                lineHeight = 12.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

@Composable
private fun SettingsDetailPane(
    category: SettingsCategory,
    draft: PageSettings,
    onChange: (PageSettings) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            text = category.label,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            style = LocalTextStyle.current.copy(
                fontSize = 14.sp,
                lineHeight = 14.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DialogButton(label = "Cancel", primary = false, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            DialogButton(label = "Apply", primary = true, onClick = onApply)
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
    Spacer(Modifier.height(10.dp))
    CheckRow(
        label = "Save while idle",
        description = "Save after N seconds of no input. 0 = disabled.",
        checked = o.idleSeconds > 0,
        onToggle = { onChange(o.copy(idleSeconds = if (o.idleSeconds > 0) 0 else 15)) },
    )
    Spacer(Modifier.height(6.dp))
    NumberField(
        label = "Seconds",
        value = o.idleSeconds.toString(),
        onChange = { v -> onChange(o.copy(idleSeconds = v.toIntOrNull()?.coerceIn(0, 3600) ?: o.idleSeconds)) },
    )
    Spacer(Modifier.height(10.dp))
    CheckRow(
        label = "Save before Run",
        description = "Always flush to disk before running.",
        checked = o.beforeRun,
        onToggle = { onChange(o.copy(beforeRun = !o.beforeRun)) },
    )
    Spacer(Modifier.height(10.dp))
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
    Spacer(Modifier.height(10.dp))
    NumberField(
        label = "Tab size",
        value = o.tabSize.toString(),
        onChange = { v -> onChange(o.copy(tabSize = v.toIntOrNull()?.coerceIn(1, 16) ?: o.tabSize)) },
    )
    Spacer(Modifier.height(10.dp))
    CheckRow(
        label = "Insert spaces for Tab",
        description = "Use space characters when Tab is pressed (off = literal \\t).",
        checked = o.useSpacesForTab,
        onToggle = { onChange(o.copy(useSpacesForTab = !o.useSpacesForTab)) },
    )
    Spacer(Modifier.height(10.dp))
    CheckRow(
        label = "Show line numbers",
        description = "Display line numbers in the left gutter.",
        checked = o.showLineNumbers,
        onToggle = { onChange(o.copy(showLineNumbers = !o.showLineNumbers)) },
    )
    Spacer(Modifier.height(10.dp))
    CheckRow(
        label = "Minimap",
        description = "Show minimap on the right (applies after restart).",
        checked = o.showMinimap,
        onToggle = { onChange(o.copy(showMinimap = !o.showMinimap)) },
    )
    Spacer(Modifier.height(10.dp))
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
    Spacer(Modifier.height(10.dp))
    CheckRow(
        label = "Trigger completion mid-word",
        description = "Show completion menu when typing inside an existing word.",
        checked = o.triggerCompletionMidWord,
        onToggle = { onChange(o.copy(triggerCompletionMidWord = !o.triggerCompletionMidWord)) },
    )
    Spacer(Modifier.height(10.dp))
    NumberField(
        label = "Hover delay (ms)",
        value = o.hoverDelayMs.toString(),
        onChange = { v -> onChange(o.copy(hoverDelayMs = v.toIntOrNull()?.coerceIn(0, 5000) ?: o.hoverDelayMs)) },
    )
    Spacer(Modifier.height(10.dp))
    CheckRow(
        label = "Inline diagnostics",
        description = "Draw errors/warnings directly in the editor (Problems panel stays available).",
        checked = o.showInlineDiagnostics,
        onToggle = { onChange(o.copy(showInlineDiagnostics = !o.showInlineDiagnostics)) },
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Server executable overrides",
        color = MaterialTheme.colorScheme.onSurface,
        style = LocalTextStyle.current.copy(
            fontSize = 12.sp,
            lineHeight = 14.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = "Point PAGE at a specific LSP binary. Empty = auto-detect (PAGE installer → PATH).",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = LocalTextStyle.current.copy(
            fontSize = 10.sp,
            lineHeight = 12.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )
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
    Spacer(Modifier.height(10.dp))
    CheckRow(
        label = "Auto-close HTML tags",
        description = "In .html/.htm, typing <div> inserts </div>.",
        checked = o.htmlTags,
        onToggle = { onChange(o.copy(htmlTags = !o.htmlTags)) },
    )
    Spacer(Modifier.height(10.dp))
    CheckRow(
        label = "Backspace deletes empty pair",
        description = "Backspace inside (|) removes both characters.",
        checked = o.backspaceDeletesPair,
        onToggle = { onChange(o.copy(backspaceDeletesPair = !o.backspaceDeletesPair)) },
    )
}

@Composable
private fun UiSection(o: UiOptions, onChange: (UiOptions) -> Unit) {
    Text(
        text = "Glass palette",
        color = MaterialTheme.colorScheme.onSurface,
        style = LocalTextStyle.current.copy(
            fontSize = 12.sp,
            lineHeight = 14.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )
    Spacer(Modifier.height(6.dp))
    Row {
        for (p in GlassPalette.values()) {
            PaletteChip(p = p, selected = p == o.palette, onClick = { onChange(o.copy(palette = p)) })
            Spacer(Modifier.width(6.dp))
        }
    }
    Spacer(Modifier.height(14.dp))
    NumberField(
        label = "Sidebar width (dp)",
        value = o.sidebarWidth.toString(),
        onChange = { v -> onChange(o.copy(sidebarWidth = v.toIntOrNull()?.coerceIn(120, 800) ?: o.sidebarWidth)) },
    )
    Spacer(Modifier.height(10.dp))
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
    Spacer(Modifier.height(10.dp))
    CheckRow(
        label = "Open Terminal on Run",
        description = "Reveal the terminal panel when Run starts.",
        checked = o.openTerminalOnRun,
        onToggle = { onChange(o.copy(openTerminalOnRun = !o.openTerminalOnRun)) },
    )
}

@Composable
private fun PaletteChip(p: GlassPalette, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .height(24.dp)
            .background(bg)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = p.name,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = LocalTextStyle.current.copy(
                fontSize = 11.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = LocalTextStyle.current.copy(
                fontSize = 11.sp,
                lineHeight = 13.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            modifier = Modifier.width(140.dp),
        )
        Box(
            modifier = Modifier
                .width(90.dp)
                .height(26.dp)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(4)) },
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

@Composable
private fun PathField(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = LocalTextStyle.current.copy(
                fontSize = 11.sp,
                lineHeight = 13.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            modifier = Modifier.width(140.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(26.dp)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(16.dp)
                .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = LocalTextStyle.current.copy(
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = LocalTextStyle.current.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = LocalTextStyle.current.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun DialogButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(if (primary) 100.dp else 80.dp)
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            style = LocalTextStyle.current.copy(
                fontSize = 11.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}
