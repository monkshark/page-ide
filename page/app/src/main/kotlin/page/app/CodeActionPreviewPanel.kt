package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.lsp.CodeActionEntry
import page.lsp.CodeActionPreview

@Composable
internal fun CodeActionPreviewPanel(
    actions: List<CodeActionEntry>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    currentUri: String?,
    currentText: String?,
    onApply: (CodeActionEntry) -> Unit,
    onDismiss: () -> Unit,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val current = actions.getOrNull(selected)
    val previews = current?.let {
        CodeActionPreview.build(it.edit, currentUri, currentText, contextLines = 2)
    } ?: emptyList()
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        if (selected in actions.indices) listState.animateScrollToItem(selected)
    }
    Row(modifier = modifier.width(width).fillMaxHeight()) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PanelHeader(count = actions.size)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(((actions.size.coerceAtMost(6)) * 28 + 4).dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(actions) { idx, action ->
                            ActionRow(
                                action = action,
                                isSelected = idx == selected,
                                onClick = { onSelectedChange(idx) },
                                onDoubleClick = {
                                    onSelectedChange(idx)
                                    if (action.isExecutable) onApply(action) else onDismiss()
                                },
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        current == null -> PanelMessage("선택된 액션 없음")
                        current.command != null && !current.hasEdit ->
                            PanelMessage("서버 실행 액션 (변경 미리보기 없음)\n• command: ${current.command}")
                        previews.isEmpty() -> PanelMessage("변경 내역 없음")
                        else -> PreviewContent(previews)
                    }
                }
                FooterBar(
                    canApply = current?.isExecutable == true,
                    onCancel = onDismiss,
                    onApply = { current?.let { if (it.isExecutable) onApply(it) else onDismiss() } },
                )
            }
        }
    }
}

@Composable
private fun PanelHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Code Actions",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${count}건",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "↑↓ · Enter · Esc",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ActionRow(
    action: CodeActionEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
) {
    val rowBg = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else Color.Transparent
    val titleColor = if (action.isExecutable)
        MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (action.isPreferred) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(10.dp))
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = action.title,
            color = titleColor,
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val kindLabel = action.kind?.takeIf { it.isNotBlank() }
        if (kindLabel != null) {
            Text(
                text = kindLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun PanelMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PreviewContent(previews: List<CodeActionPreview.FilePreview>) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = 4.dp),
    ) {
        for (file in previews) {
            FileHunkBlock(file)
        }
    }
}

@Composable
private fun FileHunkBlock(file: CodeActionPreview.FilePreview) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = file.basename,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${file.editCount} edit(s)",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 10.sp,
            )
        }
        if (file.lines.isEmpty()) {
            Text(
                text = if (file.isCurrent) "(변경 없음)" else "(다른 파일 — 미리보기 생략)",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )
        } else {
            for (line in file.lines) {
                HunkLineRow(line)
            }
        }
    }
}

@Composable
private fun HunkLineRow(line: CodeActionPreview.HunkLine) {
    val style = styleFor(line.kind)
    val gutterText = when (line.kind) {
        CodeActionPreview.LineKind.ADDED -> "new"
        CodeActionPreview.LineKind.OMITTED -> "…"
        else -> line.oldLineNo?.toString() ?: ""
    }
    val gutterColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
        alpha = if (line.kind == CodeActionPreview.LineKind.ADDED) 0.75f else 0.45f,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.background)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = gutterText,
            color = gutterColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(34.dp).padding(end = 6.dp),
        )
        Text(
            text = style.prefix,
            color = style.prefixColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(14.dp),
        )
        Text(
            text = if (line.text.isEmpty()) " " else line.text,
            color = style.textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FooterBar(
    canApply: Boolean,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        FooterButton(
            label = "Cancel",
            isPrimary = false,
            enabled = true,
            onClick = onCancel,
        )
        Spacer(Modifier.width(8.dp))
        FooterButton(
            label = "Refactor",
            isPrimary = true,
            enabled = canApply,
            onClick = onApply,
        )
    }
}

@Composable
private fun FooterButton(
    label: String,
    isPrimary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        isPrimary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        isPrimary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = if (!isPrimary)
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    else Color.Transparent
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

private data class HunkStyle(
    val background: Color,
    val prefix: String,
    val prefixColor: Color,
    val textColor: Color,
)

@Composable
private fun styleFor(kind: CodeActionPreview.LineKind): HunkStyle = when (kind) {
    CodeActionPreview.LineKind.ADDED -> HunkStyle(
        background = Color(0xFF43A047).copy(alpha = 0.18f),
        prefix = "+",
        prefixColor = Color(0xFF66BB6A),
        textColor = MaterialTheme.colorScheme.onSurface,
    )
    CodeActionPreview.LineKind.REMOVED -> HunkStyle(
        background = Color(0xFFE53935).copy(alpha = 0.16f),
        prefix = "-",
        prefixColor = Color(0xFFEF5350),
        textColor = MaterialTheme.colorScheme.onSurface,
    )
    CodeActionPreview.LineKind.OMITTED -> HunkStyle(
        background = Color.Transparent,
        prefix = "·",
        prefixColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
    )
    CodeActionPreview.LineKind.CONTEXT -> HunkStyle(
        background = Color.Transparent,
        prefix = " ",
        prefixColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}
