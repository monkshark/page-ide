package page.atlas.render

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import page.atlas.graph.CycleGroup
import page.atlas.graph.GraphInsights
import page.atlas.graph.GraphSlice
import page.atlas.graph.HubFile
import page.ui.EditorFontFamily

private const val HUB_MIN_DEPENDENTS = 8

@Composable
fun DependencyInsightPanel(
    slice: GraphSlice,
    focusId: String?,
    onOpen: (FilePath) -> Unit,
    onRefocus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val impact = remember(slice, focusId) {
        focusId?.let { GraphInsights.impact(slice, it) } ?: emptyList()
    }
    val cycleGroups = remember(slice, focusId) {
        val groups = GraphInsights.cycleGroups(slice)
        if (focusId == null) groups
        else groups.sortedByDescending { group -> group.members.any { it.id == focusId } }
    }
    val hubs = remember(slice) {
        GraphInsights.hubs(slice).filter { it.dependents >= HUB_MIN_DEPENDENTS }
    }
    val direct = impact.count { it.depth == 1 }
    val indirect = impact.size - direct

    Box(modifier.fillMaxSize().background(AtlasInk.canvas)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp)) {
            val wide = maxWidth >= 560.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    ImpactColumn(impact.size, direct, indirect, focusId != null, Modifier.weight(1f))
                    ProblemsColumn(cycleGroups, hubs, onRefocus, onOpen, Modifier.weight(1f))
                }
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    ImpactColumn(impact.size, direct, indirect, focusId != null, Modifier.fillMaxWidth())
                    ProblemsColumn(cycleGroups, hubs, onRefocus, onOpen, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ImpactColumn(total: Int, direct: Int, indirect: Int, hasFocus: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionLabel("IMPACT OF A CHANGE")
        if (!hasFocus) {
            Text(
                "Open a file to measure its blast radius",
                style = mono(13.sp, AtlasInk.dim),
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(total.toString(), style = mono(48.sp, AtlasInk.bright, FontWeight.SemiBold))
                Text(
                    "files would change",
                    style = mono(13.sp, AtlasInk.dim),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBox(direct, "DIRECT", AtlasInk.bright, Modifier.weight(1f))
                StatBox(indirect, "INDIRECT", AtlasInk.label, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatBox(value: Int, label: String, valueColor: Color, modifier: Modifier) {
    Column(
        modifier
            .background(AtlasInk.boxFill, RoundedCornerShape(11.dp))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(11.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value.toString(), style = mono(19.sp, valueColor, FontWeight.SemiBold))
        Text(label, style = mono(9.sp, AtlasInk.sub), letterSpacing = 1f)
    }
}

@Composable
private fun ProblemsColumn(
    cycleGroups: List<CycleGroup>,
    hubs: List<HubFile>,
    onRefocus: (String) -> Unit,
    onOpen: (FilePath) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("PROBLEMS")
            Pill(problemsSummary(cycleGroups.size, hubs.size))
        }
        if (cycleGroups.isEmpty() && hubs.isEmpty()) {
            Text("No structural problems found", style = mono(12.sp, AtlasInk.dim))
        } else {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (group in cycleGroups) {
                    CycleCard(group, onRefocus)
                }
                for (hub in hubs) {
                    HubCard(hub, onRefocus, onOpen)
                }
            }
        }
    }
}

@Composable
private fun CycleCard(group: CycleGroup, onRefocus: (String) -> Unit) {
    val anchor = group.members.first()
    Column(
        Modifier
            .fillMaxWidth()
            .background(AtlasInk.cycleCardFill, RoundedCornerShape(13.dp))
            .border(1.dp, AtlasInk.cycle.copy(alpha = 0.22f), RoundedCornerShape(13.dp))
            .pointerInput(anchor.id) { detectTapGestures { onRefocus(anchor.id) } }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(8.dp).background(AtlasInk.cycle, CircleShape))
            Text("Dependency cycle", style = mono(11.5.sp, Color(0xFFF0D9D8), FontWeight.Medium), modifier = Modifier.weight(1f))
            Badge("HIGH", AtlasInk.cycle)
        }
        Text(
            cycleChain(group),
            style = mono(9.5.sp, AtlasInk.label),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp),
        )
    }
}

@Composable
private fun HubCard(hub: HubFile, onRefocus: (String) -> Unit, onOpen: (FilePath) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AtlasInk.hubCardFill, RoundedCornerShape(13.dp))
            .border(1.dp, AtlasInk.hub.copy(alpha = 0.18f), RoundedCornerShape(13.dp))
            .pointerInput(hub.node.id) {
                detectTapGestures(
                    onTap = { onRefocus(hub.node.id) },
                    onDoubleTap = { hub.node.path?.let(onOpen) },
                )
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).background(AtlasInk.hub, CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ellipsizeMiddle(hub.node.label, 40), style = mono(11.5.sp, AtlasInk.label), maxLines = 1)
            Text("hub · imported by ${hub.dependents}", style = mono(9.sp, AtlasInk.sub), maxLines = 1)
        }
        Text("hub", style = mono(9.5.sp, AtlasInk.hub))
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = mono(8.5.sp, color), letterSpacing = 1f)
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        Modifier
            .background(Color(0x0AFFFFFF), RoundedCornerShape(5.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, style = mono(10.sp, AtlasInk.dim))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = mono(11.sp, AtlasInk.sub), letterSpacing = 2.5f)
}

private fun cycleChain(group: CycleGroup): String {
    val labels = group.members.map { it.label.removeSuffix(".kt") }
    return (labels + labels.first()).joinToString(" → ")
}

private fun problemsSummary(cycles: Int, hubs: Int): String {
    val c = "$cycles cycle${if (cycles == 1) "" else "s"}"
    val h = "$hubs hub${if (hubs == 1) "" else "s"}"
    return "$c · $h"
}

private fun mono(
    size: androidx.compose.ui.unit.TextUnit,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(fontSize = size, color = color, fontWeight = weight, fontFamily = EditorFontFamily)

@Composable
private fun Text(text: String, style: TextStyle, letterSpacing: Float, modifier: Modifier = Modifier) {
    Text(text = text, style = style.copy(letterSpacing = letterSpacing.sp), modifier = modifier)
}

private fun ellipsizeMiddle(text: String, max: Int): String {
    if (text.length <= max) return text
    val keep = (max - 1).coerceAtLeast(2)
    val head = (keep + 1) / 2
    val tail = keep / 2
    return text.take(head) + "…" + text.takeLast(tail)
}
