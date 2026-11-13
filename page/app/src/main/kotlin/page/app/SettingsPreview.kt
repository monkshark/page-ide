package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.shared.syntax.BracketScan
import page.shared.syntax.KotlinLexer
import page.shared.syntax.SyntaxPalette
import page.shared.syntax.UnusedSymbols
import page.shared.syntax.colorizeCode
import page.ui.Glass

private const val CARET_WORD = "ext"

private val PREVIEW_LINES = listOf(
    0 to "import java.nio.file.Files",
    0 to "",
    0 to "object FileIcons {",
    1 to "fun resourceFor(path: Path): String? {",
    2 to "val ext = path.name.substringAfterLast('.')",
    2 to "return EXT[ext]?.let { \"fileicons/${'$'}it.svg\" }",
    1 to "}",
    0 to "}",
)

private const val CARET_LINE = 4

private const val PREVIEW_LINE_SPACING = 1.6f
private val PREVIEW_CHROME = 34.dp
private val PREVIEW_MAX_HEIGHT = 260.dp

internal fun previewHeightFor(editor: EditorOptions): Dp {
    val body = (PREVIEW_LINES.size * editor.fontSize * PREVIEW_LINE_SPACING).dp
    return (PREVIEW_CHROME + body).coerceAtMost(PREVIEW_MAX_HEIGHT)
}

internal data class PreviewLine(val depth: Int, val start: Int, val end: Int)

internal fun previewSource(tabSize: Int): Pair<String, List<PreviewLine>> {
    val unit = " ".repeat(tabSize.coerceIn(1, 16))
    val sb = StringBuilder()
    val lines = ArrayList<PreviewLine>(PREVIEW_LINES.size)
    for ((index, entry) in PREVIEW_LINES.withIndex()) {
        val (depth, body) = entry
        if (index > 0) sb.append('\n')
        val lineStart = sb.length
        if (body.isNotEmpty()) repeat(depth) { sb.append(unit) }
        sb.append(body)
        lines += PreviewLine(depth = if (body.isEmpty()) 0 else depth, start = lineStart, end = sb.length)
    }
    return sb.toString() to lines
}

@Composable
internal fun SettingsPreview(editor: EditorOptions, modifier: Modifier = Modifier) {
    val colors = Glass.colors
    val palette = colors.syntax
    val source = remember(editor.tabSize) { previewSource(editor.tabSize) }
    val code = source.first
    val lines = source.second
    val styled = remember(editor, palette, code) {
        buildPreview(code, editor, palette, colors.faint, colors.primarySoft)
    }
    val fontSize = editor.fontSize.sp
    val lineHeight = (editor.fontSize * PREVIEW_LINE_SPACING).sp
    val scroll = rememberScrollState()
    val indentUnit = remember(editor.tabSize) { " ".repeat(editor.tabSize.coerceIn(1, 16)) }

    Column(modifier = modifier.background(colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Preview",
                color = colors.muted,
                fontSize = Glass.type.ui,
            )
            Box(Modifier.weight(1f))
            Text(
                text = "${editor.syntaxPreset.label} · ${editor.fontSize}sp · tab ${editor.tabSize}",
                color = colors.faint,
                fontSize = Glass.type.label,
                fontFamily = FontFamily.Monospace,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.weight(1f).verticalScroll(scroll).padding(vertical = 5.dp)) {
                for ((index, line) in lines.withIndex()) {
                    val onCaretLine = editor.highlightCurrentLine && index == CARET_LINE
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (onCaretLine) colors.surfaceL2 else Color.Transparent),
                    ) {
                        if (editor.showLineNumbers) {
                            Text(
                                text = "${index + 1}",
                                color = colors.faint,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(38.dp).padding(end = 10.dp),
                            )
                        } else {
                            Box(Modifier.width(12.dp))
                        }
                        repeat(line.depth) { level ->
                            IndentCell(
                                unit = indentUnit,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                guide = guideColor(editor, level),
                            )
                        }
                        val bodyStart = (line.start + indentUnit.length * line.depth).coerceAtMost(line.end)
                        Text(
                            text = styled.subSequence(bodyStart, line.end),
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            fontFamily = FontFamily.Monospace,
                            color = colors.text,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (editor.showMinimap) {
                Box(modifier = Modifier.width(26.dp).fillMaxHeight().background(colors.surfaceL1)) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        for (line in lines) {
                            val width = ((line.end - line.start).coerceAtMost(30)) * 0.6f
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 2.dp)
                                    .width(width.dp)
                                    .height(1.dp)
                                    .background(colors.faint),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IndentCell(
    unit: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    guide: Color?,
) {
    Text(
        text = unit,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = FontFamily.Monospace,
        color = Color.Transparent,
        maxLines = 1,
        modifier = Modifier.drawBehind {
            if (guide != null) {
                drawRect(color = guide, size = Size(1.dp.toPx(), size.height))
            }
        },
    )
}

@Composable
private fun guideColor(editor: EditorOptions, level: Int): Color? {
    if (editor.scopeGuides == ScopeGuideMode.OFF) return null
    val colors = Glass.colors
    val depths = colors.syntax.bracketDepths
    return when {
        editor.rainbowBrackets && depths.isNotEmpty() ->
            depths[level.mod(depths.size)].copy(alpha = 0.38f)
        else -> colors.outline
    }
}

private fun buildPreview(
    code: String,
    editor: EditorOptions,
    palette: SyntaxPalette,
    faint: Color,
    occurrenceTint: Color,
): AnnotatedString {
    val base = colorizeCode(code, KotlinLexer, palette, editor.syntaxPreset)
    val tokens = KotlinLexer.tokenize(code)
    val pairs = BracketScan.pairs(code, tokens)
    return buildAnnotatedString {
        append(base)
        if (editor.rainbowBrackets && palette.bracketDepths.isNotEmpty()) {
            for (pair in pairs) {
                val tint = palette.bracketDepths[pair.depth.mod(palette.bracketDepths.size)]
                addStyle(SpanStyle(color = tint), pair.open, pair.open + 1)
                addStyle(SpanStyle(color = tint), pair.close, pair.close + 1)
            }
        }
        if (editor.dimUnusedSymbols) {
            for (range in UnusedSymbols.find(code, tokens, pairs)) {
                addStyle(SpanStyle(color = faint), range.first, (range.last + 1).coerceAtMost(code.length))
            }
        }
        if (editor.highlightIdentifierUnderCaret) {
            for (range in wordOccurrences(code, CARET_WORD)) {
                addStyle(SpanStyle(background = occurrenceTint), range.first, range.last + 1)
            }
        }
    }
}

private fun wordOccurrences(code: String, word: String): List<IntRange> {
    val out = ArrayList<IntRange>()
    var from = 0
    while (true) {
        val at = code.indexOf(word, from)
        if (at < 0) break
        val before = code.getOrNull(at - 1)
        val after = code.getOrNull(at + word.length)
        val bounded = (before == null || !before.isLetterOrDigit() && before != '_') &&
            (after == null || !after.isLetterOrDigit() && after != '_')
        if (bounded) out += at until (at + word.length)
        from = at + word.length
    }
    return out
}
