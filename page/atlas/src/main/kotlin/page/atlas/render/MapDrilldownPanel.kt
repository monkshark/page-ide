package page.atlas.render

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath

@Composable
internal fun MapDrilldownPanel(
    drill: MapDrilldown,
    showCounterparts: Boolean,
    onOpen: (FilePath) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(190.dp)
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
    ) {
        DrillSection("Used by", drill.usedBy, MaterialTheme.colorScheme.tertiary, showCounterparts, onOpen)
        DrillSection("Uses", drill.uses, MaterialTheme.colorScheme.primary, showCounterparts, onOpen)
    }
}

@Composable
private fun DrillSection(
    title: String,
    entries: List<DrillEntry>,
    accent: Color,
    showCounterparts: Boolean,
    onOpen: (FilePath) -> Unit,
) {
    if (entries.isEmpty()) return
    Text(
        text = "$title (${entries.size})",
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
    )
    for (entry in entries) {
        val path = entry.path
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (path != null) Modifier.clickable { onOpen(path) } else Modifier)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(entry.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
            if (showCounterparts && entry.counterparts.isNotEmpty()) {
                Text(
                    text = entry.counterparts.joinToString(", "),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
