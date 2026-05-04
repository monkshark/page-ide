package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.editor.SearchState
import page.editor.SyntaxLexer
import page.editor.TextBuffer
import page.editor.Token
import page.editor.TokenKind
import page.ui.GlassDarkSyntax
import page.ui.SyntaxPalette

@Composable
fun EditorPanel(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    search: SearchState?,
    onQueryChange: (String) -> Unit,
    onToggleCase: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchPrev: () -> Unit,
    onSearchClose: () -> Unit,
    lexer: SyntaxLexer?,
    modifier: Modifier = Modifier,
) {
    val buffer = remember(value.text) { TextBuffer(value.text) }
    val caretOffset = value.selection.start.coerceIn(0, buffer.length)
    val caret = buffer.lineColOf(caretOffset)

    val matchBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val palette = GlassDarkSyntax

    val tokens = remember(value.text, lexer) {
        lexer?.tokenize(value.text).orEmpty()
    }

    val visualTransformation = remember(search, tokens, matchBg, activeBg, palette) {
        val matches = search?.matches.orEmpty()
        val activeIndex = search?.activeMatchIndex ?: -1
        if (tokens.isEmpty() && matches.isEmpty()) {
            VisualTransformation.None
        } else {
            CombinedHighlightTransformation(
                tokens = tokens,
                palette = palette,
                matches = matches,
                activeIndex = activeIndex,
                matchBg = matchBg,
                activeBg = activeBg,
            )
        }
    }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        if (search != null) {
            SearchBar(
                state = search,
                onQueryChange = onQueryChange,
                onToggleCase = onToggleCase,
                onNext = onSearchNext,
                onPrev = onSearchPrev,
                onClose = onSearchClose,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
        )
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
