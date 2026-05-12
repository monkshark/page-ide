package page.app

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import page.editor.BracketMatch
import page.editor.FoldRegions
import page.editor.Indent
import page.editor.MarkdownFence
import page.editor.SearchState
import page.editor.SyntaxLexer
import page.editor.TextBuffer
import page.editor.TextEdit
import page.editor.Token
import page.editor.TokenKind
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.launch
import page.lsp.CompletionItem as LspCompletionItem
import page.lsp.CompletionItemKind as LspCompletionItemKind
import page.lsp.CompletionList
import page.lsp.SnippetExpander
import page.lsp.DefinitionTarget
import page.lsp.Diagnostic
import page.lsp.DiagnosticSeverity
import page.lsp.HoverInfo
import page.lsp.enrichForPropertyDecl
import page.lsp.enrichWithKDocFromDefinition
import page.lsp.needsKdocEnrichment
import page.ui.CodeEditor
import page.ui.CompletionDisplay
import page.ui.EditorDecoration
import page.ui.EditorFontFamily
import page.ui.GlassDarkSyntax
import page.ui.SyntaxPalette

@Composable
fun EditorPanel(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    search: SearchState?,
    onQueryChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onToggleCase: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onSearchClose: () -> Unit,
    onWindowShortcut: (KeyEvent) -> Boolean,
    lexer: SyntaxLexer?,
    activePath: Path?,
    diagnostics: List<Diagnostic> = emptyList(),
    lspStatusText: String? = null,
    lspActivities: List<LspController.Activity> = emptyList(),
    onProblemsToggle: (() -> Unit)? = null,
    onRequestCompletion: ((line: Int, character: Int, triggerCharacter: String?) -> CompletableFuture<CompletionList>)? = null,
    onRequestHover: ((line: Int, character: Int) -> CompletableFuture<HoverInfo?>)? = null,
    onRequestDefinition: ((line: Int, character: Int) -> CompletableFuture<List<DefinitionTarget>>)? = null,
    onGoToDefinition: ((DefinitionTarget) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isMarkdown = remember(activePath) {
        val name = activePath?.fileName?.toString()?.lowercase()
        name != null && (name.endsWith(".md") || name.endsWith(".markdown"))
    }
    val buffer = remember(value.text) { TextBuffer(value.text) }
    val caretOffset = value.selection.start.coerceIn(0, buffer.length)
    val caret = buffer.lineColOf(caretOffset)

    val matchBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val currentLineBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
    val bracketBg = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
    val foldPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
    val foldPlaceholderBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
    val palette = GlassDarkSyntax

    val tokens = remember(value.text, lexer) {
        lexer?.tokenize(value.text).orEmpty()
    }

    val bracketMatch = remember(value.text, value.selection.start, value.selection.end) {
        if (value.selection.start != value.selection.end) null
        else BracketMatch.find(value.text, value.selection.start)
    }

    val errorColor = androidx.compose.ui.graphics.Color(0xFFE5484D)
    val warningColor = androidx.compose.ui.graphics.Color(0xFFE5C03A)
    val infoColor = MaterialTheme.colorScheme.primary
    val tabstopActiveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val tabstopPendingColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val diagnosticDecorations = remember(value.text, diagnostics) {
        diagnostics.mapNotNull { d ->
            val startOff = lineColToOffset(value.text, d.start.line, d.start.character)
            val endOff = lineColToOffset(value.text, d.end.line, d.end.character)
            if (startOff < 0 || endOff < 0 || endOff <= startOff) return@mapNotNull null
            val color = when (d.severity) {
                DiagnosticSeverity.ERROR -> errorColor
                DiagnosticSeverity.WARNING -> warningColor
                DiagnosticSeverity.INFO, DiagnosticSeverity.HINT -> infoColor
            }
            EditorDecoration(startOff, endOff, color, EditorDecoration.Style.WAVY_UNDERLINE)
        }
    }
    val errorCount = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val warningCount = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }

    val diagnosticRanges = remember(value.text, diagnostics) {
        diagnostics.mapNotNull { d ->
            val s = lineColToOffset(value.text, d.start.line, d.start.character)
            val e = lineColToOffset(value.text, d.end.line, d.end.character)
            if (s < 0 || e <= s) null else Triple(s, e, d)
        }
    }
    var pendingHoverDiagnostic by remember(diagnostics) { mutableStateOf<Diagnostic?>(null) }
    var hoverDiagnostic by remember(diagnostics) { mutableStateOf<Diagnostic?>(null) }
    var hoverOffset by remember(activePath) { mutableStateOf<Int?>(null) }
    var lspHoverInfo by remember(activePath) { mutableStateOf<HoverInfo?>(null) }
    var lspHoverRequestToken by remember(activePath) { mutableStateOf(0) }
    LaunchedEffect(pendingHoverDiagnostic) {
        val target = pendingHoverDiagnostic
        if (target == null) {
            hoverDiagnostic = null
        } else if (target !== hoverDiagnostic) {
            delay(250)
            hoverDiagnostic = target
        }
    }
    LaunchedEffect(hoverOffset, value.text) {
        val off = hoverOffset
        lspHoverInfo = null
        lspHoverRequestToken += 1
        val token = lspHoverRequestToken
        if (off == null) return@LaunchedEffect
        val cb = onRequestHover ?: return@LaunchedEffect
        val text = value.text
        val safeOff = off.coerceIn(0, text.length)
        val pos = TextBuffer(text).lineColOf(safeOff)
        delay(350)
        if (token != lspHoverRequestToken) return@LaunchedEffect
        cb(pos.line, pos.col).whenComplete { info, _ ->
            if (token != lspHoverRequestToken || info == null) return@whenComplete
            val lineText = TextBuffer(text).lineAt(pos.line)
            val enriched = info.enrichForPropertyDecl(lineText, pos.col)
            lspHoverInfo = enriched
            if (enriched.needsKdocEnrichment()) {
                val defCb = onRequestDefinition ?: return@whenComplete
                defCb(pos.line, pos.col).whenComplete { targets, _ ->
                    if (token != lspHoverRequestToken) return@whenComplete
                    val target = targets?.firstOrNull() ?: return@whenComplete
                    val defText = readDefinitionFileText(target.uri) ?: return@whenComplete
                    val final = enriched.enrichWithKDocFromDefinition(defText, target.startLine)
                    if (token == lspHoverRequestToken) lspHoverInfo = final
                }
            }
        }
    }

    val foldRegions = remember(value.text) { FoldRegions.detect(value.text) }
    var foldedRegions by remember(activePath) { mutableStateOf<Set<FoldRegions.Region>>(emptySet()) }
    val activeFolds = remember(foldedRegions, foldRegions) {
        val live = foldRegions.toSet()
        foldedRegions.filter { it in live }.toSet()
    }
    val foldSegments = remember(value.text, activeFolds) {
        FoldRegions.segmentsFor(value.text, activeFolds)
    }
    val foldStartByLine = remember(foldRegions) { foldRegions.associateBy { it.startLine } }
    val foldedStartLines = remember(activeFolds) { activeFolds.map { it.startLine }.toSet() }
    val hiddenLines = remember(activeFolds) {
        val set = mutableSetOf<Int>()
        for (r in activeFolds) for (l in (r.startLine + 1)..r.endLine) set.add(l)
        set
    }
    val severityByLine = remember(diagnostics) {
        val map = mutableMapOf<Int, DiagnosticSeverity>()
        for (d in diagnostics) {
            val ln = d.start.line
            val cur = map[ln]
            if (cur == null || severityRank(d.severity) < severityRank(cur)) {
                map[ln] = d.severity
            }
        }
        map.toMap()
    }
    val gutterLines = remember(buffer.lineCount, foldStartByLine, foldedStartLines, hiddenLines, severityByLine) {
        (0 until buffer.lineCount)
            .filter { it !in hiddenLines }
            .map { ln ->
                GutterLine(
                    originalLine = ln,
                    foldable = ln in foldStartByLine,
                    folded = ln in foldedStartLines,
                    severity = severityByLine[ln],
                )
            }
    }

    val visualTransformation = remember(
        search, tokens, bracketMatch, matchBg, activeBg, bracketBg, palette,
        foldSegments, foldPlaceholderColor, foldPlaceholderBg,
    ) {
        val matches = search?.matches.orEmpty()
        val activeIndex = search?.activeMatchIndex ?: -1
        if (tokens.isEmpty() && matches.isEmpty() && bracketMatch == null && foldSegments.isEmpty()) {
            VisualTransformation.None
        } else {
            CombinedHighlightTransformation(
                tokens = tokens,
                palette = palette,
                matches = matches,
                activeIndex = activeIndex,
                matchBg = matchBg,
                activeBg = activeBg,
                bracketMatch = bracketMatch,
                bracketBg = bracketBg,
                foldSegments = foldSegments,
                foldPlaceholderStyle = SpanStyle(
                    color = foldPlaceholderColor,
                    background = foldPlaceholderBg,
                ),
            )
        }
    }

    val textStyle = TextStyle(
        color = MaterialTheme.colorScheme.onBackground,
        fontFamily = EditorFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    var savedScrollOnPress by remember { mutableStateOf(0) }
    var focusGainVersion by remember { mutableStateOf(0) }
    val latestValue by rememberUpdatedState(value)
    val latestActiveFolds by rememberUpdatedState(activeFolds)
    val latestToggleFold by rememberUpdatedState({ region: FoldRegions.Region ->
        foldedRegions = if (region in foldedRegions) foldedRegions - region
        else foldedRegions + region
    })

    val completionScope = rememberCoroutineScope()
    var completionItems by remember(activePath) { mutableStateOf<List<LspCompletionItem>>(emptyList()) }
    var completionSelectedIndex by remember(activePath) { mutableStateOf(0) }
    var completionTriggerOffset by remember(activePath) { mutableStateOf(-1) }
    var completionRequestToken by remember(activePath) { mutableStateOf(0) }
    var lastSeenText by remember(activePath) { mutableStateOf(value.text) }
    var activeTabstops by remember(activePath) { mutableStateOf<List<page.lsp.SnippetTabstop>>(emptyList()) }
    var activeTabstopIndex by remember(activePath) { mutableStateOf(0) }
    var activeTabstopBase by remember(activePath) { mutableStateOf(0) }
    var activeTabstopBaseTextLen by remember(activePath) { mutableStateOf(0) }

    val completionPrefix = run {
        val trig = completionTriggerOffset
        val caret = value.selection.end
        if (trig < 0 || caret < trig || caret > value.text.length) ""
        else value.text.substring(trig, caret)
    }
    val filteredItems = remember(completionItems, completionPrefix) {
        if (completionPrefix.isEmpty()) completionItems
        else completionItems.filter { item ->
            val key = item.filterText.takeIf { it.isNotBlank() } ?: item.label
            key.startsWith(completionPrefix, ignoreCase = true)
        }
    }
    LaunchedEffect(filteredItems.size) {
        if (completionSelectedIndex >= filteredItems.size) completionSelectedIndex = 0
    }

    val clearTabstops: () -> Unit = {
        activeTabstops = emptyList()
        activeTabstopIndex = 0
        activeTabstopBase = 0
        activeTabstopBaseTextLen = 0
    }

    val closeCompletion: () -> Unit = {
        completionItems = emptyList()
        completionSelectedIndex = 0
        completionTriggerOffset = -1
    }

    val triggerCompletion: (Int, Int, String?) -> Unit = lambda@{ triggerOffset, requestOffset, triggerChar ->
        val cb = onRequestCompletion ?: return@lambda
        if (activePath == null) return@lambda
        val req = requestOffset.coerceIn(0, value.text.length)
        val pos = TextBuffer(value.text).lineColOf(req)
        completionTriggerOffset = triggerOffset.coerceIn(0, value.text.length)
        completionItems = emptyList()
        completionSelectedIndex = 0
        completionRequestToken += 1
        val token = completionRequestToken
        completionScope.launch {
            if (triggerChar != null) delay(80)
            if (token != completionRequestToken) return@launch
            cb(pos.line, pos.col, triggerChar).whenComplete { list, throwable ->
                if (throwable != null || list == null) return@whenComplete
                completionScope.launch {
                    if (token == completionRequestToken) {
                        completionItems = list.items
                        completionSelectedIndex = 0
                    }
                }
            }
        }
    }

    val refreshCompletion: () -> Unit = lambda@{
        val cb = onRequestCompletion ?: return@lambda
        if (activePath == null) return@lambda
        if (completionTriggerOffset < 0) return@lambda
        val caret = value.selection.end.coerceIn(0, value.text.length)
        val pos = TextBuffer(value.text).lineColOf(caret)
        completionRequestToken += 1
        val token = completionRequestToken
        completionScope.launch {
            delay(80)
            if (token != completionRequestToken) return@launch
            cb(pos.line, pos.col, null).whenComplete { list, throwable ->
                if (throwable != null || list == null) return@whenComplete
                completionScope.launch {
                    if (token == completionRequestToken) {
                        completionItems = list.items
                        if (completionSelectedIndex >= list.items.size) completionSelectedIndex = 0
                    }
                }
            }
        }
    }

    val applySelected: () -> Unit = lambda@{
        val item = filteredItems.getOrNull(completionSelectedIndex)
        if (item == null) {
            closeCompletion()
            return@lambda
        }
        val text = value.text
        val caret = value.selection.end.coerceIn(0, text.length)
        val edit = item.edit
        val replaceStart: Int
        val replaceEnd: Int
        if (edit != null) {
            val s = lineColToOffset(text, edit.startLine, edit.startCharacter)
            val e = lineColToOffset(text, edit.endLine, edit.endCharacter)
            if (s < 0 || e < 0 || s > e) {
                replaceStart = completionTriggerOffset.coerceIn(0, caret)
                replaceEnd = caret
            } else {
                replaceStart = s
                replaceEnd = maxOf(e, caret)
            }
        } else {
            replaceStart = completionTriggerOffset.coerceIn(0, caret)
            replaceEnd = caret
        }
        val rawInsert = item.insertText
        val expanded = if (item.isSnippet) SnippetExpander.expand(rawInsert)
        else page.lsp.ExpandedSnippet(rawInsert, rawInsert.length)
        val addEdits = item.additionalEdits
            .map { ed ->
                val s = lineColToOffset(text, ed.startLine, ed.startCharacter)
                val e = lineColToOffset(text, ed.endLine, ed.endCharacter)
                Triple(s, e, ed.newText)
            }
            .filter { (s, e, _) ->
                s in 0..text.length && e in s..text.length && (e <= replaceStart || s >= replaceEnd)
            }
        val allEdits = (addEdits + Triple(replaceStart, replaceEnd, expanded.text))
            .sortedByDescending { it.first }
        var working = text
        for ((s, e, t) in allEdits) {
            working = working.substring(0, s) + t + working.substring(e)
        }
        val newText = working
        val preShift = addEdits
            .filter { (s, _, _) -> s < replaceStart }
            .sumOf { (s, e, t) -> t.length - (e - s) }
        val caretBase = replaceStart + preShift
        val firstStop = expanded.tabstops.firstOrNull()
        val newSel = if (firstStop != null) {
            TextRange(caretBase + firstStop.start, caretBase + firstStop.end)
        } else {
            TextRange(caretBase + expanded.finalCaret)
        }
        onValueChange(value.copy(text = newText, selection = newSel))
        if (expanded.tabstops.size >= 2) {
            activeTabstops = expanded.tabstops
            activeTabstopIndex = 0
            activeTabstopBase = replaceStart
            activeTabstopBaseTextLen = newText.length
        } else {
            clearTabstops()
        }
        closeCompletion()
    }

    val advanceTabstop: () -> Boolean = lambda@{
        val stops = activeTabstops
        if (stops.isEmpty()) return@lambda false
        val nextIdx = activeTabstopIndex + 1
        if (nextIdx >= stops.size) {
            clearTabstops()
            return@lambda false
        }
        val stop = stops[nextIdx]
        val base = activeTabstopBase
        val textLen = value.text.length
        val delta = textLen - activeTabstopBaseTextLen
        val from = (base + stop.start + delta).coerceIn(0, textLen)
        val to = (base + stop.end + delta).coerceIn(from, textLen)
        activeTabstopIndex = nextIdx
        onValueChange(value.copy(selection = TextRange(from, to)))
        true
    }

    LaunchedEffect(value.text, value.selection.end) {
        val newText = value.text
        val newCaret = value.selection.end
        val oldText = lastSeenText
        lastSeenText = newText
        val shrink = oldText.length - newText.length
        if (activeTabstops.isNotEmpty() && shrink >= 2) {
            clearTabstops()
        }
        val isInsertOne = newText.length == oldText.length + 1 && newCaret > 0
        var didTrigger = false
        if (isInsertOne) {
            val inserted = newText[newCaret - 1]
            val isWordChar = inserted.isLetter() || inserted == '_'
            val prevIsBoundary = newCaret < 2 ||
                !(newText[newCaret - 2].isLetterOrDigit() || newText[newCaret - 2] == '_')
            when {
                inserted == '.' -> { triggerCompletion(newCaret, newCaret, "."); didTrigger = true }
                inserted == ':' -> { triggerCompletion(newCaret, newCaret, null); didTrigger = true }
                isWordChar && completionTriggerOffset < 0 && prevIsBoundary -> {
                    triggerCompletion(newCaret - 1, newCaret, null); didTrigger = true
                }
            }
        }
        if (completionTriggerOffset >= 0 && !didTrigger) {
            if (newCaret <= completionTriggerOffset || value.selection.start != value.selection.end) {
                closeCompletion()
            } else {
                val typed = newText.substring(completionTriggerOffset, newCaret)
                val hasNonWord = typed.any { !it.isLetterOrDigit() && it != '_' }
                if (hasNonWord) {
                    closeCompletion()
                } else if (isInsertOne) {
                    refreshCompletion()
                }
            }
        }
    }

    val completionDisplay = remember(filteredItems) {
        filteredItems.map { item ->
            val displayLabel = sanitizeLabel(item.label)
                ?: sanitizeLabel(item.filterText)
                ?: sanitizeLabel(item.insertText)
                ?: "<unnamed>"
            CompletionDisplay(
                label = displayLabel,
                kindHint = kindHint(item.kind),
                detail = item.detail?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }

    val tabstopDecorations: List<EditorDecoration> = if (activeTabstops.isEmpty()) {
        emptyList()
    } else {
        val textLen = value.text.length
        val delta = textLen - activeTabstopBaseTextLen
        val caret = value.selection.end
        val out = mutableListOf<EditorDecoration>()
        for (i in activeTabstopIndex until activeTabstops.size) {
            val stop = activeTabstops[i]
            val isActive = i == activeTabstopIndex
            val rangeStart: Int
            val rangeEnd: Int
            if (isActive) {
                rangeStart = (activeTabstopBase + stop.start).coerceIn(0, textLen)
                val typedEnd = (activeTabstopBase + stop.end + delta).coerceIn(rangeStart, textLen)
                rangeEnd = if (caret in rangeStart..typedEnd) caret else typedEnd
            } else {
                rangeStart = (activeTabstopBase + stop.start + delta).coerceIn(0, textLen)
                rangeEnd = (activeTabstopBase + stop.end + delta).coerceIn(rangeStart, textLen)
            }
            val color = if (isActive) tabstopActiveColor else tabstopPendingColor
            val style = if (isActive) EditorDecoration.Style.TABSTOP_ACTIVE
                        else EditorDecoration.Style.TABSTOP_PENDING
            out += EditorDecoration(rangeStart, rangeEnd, color, style)
        }
        out
    }
    val decorations = diagnosticDecorations + tabstopDecorations

    LaunchedEffect(focusGainVersion) {
        if (focusGainVersion > 0) {
            val target = savedScrollOnPress
            scrollState.scroll(MutatePriority.PreventUserInput) {
                val end = System.nanoTime() + 250_000_000L
                while (System.nanoTime() < end) {
                    if (scrollState.value != target) {
                        scrollBy(target.toFloat() - scrollState.value)
                    }
                    delay(8)
                }
            }
        }
    }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        if (search != null) {
            SearchBar(
                state = search,
                onQueryChange = onQueryChange,
                onReplaceChange = onReplaceChange,
                onToggleCase = onToggleCase,
                onNext = onSearchNext,
                onPrev = onSearchPrev,
                onReplace = onReplace,
                onReplaceAll = onReplaceAll,
                onClose = onSearchClose,
                onWindowShortcut = onWindowShortcut,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                savedScrollOnPress = scrollState.value
                            }
                        }
                    }
                }
                .verticalScroll(scrollState)
                .drawBehind {
                    val lineH = 20.sp.toPx()
                    val topPad = 16.dp.toPx()
                    val y = topPad + caret.line * lineH
                    drawRect(
                        color = currentLineBg,
                        topLeft = Offset(0f, y),
                        size = Size(size.width, lineH),
                    )
                },
        ) {
            LineNumberGutter(
                lines = gutterLines,
                currentOriginalLine = caret.line,
                onToggleFold = { line ->
                    val region = foldStartByLine[line] ?: return@LineNumberGutter
                    foldedRegions = if (region in foldedRegions) foldedRegions - region
                    else foldedRegions + region
                },
                textStyle = textStyle,
            )
            CodeEditor(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
                    .onFocusChanged { state ->
                        if (state.isFocused) focusGainVersion++
                    },
                textStyle = textStyle,
                visualTransformation = visualTransformation,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(
                    start = 8.dp, end = 20.dp, top = 16.dp, bottom = 16.dp,
                ),
                onPointerPress = { transOff ->
                    if (latestActiveFolds.isEmpty()) false
                    else {
                        val region = FoldRegions.foldedRegionAt(
                            latestValue.text,
                            latestActiveFolds,
                            transOff,
                        )
                        if (region != null) {
                            latestToggleFold(region)
                            true
                        } else false
                    }
                },
                onPreviewKeyEvent = { event ->
                    if (event.type != KeyEventType.KeyDown) return@CodeEditor false
                    if (filteredItems.isNotEmpty()) {
                        when (event.key) {
                            Key.DirectionUp -> {
                                val n = filteredItems.size
                                completionSelectedIndex =
                                    if (completionSelectedIndex - 1 < 0) n - 1
                                    else completionSelectedIndex - 1
                                return@CodeEditor true
                            }
                            Key.DirectionDown -> {
                                completionSelectedIndex =
                                    (completionSelectedIndex + 1) % filteredItems.size
                                return@CodeEditor true
                            }
                            Key.Tab, Key.Enter, Key.NumPadEnter -> {
                                applySelected()
                                return@CodeEditor true
                            }
                            Key.Escape -> {
                                closeCompletion()
                                return@CodeEditor true
                            }
                            else -> Unit
                        }
                    }
                    if (event.key == Key.Tab && !event.isShiftPressed && activeTabstops.isNotEmpty()) {
                        if (advanceTabstop()) return@CodeEditor true
                    }
                    if (event.key == Key.Escape && activeTabstops.isNotEmpty()) {
                        clearTabstops()
                        return@CodeEditor true
                    }
                    if (event.isCtrlPressed && event.key == Key.Spacebar) {
                        val text = value.text
                        val caret = value.selection.end.coerceIn(0, text.length)
                        val wordStart = wordStartAt(text, caret)
                        triggerCompletion(wordStart, caret, null)
                        return@CodeEditor true
                    }
                    if (event.key == Key.F12 && !event.isCtrlPressed && !event.isShiftPressed) {
                        val cb = onRequestDefinition
                        val nav = onGoToDefinition
                        if (cb != null && nav != null) {
                            val text = value.text
                            val caret = value.selection.end.coerceIn(0, text.length)
                            val pos = TextBuffer(text).lineColOf(caret)
                            cb(pos.line, pos.col).whenComplete { targets, _ ->
                                val first = targets?.firstOrNull()
                                if (first != null) nav(first)
                            }
                            return@CodeEditor true
                        }
                    }
                    if (
                        event.key == Key.Tab && !event.isShiftPressed &&
                        value.selection.collapsed
                    ) {
                        val text = value.text
                        val caret = value.selection.end
                        if (caret < text.length && text[caret] in CLOSING_PUNCT) {
                            onValueChange(value.copy(selection = TextRange(caret + 1)))
                            return@CodeEditor true
                        }
                    }
                    if (
                        event.key == Key.Tab && !event.isShiftPressed &&
                        isMarkdown &&
                        MarkdownFence.isInsideFence(value.text, value.selection.start)
                    ) {
                        val edit = TextEdit(
                            value.text, value.selection.start, value.selection.end,
                        )
                        val r = Indent.handleLiteralTab(edit)
                        onValueChange(
                            value.copy(
                                text = r.text,
                                selection = TextRange(r.selectionStart, r.selectionEnd),
                            ),
                        )
                        true
                    } else false
                },
                manageHistory = false,
                viewportHeightProvider = { scrollState.viewportSize.toFloat() },
                decorations = decorations,
                onHover = { origOff ->
                    pendingHoverDiagnostic = if (origOff == null) null
                    else diagnosticRanges.firstOrNull { (s, e, _) ->
                        origOff in s until e
                    }?.third
                    hoverOffset = origOff
                },
                hoverText = lspHoverInfo?.markdown?.takeIf { it.isNotBlank() },
                hoverDiagnostic = hoverDiagnostic?.let { d ->
                    page.ui.HoverDiagnostic(
                        severity = when (d.severity) {
                            DiagnosticSeverity.ERROR -> page.ui.HoverDiagnosticSeverity.ERROR
                            DiagnosticSeverity.WARNING -> page.ui.HoverDiagnosticSeverity.WARNING
                            DiagnosticSeverity.INFO -> page.ui.HoverDiagnosticSeverity.INFO
                            DiagnosticSeverity.HINT -> page.ui.HoverDiagnosticSeverity.HINT
                        },
                        message = d.message,
                    )
                },
                completionItems = completionDisplay,
                completionSelectedIndex = completionSelectedIndex,
                completionAnchorOffset = completionTriggerOffset.takeIf { it >= 0 },
            )
        }
        EditorStatusBar(
            line = caret.line,
            col = caret.col,
            lineCount = buffer.lineCount,
            charCount = buffer.length,
            errorCount = errorCount,
            warningCount = warningCount,
            lspStatusText = lspStatusText,
            lspActivities = lspActivities,
            onProblemsToggle = onProblemsToggle,
        )
    }
}

private val CLOSING_PUNCT: Set<Char> = setOf(')', ']', '}', '"', '\'', '`')

private fun severityRank(s: DiagnosticSeverity): Int = when (s) {
    DiagnosticSeverity.ERROR -> 0
    DiagnosticSeverity.WARNING -> 1
    DiagnosticSeverity.INFO -> 2
    DiagnosticSeverity.HINT -> 3
}

private fun kindHint(kind: LspCompletionItemKind): String = when (kind) {
    LspCompletionItemKind.METHOD -> "M"
    LspCompletionItemKind.FUNCTION -> "ƒ"
    LspCompletionItemKind.CONSTRUCTOR -> "C"
    LspCompletionItemKind.FIELD -> "f"
    LspCompletionItemKind.VARIABLE -> "v"
    LspCompletionItemKind.CLASS -> "C"
    LspCompletionItemKind.INTERFACE -> "I"
    LspCompletionItemKind.MODULE -> "m"
    LspCompletionItemKind.PROPERTY -> "p"
    LspCompletionItemKind.UNIT -> "u"
    LspCompletionItemKind.VALUE -> "V"
    LspCompletionItemKind.ENUM, LspCompletionItemKind.ENUM_MEMBER -> "E"
    LspCompletionItemKind.KEYWORD -> "K"
    LspCompletionItemKind.SNIPPET -> "▤"
    LspCompletionItemKind.CONSTANT -> "c"
    LspCompletionItemKind.STRUCT -> "S"
    LspCompletionItemKind.TYPE_PARAMETER -> "T"
    else -> "•"
}

private fun sanitizeLabel(s: String?): String? {
    if (s.isNullOrEmpty()) return null
    val collapsed = s.replace(Regex("\\s+"), " ").trim()
    return collapsed.ifEmpty { null }
}

private fun wordStartAt(text: String, caret: Int): Int {
    var i = caret.coerceIn(0, text.length)
    while (i > 0) {
        val ch = text[i - 1]
        if (ch.isLetterOrDigit() || ch == '_') i-- else break
    }
    return i
}

private fun readDefinitionFileText(uri: String): String? {
    return try {
        val parsed = java.net.URI(uri)
        if (parsed.scheme != "file") return null
        val path = java.nio.file.Paths.get(parsed)
        if (!java.nio.file.Files.isRegularFile(path)) return null
        if (java.nio.file.Files.size(path) > 4L * 1024 * 1024) return null
        java.nio.file.Files.readString(path)
    } catch (_: Throwable) {
        null
    }
}

private fun lineColToOffset(text: String, line: Int, col: Int): Int {
    if (line < 0 || col < 0) return -1
    var lineIndex = 0
    var i = 0
    while (i < text.length && lineIndex < line) {
        if (text[i] == '\n') lineIndex++
        i++
    }
    if (lineIndex < line) return -1
    val lineStart = i
    val lineEnd = run {
        var j = i
        while (j < text.length && text[j] != '\n') j++
        j
    }
    val target = lineStart + col
    return target.coerceIn(lineStart, lineEnd)
}

private class CombinedHighlightTransformation(
    private val tokens: List<Token>,
    private val palette: SyntaxPalette,
    private val matches: List<IntRange>,
    private val activeIndex: Int,
    private val matchBg: androidx.compose.ui.graphics.Color,
    private val activeBg: androidx.compose.ui.graphics.Color,
    private val bracketMatch: Pair<Int, Int>?,
    private val bracketBg: androidx.compose.ui.graphics.Color,
    private val foldSegments: List<FoldRegions.Segment>,
    private val foldPlaceholderStyle: SpanStyle,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = applyHighlights(text)
        if (foldSegments.isEmpty()) {
            return TransformedText(styled, OffsetMapping.Identity)
        }
        val builder = AnnotatedString.Builder()
        var cursor = 0
        for (seg in foldSegments) {
            if (cursor < seg.origStart) {
                builder.append(styled.subSequence(cursor, seg.origStart))
            }
            val placeholderStart = builder.length
            builder.append(seg.replacement)
            val dotsOffset = seg.replacement.indexOf("...")
            if (dotsOffset >= 0) {
                val dotsStart = placeholderStart + dotsOffset
                val dotsEnd = dotsStart + 3
                builder.addStyle(foldPlaceholderStyle, dotsStart, dotsEnd)
            }
            cursor = seg.origEnd
        }
        if (cursor < styled.length) {
            builder.append(styled.subSequence(cursor, styled.length))
        }
        return TransformedText(builder.toAnnotatedString(), FoldOffsetMapping(foldSegments))
    }

    private fun applyHighlights(text: AnnotatedString): AnnotatedString {
        val builder = AnnotatedString.Builder(text)
        for (token in tokens) {
            val start = token.range.first.coerceIn(0, text.length)
            val end = (token.range.last + 1).coerceIn(start, text.length)
            if (start == end) continue
            val color = colorFor(token.kind, palette) ?: continue
            builder.addStyle(SpanStyle(color = color), start, end)
        }
        matches.forEachIndexed { index, range ->
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(start, text.length)
            if (start == end) return@forEachIndexed
            val bg = if (index == activeIndex) activeBg else matchBg
            builder.addStyle(SpanStyle(background = bg), start, end)
        }
        if (bracketMatch != null) {
            for (off in listOf(bracketMatch.first, bracketMatch.second)) {
                val start = off.coerceIn(0, text.length)
                val end = (off + 1).coerceIn(start, text.length)
                if (start == end) continue
                builder.addStyle(SpanStyle(background = bracketBg), start, end)
            }
        }
        return builder.toAnnotatedString()
    }

    private fun colorFor(kind: TokenKind, palette: SyntaxPalette) = when (kind) {
        TokenKind.KEYWORD -> palette.keyword
        TokenKind.STRING -> palette.string
        TokenKind.NUMBER -> palette.number
        TokenKind.COMMENT -> palette.comment
        TokenKind.ANNOTATION -> palette.annotation
        TokenKind.TYPE -> palette.type
        TokenKind.PUNCT -> null
    }
}

private class FoldOffsetMapping(
    private val segments: List<FoldRegions.Segment>,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int =
        FoldRegions.originalToTransformed(segments, offset)

    override fun transformedToOriginal(offset: Int): Int =
        FoldRegions.transformedToOriginal(segments, offset)
}

@Composable
private fun EditorStatusBar(
    line: Int,
    col: Int,
    lineCount: Int,
    charCount: Int,
    errorCount: Int = 0,
    warningCount: Int = 0,
    lspStatusText: String? = null,
    lspActivities: List<LspController.Activity> = emptyList(),
    onProblemsToggle: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StatusItem("Ln ${line + 1}, Col ${col + 1}")
            StatusItem("$lineCount lines")
            StatusItem("$charCount chars")
            if (errorCount > 0) DiagnosticBadge(
                count = errorCount,
                color = androidx.compose.ui.graphics.Color(0xFFE5484D),
                label = "errors",
                onClick = onProblemsToggle,
            )
            if (warningCount > 0) DiagnosticBadge(
                count = warningCount,
                color = androidx.compose.ui.graphics.Color(0xFFE5C03A),
                label = "warnings",
                onClick = onProblemsToggle,
            )
            val showLifecycle = !lspStatusText.isNullOrBlank()
            val showActivities = lspActivities.isNotEmpty()
            if (showLifecycle || showActivities) {
                Box(modifier = Modifier.weight(1f))
                if (showLifecycle) {
                    LspLifecycleItem(text = lspStatusText!!)
                } else {
                    LspActivitiesItem(activities = lspActivities)
                }
            }
        }
    }
}

@Composable
private fun LspLifecycleItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LspActivitiesItem(activities: List<LspController.Activity>) {
    if (activities.isEmpty()) return
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activities.size) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }
    var expanded by remember { mutableStateOf(false) }
    val firstLabel = activities.first().label
    val firstStartedAt = activities.first().startedAtMs
    val firstElapsed = ((nowMs - firstStartedAt) / 1000L).coerceAtLeast(0L).toInt()
    val canExpand = activities.size >= 2
    val rowMod = Modifier.then(
        if (canExpand) Modifier.clickable { expanded = !expanded } else Modifier,
    )
    Box {
        Row(
            modifier = rowMod,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier.width(72.dp).height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = if (firstElapsed > 0) "LSP · $firstLabel (${firstElapsed}s)"
                else "LSP · $firstLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canExpand) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "+${activities.size - 1}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        if (canExpand && expanded) {
            androidx.compose.material3.DropdownMenu(
                expanded = true,
                onDismissRequest = { expanded = false },
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (a in activities) {
                            val secs = ((nowMs - a.startedAtMs) / 1000L).coerceAtLeast(0L).toInt()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    modifier = Modifier.width(72.dp).height(3.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Text(
                                    text = if (secs > 0) "${a.label} (${secs}s)" else a.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticBadge(
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    label: String,
    onClick: (() -> Unit)? = null,
) {
    val rowMod = Modifier.then(
        if (onClick != null) Modifier.clickable { onClick() } else Modifier,
    )
    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
