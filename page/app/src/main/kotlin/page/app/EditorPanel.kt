package page.app

import page.ui.isPrimaryPressed
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
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
import page.editor.TokenShift
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.launch
import page.lsp.CompletionItem as LspCompletionItem
import page.lsp.CompletionItemKind as LspCompletionItemKind
import page.lsp.CodeActionEntry
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
import page.ui.CompletionDisplay
import page.ui.CompletionKindIcon
import page.ui.EditorDecoration
import page.ui.EditorFontFamily
import page.ui.Glass
import page.ui.SignatureHelpDisplay
import page.ui.SyntaxPalette

private const val ASYNC_TOKENIZE_THRESHOLD = 20_000

private data class TokenizedText(val text: String, val tokens: List<Token>)

private const val FOLD_DETECT_DEBOUNCE_MS = 120L

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
    usedElsewhere: (String) -> Boolean = { false },
    usageRevision: Int = 0,
    onRequestCompletion: ((line: Int, character: Int, triggerCharacter: String?) -> CompletableFuture<CompletionList>)? = null,
    onRequestHover: ((line: Int, character: Int) -> CompletableFuture<HoverInfo?>)? = null,
    onRequestDefinition: ((line: Int, character: Int) -> CompletableFuture<List<DefinitionTarget>>)? = null,
    onRequestSignatureHelp: ((line: Int, character: Int, triggerCharacter: String?, isRetrigger: Boolean) -> CompletableFuture<SignatureHelpInfo?>)? = null,
    onResolveCompletion: ((token: Long) -> CompletableFuture<ResolvedCompletion?>)? = null,
    onGoToDefinition: ((DefinitionTarget) -> Unit)? = null,
    onRequestPrepareRename: ((line: Int, character: Int) -> CompletableFuture<RenamePrepare?>)? = null,
    onRequestRename: ((line: Int, character: Int, newName: String) -> CompletableFuture<RenameWorkspaceEdit>)? = null,
    onApplyRename: ((RenameWorkspaceEdit) -> Unit)? = null,
    onRequestReferences: ((line: Int, character: Int, symbolName: String, surface: ReferencesSurface) -> Unit)? = null,
    references: ReferencesQueryState? = null,
    codeAction: page.app.ui.CodeActionPreviewBinding = page.app.ui.CodeActionPreviewBinding(),
    onReferenceJump: (Path, Int, Int) -> Unit = { _, _, _ -> },
    onReferencesOpenInPanel: () -> Unit = {},
    onReferencesDismiss: () -> Unit = {},
    referenceLinePreview: (String, Int) -> String? = { _, _ -> null },
    onShowCallGraph: ((line: Int, character: Int) -> Unit)? = null,
    onRequestInlayHints: ((startLine: Int, startCharacter: Int, endLine: Int, endCharacter: Int) -> CompletableFuture<List<InlayHintItem>>)? = null,
    workspaceRoot: Path? = null,
    editorFocusVersion: Int = 0,
    initialFoldedStartLines: Set<Int> = emptySet(),
    onFoldStartLinesChange: (Set<Int>) -> Unit = {},
    initialVScroll: Int = 0,
    initialHScroll: Int = 0,
    onScrollChange: (vertical: Int, horizontal: Int) -> Unit = { _, _ -> },
    pageSettings: PageSettings = LocalPageSettings.current,
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
    val occurrenceBg = Glass.colors.accent.copy(alpha = 0.18f)
    val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val currentLineBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
    val bracketBg = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
    val foldPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
    val foldPlaceholderBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
    val palette = Glass.colors.syntax
    val syntaxPreset = pageSettings.editor.syntaxPreset
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

    val sourceText = value.text
    val tokenized by produceState(initialValue = TokenizedText("", emptyList()), sourceText, lexer) {
        val lx = lexer
        if (lx == null) {
            this.value = TokenizedText(sourceText, emptyList())
            return@produceState
        }
        if (sourceText.length > ASYNC_TOKENIZE_THRESHOLD) delay(60)
        this.value = withContext(Dispatchers.Default) {
            TokenizedText(sourceText, page.shared.syntax.SyntaxRoles.refine(sourceText, lx.tokenize(sourceText)))
        }
    }
    val tokens = remember(tokenized, sourceText) {
        if (tokenized.text == sourceText) tokenized.tokens
        else TokenShift.shift(tokenized.tokens, tokenized.text, sourceText)
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

    val bracketPairs = remember(value.text, tokens) {
        page.shared.syntax.BracketScan.pairs(value.text, tokens)
    }

    val identifierOccurrences = remember(
        value.text, value.selection.start, value.selection.end, tokens,
        pageSettings.editor.highlightIdentifierUnderCaret,
    ) {
        if (!pageSettings.editor.highlightIdentifierUnderCaret) emptyList()
        else if (value.selection.start != value.selection.end) emptyList()
        else IdentifierOccurrences.find(value.text, tokens, value.selection.start)
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
    val unusedRanges = remember(
        value.text, tokens, bracketPairs, pageSettings.editor.dimUnusedSymbols,
        usedElsewhere, usageRevision,
    ) {
        if (!pageSettings.editor.dimUnusedSymbols) emptyList()
        else page.shared.syntax.UnusedSymbols.find(value.text, tokens, bracketPairs, usedElsewhere)
    }

    val unusedUri = remember(activePath) { activePath?.toUri()?.toString() }
    LaunchedEffect(unusedUri, unusedRanges) {
        val uri = unusedUri ?: return@LaunchedEffect
        page.app.lsp.LocalDiagnostics.set(
            uri,
            page.app.lsp.UnusedQuickFixes.diagnostics(value.text, unusedRanges),
        )
    }
    DisposableEffect(unusedUri) {
        onDispose { unusedUri?.let { page.app.lsp.LocalDiagnostics.remove(it) } }
    }

    val unnecessaryRanges = remember(value.text, diagnostics, showInlineDiagnostics, unusedRanges) {
        unusedRanges + if (!showInlineDiagnostics) emptyList()
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
    var lspHoverPending by remember(activePath) { mutableStateOf(false) }
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
        lspHoverPending = false
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
        lspHoverPending = true
        val info = runCatching { cb(pos.line, pos.col).await() }.getOrNull()
        if (token != lspHoverRequestToken) return@LaunchedEffect
        lspHoverPending = false
        if (info == null) return@LaunchedEffect
        val lineText = TextBuffer(text).lineAt(pos.line)
        val enriched = info.enrichForPropertyDecl(lineText, pos.col)
        lspHoverInfo = enriched
        if (!enriched.needsKdocEnrichment()) return@LaunchedEffect
        val defCb = onRequestDefinition ?: return@LaunchedEffect
        val targets = runCatching { defCb(pos.line, pos.col).await() }.getOrNull()
        if (token != lspHoverRequestToken) return@LaunchedEffect
        val target = targets?.firstOrNull() ?: return@LaunchedEffect
        val defText = readDefinitionFileText(target.uri) ?: return@LaunchedEffect
        val final = enriched.enrichWithKDocFromDefinition(defText, target.startLine)
        if (token == lspHoverRequestToken) lspHoverInfo = final
    }

    val foldExtension = remember(activePath) {
        activePath?.fileName?.toString()?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
    }
    val foldRegions by produceState(emptyList<FoldRegions.Region>(), value.text, foldExtension) {
        val text = value.text
        val extension = foldExtension
        kotlinx.coroutines.delay(FOLD_DETECT_DEBOUNCE_MS)
        this.value = withContext(Dispatchers.Default) {
            LanguageFolders.forExtension(extension).detect(text)
        }
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
        val hints = runCatching { cb(0, 0, lineCount, 0).await() }.getOrNull()
        inlayHints = hints.orEmpty()
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
        search, tokens, bracketMatch, matchBg, activeBg, bracketBg, palette, syntaxPreset,
        identifierOccurrences, occurrenceBg,
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
                preset = syntaxPreset,
                todoColors = todoColors,
                commentColorOverrides = keywordOverrideByRange,
                matches = matches,
                activeIndex = activeIndex,
                matchBg = matchBg,
                activeBg = activeBg,
                bracketMatch = bracketMatch,
                bracketBg = bracketBg,
                occurrences = identifierOccurrences,
                occurrenceBg = occurrenceBg,
                rainbowPairs = if (pageSettings.editor.rainbowBrackets) bracketPairs else emptyList(),
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
            .debounce(200)
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
    var pendingDeclaration by remember(activePath) { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingDeclaration) {
        if (pendingDeclaration != null) {
            kotlinx.coroutines.delay(8_000)
            pendingDeclaration = null
        }
    }
    var declarationCacheText by remember(activePath) { mutableStateOf<String?>(null) }
    var declarationCache by remember(activePath) { mutableStateOf<Map<String, Int>>(emptyMap()) }
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
        val resolved = runCatching { resolve(token).await() }.getOrNull()
        if (resolved != null) resolvedByToken[token] = resolved
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
        completionScope.launch {
            val info = runCatching { cb(pos.line, pos.col, trig, retrigger).await() }.getOrNull()
            if (token != lspSignatureRequestToken) return@launch
            if (info != null && !info.isEmpty) {
                apply(info)
                return@launch
            }
            delay(180)
            if (token != lspSignatureRequestToken) return@launch
            val retry = runCatching { cb(pos.line, pos.col, trig, true).await() }.getOrNull()
            if (token != lspSignatureRequestToken) return@launch
            apply(retry)
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
            val list = runCatching { cb(pos.line, pos.col, null).await() }.getOrNull()
            if (list != null && token == completionRequestToken) {
                completionItems = list.items
                if (completionSelectedIndex >= list.items.size) completionSelectedIndex = 0
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
            completionScope.launch {
                val resolved = runCatching {
                    resolve(token).orTimeout(400, java.util.concurrent.TimeUnit.MILLISECONDS).await()
                }.getOrNull()
                if (resolved != null) resolvedByToken[token] = resolved
                finishApply(resolved?.additionalEdits?.takeIf { it.isNotEmpty() } ?: item.additionalEdits)
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
                CompletionDisplay(label = " ", kindIcon = CompletionKindIcon.GENERIC, detail = null)
            } else {
                val displayLabel = sanitizeLabel(item.label)
                    ?: sanitizeLabel(item.filterText)
                    ?: sanitizeLabel(item.insertText)
                    ?: "<unnamed>"
                val resolved = item.resolveToken?.let { resolvedByToken[it] }
                val effectiveDetail = resolved?.detail?.takeIf { it.isNotBlank() } ?: item.detail
                CompletionDisplay(
                    label = displayLabel,
                    kindIcon = completionKindIcon(item.kind),
                    detail = effectiveDetail?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() },
                    documentation = resolved?.documentation?.takeIf { it.isNotBlank() } ?: item.documentation,
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
                    viewportHeightProvider = { scrollState.viewportSize.toFloat() },
                    scrollOffsetProvider = { scrollState.value.toFloat() },
                )
            }
            fun declarationsIn(text: String): Map<String, Int> {
                if (declarationCacheText != text) {
                    declarationCache = page.shared.syntax.SymbolNames.scan(text).defs
                    declarationCacheText = text
                }
                return declarationCache
            }
            fun triggerDefinitionOrReferences(text: String, offset: Int): Boolean {
                val word = wordRangeAt(text, offset) ?: return false
                val symbolName = text.substring(word.first, word.second)
                if (isInStringOrComment(tokens, text, offset) || !isRenamableIdentifier(symbolName)) return false
                val pos = TextBuffer(text).lineColOf(offset)
                val selfUri = activePath?.toUri()?.toString()
                val refCb = onRequestReferences
                val declaredHere = declarationsIn(text)[symbolName] == word.first
                val defCb = onRequestDefinition
                val nav = onGoToDefinition
                if (declaredHere || defCb == null || nav == null) {
                    if (refCb == null) return false
                    refCb(pos.line, pos.col, symbolName, ReferencesSurface.Popup)
                    return true
                }
                pendingDeclaration = symbolName
                completionScope.launch {
                    val targets = runCatching { defCb(pos.line, pos.col).await() }.getOrNull()
                    pendingDeclaration = null
                    val first = targets?.firstOrNull()
                    val atDeclaration = first != null && refCb != null && selfUri != null &&
                        first.uri == selfUri && definitionTargetContains(first, pos.line, pos.col)
                    if (atDeclaration) refCb!!(pos.line, pos.col, symbolName, ReferencesSurface.Popup)
                    else if (first != null) nav(first)
                    else if (refCb != null) refCb(pos.line, pos.col, symbolName, ReferencesSurface.Popup)
                }
                return true
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
                    triggerDefinitionOrReferences(latestValue.text, origOff)
                },
                ctrlHoverLabel = { origOff ->
                    val text = latestValue.text
                    val word = wordRangeAt(text, origOff)
                    if (word == null) null
                    else {
                        val name = text.substring(word.first, word.second)
                        val declaredHere = declarationsIn(text)[name] == word.first
                        if (declaredHere || onRequestDefinition == null) "Find usages" else "Go to declaration"
                    }
                },
                caretPopup = when {
                    renameRequest != null && onRequestRename != null -> {
                        val active = renameRequest!!
                        val renameCb = onRequestRename
                        {
                            RenamePopup(
                                request = active,
                                inProgress = renameInProgress,
                                error = renameError,
                                onComputeEdit = { name ->
                                    renameCb(active.line, active.character, name)
                                },
                                onApply = { _, edit ->
                                    if (edit.isEmpty) {
                                        renameError = "No changes"
                                    } else {
                                        renameInProgress = true
                                        onApplyRename?.invoke(edit)
                                        renameInProgress = false
                                        renameRequest = null
                                        renameError = null
                                    }
                                },
                                onPreview = { name, edit ->
                                    if (edit.isEmpty) {
                                        renameError = "No changes"
                                    } else {
                                        codeAction.onOpenInPanel(
                                            CodeActionEntry(
                                                title = "Rename to $name",
                                                kind = "refactor.rename",
                                                isPreferred = false,
                                                edit = edit,
                                                command = null,
                                            ),
                                        )
                                        renameRequest = null
                                        renameError = null
                                    }
                                },
                                onDismiss = {
                                    renameRequest = null
                                    renameError = null
                                },
                            )
                        }
                    }
                    codeAction.visible && codeAction.actions.isNotEmpty() -> {
                        {
                            CodeActionPopup(
                                actions = codeAction.actions,
                                selected = codeAction.selected,
                                pending = codeAction.pending,
                                currentUri = codeAction.uri,
                                currentText = codeAction.text,
                                onSelectedChange = codeAction.onSelectedChange,
                                onApply = codeAction.onApply,
                                onOpenInPanel = {
                                    codeAction.actions.getOrNull(codeAction.selected)
                                        ?.let { entry -> codeAction.onOpenInPanel(entry) }
                                },
                            )
                        }
                    }
                    references != null -> {
                        {
                            ReferencesPopup(
                                state = references,
                                linePreviewFor = referenceLinePreview,
                                onJump = onReferenceJump,
                                onOpenInPanel = onReferencesOpenInPanel,
                                onDismiss = onReferencesDismiss,
                            )
                        }
                    }
                    pendingDeclaration != null -> {
                        { CaretBusyPopup("Resolving declaration of ${'$'}pendingDeclaration…") }
                    }
                    else -> null
                },
                caretPopupFocusable = references != null || codeAction.visible || renameRequest != null,
                onCaretPopupDismiss = when {
                    renameRequest != null -> {
                        {
                            renameRequest = null
                            renameError = null
                        }
                    }
                    codeAction.visible -> codeAction.onDismiss
                    references != null -> onReferencesDismiss
                    else -> null
                },
                onResolveCtrlHoverLink = { origOff ->
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
                        !event.isShiftPressed && !event.isPrimaryPressed() && !event.isAltPressed &&
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
                    if (event.isPrimaryPressed() && event.isShiftPressed && event.key == Key.Spacebar) {
                        val caret = value.selection.end.coerceIn(0, value.text.length)
                        triggerSignatureHelp(caret, null, lspSignatureInfo != null)
                        return@CodeEditor true
                    }
                    if (event.isPrimaryPressed() && event.key == Key.Spacebar) {
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
                        (event.key == Key.F2 && !event.isPrimaryPressed() && !event.isShiftPressed) ||
                            (event.key == Key.F6 && event.isShiftPressed && !event.isPrimaryPressed())
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
                            completionScope.launch {
                                val p = runCatching { prepare(pos.line, pos.col).await() }.getOrNull()
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
                    if (event.key == Key.F12 && event.isShiftPressed && !event.isPrimaryPressed()) {
                        val cb = onRequestReferences
                        if (cb != null) {
                            val text = value.text
                            val caret = value.selection.end.coerceIn(0, text.length)
                            if (!isInStringOrComment(tokens, text, caret)) {
                                val pos = TextBuffer(text).lineColOf(caret)
                                val word = wordRangeAt(text, caret)
                                val symbolName = if (word != null) text.substring(word.first, word.second) else ""
                                if (isRenamableIdentifier(symbolName)) {
                                    cb(pos.line, pos.col, symbolName, ReferencesSurface.Panel)
                                }
                            }
                            return@CodeEditor true
                        }
                    }
                    if (event.key == Key.B && event.isPrimaryPressed() && !event.isShiftPressed && !event.isAltPressed) {
                        val caret = value.selection.end.coerceIn(0, value.text.length)
                        if (triggerDefinitionOrReferences(value.text, caret)) {
                            return@CodeEditor true
                        }
                    }
                    if (event.key == Key.F12 && !event.isPrimaryPressed() && !event.isShiftPressed) {
                        val cb = onRequestDefinition
                        val nav = onGoToDefinition
                        if (cb != null && nav != null) {
                            val text = value.text
                            val caret = value.selection.end.coerceIn(0, text.length)
                            val pos = TextBuffer(text).lineColOf(caret)
                            completionScope.launch {
                                val targets = runCatching { cb(pos.line, pos.col).await() }.getOrNull()
                                val first = targets?.firstOrNull()
                                if (first != null) nav(first)
                            }
                            return@CodeEditor true
                        }
                    }
                    if (
                        event.isPrimaryPressed() && !event.isShiftPressed &&
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
                viewportWidthProvider = { horizontalScrollState.viewportSize.toFloat() },
                scrollOffsetProvider = { scrollState.value.toFloat() },
                scopeGuides = page.ui.ScopeGuides(
                    enabled = pageSettings.editor.scopeGuides != ScopeGuideMode.OFF,
                    onlyCurrentBlock = pageSettings.editor.scopeGuides == ScopeGuideMode.CURRENT,
                    rainbow = pageSettings.editor.rainbowBrackets,
                    color = Glass.colors.muted.copy(alpha = 0.22f),
                    activeColor = Glass.colors.primary.copy(alpha = 0.55f),
                    depthColors = palette.bracketDepths,
                ),
                bracketPairs = bracketPairs,
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
                hoverPending = lspHoverPending,
                hoverSyntaxPreset = pageSettings.editor.syntaxPreset,
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
                clipboardShortcuts = page.ui.EditorClipboardShortcuts(
                    cut = page.ui.ShortcutLabels.of(
                        page.ui.Binding(Key.X, primary = true),
                    ),
                    copy = page.ui.ShortcutLabels.of(
                        page.ui.Binding(Key.C, primary = true),
                    ),
                    paste = page.ui.ShortcutLabels.of(
                        page.ui.Binding(Key.V, primary = true),
                    ),
                    selectAll = page.ui.ShortcutLabels.of(
                        page.ui.Binding(Key.A, primary = true),
                    ),
                ),
                contextMenuActions = buildList {
                    if (onRequestDefinition != null || onRequestReferences != null) {
                        add(
                            page.ui.EditorContextAction(
                                label = "Go to Declaration",
                                icon = Icons.AutoMirrored.Outlined.ArrowForward,
                                shortcut = page.ui.ShortcutLabels.of(
                                    page.ui.Binding(Key.B, primary = true),
                                ),
                            ) {
                                val text = latestValue.text
                                triggerDefinitionOrReferences(text, latestValue.selection.end.coerceIn(0, text.length))
                            },
                        )
                    }
                    val refCb = onRequestReferences
                    if (refCb != null) {
                        add(
                            page.ui.EditorContextAction(
                                label = "Find Usages",
                                icon = Icons.Outlined.Search,
                                shortcut = page.ui.ShortcutLabels.of(
                                    page.ui.Binding(Key.F12, shift = true),
                                ),
                            ) {
                                val text = latestValue.text
                                val offset = latestValue.selection.end.coerceIn(0, text.length)
                                val word = wordRangeAt(text, offset)
                                val symbolName = if (word != null) text.substring(word.first, word.second) else ""
                                if (!isInStringOrComment(tokens, text, offset) && isRenamableIdentifier(symbolName)) {
                                    val pos = TextBuffer(text).lineColOf(offset)
                                    refCb(pos.line, pos.col, symbolName, ReferencesSurface.Popup)
                                }
                            },
                        )
                    }
                    val callGraph = onShowCallGraph
                    if (callGraph != null) {
                        add(
                            page.ui.EditorContextAction(
                                label = "Show Call Graph",
                                icon = Icons.Outlined.AccountTree,
                            ) {
                                val text = value.text
                                val offset = value.selection.end.coerceIn(0, text.length)
                                val word = wordRangeAt(text, offset)
                                val symbolName = if (word != null) text.substring(word.first, word.second) else ""
                                if (!isInStringOrComment(tokens, text, offset) && isRenamableIdentifier(symbolName)) {
                                    val pos = TextBuffer(text).lineColOf(offset)
                                    callGraph(pos.line, pos.col)
                                }
                            },
                        )
                    }
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
    }

}

private val CLOSING_PUNCT: Set<Char> = setOf(')', ']', '}', '"', '\'', '`')

private fun severityRank(s: DiagnosticSeverity): Int = when (s) {
    DiagnosticSeverity.ERROR -> 0
    DiagnosticSeverity.WARNING -> 1
    DiagnosticSeverity.INFO -> 2
    DiagnosticSeverity.HINT -> 3
}

internal fun completionKindIcon(kind: LspCompletionItemKind): CompletionKindIcon = when (kind) {
    LspCompletionItemKind.METHOD,
    LspCompletionItemKind.FUNCTION,
    LspCompletionItemKind.CONSTRUCTOR -> CompletionKindIcon.METHOD
    LspCompletionItemKind.PROPERTY,
    LspCompletionItemKind.FIELD -> CompletionKindIcon.PROPERTY
    LspCompletionItemKind.CLASS,
    LspCompletionItemKind.STRUCT -> CompletionKindIcon.CLASS
    LspCompletionItemKind.INTERFACE -> CompletionKindIcon.INTERFACE
    LspCompletionItemKind.ENUM,
    LspCompletionItemKind.ENUM_MEMBER -> CompletionKindIcon.ENUM
    LspCompletionItemKind.KEYWORD,
    LspCompletionItemKind.OPERATOR -> CompletionKindIcon.KEYWORD
    LspCompletionItemKind.VARIABLE,
    LspCompletionItemKind.VALUE -> CompletionKindIcon.VARIABLE
    LspCompletionItemKind.CONSTANT -> CompletionKindIcon.CONSTANT
    LspCompletionItemKind.MODULE -> CompletionKindIcon.MODULE
    LspCompletionItemKind.SNIPPET -> CompletionKindIcon.SNIPPET
    else -> CompletionKindIcon.GENERIC
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

internal fun definitionTargetContains(target: DefinitionTarget, line: Int, character: Int): Boolean {
    if (line < target.startLine || line > target.endLine) return false
    if (line == target.startLine && character < target.startCharacter) return false
    if (line == target.endLine && character > target.endCharacter) return false
    return true
}

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
    private val preset: page.shared.syntax.SyntaxPreset,
    private val todoColors: Map<String, androidx.compose.ui.graphics.Color>,
    private val commentColorOverrides: Map<IntRange, androidx.compose.ui.graphics.Color>,
    private val matches: List<IntRange>,
    private val activeIndex: Int,
    private val matchBg: androidx.compose.ui.graphics.Color,
    private val activeBg: androidx.compose.ui.graphics.Color,
    private val bracketMatch: Pair<Int, Int>?,
    private val bracketBg: androidx.compose.ui.graphics.Color,
    private val occurrences: List<IntRange> = emptyList(),
    private val occurrenceBg: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    private val rainbowPairs: List<page.shared.syntax.BracketPair> = emptyList(),
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
            val span = page.shared.syntax.SyntaxStyles.spanFor(token.kind, palette, preset)
                ?.copy(color = color)
                ?: SpanStyle(color = color)
            builder.addStyle(span, start, end)
        }
        matches.forEachIndexed { index, range ->
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(start, text.length)
            if (start == end) return@forEachIndexed
            val bg = if (index == activeIndex) activeBg else matchBg
            builder.addStyle(SpanStyle(background = bg), start, end)
        }
        for (pair in rainbowPairs) {
            val color = palette.bracketAt(pair.depth)
            for (off in listOf(pair.open, pair.close)) {
                val start = off.coerceIn(0, text.length)
                val end = (off + 1).coerceIn(start, text.length)
                if (start == end) continue
                builder.addStyle(SpanStyle(color = color), start, end)
            }
        }
        if (occurrenceBg != androidx.compose.ui.graphics.Color.Unspecified) {
            for (range in occurrences) {
                val start = range.first.coerceIn(0, text.length)
                val end = (range.last + 1).coerceIn(start, text.length)
                if (start == end) continue
                builder.addStyle(SpanStyle(background = occurrenceBg), start, end)
            }
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

    private fun colorFor(kind: TokenKind, palette: SyntaxPalette) =
        page.shared.syntax.SyntaxStyles.colorFor(kind, palette, preset)
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
