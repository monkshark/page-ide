package page.app

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import page.editor.AutoClose
import page.editor.BracketMatch
import page.editor.Indent
import page.editor.LineMove
import page.editor.SearchState
import page.editor.SyntaxLexer
import page.editor.TextBuffer
import page.editor.TextEdit
import page.editor.Token
import page.editor.TokenKind
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
    modifier: Modifier = Modifier,
) {
    val buffer = remember(value.text) { TextBuffer(value.text) }
    val caretOffset = value.selection.start.coerceIn(0, buffer.length)
    val caret = buffer.lineColOf(caretOffset)

    val matchBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val currentLineBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
    val bracketBg = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
    val palette = GlassDarkSyntax

    val tokens = remember(value.text, lexer) {
        lexer?.tokenize(value.text).orEmpty()
    }

    val bracketMatch = remember(value.text, value.selection.start, value.selection.end) {
        if (value.selection.start != value.selection.end) null
        else BracketMatch.find(value.text, value.selection.start)
    }

    val visualTransformation = remember(search, tokens, bracketMatch, matchBg, activeBg, bracketBg, palette) {
        val matches = search?.matches.orEmpty()
        val activeIndex = search?.activeMatchIndex ?: -1
        if (tokens.isEmpty() && matches.isEmpty() && bracketMatch == null) {
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
    var savedScrollOnPress by remember { mutableStateOf(0) }
    var focusGainVersion by remember { mutableStateOf(0) }

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
                lineCount = buffer.lineCount,
                currentLine = caret.line,
                textStyle = textStyle,
            )
            BasicTextField(
                value = value,
                onValueChange = { newValue ->
                    val oldEdit = TextEdit(value.text, value.selection.start, value.selection.end)
                    val newEdit = TextEdit(newValue.text, newValue.selection.start, newValue.selection.end)
                    val afterAutoClose = AutoClose.apply(oldEdit, newEdit)
                    val afterUnindent = Indent.maybeUnindentClosingBrace(oldEdit, afterAutoClose)
                    val result = Indent.maybeApplyEnter(oldEdit, afterUnindent)
                    val adjusted = if (
                        result.text == newValue.text &&
                        result.selectionStart == newValue.selection.start &&
                        result.selectionEnd == newValue.selection.end
                    ) {
                        newValue
                    } else {
                        newValue.copy(
                            text = result.text,
                            selection = TextRange(result.selectionStart, result.selectionEnd),
                        )
                    }
                    onValueChange(adjusted)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            focusGainVersion++
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (event.isAltPressed && (event.key == Key.DirectionUp || event.key == Key.DirectionDown)) {
                            val edit = TextEdit(
                                value.text,
                                value.selection.start,
                                value.selection.end,
                            )
                            val r = when {
                                event.key == Key.DirectionUp && event.isShiftPressed -> LineMove.duplicateUp(edit)
                                event.key == Key.DirectionDown && event.isShiftPressed -> LineMove.duplicateDown(edit)
                                event.key == Key.DirectionUp -> LineMove.moveUp(edit)
                                else -> LineMove.moveDown(edit)
                            }
                            return@onPreviewKeyEvent if (r != null) {
                                onValueChange(
                                    value.copy(
                                        text = r.text,
                                        selection = TextRange(r.selectionStart, r.selectionEnd),
                                    )
                                )
                                true
                            } else true
                        }
                        when (event.key) {
                            Key.Tab -> {
                                val edit = TextEdit(
                                    value.text,
                                    value.selection.start,
                                    value.selection.end,
                                )
                                val r = if (event.isShiftPressed) Indent.handleShiftTab(edit)
                                else Indent.handleTab(edit)
                                onValueChange(
                                    value.copy(
                                        text = r.text,
                                        selection = TextRange(r.selectionStart, r.selectionEnd),
                                    )
                                )
                                true
                            }
                            Key.Enter, Key.NumPadEnter -> {
                                val edit = TextEdit(
                                    value.text,
                                    value.selection.start,
                                    value.selection.end,
                                )
                                val r = Indent.handleEnter(edit)
                                onValueChange(
                                    value.copy(
                                        text = r.text,
                                        selection = TextRange(r.selectionStart, r.selectionEnd),
                                    )
                                )
                                true
                            }
                            Key.Backspace -> {
                                val edit = TextEdit(
                                    value.text,
                                    value.selection.start,
                                    value.selection.end,
                                )
                                val r = Indent.handleBackspace(edit)
                                if (r != null) {
                                    onValueChange(
                                        value.copy(
                                            text = r.text,
                                            selection = TextRange(r.caret),
                                        )
                                    )
                                    true
                                } else false
                            }
                            else -> false
                        }
                    },
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = visualTransformation,
            )
        }
        EditorStatusBar(
            line = caret.line,
            col = caret.col,
            lineCount = buffer.lineCount,
            charCount = buffer.length,
        )
    }
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
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
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
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
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

@Composable
private fun EditorStatusBar(line: Int, col: Int, lineCount: Int, charCount: Int) {
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
        }
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
