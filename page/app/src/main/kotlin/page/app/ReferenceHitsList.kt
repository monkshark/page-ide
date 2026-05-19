package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path

@Composable
internal fun ReferenceHitsList(
    hits: List<ReferenceHit>,
    rootDir: Path?,
    onJumpToHit: (ReferenceHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
    ) {
        items(hits, key = { hit -> "${hit.file}:${hit.line}:${hit.column}" }) { hit ->
            HitRow(hit, rootDir, onJumpToHit)
        }
    }
}

@Composable
private fun HitRow(hit: ReferenceHit, rootDir: Path?, onClick: (ReferenceHit) -> Unit) {
    val display = relativeOrName(hit.file, rootDir)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(hit) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "$display:${hit.line + 1}  ·  ${hit.symbol}",
            color = MaterialTheme.colorScheme.onSurface,
            style = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
        )
        if (hit.preview.isNotEmpty()) {
            Text(
                text = hit.preview,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = LocalTextStyle.current.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                maxLines = 1,
            )
        }
    }
}

private fun relativeOrName(file: Path, rootDir: Path?): String {
    if (rootDir != null) {
        val rel = runCatching { rootDir.relativize(file).toString() }.getOrNull()
        if (!rel.isNullOrEmpty() && !rel.startsWith("..")) {
            return rel.replace('\\', '/')
        }
    }
    return file.fileName?.toString() ?: file.toString()
}
