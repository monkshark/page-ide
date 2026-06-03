package page.app

import page.runtime.*
import page.workspace.*

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
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
import kotlinx.coroutines.future.await
import page.language.LspController
import page.editor.BracketMatch
import page.editor.FoldRegions
import page.editor.Indent
import page.editor.LanguageFolders
import page.editor.LineComment
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
import page.lsp.InlayHintItem
import page.lsp.RenamePrepare
import page.lsp.RenameWorkspaceEdit
import page.lsp.ResolvedCompletion
import page.lsp.SignatureActiveParam
import page.lsp.SignatureHelpInfo
import page.lsp.enrichForPropertyDecl
import page.lsp.enrichWithKDocFromDefinition
import page.lsp.needsKdocEnrichment
import page.ui.CodeEditor
import page.ui.CompactDropdown
import page.ui.CompletionDisplay
import page.ui.EditorDecoration
import page.ui.EditorFontFamily
import page.ui.Glass
import page.ui.SignatureHelpDisplay
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
    onLspStatusClick: (() -> Unit)? = null,
    onActivityClick: ((LspController.Activity) -> Unit)? = null,
    onProblemsToggle: (() -> Unit)? = null,
    todoCount: Int = 0,
    onTodoToggle: (() -> Unit)? = null,
    onRequestCompletion: ((line: Int, character: Int, triggerCharacter: String?) -> CompletableFuture<CompletionList>)? = null,
    onRequestHover: ((line: Int, character: Int) -> CompletableFuture<HoverInfo?>)? = null,
    onRequestDefinition: ((line: Int, character: Int) -> CompletableFuture<List<DefinitionTarget>>)? = null,
    onRequestSignatureHelp: ((line: Int, character: Int, triggerCharacter: String?, isRetrigger: Boolean) -> CompletableFuture<SignatureHelpInfo?>)? = null,
    onResolveCompletion: ((token: Long) -> CompletableFuture<ResolvedCompletion?>)? = null,
    onGoToDefinition: ((DefinitionTarget) -> Unit)? = null,
    onRequestPrepareRename: ((line: Int, character: Int) -> CompletableFuture<RenamePrepare?>)? = null,
    onRequestRename: ((line: Int, character: Int, newName: String) -> CompletableFuture<RenameWorkspaceEdit>)? = null,
    onApplyRename: ((RenameWorkspaceEdit) -> Unit)? = null,
    onRequestReferences: ((line: Int, character: Int, symbolName: String) -> Unit)? = null,
    onRequestInlayHints: ((startLine: Int, startCharacter: Int, endLine: Int, endCharacter: Int) -> CompletableFuture<List<InlayHintItem>>)? = null,
    workspaceRoot: Path? = null,
    editorFocusVersion: Int = 0,
    initialFoldedStartLines: Set<Int> = emptySet(),
    onFoldStartLinesChange: (Set<Int>) -> Unit = {},
    initialVScroll: Int = 0,
    initialHScroll: Int = 0,
    onScrollChange: (vertical: Int, horizontal: Int) -> Unit = { _, _ -> },
    jdkVersion: String? = null,
    jdkVersionTooltip: String? = null,
    onJdkVersionClick: (() -> Unit)? = null,
    pageSettings: PageSettings = PageSettings(),
    modifier: Modifier = Modifier,
) {
    val isMarkdown = remember(activePath) {
        val name = activePath?.fileName?.toString()?.lowercase()
        name != null && (name.endsWith(".md") || name.endsWith(".markdown"))
    }
    val commentPrefix = remember(activePath) { LineComment.prefixFor(activePath) }
    val buffer = remember(value.text) { TextBuffer(value.text) }
    val caretOffset = value.selection.start.coerceIn(0, buffer.length)
    val caret = buffer.lineColOf(caretOffset)

    val matchBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val currentLineBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
    val bracketBg = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
    val foldPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
    val foldPlaceholderBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
    val palette = Glass.colors.syntax
    val defaultTodoColors = mapOf(
        "TODO" to MaterialTheme.colorScheme.secondary,
        "FIXME" to Glass.colors.error,
        "HACK" to Glass.colors.warn,
        "XXX" to MaterialTheme.colorScheme.tertiary,
        "NOTE" to MaterialTheme.colorScheme.primary,
        "BUG" to Color(0xFFD13438),
        "REVIEW" to Color(0xFF1AB1A8),
        "EXCEPTION" to Color(0xFFC2185B),
    )
    val customKeywordColors = workspaceRoot?.let { KeywordOverridesStore.load(it).colors }.orEmpty()
    val todoColors = defaultTodoColors + customKeywordColors.mapNotNull { (k, hex) ->
        parseHexColor(hex)?.let { k.uppercase() to it }
    }.toMap()

    val tokens = remember(value.text, lexer) {
        lexer?.tokenize(value.text).orEmpty()
    }

    val multiKeywordComments = remember(value.text, tokens) {
        TodoMultiKeyword.analyze(value.text, tokens)
    }
    val keywordOverrideByRange = remember(multiKeywordComments, todoColors, palette) {
        multiKeywordComments.associate { c ->
            c.commentRange to (todoColors[c.effectiveKeyword] ?: palette.todoTag)
        }
    }
    val multiKeywordByLine = remember(multiKeywordComments, todoColors, palette) {
        multiKeywordComments.associateBy({ it.line }) { c ->
            MultiKeywordChoice(
                commentRange = c.commentRange,
                chosenKeyword = c.effectiveKeyword,
                keywords = c.keywords,
                chosenColor = todoColors[c.effectiveKeyword] ?: palette.todoTag,
                keywordColors = c.keywords.associateWith { kw -> todoColors[kw] ?: palette.todoTag },
            )
        }
    }
    val onPickKeyword: (IntRange, String, String) -> Unit = { commentRange, oldKeyword, newKeyword ->
        if (oldKeyword != newKeyword) {
            val rewritten = rewriteFirstKeyword(value.text, commentRange, oldKeyword, newKeyword)
            if (rewritten != null) {
                val delta = rewritten.length - value.text.length
                val sel = value.selection
                val newSel = if (sel.start > commentRange.last) {
                    TextRange(sel.start + delta, sel.end + delta)
                } else sel
                onValueChange(value.copy(text = rewritten, selection = newSel))
            }
        }
    }

    val bracketMatch = remember(value.text, value.selection.start, value.selection.end) {
        if (value.selection.start != value.selection.end) null
        else BracketMatch.find(value.text, value.selection.start)
    }

    val errorColor = Glass.colors.error
    val warningColor = Glass.colors.warn
    val infoColor = MaterialTheme.colorScheme.primary
    val hintColor = MaterialTheme.colorScheme.tertiary
    val tabstopActiveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val tabstopPendingColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val showInlineDiagnostics = pageSettings.lsp.showInlineDiagnostics
    val unnecessaryColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
    val diagnosticDecorations = remember(value.text, diagnostics, showInlineDiagnostics) {
        if (!showInlineDiagnostics) emptyList()
        else diagnostics.mapNotNull { d ->
            if (d.unnecessary) return@mapNotNull null
            val (startOff, endOff) = diagnosticUnderlineRange(
                value.text,
                lineColToOffset(value.text, d.start.line, d.start.character),
                lineColToOffset(value.text, d.end.line, d.end.character),
            ) ?: return@mapNotNull null
            val color = when (d.severity) {
                DiagnosticSeverity.ERROR -> errorColor
                DiagnosticSeverity.WARNING -> warningColor
                DiagnosticSeverity.INFO -> infoColor
                DiagnosticSeverity.HINT -> hintColor
            }
            val style = when (d.severity) {
                DiagnosticSeverity.ERROR, DiagnosticSeverity.WARNING ->
                    EditorDecoration.Style.WAVY_UNDERLINE
                DiagnosticSeverity.INFO, DiagnosticSeverity.HINT ->
                    EditorDecoration.Style.DOTTED_UNDERLINE
            }
            EditorDecoration(startOff, endOff, color, style)
        }
    }
    val unnecessaryRanges = remember(value.text, diagnostics, showInlineDiagnostics) {
        if (!showInlineDiagnostics) emptyList()
        else diagnostics.filter { it.unnecessary }.mapNotNull { d ->
            val rawStart = lineColToOffset(value.text, d.start.line, d.start.character)
            val rawEnd = lineColToOffset(value.text, d.end.line, d.end.character)
            val lineStart = lineColToOffset(value.text, d.start.line, 0)
            val lineEnd = value.text.indexOf('\n', rawEnd).let { if (it < 0) value.text.length else it }
            val lineText = value.text.substring(lineStart.coerceIn(0, value.text.length), lineEnd.coerceIn(0, value.text.length))
            val isImport = lineText.trimStart().startsWith("import ")
            val start = if (isImport) lineStart + (lineText.length - lineText.trimStart().length) else rawStart
            val end = if (isImport) lineEnd else rawEnd
            if (start >= end) null else start until end
        }
    }
    val errorCount = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val warningCount = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }

    val diagnosticRanges = remember(value.text, diagnostics) {
        diagnostics.mapNotNull { d ->
            val widened = diagnosticUnderlineRange(
                value.text,
                lineColToOffset(value.text, d.start.line, d.start.character),
                lineColToOffset(value.text, d.end.line, d.end.character),
            ) ?: return@mapNotNull null
            Triple(widened.first, widened.second, d)
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
        if (isInStringOrComment(tokens, text, safeOff)) return@LaunchedEffect
        val pos = TextBuffer(text).lineColOf(safeOff)
        delay(pageSettings.lsp.hoverDelayMs.toLong().coerceAtLeast(0L))
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

    val foldExtension = remember(activePath) {
        activePath?.fileName?.toString()?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
    }
    val foldRegions = remember(value.text, foldExtension) {
        LanguageFolders.forExtension(foldExtension).detect(value.text)
    }
    var foldedRegions by remember(activePath) { mutableStateOf<Set<FoldRegions.Region>>(emptySet()) }
    var initialFoldApplied by remember(activePath) { mutableStateOf(false) }
    LaunchedEffect(activePath, foldRegions) {
        if (initialFoldApplied) return@LaunchedEffect
        if (initialFoldedStartLines.isEmpty()) {
            initialFoldApplied = true
            return@LaunchedEffect
        }
        val matched = foldRegions.filter { it.startLine in initialFoldedStartLines }.toSet()
        if (matched.isNotEmpty()) foldedRegions = matched
        initialFoldApplied = true
    }
    LaunchedEffect(foldedRegions, initialFoldApplied) {
        if (!initialFoldApplied) return@LaunchedEffect
        onFoldStartLinesChange(foldedRegions.map { it.startLine }.toSet())
    }
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
    val gutterLines = remember(buffer.lineCount, foldStartByLine, foldedStartLines, hiddenLines, severityByLine, multiKeywordByLine) {
        (0 until buffer.lineCount)
            .filter { it !in hiddenLines }
            .map { ln ->
                GutterLine(
                    originalLine = ln,
                    foldable = ln in foldStartByLine,
                    folded = ln in foldedStartLines,
                    severity = severityByLine[ln],
                    multiKeyword = multiKeywordByLine[ln],
                )
            }
    }

    var inlayHints by remember(activePath) { mutableStateOf<List<InlayHintItem>>(emptyList()) }
    LaunchedEffect(activePath, value.text, onRequestInlayHints, pageSettings.lsp.showInlayHints) {
        val cb = onRequestInlayHints
        if (cb == null || !pageSettings.lsp.showInlayHints) {
            inlayHints = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        val lineCount = buffer.lineCount
        cb(0, 0, lineCount, 0).whenComplete { hints, _ ->
            inlayHints = hints.orEmpty()
        }
    }
    val inlayHintDisplays = remember(inlayHints, value.text) {
        val text = value.text
        inlayHints.mapNotNull { h ->
            val off = lineColToOffset(text, h.line, h.character)
            if (off < 0) null
            else InlayHintDisplay(
                originalOffset = off,
                label = h.label,
                kind = h.kind,
                paddingLeft = h.paddingLeft,
                paddingRight = h.paddingRight,
            )
        }
    }
    val inlayHintColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)

    val visualTransformation = remember(
        search, tokens, bracketMatch, matchBg, activeBg, bracketBg, palette,
        foldSegments, foldPlaceholderColor, foldPlaceholderBg, todoColors,
        keywordOverrideByRange, inlayHintDisplays, inlayHintColor,
        unnecessaryRanges, unnecessaryColor,
    ) {
        val matches = search?.matches.orEmpty()
        val activeIndex = search?.activeMatchIndex ?: -1
        if (tokens.isEmpty() && matches.isEmpty() && bracketMatch == null && foldSegments.isEmpty() && inlayHintDisplays.isEmpty() && unnecessaryRanges.isEmpty()) {
            VisualTransformation.None
        } else {
            CombinedHighlightTransformation(
                tokens = tokens,
                palette = palette,
                todoColors = todoColors,
                commentColorOverrides = keywordOverrideByRange,
                matches = matches,
                activeIndex = activeIndex,
                matchBg = matchBg,
                activeBg = activeBg,
                bracketMatch = bracketMatch,
                bracketBg = bracketBg,
                unnecessaryRanges = unnecessaryRanges,
                unnecessaryStyle = SpanStyle(color = unnecessaryColor),
                foldSegments = foldSegments,
                foldPlaceholderStyle = SpanStyle(
                    color = foldPlaceholderColor,
                    background = foldPlaceholderBg,
                ),
                foldCloserStyle = SpanStyle(
                    color = palette.comment,
                ),
                foldPrefixStyle = SpanStyle(
                    color = palette.keyword,
                ),
                inlayHints = inlayHintDisplays,
                inlayHintStyle = SpanStyle(
                    color = inlayHintColor,
                    fontSize = 11.sp,
                ),
            )
        }
    }

    val editorFontSize = pageSettings.editor.fontSize.sp
    val editorLineHeight = (pageSettings.editor.fontSize * 1.43f).sp
    val textStyle = TextStyle(
        color = MaterialTheme.colorScheme.onBackground,
        fontFamily = EditorFontFamily,
        fontSize = editorFontSize,
        lineHeight = editorLineHeight,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val pendingInitialV by rememberUpdatedState(initialVScroll)
    val pendingInitialH by rememberUpdatedState(initialHScroll)
    var caretBringArmed by remember(activePath) { mutableStateOf(false) }
    LaunchedEffect(activePath) {
        if (activePath == null) return@LaunchedEffect
        val targetV = pendingInitialV
        val targetH = pendingInitialH
        if (targetV > 0) {
            withTimeoutOrNull(3000L) {
                snapshotFlow { scrollState.maxValue }
                    .first { it >= targetV }
            }
        }
        val finalV = targetV.coerceAtMost(scrollState.maxValue)
        val finalH = targetH.coerceAtMost(horizontalScrollState.maxValue)
        scrollState.scrollTo(finalV)
        horizontalScrollState.scrollTo(finalH)
        caretBringArmed = true
        snapshotFlow { scrollState.value to horizontalScrollState.value }
            .distinctUntilChanged()
            .drop(1)
            .collect { (v, h) -> onScrollChange(v, h) }
    }
    var savedScrollOnPress by remember { mutableStateOf(-1) }
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
    val resolvedByToken = remember(activePath) { mutableStateMapOf<Long, ResolvedCompletion>() }
    var completionTriggerOffset by remember(activePath) { mutableStateOf(-1) }
    var completionRequestToken by remember(activePath) { mutableStateOf(0) }
    var commentKeywordMode by remember(activePath) { mutableStateOf(false) }
    var commentKeywordNeedsSpace by remember(activePath) { mutableStateOf(false) }
    var lastSeenText by remember(activePath) { mutableStateOf(value.text) }
    var lastSeenSelectionLen by remember(activePath) { mutableStateOf(0) }
    var activeTabstops by remember(activePath) { mutableStateOf<List<page.lsp.SnippetTabstop>>(emptyList()) }
    var activeTabstopIndex by remember(activePath) { mutableStateOf(0) }
    var activeTabstopBase by remember(activePath) { mutableStateOf(0) }
    var activeTabstopBaseTextLen by remember(activePath) { mutableStateOf(0) }
    var activeTabstopFinalCaret by remember(activePath) { mutableStateOf(-1) }

    var lspSignatureInfo by remember(activePath) { mutableStateOf<SignatureHelpInfo?>(null) }
    var lspSignatureActiveParam by remember(activePath) { mutableStateOf(0) }
    var lspSignatureRequestToken by remember(activePath) { mutableStateOf(0) }

    var renameRequest by remember(activePath) { mutableStateOf<RenameRequestState?>(null) }
    var renameInProgress by remember(activePath) { mutableStateOf(false) }
    var renameError by remember(activePath) { mutableStateOf<String?>(null) }

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
            fuzzyMatch(completionPrefix, key)
        }.sortedWith(
            compareByDescending<LspCompletionItem> { item ->
                val key = item.filterText.takeIf { it.isNotBlank() } ?: item.label
                fuzzyScore(completionPrefix, key)
            }
                .thenByDescending { it.kind == LspCompletionItemKind.KEYWORD }
                .thenBy { it.sortText }
                .thenBy { (it.filterText.takeIf { f -> f.isNotBlank() } ?: it.label).length },
        )
    }
    LaunchedEffect(filteredItems.size) {
        if (completionSelectedIndex >= filteredItems.size) completionSelectedIndex = 0
    }
    LaunchedEffect(filteredItems, completionSelectedIndex) {
        val resolve = onResolveCompletion ?: return@LaunchedEffect
        val item = filteredItems.getOrNull(completionSelectedIndex) ?: return@LaunchedEffect
        val token = item.resolveToken ?: return@LaunchedEffect
        if (resolvedByToken.containsKey(token)) return@LaunchedEffect
        delay(120)
        resolve(token).whenComplete { resolved, _ ->
            if (resolved != null) resolvedByToken[token] = resolved
        }
    }

    val clearTabstops: () -> Unit = {
        activeTabstops = emptyList()
        activeTabstopIndex = 0
        activeTabstopBase = 0
        activeTabstopBaseTextLen = 0
        activeTabstopFinalCaret = -1
    }

    val closeCompletion: () -> Unit = {
        completionItems = emptyList()
        completionSelectedIndex = 0
        completionTriggerOffset = -1
        commentKeywordMode = false
        commentKeywordNeedsSpace = false
    }

    val closeSignatureHelp: () -> Unit = {
        lspSignatureInfo = null
        lspSignatureRequestToken += 1
    }

    val triggerSignatureHelp: (Int, String?, Boolean) -> Unit = lambda@{ requestOffset, trig, retrigger ->
        val cb = onRequestSignatureHelp ?: return@lambda
        if (activePath == null) return@lambda
        val req = requestOffset.coerceIn(0, value.text.length)
        val pos = TextBuffer(value.text).lineColOf(req)
        lspSignatureRequestToken += 1
        val token = lspSignatureRequestToken
        fun apply(info: SignatureHelpInfo?) {
            if (info == null || info.isEmpty) {
                lspSignatureInfo = null
            } else {
                lspSignatureInfo = info
                lspSignatureActiveParam = info.effectiveActiveParameter()
            }
        }
        cb(pos.line, pos.col, trig, retrigger).whenComplete { info, _ ->
            if (token != lspSignatureRequestToken) return@whenComplete
            if (info != null && !info.isEmpty) {
                apply(info)
                return@whenComplete
            }
            completionScope.launch {
                delay(180)
                if (token != lspSignatureRequestToken) return@launch
                cb(pos.line, pos.col, trig, true).whenComplete { retry, _ ->
                    if (token != lspSignatureRequestToken) return@whenComplete
                    apply(retry)
                }
            }
        }
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
            val maxAttempts = if (triggerChar != null) 6 else 1
            var attempt = 0
            while (attempt < maxAttempts) {
                if (token != completionRequestToken) return@launch
                val list = runCatching { cb(pos.line, pos.col, triggerChar).await() }.getOrNull()
                if (token != completionRequestToken) return@launch
                if (list != null && list.items.isNotEmpty()) {
                    completionItems = list.items
                    completionSelectedIndex = 0
                    return@launch
                }
                attempt++
                if (attempt < maxAttempts) delay(900)
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
        if (commentKeywordMode && item.insertText.isEmpty() && item.label.isEmpty()) {
            closeCompletion()
            return@lambda
        }
        page.lsp.CompletionFrecency.recordSelection(item.label, item.kind)
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
        val finishApply: (List<page.lsp.CompletionEdit>) -> Unit = { additionalEdits ->
            val insertText = if (commentKeywordMode) item.insertText else keywordInsertText(item)
            val baseInsert = if (commentKeywordMode && commentKeywordNeedsSpace) " " + insertText
            else insertText
            val needsParens = !item.isSnippet &&
                !baseInsert.contains("(") &&
                (item.kind == page.lsp.CompletionItemKind.METHOD ||
                 item.kind == page.lsp.CompletionItemKind.FUNCTION ||
                 item.kind == page.lsp.CompletionItemKind.CONSTRUCTOR)
            val rawInsert = if (needsParens) "$baseInsert($1)" else baseInsert
            val expandedRaw = if (item.isSnippet || needsParens) SnippetExpander.expand(rawInsert)
            else page.lsp.ExpandedSnippet(rawInsert, rawInsert.length)
            val expanded = SnippetExpander.reindentContinuationLines(expandedRaw, currentLineIndent(text, replaceStart))
            val addEdits = additionalEdits
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
            val lastStopEnd = expanded.tabstops.lastOrNull()?.end ?: -1
            val finalIsLandable = expanded.tabstops.isNotEmpty() && expanded.finalCaret > lastStopEnd
            if (expanded.tabstops.size >= 2 || finalIsLandable) {
                activeTabstops = expanded.tabstops
                activeTabstopIndex = 0
                activeTabstopBase = replaceStart
                activeTabstopBaseTextLen = newText.length
                activeTabstopFinalCaret = if (finalIsLandable) expanded.finalCaret else -1
            } else {
                clearTabstops()
            }
            closeCompletion()
        }
        val token = item.resolveToken
        val cached = token?.let { resolvedByToken[it] }
        val resolve = onResolveCompletion
        if (token != null && resolve != null && cached == null && item.additionalEdits.isEmpty()) {
            resolve(token)
                .orTimeout(400, java.util.concurrent.TimeUnit.MILLISECONDS)
                .whenComplete { resolved, _ ->
                    completionScope.launch {
                        if (resolved != null) resolvedByToken[token] = resolved
                        finishApply(resolved?.additionalEdits?.takeIf { it.isNotEmpty() } ?: item.additionalEdits)
                    }
                }
        } else {
            finishApply(cached?.additionalEdits?.takeIf { it.isNotEmpty() } ?: item.additionalEdits)
        }
    }

    val advanceTabstop: () -> Boolean = lambda@{
        val stops = activeTabstops
        if (stops.isEmpty()) return@lambda false
        val base = activeTabstopBase
        val textLen = value.text.length
        val delta = textLen - activeTabstopBaseTextLen
        val nextIdx = activeTabstopIndex + 1
        if (nextIdx < stops.size) {
            val stop = stops[nextIdx]
            val from = (base + stop.start + delta).coerceIn(0, textLen)
            val to = (base + stop.end + delta).coerceIn(from, textLen)
            activeTabstopIndex = nextIdx
            onValueChange(value.copy(selection = TextRange(from, to)))
            return@lambda true
        }
        val finalCaret = activeTabstopFinalCaret
        if (finalCaret >= 0) {
            val pos = (base + finalCaret + delta).coerceIn(0, textLen)
            clearTabstops()
            onValueChange(value.copy(selection = TextRange(pos)))
            return@lambda true
        }
        clearTabstops()
        false
    }

    LaunchedEffect(value.text, value.selection.end) {
        val newText = value.text
        val newCaret = value.selection.end
        val oldText = lastSeenText
        val prevSelLen = lastSeenSelectionLen
        lastSeenText = newText
        lastSeenSelectionLen = value.selection.end - value.selection.start
        val shrink = oldText.length - newText.length
        val typedOverSelection = prevSelLen >= 2 && shrink <= prevSelLen
        if (activeTabstops.isNotEmpty() && shrink >= 2 && !typedOverSelection) {
            clearTabstops()
        }
        val isInsertOne = newText.length == oldText.length + 1 && newCaret > 0
        var didTrigger = false
        val commentCtx = CommentKeywordCompletion.detect(newText, newCaret)
            ?.takeUnless { isInsideStringLiteral(newText, it.anchor) }
        if (commentCtx != null) {
            val needsRefresh = !commentKeywordMode || completionTriggerOffset != commentCtx.anchor
            if (needsRefresh) {
                completionRequestToken += 1
                completionItems = CommentKeywordCompletion.items
                completionTriggerOffset = commentCtx.anchor
                completionSelectedIndex = 0
                commentKeywordMode = true
            }
            commentKeywordNeedsSpace = commentCtx.needsLeadingSpace
            didTrigger = true
        } else if (commentKeywordMode) {
            closeCompletion()
        }
        if (isInsertOne && !didTrigger) {
            val inserted = newText[newCaret - 1]
            val isWordChar = inserted.isLetter() || inserted == '_'
            val insideString = isInsideStringLiteral(newText, newCaret - 1)
            when {
                inserted == '.' && !insideString -> { triggerCompletion(newCaret, newCaret, "."); didTrigger = true }
                inserted == ':' && !insideString -> { triggerCompletion(newCaret, newCaret, null); didTrigger = true }
                isWordChar && !insideString && completionTriggerOffset < 0 -> {
                    val midWordEnabled = pageSettings.lsp.triggerCompletionMidWord
                    val prevIsBoundary = newCaret < 2 ||
                        !(newText[newCaret - 2].isLetterOrDigit() || newText[newCaret - 2] == '_')
                    if (midWordEnabled || prevIsBoundary) {
                        var wordStart = newCaret - 1
                        while (wordStart > 0) {
                            val c = newText[wordStart - 1]
                            if (!(c.isLetterOrDigit() || c == '_')) break
                            wordStart--
                        }
                        triggerCompletion(wordStart, newCaret, null)
                        didTrigger = true
                    }
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
                } else {
                    refreshCompletion()
                }
            }
        }

        if (onRequestSignatureHelp != null && activePath != null) {
            val sigBuf = TextBuffer(newText)
            val sigPos = sigBuf.lineColOf(newCaret.coerceIn(0, newText.length))
            val sigLine = if (sigPos.line < sigBuf.lineCount) sigBuf.lineAt(sigPos.line) else ""
            val activeP = SignatureActiveParam.fromLineText(sigLine, sigPos.col)
            if (activeP == null) {
                if (lspSignatureInfo != null) closeSignatureHelp()
            } else {
                val triggerChar = if (isInsertOne) {
                    when (newText[newCaret - 1]) {
                        '(' -> "("
                        ',' -> ","
                        else -> null
                    }
                } else null
                if (triggerChar != null) {
                    triggerSignatureHelp(newCaret, triggerChar, false)
                } else {
                    lspSignatureActiveParam = activeP
                }
            }
        }
    }

    val completionDisplay = remember(filteredItems) {
        filteredItems.map { item ->
            if (item.insertText.isEmpty() && item.label.isEmpty()) {
                CompletionDisplay(label = " ", kindHint = "", detail = null)
            } else {
                val displayLabel = sanitizeLabel(item.label)
                    ?: sanitizeLabel(item.filterText)
                    ?: sanitizeLabel(item.insertText)
                    ?: "<unnamed>"
                val resolved = item.resolveToken?.let { resolvedByToken[it] }
                val effectiveDetail = resolved?.detail?.takeIf { it.isNotBlank() } ?: item.detail
                CompletionDisplay(
                    label = displayLabel,
                    kindHint = kindHint(item.kind),
                    detail = effectiveDetail?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() },
                    documentation = resolved?.documentation?.takeIf { it.isNotBlank() } ?: item.documentation,
                    kindColor = kindColor(item.kind),
                )
            }
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
        if (focusGainVersion > 0 && savedScrollOnPress >= 0) {
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
                    if (!pageSettings.editor.highlightCurrentLine) return@drawBehind
                    val lineH = editorLineHeight.toPx()
                    val topPad = 16.dp.toPx()
                    val y = topPad + caret.line * lineH
                    drawRect(
                        color = currentLineBg,
                        topLeft = Offset(0f, y),
                        size = Size(size.width, lineH),
                    )
                },
        ) {
            if (pageSettings.editor.showLineNumbers) {
                LineNumberGutter(
                    lines = gutterLines,
                    currentOriginalLine = caret.line,
                    onToggleFold = { line ->
                        val region = foldStartByLine[line] ?: return@LineNumberGutter
                        foldedRegions = if (region in foldedRegions) foldedRegions - region
                        else foldedRegions + region
                    },
                    onPickKeyword = onPickKeyword,
                    textStyle = textStyle,
                )
            }
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
                onCtrlPress = { origOff ->
                    val cb = onRequestReferences
                    if (cb == null) false
                    else {
                        val text = latestValue.text
                        val word = wordRangeAt(text, origOff)
                        val symbolName = if (word != null) text.substring(word.first, word.second) else ""
                        if (
                            !isInStringOrComment(tokens, text, origOff) &&
                            isRenamableIdentifier(symbolName)
                        ) {
                            val pos = TextBuffer(text).lineColOf(origOff)
                            cb(pos.line, pos.col, symbolName)
                            true
                        } else false
                    }
                },
                onResolveCtrlHoverLink = { origOff ->
                    if (onRequestReferences == null) null
                    else {
                        val text = latestValue.text
                        val word = wordRangeAt(text, origOff)
                        if (word == null) null
                        else {
                            val symbolName = text.substring(word.first, word.second)
                            if (
                                !isInStringOrComment(tokens, text, origOff) &&
                                isRenamableIdentifier(symbolName)
                            ) word.first until word.second
                            else null
                        }
                    }
                },
                ctrlHoverLinkColor = MaterialTheme.colorScheme.primary,
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
                                if (event.key == Key.Tab && activeTabstops.isNotEmpty() && advanceTabstop()) {
                                    closeCompletion()
                                } else {
                                    applySelected()
                                }
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
                    if (
                        (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                        !event.isShiftPressed && !event.isCtrlPressed && !event.isAltPressed &&
                        value.selection.start == value.selection.end
                    ) {
                        val caret = value.selection.end.coerceIn(0, value.text.length)
                        val cont = KdocContinuation.compute(value.text, caret, tokens)
                        if (cont != null) {
                            val resumeAt = (caret + cont.consumeAfterCaret).coerceAtMost(value.text.length)
                            val newText = value.text.substring(0, caret) +
                                cont.insertText + value.text.substring(resumeAt)
                            val newCaret = caret + cont.caretOffsetWithinInsert
                            onValueChange(
                                value.copy(text = newText, selection = TextRange(newCaret))
                            )
                            return@CodeEditor true
                        }
                    }
                    if (event.isCtrlPressed && event.isShiftPressed && event.key == Key.Spacebar) {
                        val caret = value.selection.end.coerceIn(0, value.text.length)
                        triggerSignatureHelp(caret, null, lspSignatureInfo != null)
                        return@CodeEditor true
                    }
                    if (event.isCtrlPressed && event.key == Key.Spacebar) {
                        val text = value.text
                        val caret = value.selection.end.coerceIn(0, text.length)
                        val wordStart = wordStartAt(text, caret)
                        triggerCompletion(wordStart, caret, null)
                        return@CodeEditor true
                    }
                    if (event.key == Key.Escape && lspSignatureInfo != null) {
                        closeSignatureHelp()
                        return@CodeEditor true
                    }
                    val isRenameKey =
                        (event.key == Key.F2 && !event.isCtrlPressed && !event.isShiftPressed) ||
                            (event.key == Key.F6 && event.isShiftPressed && !event.isCtrlPressed)
                    if (isRenameKey && onRequestRename != null) {
                        val text = value.text
                        val caret = value.selection.end.coerceIn(0, text.length)
                        if (isInStringOrComment(tokens, text, caret)) return@CodeEditor true
                        val pos = TextBuffer(text).lineColOf(caret)
                        val word = wordRangeAt(text, caret)
                        val placeholder = if (word != null) text.substring(word.first, word.second) else ""
                        if (!isRenamableIdentifier(placeholder)) return@CodeEditor true
                        renameError = null
                        val prepare = onRequestPrepareRename
                        if (prepare != null) {
                            prepare(pos.line, pos.col).whenComplete { p, _ ->
                                renameRequest = when {
                                    p == null -> RenameRequestState(pos.line, pos.col, placeholder)
                                    p.isDefaultBehavior -> RenameRequestState(pos.line, pos.col, placeholder)
                                    else -> RenameRequestState(
                                        line = pos.line,
                                        character = pos.col,
                                        placeholder = p.placeholder ?: placeholder,
                                    )
                                }
                            }
                        } else {
                            renameRequest = RenameRequestState(pos.line, pos.col, placeholder)
                        }
                        return@CodeEditor true
                    }
                    if ((event.key == Key.F12 && event.isShiftPressed && !event.isCtrlPressed) ||
                        (event.key == Key.B && event.isCtrlPressed && !event.isShiftPressed && !event.isAltPressed)
                    ) {
                        val cb = onRequestReferences
                        if (cb != null) {
                            val text = value.text
                            val caret = value.selection.end.coerceIn(0, text.length)
                            if (!isInStringOrComment(tokens, text, caret)) {
                                val pos = TextBuffer(text).lineColOf(caret)
                                val word = wordRangeAt(text, caret)
                                val symbolName = if (word != null) text.substring(word.first, word.second) else ""
                                if (isRenamableIdentifier(symbolName)) {
                                    cb(pos.line, pos.col, symbolName)
                                }
                            }
                            return@CodeEditor true
                        }
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
                        event.isCtrlPressed && !event.isShiftPressed &&
                        event.key == Key.Slash && commentPrefix != null
                    ) {
                        val r = LineComment.toggle(
                            value.text,
                            value.selection.start,
                            value.selection.end,
                            commentPrefix,
                        )
                        if (r.text != value.text) {
                            onValueChange(
                                value.copy(
                                    text = r.text,
                                    selection = TextRange(r.selectionStart, r.selectionEnd),
                                ),
                            )
                        }
                        return@CodeEditor true
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
                focusRequestVersion = editorFocusVersion,
                caretBringIntoViewEnabled = caretBringArmed,
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
                languageMode = remember(activePath) {
                    val ext = activePath?.fileName?.toString()?.substringAfterLast('.', "")?.lowercase()
                    if (ext == "html" || ext == "htm" || ext == "xhtml") "html" else null
                },
                autoPairs = pageSettings.autoInput.pairs,
                autoHtmlTags = pageSettings.autoInput.htmlTags,
                backspaceDeletesPair = pageSettings.autoInput.backspaceDeletesPair,
                tabSize = pageSettings.editor.tabSize,
                useSpacesForTab = pageSettings.editor.useSpacesForTab,
                completionItems = completionDisplay,
                completionSelectedIndex = completionSelectedIndex,
                completionAnchorOffset = completionTriggerOffset.takeIf { it >= 0 },
                onCompletionItemClick = { idx ->
                    completionSelectedIndex = idx
                    applySelected()
                },
                signatureHelp = lspSignatureInfo?.let { info ->
                    val sig = info.active ?: return@let null
                    val activeIdx = lspSignatureActiveParam.coerceAtLeast(0)
                    val param = sig.parameters.getOrNull(activeIdx)
                    SignatureHelpDisplay(
                        label = sig.label,
                        activeParamRange = param?.labelRange,
                        documentation = sig.documentation,
                        activeParamDoc = param?.documentation,
                        signatureIndex = info.activeSignature,
                        signatureCount = info.signatures.size,
                    )
                },
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
            onLspStatusClick = onLspStatusClick,
            onActivityClick = onActivityClick,
            onProblemsToggle = onProblemsToggle,
            todoCount = todoCount,
            onTodoToggle = onTodoToggle,
            jdkVersion = jdkVersion,
            jdkVersionTooltip = jdkVersionTooltip,
            onJdkVersionClick = onJdkVersionClick,
        )
    }

    val activeRename = renameRequest
    if (activeRename != null && onRequestRename != null) {
        RenameDialog(
            request = activeRename,
            inProgress = renameInProgress,
            error = renameError,
            onDismiss = {
                if (!renameInProgress) {
                    renameRequest = null
                    renameError = null
                }
            },
            onSubmit = { newName ->
                renameInProgress = true
                renameError = null
                onRequestRename(activeRename.line, activeRename.character, newName)
                    .whenComplete { edit, err ->
                        renameInProgress = false
                        when {
                            err != null -> renameError = err.message ?: err.toString()
                            edit.isEmpty -> renameError = "No changes"
                            else -> {
                                onApplyRename?.invoke(edit)
                                renameRequest = null
                                renameError = null
                            }
                        }
                    }
            },
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

private fun kindColor(kind: LspCompletionItemKind): Color = when (kind) {
    LspCompletionItemKind.METHOD, LspCompletionItemKind.FUNCTION -> Color(0xFF3B82F6)
    LspCompletionItemKind.CONSTRUCTOR -> Color(0xFF8B5CF6)
    LspCompletionItemKind.CLASS, LspCompletionItemKind.INTERFACE, LspCompletionItemKind.STRUCT -> Color(0xFFF59E0B)
    LspCompletionItemKind.VARIABLE, LspCompletionItemKind.FIELD, LspCompletionItemKind.PROPERTY -> Color(0xFF6366F1)
    LspCompletionItemKind.KEYWORD -> Color(0xFFEF4444)
    LspCompletionItemKind.SNIPPET -> Color(0xFF10B981)
    LspCompletionItemKind.ENUM, LspCompletionItemKind.ENUM_MEMBER -> Color(0xFFEC4899)
    LspCompletionItemKind.MODULE -> Color(0xFF14B8A6)
    LspCompletionItemKind.CONSTANT -> Color(0xFFF97316)
    LspCompletionItemKind.TYPE_PARAMETER -> Color(0xFF0EA5E9)
    else -> Color(0xFF9CA3AF)
}

private fun sanitizeLabel(s: String?): String? {
    if (s.isNullOrEmpty()) return null
    val collapsed = s.replace(Regex("\\s+"), " ").trim()
    return collapsed.ifEmpty { null }
}

private val KOTLIN_HARD_KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
    "if", "in", "interface", "is", "null", "object", "package", "return",
    "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var",
    "when", "while",
)

private fun isRenamableIdentifier(s: String): Boolean {
    if (s.isEmpty()) return false
    val first = s[0]
    if (!first.isLetter() && first != '_') return false
    if (s.any { !it.isLetterOrDigit() && it != '_' }) return false
    return s !in KOTLIN_HARD_KEYWORDS
}

private fun wordRangeAt(text: String, caret: Int): Pair<Int, Int>? {
    val c = caret.coerceIn(0, text.length)
    var start = c
    while (start > 0) {
        val ch = text[start - 1]
        if (ch.isLetterOrDigit() || ch == '_') start-- else break
    }
    var end = c
    while (end < text.length) {
        val ch = text[end]
        if (ch.isLetterOrDigit() || ch == '_') end++ else break
    }
    if (start == end) return null
    return start to end
}

private fun wordStartAt(text: String, caret: Int): Int {
    var i = caret.coerceIn(0, text.length)
    while (i > 0) {
        val ch = text[i - 1]
        if (ch.isLetterOrDigit() || ch == '_') i-- else break
    }
    return i
}

private fun isInStringOrComment(tokens: List<Token>, text: String, caret: Int): Boolean {
    if (tokens.isNotEmpty()) {
        val hit = tokens.firstOrNull { caret >= it.start && caret < it.endExclusive }
        if (hit != null) return hit.kind == TokenKind.STRING ||
            hit.kind == TokenKind.COMMENT ||
            hit.kind == TokenKind.DOC_COMMENT
    }
    return isInsideStringLiteral(text, caret)
}

private fun isInsideStringLiteral(text: String, caret: Int): Boolean {
    val end = caret.coerceIn(0, text.length)
    var lineStart = end
    while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
    var inString = false
    var i = lineStart
    while (i < end) {
        val ch = text[i]
        if (ch == '\\' && inString) {
            i += 2
            continue
        }
        if (ch == '"') inString = !inString
        i++
    }
    return inString
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

private fun currentLineIndent(text: String, offset: Int): String {
    val safe = offset.coerceIn(0, text.length)
    var lineStart = safe
    while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
    val sb = StringBuilder()
    var i = lineStart
    while (i < text.length && (text[i] == ' ' || text[i] == '\t')) {
        sb.append(text[i])
        i++
    }
    return sb.toString()
}

internal fun diagnosticUnderlineRange(text: String, startOff: Int, endOff: Int): Pair<Int, Int>? {
    if (startOff < 0 || endOff < 0) return null
    if (endOff > startOff) return startOff to endOff
    if (startOff < text.length && text[startOff] != '\n') return startOff to startOff + 1
    if (startOff > 0 && text[startOff - 1] != '\n') return startOff - 1 to startOff
    return null
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

internal data class InlayHintDisplay(
    val originalOffset: Int,
    val label: String,
    val kind: InlayHintItem.Kind,
    val paddingLeft: Boolean,
    val paddingRight: Boolean,
)

private class CombinedHighlightTransformation(
    private val tokens: List<Token>,
    private val palette: SyntaxPalette,
    private val todoColors: Map<String, androidx.compose.ui.graphics.Color>,
    private val commentColorOverrides: Map<IntRange, androidx.compose.ui.graphics.Color>,
    private val matches: List<IntRange>,
    private val activeIndex: Int,
    private val matchBg: androidx.compose.ui.graphics.Color,
    private val activeBg: androidx.compose.ui.graphics.Color,
    private val bracketMatch: Pair<Int, Int>?,
    private val bracketBg: androidx.compose.ui.graphics.Color,
    private val unnecessaryRanges: List<IntRange> = emptyList(),
    private val unnecessaryStyle: SpanStyle = SpanStyle(),
    private val foldSegments: List<FoldRegions.Segment>,
    private val foldPlaceholderStyle: SpanStyle,
    private val foldCloserStyle: SpanStyle,
    private val foldPrefixStyle: SpanStyle = SpanStyle(),
    private val inlayHints: List<InlayHintDisplay> = emptyList(),
    private val inlayHintStyle: SpanStyle = SpanStyle(),
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = applyHighlights(text)
        val hasFolds = foldSegments.isNotEmpty()
        val hasHints = inlayHints.isNotEmpty()
        if (!hasFolds && !hasHints) {
            return TransformedText(styled, OffsetMapping.Identity)
        }
        val foldBuilder = AnnotatedString.Builder()
        if (hasFolds) {
            var cursor = 0
            for (seg in foldSegments) {
                if (cursor < seg.origStart) {
                    foldBuilder.append(styled.subSequence(cursor, seg.origStart))
                }
                val placeholderStart = foldBuilder.length
                foldBuilder.append(seg.replacement)
                val dotsOffset = seg.replacement.indexOf("...")
                if (dotsOffset >= 0) {
                    val dotsStart = placeholderStart + dotsOffset
                    val dotsEnd = dotsStart + 3
                    if (!seg.replacement.startsWith(' ')) {
                        var prefixLen = dotsOffset
                        while (prefixLen > 0 && seg.replacement[prefixLen - 1] == ' ') prefixLen--
                        if (prefixLen > 0) {
                            foldBuilder.addStyle(foldPrefixStyle, placeholderStart, placeholderStart + prefixLen)
                        }
                    }
                    foldBuilder.addStyle(foldPlaceholderStyle, dotsStart, dotsEnd)
                    val visibleEnd = if (seg.replacement.endsWith("\n")) {
                        placeholderStart + seg.replacement.length - 1
                    } else {
                        placeholderStart + seg.replacement.length
                    }
                    val trimmedReplacement = if (seg.replacement.endsWith("\n")) {
                        seg.replacement.dropLast(1)
                    } else {
                        seg.replacement
                    }
                    if (visibleEnd > dotsEnd && trimmedReplacement.endsWith("*/")) {
                        foldBuilder.addStyle(foldCloserStyle, dotsEnd, visibleEnd)
                    }
                    if (bracketMatch != null && seg.closerLength > 0) {
                        val closerOrigEnd = seg.closerOrigStart + seg.closerLength
                        val touchesCloser =
                            bracketMatch.first in seg.closerOrigStart until closerOrigEnd ||
                                bracketMatch.second in seg.closerOrigStart until closerOrigEnd
                        if (touchesCloser) {
                            val closerVisStart = placeholderStart + seg.closerInRepStart
                            val closerVisEnd = closerVisStart + seg.closerLength
                            foldBuilder.addStyle(
                                SpanStyle(background = bracketBg),
                                closerVisStart,
                                closerVisEnd,
                            )
                        }
                    }
                }
                cursor = seg.origEnd
            }
            if (cursor < styled.length) {
                foldBuilder.append(styled.subSequence(cursor, styled.length))
            }
        } else {
            foldBuilder.append(styled)
        }
        val foldText = foldBuilder.toAnnotatedString()
        if (!hasHints) {
            return TransformedText(foldText, FoldOffsetMapping(foldSegments))
        }
        val foldMapping = if (hasFolds) FoldOffsetMapping(foldSegments) else IdentityFoldMapping
        val insertions = mutableListOf<HintInsertion>()
        for (hint in inlayHints) {
            val origAnchor = hint.originalOffset.coerceIn(0, text.length)
            if (isInsideFoldSegment(origAnchor)) continue
            val foldAnchor = foldMapping.originalToTransformed(origAnchor)
            val rendered = buildString {
                if (hint.paddingLeft) append(' ')
                append(hint.label)
                if (hint.paddingRight) append(' ')
            }
            if (rendered.isEmpty()) continue
            insertions += HintInsertion(foldAnchor, origAnchor, rendered)
        }
        if (insertions.isEmpty()) {
            return TransformedText(foldText, FoldOffsetMapping(foldSegments))
        }
        insertions.sortBy { it.foldOffset }
        val finalBuilder = AnnotatedString.Builder()
        var cursor = 0
        for (ins in insertions) {
            val target = ins.foldOffset.coerceIn(cursor, foldText.length)
            if (target > cursor) {
                finalBuilder.append(foldText.subSequence(cursor, target))
            }
            val labelStart = finalBuilder.length
            finalBuilder.append(ins.label)
            finalBuilder.addStyle(inlayHintStyle, labelStart, labelStart + ins.label.length)
            cursor = target
        }
        if (cursor < foldText.length) {
            finalBuilder.append(foldText.subSequence(cursor, foldText.length))
        }
        return TransformedText(
            finalBuilder.toAnnotatedString(),
            ComposedFoldInlayMapping(foldMapping, insertions),
        )
    }

    private fun isInsideFoldSegment(originalOffset: Int): Boolean {
        for (seg in foldSegments) {
            if (originalOffset >= seg.origStart && originalOffset < seg.origEnd) return true
        }
        return false
    }

    private fun applyHighlights(text: AnnotatedString): AnnotatedString {
        val builder = AnnotatedString.Builder(text)
        val raw = text.text
        val commentOverride = HashMap<IntRange, androidx.compose.ui.graphics.Color>()
        val tokenOverride = HashMap<IntRange, androidx.compose.ui.graphics.Color>()
        for (i in tokens.indices) {
            val t = tokens[i]
            if (t.kind != TokenKind.TODO_TAG) continue
            val ks = t.range.first.coerceIn(0, text.length)
            val ke = (t.range.last + 1).coerceIn(ks, text.length)
            if (ks == ke) continue
            val color = todoKeywordColor(raw, ks, ke) ?: continue
            for (j in i - 1 downTo 0) {
                val p = tokens[j]
                val isComment = p.kind == TokenKind.COMMENT || p.kind == TokenKind.DOC_COMMENT
                if (!isComment) continue
                if (p.range.first <= t.range.first && p.range.last >= t.range.last) {
                    val forced = commentColorOverrides[p.range]
                    if (forced != null) {
                        commentOverride[p.range] = forced
                        tokenOverride[t.range] = forced
                    } else {
                        commentOverride.putIfAbsent(p.range, color)
                        tokenOverride[t.range] = commentOverride[p.range]!!
                    }
                }
                break
            }
        }
        for (token in tokens) {
            val start = token.range.first.coerceIn(0, text.length)
            val end = (token.range.last + 1).coerceIn(start, text.length)
            if (start == end) continue
            if (unnecessaryRanges.any { start < (it.last + 1) && end > it.first }) continue
            val color = when {
                token.kind == TokenKind.TODO_TAG ->
                    tokenOverride[token.range]
                        ?: todoKeywordColor(raw, start, end)
                        ?: palette.todoTag
                (token.kind == TokenKind.COMMENT || token.kind == TokenKind.DOC_COMMENT) &&
                    commentOverride.containsKey(token.range) ->
                    commentOverride[token.range]!!
                else -> colorFor(token.kind, palette) ?: continue
            }
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
        for (range in unnecessaryRanges) {
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(start, text.length)
            if (start == end) continue
            builder.addStyle(unnecessaryStyle, start, end)
        }
        return builder.toAnnotatedString()
    }

    private fun todoKeywordColor(raw: String, start: Int, end: Int): androidx.compose.ui.graphics.Color? {
        for ((keyword, color) in todoColors) {
            if (end - start >= keyword.length && raw.regionMatches(start, keyword, 0, keyword.length)) {
                return color
            }
        }
        return null
    }

    private fun colorFor(kind: TokenKind, palette: SyntaxPalette) = when (kind) {
        TokenKind.KEYWORD -> palette.keyword
        TokenKind.STRING -> palette.string
        TokenKind.NUMBER -> palette.number
        TokenKind.COMMENT -> palette.comment
        TokenKind.DOC_COMMENT -> palette.docComment
        TokenKind.TODO_TAG -> palette.todoTag
        TokenKind.ANNOTATION -> palette.annotation
        TokenKind.TYPE -> palette.type
        TokenKind.IDENTIFIER -> palette.identifier
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

internal object IdentityFoldMapping : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = offset
    override fun transformedToOriginal(offset: Int): Int = offset
}

internal data class HintInsertion(
    val foldOffset: Int,
    val originalAnchor: Int,
    val label: String,
)

internal class ComposedFoldInlayMapping(
    private val foldMapping: OffsetMapping,
    private val insertions: List<HintInsertion>,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val foldOffset = foldMapping.originalToTransformed(offset)
        var shift = 0
        for (ins in insertions) {
            if (ins.foldOffset < foldOffset) shift += ins.label.length else break
        }
        return foldOffset + shift
    }

    override fun transformedToOriginal(offset: Int): Int {
        var shift = 0
        for (ins in insertions) {
            val transStart = ins.foldOffset + shift
            if (offset <= transStart) return foldMapping.transformedToOriginal(offset - shift)
            val transEnd = transStart + ins.label.length
            if (offset < transEnd) return ins.originalAnchor
            shift += ins.label.length
        }
        return foldMapping.transformedToOriginal(offset - shift)
    }
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
    onLspStatusClick: (() -> Unit)? = null,
    onActivityClick: ((LspController.Activity) -> Unit)? = null,
    onProblemsToggle: (() -> Unit)? = null,
    todoCount: Int = 0,
    onTodoToggle: (() -> Unit)? = null,
    jdkVersion: String? = null,
    jdkVersionTooltip: String? = null,
    onJdkVersionClick: (() -> Unit)? = null,
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
                color = Glass.colors.error,
                label = "errors",
                onClick = onProblemsToggle,
            )
            if (warningCount > 0) DiagnosticBadge(
                count = warningCount,
                color = Glass.colors.warn,
                label = "warnings",
                onClick = onProblemsToggle,
            )
            TodoStatusBadge(
                count = todoCount,
                color = MaterialTheme.colorScheme.secondary,
                onClick = onTodoToggle,
            )
            val showActivities = lspActivities.isNotEmpty()
            val showLifecycle = !lspStatusText.isNullOrBlank()
            val showJdk = !jdkVersion.isNullOrBlank()
            if (showActivities || showLifecycle || showJdk) {
                Box(modifier = Modifier.weight(1f))
                if (showActivities) {
                    LspActivitiesItem(activities = lspActivities, onActivityClick = onActivityClick)
                }
                if (showLifecycle) {
                    LspLifecycleItem(text = lspStatusText!!, onClick = onLspStatusClick)
                }
                if (showJdk) {
                    RuntimeVersionItem(label = jdkVersion!!, tooltip = jdkVersionTooltip, onClick = onJdkVersionClick)
                }
            }
        }
    }
}

@Composable
private fun LspLifecycleItem(text: String, onClick: (() -> Unit)? = null) {
    val baseColor = MaterialTheme.colorScheme.onSurfaceVariant
    val color = if (onClick != null) MaterialTheme.colorScheme.primary else baseColor
    val mod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(verticalAlignment = Alignment.CenterVertically, modifier = mod) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RuntimeVersionItem(label: String, tooltip: String? = null, onClick: (() -> Unit)? = null) {
    val color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val mod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    if (tooltip != null) {
        androidx.compose.foundation.TooltipArea(
            tooltip = {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                ) {
                    Text(
                        text = tooltip,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            },
        ) {
            Text(text = label, modifier = mod, style = MaterialTheme.typography.labelSmall, color = color)
        }
    } else {
        Text(text = label, modifier = mod, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun LspActivitiesItem(
    activities: List<LspController.Activity>,
    onActivityClick: ((LspController.Activity) -> Unit)? = null,
) {
    if (activities.isEmpty()) return
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activities.size) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }
    var expanded by remember { mutableStateOf(false) }
    val first = activities.first()
    val firstElapsed = ((nowMs - first.startedAtMs) / 1000L).coerceAtLeast(0L).toInt()
    val canExpand = activities.size >= 2
    val firstClickable = first.installerId != null && onActivityClick != null
    val rowMod = Modifier.then(
        when {
            canExpand -> Modifier.clickable { expanded = !expanded }
            firstClickable -> Modifier.clickable { onActivityClick!!(first) }
            else -> Modifier
        },
    )
    Box {
        Row(
            modifier = rowMod,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActivityProgressBar(first.progress)
            Text(
                text = if (firstElapsed > 0) "LSP · ${first.label} (${firstElapsed}s)"
                else "LSP · ${first.label}",
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
        CompactDropdown(
            expanded = canExpand && expanded,
            onDismissRequest = { expanded = false },
            minWidth = 220.dp,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (a in activities) {
                        val secs = ((nowMs - a.startedAtMs) / 1000L).coerceAtLeast(0L).toInt()
                        val clickable = a.installerId != null && onActivityClick != null
                        Row(
                            modifier = Modifier.then(
                                if (clickable) Modifier.clickable {
                                    expanded = false
                                    onActivityClick!!(a)
                                } else Modifier,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActivityProgressBar(a.progress)
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

@Composable
private fun ActivityProgressBar(progress: Float?) {
    val barMod = Modifier.width(72.dp).height(3.dp)
    if (progress != null) {
        val animated by androidx.compose.animation.core.animateFloatAsState(
            targetValue = progress,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
            label = "installProgress",
        )
        androidx.compose.material3.LinearProgressIndicator(
            progress = { animated },
            modifier = barMod,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    } else {
        androidx.compose.material3.LinearProgressIndicator(
            modifier = barMod,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
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
private fun TodoStatusBadge(
    count: Int,
    color: androidx.compose.ui.graphics.Color,
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
            text = if (count > 0) "TODO $count" else "TODO",
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

internal fun keywordInsertText(item: LspCompletionItem): String {
    if (item.kind != LspCompletionItemKind.KEYWORD) return item.insertText
    val text = item.insertText
    if (text.isEmpty() || text.contains('$') || text.contains('\n')) return text
    if (text.last().isWhitespace()) return text
    return "$text "
}

internal fun fuzzyMatch(query: String, target: String): Boolean {
    if (query.isEmpty()) return true
    val q = query.lowercase()
    val t = target.lowercase()
    if (t.startsWith(q)) return true
    var qi = 0
    for (ch in t) {
        if (qi < q.length && ch == q[qi]) qi++
        if (qi == q.length) return true
    }
    return false
}

internal fun fuzzyScore(query: String, target: String): Int {
    if (query.isEmpty()) return 0
    val q = query.lowercase()
    val t = target.lowercase()
    if (t.startsWith(q)) return if (target.startsWith(query)) 2000 else 1000
    var score = 0
    var qi = 0
    var prevMatch = false
    var consecutiveBonus = 0
    for ((i, ch) in t.withIndex()) {
        if (qi < q.length && ch == q[qi]) {
            score += 10
            if (i == 0 || t[i - 1] == '.' || t[i - 1] == '-' || t[i - 1] == '_') score += 20
            if (prevMatch) { consecutiveBonus += 5; score += consecutiveBonus } else consecutiveBonus = 0
            prevMatch = true
            qi++
        } else {
            prevMatch = false
            consecutiveBonus = 0
        }
    }
    if (qi < q.length) return -1
    return score
}
