package page.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.atlas.graph.MapPart
import page.atlas.graph.ProjectMap
import page.atlas.graph.ProjectMapBuilder
import page.ui.Glass
import java.nio.file.Path
import java.util.prefs.Preferences

internal const val SUMMARY_ROWS = 6

internal fun summaryParts(map: ProjectMap): List<MapPart> =
    map.parts
        .filter { it.id != ProjectMapBuilder.OTHER_ID }
        .sortedWith(compareByDescending<MapPart> { it.layer }.thenByDescending { it.fileCount })
        .take(SUMMARY_ROWS)

internal fun worthSummarising(map: ProjectMap?): Boolean =
    map != null && map.parts.count { it.id != ProjectMapBuilder.OTHER_ID } >= 2

interface SummarySeen {
    fun seen(root: Path): Boolean
    fun markSeen(root: Path)

    companion object {
        fun preferences(): SummarySeen = object : SummarySeen {
            private val node = runCatching { Preferences.userRoot().node("page/project-summary") }.getOrNull()
            private fun key(root: Path) = Integer.toHexString(root.toAbsolutePath().normalize().toString().lowercase().hashCode())
            override fun seen(root: Path): Boolean = node?.getBoolean(key(root), false) ?: true
            override fun markSeen(root: Path) { runCatching { node?.putBoolean(key(root), true) } }
        }
    }
}

@Composable
fun ProjectSummaryCard(map: ProjectMap, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Glass.colors
    val shape = RoundedCornerShape(10.dp)
    val shown = summaryParts(map)
    val rest = map.parts.count { it.id != ProjectMapBuilder.OTHER_ID } - shown.size
    Column(
        modifier.width(340.dp).shadow(14.dp, shape).clip(shape).background(colors.surfaceOverlay).border(1.dp, colors.outline, shape)
            .testTag("project-summary").padding(horizontal = 15.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${map.title}, in ${map.parts.size} parts", color = colors.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("✕", color = colors.faint, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onDismiss).testTag("project-summary-close").padding(horizontal = 4.dp, vertical = 2.dp))
        }
        Text("Read from the build files. Top relies on bottom.", color = colors.muted, fontSize = 12.5.sp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            shown.forEach { p ->
                Row {
                    Text(p.name, color = colors.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
                    Text(p.description, color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (rest > 0) Text("+$rest smaller parts", color = colors.faint, fontSize = 11.5.sp)
    }
}
