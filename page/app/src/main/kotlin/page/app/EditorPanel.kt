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
import page.lsp.Diagnostic
import page.lsp.DiagnosticSeverity
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
    lspStartedAtMs: Long = 0L,
    onProblemsToggle: (() -> Unit)? = null,
    onRequestCompletion: ((line: Int, character: Int) -> CompletableFuture<CompletionList>)? = null,
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
    val decorations = remember(value.text, diagnostics) {
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
    LaunchedEffect(pendingHoverDiagnostic) {
        val target = pendingHoverDiagnostic
        if (target == null) {
            hoverDiagnostic = null
        } else if (target !== hoverDiagnostic) {
            delay(250)
            hoverDiagnostic = target
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

    val closeCompletion: () -> Unit = {
        completionItems = emptyList()
        completionSelectedIndex = 0
        completionTriggerOffset = -1
    }

    val triggerCompletion: (Int, Int) -> Unit = lambda@{ triggerOffset, requestOffset ->
        val cb = onRequestCompletion ?: return@lambda
        if (activePath == null) return@lambda
        val req = requestOffset.coerceIn(0, value.text.length)
        val pos = TextBuffer(value.text).lineColOf(req)
        completionTriggerOffset = triggerOffset.coerceIn(0, value.text.length)
        completionSelectedIndex = 0
        completionRequestToken += 1
        val token = completionRequestToken
        cb(pos.line, pos.col).whenComplete { list, throwable ->
            if (throwable != null || list == null) return@whenComplete
            completionScope.launch {
                if (token == completionRequestToken) {
                    completionItems = list.items
                    completionSelectedIndex = 0
                }
            }
        }
    }

    val applySelected: () -> Unit = lambda@{
        val item = completionItems.getOrNull(completionSelectedIndex)
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
        val insert = item.insertText
        val newText = text.substring(0, replaceStart) + insert + text.substring(replaceEnd)
        val newCaret = replaceStart + insert.length
        onValueChange(value.copy(text = newText, selection = TextRange(newCaret)))
        closeCompletion()
    }

    LaunchedEffect(value.text, value.selection.end) {
        val newText = value.text
        val newCaret = value.selection.end
        val oldText = lastSeenText
        lastSeenText = newText
        if (newText.length == oldText.length + 1 && newCaret > 0) {
            val inserted = newText[newCaret - 1]
            if (inserted == '.' || inserted == ':') {
                triggerCompletion(newCaret, newCaret)
            }
        }
        if (completionTriggerOffset >= 0 && completionItems.isNotEmpty()) {
            if (newCaret < completionTriggerOffset || value.selection.start != value.selection.end) {
                closeCompletion()
            }
        }
    }

    val completionDisplay = remember(completionItems) {
        completionItems.map { item ->
            CompletionDisplay(
                label = item.label,
                kindHint = kindHint(item.kind),
                detail = item.detail,
            )
        }
    }

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
                    if (completionItems.isNotEmpty()) {
                        when (event.key) {
                            Key.DirectionUp -> {
                                val n = completionItems.size
                                completionSelectedIndex =
                                    if (completionSelectedIndex - 1 < 0) n - 1
                                    else completionSelectedIndex - 1
                                return@CodeEditor true
                            }
                            Key.DirectionDown -> {
                                completionSelectedIndex =
                                    (completionSelectedIndex + 1) % completionItems.size
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
                    if (event.isCtrlPressed && event.key == Key.Spacebar) {
                        val text = value.text
                        val caret = value.selection.end.coerceIn(0, text.length)
                        val wordStart = wordStartAt(text, caret)
                        triggerCompletion(wordStart, caret)
                        return@CodeEditor true
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
                },
                hoverText = hoverDiagnostic?.let { d ->
                    val severity = when (d.severity) {
                        DiagnosticSeverity.ERROR -> "ERROR"
                        DiagnosticSeverity.WARNING -> "WARNING"
                        DiagnosticSeverity.INFO -> "INFO"
                        DiagnosticSeverity.HINT -> "HINT"
                    }
                    "[$severity] ${d.message}"
                },
                completionItems = completionDisplay,
                completionSelectedIndex = completionSelectedIndex,
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
            lspStartedAtMs = lspStartedAtMs,
            onProblemsToggle = onProblemsToggle,
        )
    }
}

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

private fun wordStartAt(text: String, caret: Int): Int {
    var i = caret.coerceIn(0, text.length)
    while (i > 0) {
        val ch = text[i - 1]
        if (ch.isLetterOrDigit() || ch == '_') i-- else break
    }
    return i
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
    lspStartedAtMs: Long = 0L,
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
            if (!lspStatusText.isNullOrBlank()) {
                Box(modifier = Modifier.weight(1f))
                LspStatusItem(text = lspStatusText, startedAtMs = lspStartedAtMs)
            }
        }
    }
}

@Composable
private fun LspStatusItem(text: String, startedAtMs: Long) {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        if (startedAtMs <= 0L) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }
    val elapsedSec = if (startedAtMs > 0L) ((nowMs - startedAtMs) / 1000L).coerceAtLeast(0L).toInt() else 0
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
            text = if (elapsedSec > 0) "$text (${elapsedSec}s)" else text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
