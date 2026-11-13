package page.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as GPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import page.atlas.graph.FileFacts
import page.atlas.graph.MapPart
import page.atlas.graph.PartTie
import page.atlas.graph.ProjectMap
import page.atlas.graph.ProjectMapBuilder
import page.ui.EditorFontFamily
import page.ui.Glass
import java.nio.file.Path

internal fun partTiers(map: ProjectMap): List<List<MapPart>> =
    map.parts.filter { it.id != ProjectMapBuilder.OTHER_ID }
        .groupBy { it.layer }
        .toSortedMap(compareByDescending { it })
        .values.map { tier -> tier.sortedByDescending { it.fileCount } }

internal fun partRelation(tie: PartTie?, fileName: String?): String? {
    if (tie == null || fileName == null) return null
    val name = fileName.substringBeforeLast('.')
    val users = when {
        tie.users == 1 -> "1 file uses $name"
        tie.users > 1 -> "${tie.users} files use $name"
        else -> null
    }
    val needed = if (tie.needed) "Used by $name" else null
    return listOfNotNull(users, needed).joinToString(" · ").ifEmpty { null }
}

internal fun factCaption(facts: FileFacts?): String? = when {
    facts == null -> null
    facts.inLoop -> "loop"
    else -> facts.usedBy.toString()
}

internal fun partFolder(part: MapPart, root: Path?): String? {
    val dir = part.dir ?: return null
    val rel = if (root != null && dir.startsWith(root)) root.relativize(dir) else dir.fileName ?: return null
    return rel.toString().replace('\\', '/').ifEmpty { null }
}

private enum class SpineTie { HERE, USER, NEEDED, NONE, OTHER }

@Composable
internal fun PartsPanel(
    map: ProjectMap?,
    currentFile: Path?,
    ties: Map<String, PartTie>,
    loadFacts: (List<Path>) -> Map<Path, FileFacts>,
    onOpenFile: (Path) -> Unit,
    root: Path? = null,
    onBrowseFiles: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = Glass.colors
    if (map == null || map.isEmpty()) {
        Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (map == null) "Reading build files" else "No parts found", color = colors.muted, fontSize = 12.sp)
            if (map != null) {
                Text("Browse in Files", color = colors.primary, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onBrowseFiles))
            }
        }
        return
    }
    val current = currentFile?.toAbsolutePath()?.normalize()
    val currentPart = current?.let { map.partOf(it) }
    val tiers = remember(map) { partTiers(map) }
    val other = map.part(ProjectMapBuilder.OTHER_ID)
    val maxCount = remember(map) { map.parts.maxOfOrNull { it.fileCount }?.coerceAtLeast(1) ?: 1 }
    var selectedId by remember(currentPart?.id, map) { mutableStateOf(currentPart?.id ?: tiers.firstOrNull()?.firstOrNull()?.id) }
    val selected = selectedId?.let { map.part(it) }
    val facts by produceState(emptyMap<Path, FileFacts>(), selected?.id, map) {
        value = emptyMap()
        val part = selected ?: return@produceState
        value = withContext(Dispatchers.IO) { runCatching { loadFacts(part.files) }.getOrDefault(emptyMap()) }
    }
    fun tieOf(part: MapPart): SpineTie = when {
        part.id == currentPart?.id -> SpineTie.HERE
        part.id == ProjectMapBuilder.OTHER_ID -> SpineTie.OTHER
        else -> {
            val tie = ties[part.id]
            when {
                tie == null -> SpineTie.NONE
                tie.users > 0 -> SpineTie.USER
                tie.needed -> SpineTie.NEEDED
                else -> SpineTie.NONE
            }
        }
    }
    val rowTops = remember(map) { mutableStateMapOf<String, Float>() }
    val ordered = tiers.flatten() + listOfNotNull(other)
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            Text("Highest layer first", color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${tiers.sumOf { it.size }}", color = colors.faint, fontSize = 11.sp, fontFamily = EditorFontFamily)
        }
        val spineColor = colors.primary
        val baseColor = colors.text.copy(alpha = 0.10f)
        val dotColor = colors.text.copy(alpha = 0.32f)
        val otherDot = colors.text.copy(alpha = 0.18f)
        val ringFill = colors.surfaceL2
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                .testTag("parts-spine")
                .drawBehind {
                    val ys = ordered.mapNotNull { part -> rowTops[part.id]?.let { part to it + 14.dp.toPx() } }
                    if (ys.isEmpty()) return@drawBehind
                    val x = 14.dp.toPx()
                    drawLine(baseColor, Offset(x, ys.first().second), Offset(x, ys.last().second), 1.dp.toPx())
                    val hereY = ys.firstOrNull { tieOf(it.first) == SpineTie.HERE }?.second
                    if (hereY != null) {
                        for ((part, y) in ys) {
                            val tie = tieOf(part)
                            if (tie != SpineTie.USER && tie != SpineTie.NEEDED) continue
                            val from = if (tie == SpineTie.USER) y else hereY
                            val to = if (tie == SpineTie.USER) hereY else y
                            val dir = if (to >= from) 1f else -1f
                            val stroke = 1.5.dp.toPx()
                            drawLine(spineColor, Offset(x, from + dir * 5.dp.toPx()), Offset(x, to - dir * 7.dp.toPx()), stroke, StrokeCap.Round)
                            val tip = to - dir * 5.5.dp.toPx()
                            val arm = 3.5.dp.toPx()
                            val head = GPath().apply {
                                moveTo(x - arm, tip - dir * arm)
                                lineTo(x, tip)
                                lineTo(x + arm, tip - dir * arm)
                            }
                            drawPath(head, spineColor, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    for ((part, y) in ys) {
                        when (tieOf(part)) {
                            SpineTie.HERE -> drawCircle(spineColor, 4.5.dp.toPx(), Offset(x, y))
                            SpineTie.USER, SpineTie.NEEDED -> {
                                drawCircle(ringFill, 3.5.dp.toPx(), Offset(x, y))
                                drawCircle(spineColor, 3.5.dp.toPx(), Offset(x, y), style = Stroke(1.5.dp.toPx()))
                            }
                            SpineTie.NONE -> drawCircle(dotColor, 2.dp.toPx(), Offset(x, y))
                            SpineTie.OTHER -> drawCircle(otherDot, 2.dp.toPx(), Offset(x, y))
                        }
                    }
                },
        ) {
            val groups = tiers.mapIndexed { i, tier -> if (i == tiers.lastIndex) tier + listOfNotNull(other) else tier }
                .let { if (tiers.isEmpty() && other != null) listOf(listOf(other)) else it }
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    Box(Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp).fillMaxWidth().height(1.dp).background(colors.separator))
                }
                for (part in group) {
                    PartRow(
                        part = part,
                        relation = if (part.id == currentPart?.id) null else partRelation(ties[part.id], current?.fileName?.toString()),
                        here = part.id == currentPart?.id,
                        selected = part.id == selectedId,
                        weight = part.fileCount.toFloat() / maxCount,
                        onClick = { selectedId = part.id },
                        modifier = Modifier.onGloballyPositioned { rowTops[part.id] = it.positionInParent().y },
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
        if (selected != null) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selected.id == ProjectMapBuilder.OTHER_ID) "Other files" else selected.name,
                    color = colors.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                partFolder(selected, root)?.let { Text(it, color = colors.faint, fontSize = 11.sp, fontFamily = EditorFontFamily, maxLines = 1) }
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) {
                Text("File", color = colors.faint, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("Used by", color = colors.faint, fontSize = 11.sp)
            }
            val files = remember(selected) { selected.files.sortedBy { it.fileName.toString().lowercase() } }
            val maxUsers = facts.values.maxOfOrNull { it.usedBy }?.coerceAtLeast(1) ?: 1
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp).testTag("part-files")) {
                items(files, key = { it.toString() }) { file ->
                    val fact = facts[file]
                    PartFileRow(file, fact, fact?.let { it.usedBy.toFloat() / maxUsers }, file == current, onOpenFile)
                }
            }
        }
    }
}

@Composable
private fun PartRow(
    part: MapPart,
    relation: String?,
    here: Boolean,
    selected: Boolean,
    weight: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Glass.colors
    val other = part.id == ProjectMapBuilder.OTHER_ID
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (here || selected) colors.surfaceRaised else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
            .testTag("part-${part.id}"),
    ) {
        Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (other) "Other files" else part.name,
                color = if (other) colors.muted else colors.text,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(part.fileCount.toString(), color = colors.faint, fontSize = 11.sp, lineHeight = 20.sp, fontFamily = EditorFontFamily)
        }
        if (relation != null) {
            Text(relation, color = colors.text, fontSize = 12.sp, lineHeight = 16.sp)
        }
        WeightBar(weight, if (here) colors.primary.copy(alpha = 0.7f) else colors.text.copy(alpha = 0.22f), Modifier.padding(top = 4.dp, bottom = 2.dp).fillMaxWidth())
    }
}

@Composable
private fun WeightBar(fraction: Float, fill: Color, modifier: Modifier = Modifier, fromEnd: Boolean = false) {
    val track = Glass.colors.separator
    Box(
        modifier.height(2.dp).clip(RoundedCornerShape(1.dp)).background(track).drawBehind {
            val w = size.width * fraction.coerceIn(0.04f, 1f)
            val left = if (fromEnd) size.width - w else 0f
            drawRect(fill, topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(w, size.height))
        },
    )
}

@Composable
private fun PartFileRow(file: Path, facts: FileFacts?, weight: Float?, current: Boolean, onOpenFile: (Path) -> Unit) {
    val colors = Glass.colors
    Box(Modifier.fillMaxWidth().height(28.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(if (current) colors.surfaceRaised else Color.Transparent)
                .clickable { onOpenFile(file) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(file.fileName.toString(), color = colors.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (weight != null && facts?.inLoop != true) {
                Box(Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    WeightBar(
                        weight,
                        if (current) colors.primary.copy(alpha = 0.7f) else colors.text.copy(alpha = 0.22f),
                        Modifier.fillMaxWidth(),
                        fromEnd = true,
                    )
                }
            }
            val caption = factCaption(facts)
            if (caption != null) {
                Text(
                    caption,
                    color = if (facts?.inLoop == true) colors.warn else colors.faint,
                    fontSize = 11.sp,
                    fontFamily = EditorFontFamily,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(if (facts?.inLoop == true) 32.dp else 20.dp),
                )
            }
        }
        if (current) {
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = (-4).dp).width(2.dp).height(16.dp)
                    .clip(RoundedCornerShape(1.dp)).background(colors.primary),
            )
        }
    }
}
