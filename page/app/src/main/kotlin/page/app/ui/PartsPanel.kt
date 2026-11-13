package page.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

internal fun partCaption(part: MapPart, here: Boolean, tie: PartTie?): String = when {
    here -> "here"
    tie == null -> "${part.fileCount} files"
    tie.users > 0 && tie.needed -> "${tie.users} use it, needed"
    tie.users == 1 -> "1 uses it"
    tie.users > 1 -> "${tie.users} use it"
    else -> "this file needs it"
}

internal fun factCaption(facts: FileFacts?): String? = when {
    facts == null -> null
    facts.inLoop -> "loop"
    else -> "used by ${facts.usedBy}"
}

@Composable
internal fun PartsPanel(
    map: ProjectMap?,
    currentFile: Path?,
    ties: Map<String, PartTie>,
    loadFacts: (List<Path>) -> Map<Path, FileFacts>,
    onOpenFile: (Path) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Glass.colors
    if (map == null || map.isEmpty()) {
        Box(modifier.fillMaxSize().padding(16.dp)) {
            Text(if (map == null) "Reading the build files" else "No parts found, the Files tab has the tree", color = colors.muted, fontSize = 12.sp)
        }
        return
    }
    val current = currentFile?.toAbsolutePath()?.normalize()
    val currentPart = current?.let { map.partOf(it) }
    val tiers = remember(map) { partTiers(map) }
    var selectedId by remember(currentPart?.id, map) { mutableStateOf(currentPart?.id ?: tiers.firstOrNull()?.firstOrNull()?.id) }
    val selected = selectedId?.let { map.part(it) }
    val facts by produceState(emptyMap<Path, FileFacts>(), selected?.id, map) {
        value = emptyMap()
        val part = selected ?: return@produceState
        value = withContext(Dispatchers.IO) { runCatching { loadFacts(part.files) }.getOrDefault(emptyMap()) }
    }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text("Top relies on bottom", color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("${tiers.sumOf { it.size }} parts", color = colors.faint, fontSize = 11.sp, fontFamily = EditorFontFamily)
            }
            for (tier in tiers) {
                for (row in tier.chunked(3)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (part in row) {
                            PartBox(
                                part = part,
                                caption = partCaption(part, part.id == currentPart?.id, ties[part.id]),
                                here = part.id == currentPart?.id,
                                tied = part.id in ties,
                                selected = part.id == selectedId && part.id != currentPart?.id,
                                onClick = { selectedId = part.id },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            map.part(ProjectMapBuilder.OTHER_ID)?.let { other ->
                Text(
                    "Other, ${other.fileCount} loose files",
                    color = if (selectedId == other.id) colors.text else colors.faint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp).clickable { selectedId = other.id },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
        if (selected != null) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.name, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("${selected.fileCount} files", color = colors.faint, fontSize = 11.sp, fontFamily = EditorFontFamily)
                }
                if (selected.description.isNotBlank()) {
                    Text(selected.description, color = colors.muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            val files = remember(selected) { selected.files.sortedBy { it.fileName.toString().lowercase() } }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp).testTag("part-files")) {
                items(files, key = { it.toString() }) { file ->
                    PartFileRow(file, factCaption(facts[file]), facts[file]?.inLoop == true, file == current, onOpenFile)
                }
            }
        }
    }
}

@Composable
private fun PartBox(
    part: MapPart,
    caption: String,
    here: Boolean,
    tied: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Glass.colors
    val shape = RoundedCornerShape(8.dp)
    val border = when {
        here -> colors.primary.copy(alpha = 0.45f)
        tied -> colors.text.copy(alpha = 0.14f)
        else -> colors.separator
    }
    val fill = when {
        here -> colors.primarySoft
        selected -> colors.surfaceRaised
        else -> colors.background
    }
    Column(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp)
            .testTag("part-${part.id}"),
    ) {
        Text(part.name, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            caption,
            color = when {
                here -> colors.primary
                tied -> colors.muted
                else -> colors.faint
            },
            fontSize = 11.sp,
            fontFamily = EditorFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PartFileRow(file: Path, caption: String?, loop: Boolean, current: Boolean, onOpenFile: (Path) -> Unit) {
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
        ) {
            Text(file.fileName.toString(), color = colors.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (caption != null) {
                Text(caption, color = if (loop) colors.warn else colors.faint, fontSize = 11.sp, fontFamily = EditorFontFamily)
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
