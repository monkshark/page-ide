package page.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardAlt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.lsp.LspBackends
import page.shared.syntax.SyntaxPreset
import page.ui.Glass
import page.ui.GlassPalette
import page.ui.glassTokensFor

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

internal enum class SettingsCategory(
    val label: String,
    val group: String,
    val icon: ImageVector,
    val blurb: String,
    val showsCode: Boolean = false,
) {
    EDITING(
        "Editing", "Editor", Icons.AutoMirrored.Outlined.Notes,
        "How text behaves while you write it.",
        showsCode = true,
    ),
    TYPING(
        "Typing", "Editor", Icons.Outlined.Code,
        "What PAGE inserts for you as you type.",
    ),
    AUTO_SAVE(
        "Auto save", "Editor", Icons.Outlined.Save,
        "When a dirty tab is flushed to disk.",
    ),
    THEME(
        "Theme", "Appearance", Icons.Outlined.Palette,
        "The colors of every surface, and how the code area is drawn.",
        showsCode = true,
    ),
    CODE_STYLE(
        "Code style", "Appearance", Icons.Outlined.Brush,
        "How the code itself is colored and marked up.",
        showsCode = true,
    ),
    SHORTCUTS(
        "Shortcuts", "Appearance", Icons.Outlined.KeyboardAlt,
        "Every action and the keys that reach it.",
    ),
    LANGUAGE_SERVERS(
        "Language servers", "Tools", Icons.Outlined.Extension,
        "Completion, diagnostics, and where the binaries live.",
    ),
    RUN(
        "Run", "Tools", Icons.Outlined.PlayArrow,
        "What happens when a run starts.",
    ),
}

internal data class SettingsHit(val category: SettingsCategory, val label: String)

internal val SETTINGS_INDEX: List<SettingsHit> = listOf(
    SettingsHit(SettingsCategory.EDITING, "Tab size"),
    SettingsHit(SettingsCategory.EDITING, "Insert spaces for Tab"),
    SettingsHit(SettingsCategory.TYPING, "Auto-pair brackets and quotes"),
    SettingsHit(SettingsCategory.TYPING, "Auto-close HTML tags"),
    SettingsHit(SettingsCategory.TYPING, "Backspace deletes empty pair"),
    SettingsHit(SettingsCategory.AUTO_SAVE, "Save on window deactivation"),
    SettingsHit(SettingsCategory.AUTO_SAVE, "Save while idle"),
    SettingsHit(SettingsCategory.AUTO_SAVE, "Save before Run"),
    SettingsHit(SettingsCategory.AUTO_SAVE, "Save on tab close"),
    SettingsHit(SettingsCategory.THEME, "Glass palette"),
    SettingsHit(SettingsCategory.THEME, "Sidebar width"),
    SettingsHit(SettingsCategory.THEME, "Show tab close button"),
    SettingsHit(SettingsCategory.THEME, "Font size"),
    SettingsHit(SettingsCategory.THEME, "Show line numbers"),
    SettingsHit(SettingsCategory.THEME, "Minimap"),
    SettingsHit(SettingsCategory.THEME, "Highlight current line"),
    SettingsHit(SettingsCategory.CODE_STYLE, "Syntax colors"),
    SettingsHit(SettingsCategory.CODE_STYLE, "Scope guides"),
    SettingsHit(SettingsCategory.CODE_STYLE, "Rainbow brackets"),
    SettingsHit(SettingsCategory.CODE_STYLE, "Dim unused symbols"),
    SettingsHit(SettingsCategory.CODE_STYLE, "Highlight identifier under caret"),
    SettingsHit(SettingsCategory.SHORTCUTS, "Keyboard shortcuts"),
    SettingsHit(SettingsCategory.LANGUAGE_SERVERS, "Show inlay hints"),
    SettingsHit(SettingsCategory.LANGUAGE_SERVERS, "Trigger completion mid-word"),
    SettingsHit(SettingsCategory.LANGUAGE_SERVERS, "Hover delay"),
    SettingsHit(SettingsCategory.LANGUAGE_SERVERS, "Inline diagnostics"),
    SettingsHit(SettingsCategory.LANGUAGE_SERVERS, "Problems panel scope"),
    SettingsHit(SettingsCategory.LANGUAGE_SERVERS, "Server executable overrides"),
    SettingsHit(SettingsCategory.RUN, "Clear Output on Run"),
    SettingsHit(SettingsCategory.RUN, "Open Terminal on Run"),
)

internal fun searchSettings(query: String): List<SettingsHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return SETTINGS_INDEX.filter {
        it.label.contains(q, ignoreCase = true) || it.category.label.contains(q, ignoreCase = true)
    }
}

@Composable
internal fun SettingsPanel(
    settings: PageSettings,
    onApply: (PageSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val opened = remember { settings }
    var selected by remember { mutableStateOf(SettingsCategory.EDITING) }
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val colors = Glass.colors
    val dirty = settings != opened
    val revert = { if (dirty) onApply(opened) }
    val hits = remember(query) { searchSettings(query) }

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
                    Key.Escape, Key.Enter, Key.NumPadEnter -> { onClose(); true }
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
            SearchField(value = query, onChange = { query = it })
            Spacer(Modifier.width(8.dp))
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
            if (query.isNotBlank()) {
                SearchResults(
                    query = query,
                    hits = hits,
                    onPick = { hit ->
                        selected = hit.category
                        query = ""
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            } else {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SettingsDetailPane(
                        category = selected,
                        draft = settings,
                        onChange = onApply,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    if (selected.showsCode) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
                        SettingsPreview(
                            editor = settings.editor,
                            modifier = Modifier.fillMaxWidth().height(previewHeightFor(settings.editor)),
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Changes apply as you edit",
                color = colors.faint,
                fontSize = Glass.type.label,
                style = TextStyle(lineHeight = Glass.type.label, lineHeightStyle = CenteredLineHeight),
            )
            Spacer(Modifier.weight(1f))
            GlassButton(label = "Revert", primary = false, enabled = dirty, onClick = { revert() })
            Spacer(Modifier.width(8.dp))
            GlassButton(label = "Done", primary = true, onClick = onClose)
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val colors = Glass.colors
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(Glass.radius.sm))
            .background(colors.surfaceL1)
            .border(1.dp, colors.outline, RoundedCornerShape(Glass.radius.sm))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(text = "Search settings", color = colors.faint, fontSize = Glass.type.label)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            textStyle = TextStyle(color = colors.text, fontSize = Glass.type.label),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SearchResults(
    query: String,
    hits: List<SettingsHit>,
    onPick: (SettingsHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Glass.colors
    val scroll = rememberScrollState()
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            text = if (hits.isEmpty()) "Nothing matches \"$query\"" else "${hits.size} matching",
            color = colors.muted,
            fontSize = Glass.type.label,
        )
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            for (hit in hits) {
                SearchResultRow(hit = hit, onClick = { onPick(hit) })
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun SearchResultRow(hit: SettingsHit, onClick: () -> Unit) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Glass.radius.sm))
            .background(if (hovered) colors.surfaceL2 else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = hit.category.icon,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = hit.label, color = colors.text, fontSize = Glass.type.ui)
        Spacer(Modifier.weight(1f))
        Text(text = hit.category.label, color = colors.faint, fontSize = Glass.type.label)
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
        page.ui.CloseMark(tint = if (hovered) colors.text else colors.muted, size = 12.dp)
    }
}

@Composable
private fun SettingsSidebar(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(Glass.colors.surfaceL1).padding(8.dp)) {
        var lastGroup: String? = null
        for (cat in SettingsCategory.values()) {
            if (cat.group != lastGroup) {
                GroupLabel(cat.group, first = lastGroup == null)
                lastGroup = cat.group
            }
            SidebarRow(cat = cat, selected = cat == selected, onClick = { onSelect(cat) })
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun GroupLabel(text: String, first: Boolean) {
    Text(
        text = text,
        color = Glass.colors.muted,
        fontSize = Glass.type.ui,
        modifier = Modifier.padding(start = 10.dp, top = if (first) 4.dp else 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun SidebarRow(cat: SettingsCategory, selected: Boolean, onClick: () -> Unit) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        targetValue = when {
            selected -> colors.primarySoft
            hovered -> colors.surfaceRaised
            else -> colors.surfaceRaised.copy(alpha = 0f)
        },
        animationSpec = tween(120),
    )
    val fg by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.muted,
        animationSpec = tween(120),
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp)
                    .size(width = 2.5.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accent),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Glass.radius.sm))
                .background(bg)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = cat.icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = cat.label,
                color = fg,
                fontSize = Glass.type.ui,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                style = TextStyle(lineHeight = Glass.type.ui, lineHeightStyle = CenteredLineHeight),
            )
        }
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
        Spacer(Modifier.height(2.dp))
        Hint(category.blurb)
        Spacer(Modifier.height(14.dp))
        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            when (category) {
                SettingsCategory.AUTO_SAVE -> AutoSaveSection(draft.autoSave) { onChange(draft.copy(autoSave = it)) }
                SettingsCategory.EDITING -> EditorSection(draft.editor) { onChange(draft.copy(editor = it)) }
                SettingsCategory.LANGUAGE_SERVERS -> LspSection(draft.lsp) { onChange(draft.copy(lsp = it)) }
                SettingsCategory.TYPING -> AutoInputSection(draft.autoInput) { onChange(draft.copy(autoInput = it)) }
                SettingsCategory.THEME -> ThemeSection(
                    o = draft.ui,
                    e = draft.editor,
                    onChange = { onChange(draft.copy(ui = it)) },
                    onEditorChange = { onChange(draft.copy(editor = it)) },
                )
                SettingsCategory.CODE_STYLE -> CodeStyleSection(draft.editor) { onChange(draft.copy(editor = it)) }
                SettingsCategory.RUN -> RunSection(draft.run) { onChange(draft.copy(run = it)) }
                SettingsCategory.SHORTCUTS -> KeymapSection()
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
        value = o.idleSeconds,
        min = 0,
        max = 3600,
        onChange = { v -> onChange(o.copy(idleSeconds = v)) },
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
        label = "Tab size",
        value = o.tabSize,
        min = 1,
        max = 16,
        onChange = { v -> onChange(o.copy(tabSize = v)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Insert spaces for Tab",
        description = "Use space characters when Tab is pressed (off = literal \t).",
        checked = o.useSpacesForTab,
        onToggle = { onChange(o.copy(useSpacesForTab = !o.useSpacesForTab)) },
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
        value = o.hoverDelayMs,
        min = 0,
        max = 5000,
        onChange = { v -> onChange(o.copy(hoverDelayMs = v)) },
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
private fun ThemeSection(
    o: UiOptions,
    e: EditorOptions,
    onChange: (UiOptions) -> Unit,
    onEditorChange: (EditorOptions) -> Unit,
) {
    SectionLabel("Glass palette")
    Spacer(Modifier.height(3.dp))
    Hint("The token set that colors every surface, syntax, and control.")
    Spacer(Modifier.height(10.dp))
    val palettes = GlassPalette.values().toList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (rowPalettes in palettes.chunked(5)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in rowPalettes) {
                    PaletteSwatch(
                        palette = p,
                        selected = p == o.palette,
                        onClick = { onChange(o.copy(palette = p)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(5 - rowPalettes.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    SectionLabel("Window")
    Spacer(Modifier.height(8.dp))
    NumberField(
        label = "Sidebar width (dp)",
        value = o.sidebarWidth,
        min = 120,
        max = 800,
        onChange = { v -> onChange(o.copy(sidebarWidth = v)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Show tab close button",
        description = "Display the × button on tabs.",
        checked = o.showTabCloseButton,
        onToggle = { onChange(o.copy(showTabCloseButton = !o.showTabCloseButton)) },
    )

    Spacer(Modifier.height(20.dp))
    SectionLabel("Editor surface")
    Spacer(Modifier.height(3.dp))
    Hint("How the code area itself is drawn.")
    Spacer(Modifier.height(10.dp))
    NumberField(
        label = "Font size (sp)",
        value = e.fontSize,
        min = 8,
        max = 48,
        onChange = { v -> onEditorChange(e.copy(fontSize = v)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Show line numbers",
        description = "Display line numbers in the left gutter.",
        checked = e.showLineNumbers,
        onToggle = { onEditorChange(e.copy(showLineNumbers = !e.showLineNumbers)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Minimap",
        description = "Show minimap on the right (applies after restart).",
        checked = e.showMinimap,
        onToggle = { onEditorChange(e.copy(showMinimap = !e.showMinimap)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Highlight current line",
        description = "Tint the row containing the caret.",
        checked = e.highlightCurrentLine,
        onToggle = { onEditorChange(e.copy(highlightCurrentLine = !e.highlightCurrentLine)) },
    )
}

@Composable
private fun CodeStyleSection(e: EditorOptions, onEditorChange: (EditorOptions) -> Unit) {
    SectionLabel("Syntax colors")
    Spacer(Modifier.height(3.dp))
    Hint("How far the editor separates calls, members and parameters from plain names.")
    Spacer(Modifier.height(8.dp))
    Row {
        for (preset in SyntaxPreset.entries) {
            ChoiceChip(
                label = preset.label,
                selected = e.syntaxPreset == preset,
                onClick = { onEditorChange(e.copy(syntaxPreset = preset)) },
            )
            Spacer(Modifier.width(6.dp))
        }
    }
    Spacer(Modifier.height(18.dp))
    SectionLabel("Scope guides")
    Spacer(Modifier.height(3.dp))
    Hint("Vertical lines that connect a block to its closing brace.")
    Spacer(Modifier.height(8.dp))
    Row {
        for (mode in ScopeGuideMode.entries) {
            ChoiceChip(
                label = mode.label,
                selected = e.scopeGuides == mode,
                onClick = { onEditorChange(e.copy(scopeGuides = mode)) },
            )
            Spacer(Modifier.width(6.dp))
        }
    }
    Spacer(Modifier.height(18.dp))
    CheckRow(
        label = "Rainbow brackets",
        description = "Tint each bracket pair and its guide by nesting depth.",
        checked = e.rainbowBrackets,
        onToggle = { onEditorChange(e.copy(rainbowBrackets = !e.rainbowBrackets)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Dim unused symbols",
        description = "Fade locals and imports that nothing else in the file refers to.",
        checked = e.dimUnusedSymbols,
        onToggle = { onEditorChange(e.copy(dimUnusedSymbols = !e.dimUnusedSymbols)) },
    )
    Spacer(Modifier.height(8.dp))
    CheckRow(
        label = "Highlight identifier under caret",
        description = "Tint the other places the name at the caret appears.",
        checked = e.highlightIdentifierUnderCaret,
        onToggle = { onEditorChange(e.copy(highlightIdentifierUnderCaret = !e.highlightIdentifierUnderCaret)) },
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
private fun KeymapSection() {
    val grouped = remember {
        page.app.input.ActionCatalog.all
            .groupBy { it.context }
            .toSortedMap(compareBy { it.ordinal })
    }
    Hint(
        if (page.ui.Platform.isMac) "Keys shown for macOS."
        else "Keys shown for this machine."
    )
    for ((context, actions) in grouped) {
        Spacer(Modifier.height(10.dp))
        SectionLabel(keymapContextLabel(context))
        Spacer(Modifier.height(2.dp))
        for (action in actions.sortedWith(compareBy({ it.group.ordinal }, { it.label }))) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = action.label,
                    color = Glass.colors.text,
                    fontSize = Glass.type.label,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = action.group.name,
                    color = Glass.colors.faint,
                    fontSize = Glass.type.label,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = page.app.input.ActionCatalog.label(action) ?: "—",
                    color = Glass.colors.muted,
                    fontSize = Glass.type.label,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
    }
}

private fun keymapContextLabel(context: page.app.input.ActionContext): String = when (context) {
    page.app.input.ActionContext.Global -> "Anywhere"
    page.app.input.ActionContext.Editor -> "While editing"
    page.app.input.ActionContext.FileTree -> "In the file tree"
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, color = Glass.colors.text, fontSize = Glass.type.ui, fontWeight = FontWeight.Medium)
}

@Composable
private fun Hint(text: String) {
    Text(text = text, color = Glass.colors.muted, fontSize = Glass.type.label)
}

private fun paletteLabel(p: GlassPalette): String = when (p) {
    GlassPalette.SignatureLight -> "Signature Light"
    else -> p.name
}

@Composable
private fun PaletteSwatch(
    palette: GlassPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Glass.colors
    val c = remember(palette) { glassTokensFor(palette).color }
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.outline,
        animationSpec = tween(120),
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Glass.radius.md))
            .background(colors.surfaceL2)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(Glass.radius.md))
            .clickable(onClick = onClick),
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().height(26.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(c.background))
                Box(Modifier.weight(1f).fillMaxHeight().background(c.surface))
                Box(Modifier.weight(1f).fillMaxHeight().background(c.primary))
                Box(Modifier.weight(1f).fillMaxHeight().background(c.accent))
            }
            Text(
                text = paletteLabel(palette),
                color = colors.text,
                fontSize = Glass.type.label,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 6.dp),
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
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
private fun NumberField(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    fun commit() {
        val parsed = text.toIntOrNull()
        val clamped = (parsed ?: value).coerceIn(min, max)
        text = clamped.toString()
        if (clamped != value) onChange(clamped)
    }
    FieldRow(label = label, fieldWidth = 96.dp) {
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(4)
                text = digits
                val typed = digits.toIntOrNull()
                if (typed != null && typed in min..max && typed != value) onChange(typed)
            },
            singleLine = true,
            cursorBrush = SolidColor(Glass.colors.primary),
            textStyle = LocalTextStyle.current.copy(
                fontSize = Glass.type.ui,
                color = Glass.colors.text,
                lineHeight = Glass.type.ui,
                lineHeightStyle = CenteredLineHeight,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier.onFocusChanged { if (!it.isFocused) commit() },
        )
    }
}

@Composable
private fun PathField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    fun commit() {
        val trimmed = text.trim()
        text = trimmed
        if (trimmed != value) onChange(trimmed)
    }
    FieldRow(label = label, fieldWidth = null) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            cursorBrush = SolidColor(Glass.colors.primary),
            textStyle = LocalTextStyle.current.copy(
                fontSize = Glass.type.ui,
                color = Glass.colors.text,
                lineHeight = Glass.type.ui,
                lineHeightStyle = CenteredLineHeight,
            ),
            modifier = Modifier.onFocusChanged { if (!it.isFocused) commit() },
        )
    }
}

@Composable
private fun FieldRow(label: String, fieldWidth: androidx.compose.ui.unit.Dp?, field: @Composable () -> Unit) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val hoverBg = colors.surfaceRaised
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
    val hoverBg = colors.surfaceRaised
    val rowBg by animateColorAsState(
        targetValue = if (hovered) hoverBg else hoverBg.copy(alpha = 0f),
        animationSpec = tween(120),
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
        Column(modifier = Modifier.weight(1f)) {
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
        Spacer(Modifier.width(12.dp))
        ToggleSwitch(checked = checked)
    }
}

@Composable
private fun ToggleSwitch(checked: Boolean) {
    val colors = Glass.colors
    val trackBg by animateColorAsState(
        targetValue = if (checked) colors.primary else colors.surfaceL2,
        animationSpec = tween(150),
    )
    val trackBorder by animateColorAsState(
        targetValue = if (checked) colors.primary else colors.outline,
        animationSpec = tween(150),
    )
    val knobX by animateDpAsState(if (checked) 16.dp else 0.dp, tween(160))
    val knobColor by animateColorAsState(
        targetValue = if (checked) colors.onPrimary else colors.muted,
        animationSpec = tween(150),
    )
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(RoundedCornerShape(50))
            .background(trackBg)
            .border(1.dp, trackBorder, RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp)
                .offset(x = knobX)
                .size(16.dp)
                .clip(CircleShape)
                .background(knobColor),
        )
    }
}

@Composable
private fun GlassButton(
    label: String,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val active = hovered && enabled
    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> colors.surfaceL1
            primary && active -> colors.primary.copy(alpha = colors.primary.alpha * 0.85f)
            primary -> colors.primary
            active -> colors.surfaceL3
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
            .border(
                1.dp,
                when {
                    !enabled -> colors.separator
                    primary -> Color.Transparent
                    else -> colors.outline
                },
                RoundedCornerShape(Glass.radius.sm),
            )
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> colors.faint
                primary -> colors.onPrimary
                else -> colors.text
            },
            fontSize = Glass.type.ui,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
            style = TextStyle(lineHeight = Glass.type.ui, lineHeightStyle = CenteredLineHeight),
        )
    }
}
