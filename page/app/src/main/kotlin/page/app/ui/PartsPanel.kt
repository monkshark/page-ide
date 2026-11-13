package page.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import page.atlas.graph.FileMargins
import page.atlas.toNioPath
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

internal data class RelatedFiles(val users: List<Path>, val needs: List<Path>) {
    fun isEmpty(): Boolean = users.isEmpty() && needs.isEmpty()
    val all: List<Path> get() = users + needs
}

internal fun relatedFiles(map: ProjectMap, margins: FileMargins?, partId: String): RelatedFiles {
    if (margins == null) return RelatedFiles(emptyList(), emptyList())
    fun inPart(node: page.atlas.graph.GraphNode): Path? =
        node.path?.toNioPath()?.toAbsolutePath()?.normalize()?.takeIf { map.partOf(it)?.id == partId }
    return RelatedFiles(
        users = margins.usedBy.mapNotNull(::inPart).distinct(),
        needs = margins.needs.mapNotNull { inPart(it.node) }.distinct(),
    )
}

internal fun spineTip(part: MapPart, here: Boolean, openFile: String?, related: RelatedFiles): String {
    val name = if (part.id == ProjectMapBuilder.OTHER_ID) "Other files" else part.name
    if (openFile == null) return name
    fun list(paths: List<Path>): String {
        val names = paths.map { it.fileName.toString() }
        return if (names.size <= 3) names.joinToString(", ") else names.take(3).joinToString(", ") + " and ${names.size - 3} more"
    }
    return when {
        here -> "$openFile is in $name"
        related.users.isNotEmpty() && related.needs.isNotEmpty() -> "$name and $openFile depend on each other through ${list(related.all)}"
        related.users.isNotEmpty() -> "$name depends on $openFile through ${list(related.users)}"
        related.needs.isNotEmpty() -> "$openFile depends on $name through ${list(related.needs)}"
        part.id == ProjectMapBuilder.OTHER_ID -> "Files outside every part"
        else -> "$name has no link to $openFile"
    }
}

@Composable
internal fun PartsPanel(
    map: ProjectMap?,
    currentFile: Path?,
    ties: Map<String, PartTie>,
    loadFacts: (List<Path>) -> Map<Path, FileFacts>,
    onOpenFile: (Path) -> Unit,
    root: Path? = null,
    onBrowseFiles: () -> Unit = {},
    margins: FileMargins? = null,
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
    var pinnedFor by remember(currentPart?.id, map) { mutableStateOf<String?>(null) }
    val factsCache = remember(map) { mutableStateMapOf<String, Map<Path, FileFacts>>() }
    var factsLoading by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(selected?.id, map, loadFacts) {
        val part = selected ?: return@LaunchedEffect
        factsLoading = true
        val fresh = withContext(Dispatchers.IO) { runCatching { loadFacts(part.files) }.getOrNull() }
        if (fresh != null) factsCache[part.id] = fresh
        factsLoading = false
    }
    val facts = selected?.let { factsCache[it.id] }.orEmpty()
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
    var folded by remember(map) { mutableStateOf(false) }
    fun shown(part: MapPart): Boolean = !folded || part.id == currentPart?.id || part.id == selectedId || tieOf(part) == SpineTie.USER || tieOf(part) == SpineTie.NEEDED
    val ordered = (tiers.flatten() + listOfNotNull(other)).filter(::shown)
    val hiddenCount = tiers.sumOf { it.size } + (if (other != null) 1 else 0) - ordered.size
    val relatedCache = remember(map, margins) { HashMap<String, RelatedFiles>() }
    fun relatedByPart(id: String): RelatedFiles = relatedCache.getOrPut(id) { relatedFiles(map, margins, id) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val filterFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    Column(
        modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                androidx.compose.ui.input.key.Key.DirectionDown -> focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next)
                androidx.compose.ui.input.key.Key.DirectionUp -> focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Previous)
                androidx.compose.ui.input.key.Key.Slash -> runCatching { filterFocus.requestFocus() }.isSuccess
                else -> false
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { folded = !folded }
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                .testTag("parts-fold"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FoldChevron(open = !folded, tint = colors.muted)
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
                .map { group -> group.filter(::shown) }
                .filter { it.isNotEmpty() }
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    Box(Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp).fillMaxWidth().height(1.dp).background(colors.separator))
                }
                for (part in group) {
                    PartRow(
                        part = part,
                        spineTip = spineTip(part, part.id == currentPart?.id, current?.fileName?.toString(), relatedByPart(part.id)),
                        relation = if (part.id == currentPart?.id) null else partRelation(ties[part.id], current?.fileName?.toString()),
                        here = part.id == currentPart?.id,
                        selected = part.id == selectedId,
                        weight = part.fileCount.toFloat() / maxCount,
                        onClick = {
                            selectedId = part.id
                            pinnedFor = null
                        },
                        onRelationClick = {
                            selectedId = part.id
                            pinnedFor = part.id
                        },
                        modifier = Modifier.onGloballyPositioned { rowTops[part.id] = it.positionInParent().y },
                    )
                }
            }
            if (folded && hiddenCount > 0) {
                Text(
                    "$hiddenCount more parts",
                    color = colors.faint,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(start = 28.dp, top = 4.dp)
                        .clickable { folded = false }
                        .testTag("parts-more"),
                )
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
            if (currentPart != null && selected.id != currentPart.id) {
                Text(
                    "Back to ${currentPart.name}",
                    color = colors.primary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(start = 16.dp, bottom = 8.dp)
                        .clickable {
                            selectedId = currentPart.id
                            pinnedFor = null
                        }
                        .testTag("parts-back"),
                )
            }
            var query by remember(selected.id) { mutableStateOf("") }
            var byUsers by remember { mutableStateOf(false) }
            FilterField(query, { query = it }, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp).fillMaxWidth().focusRequester(filterFocus))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("File", color = colors.faint, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clickable { byUsers = !byUsers }.testTag("parts-sort"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Used by", color = if (byUsers) colors.muted else colors.faint, fontSize = 11.sp)
                    if (byUsers) FoldChevron(open = true, tint = colors.muted)
                }
            }
            val related = remember(selected, margins, pinnedFor) {
                if (pinnedFor == selected.id) relatedFiles(map, margins, selected.id) else RelatedFiles(emptyList(), emptyList())
            }
            val files = remember(selected, related, query, byUsers, facts) {
                val pinned = related.all.toSet()
                val rest = selected.files.filter { it !in pinned }
                val ordered = if (byUsers) rest.sortedWith(compareByDescending<Path> { facts[it]?.usedBy ?: -1 }.thenBy { it.fileName.toString().lowercase() })
                else rest.sortedBy { it.fileName.toString().lowercase() }
                (related.all + ordered).filter { query.isBlank() || it.fileName.toString().contains(query.trim(), ignoreCase = true) }
            }
            val maxUsers = facts.values.maxOfOrNull { it.usedBy }?.coerceAtLeast(1) ?: 1
            val openName = current?.fileName?.toString()?.substringBeforeLast('.')
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp).testTag("part-files")) {
                if (!related.isEmpty() && openName != null) {
                    item(key = "pinned-label") {
                        Text(
                            if (related.users.isNotEmpty()) "Uses $openName" else "Used by $openName",
                            color = colors.faint,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp).testTag("pinned-label"),
                        )
                    }
                }
                items(files, key = { it.toString() }) { file ->
                    val fact = facts[file]
                    PartFileRow(file, fact, fact?.let { it.usedBy.toFloat() / maxUsers }, file == current, factsLoading, onOpenFile)
                }
            }
        }
    }
}

@Composable
private fun PartRow(
    part: MapPart,
    spineTip: String,
    relation: String?,
    here: Boolean,
    selected: Boolean,
    weight: Float,
    onClick: () -> Unit,
    onRelationClick: () -> Unit = onClick,
    modifier: Modifier = Modifier,
) {
    val colors = Glass.colors
    val other = part.id == ProjectMapBuilder.OTHER_ID
    var focused by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth()) {
    page.ui.GlassTooltip(text = spineTip) {
        Box(Modifier.width(20.dp).height(28.dp).testTag("spine-${part.id}"))
    }
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(if (here) colors.surfaceRaised else Color.Transparent)
            .border(
                1.dp,
                when {
                    focused -> colors.primary
                    selected && !here -> colors.text.copy(alpha = 0.16f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(6.dp),
            )
            .onFocusChanged { focused = it.isFocused }
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
            Text(
                relation,
                color = colors.text,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onRelationClick).testTag("relation-${part.id}"),
            )
        }
        WeightBar(weight, if (here) colors.primary.copy(alpha = 0.7f) else colors.text.copy(alpha = 0.22f), Modifier.padding(top = 4.dp, bottom = 2.dp).fillMaxWidth())
    }
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
private fun PartFileRow(file: Path, facts: FileFacts?, weight: Float?, current: Boolean, stale: Boolean, onOpenFile: (Path) -> Unit) {
    val colors = Glass.colors
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(28.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(if (current) colors.surfaceRaised else Color.Transparent)
                .border(1.dp, if (focused) colors.primary else Color.Transparent, RoundedCornerShape(6.dp))
                .onFocusChanged { focused = it.isFocused }
                .clickable { onOpenFile(file) }
                .testTag("file-row-${file.fileName}")
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(file.fileName.toString(), color = colors.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            val dim = if (stale) 0.35f else 1f
            Box(Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (weight != null && facts?.inLoop != true) {
                    WeightBar(
                        weight,
                        (if (current) colors.primary.copy(alpha = 0.7f) else colors.text.copy(alpha = 0.22f)).let { it.copy(alpha = it.alpha * dim) },
                        Modifier.fillMaxWidth(),
                        fromEnd = true,
                    )
                }
            }
            Text(
                factCaption(facts).orEmpty(),
                color = (if (facts?.inLoop == true) colors.warn else colors.faint).let { it.copy(alpha = it.alpha * dim) },
                fontSize = 11.sp,
                fontFamily = EditorFontFamily,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(32.dp).testTag("file-users-${file.fileName}"),
            )
        }
        if (current) {
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = (-4).dp).width(2.dp).height(16.dp)
                    .clip(RoundedCornerShape(1.dp)).background(colors.primary),
            )
        }
    }
}

@Composable
private fun FoldChevron(open: Boolean, tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.width(12.dp).height(12.dp)) {
        val s = size.width
        val path = GPath().apply {
            if (open) {
                moveTo(s * 0.25f, s * 0.375f)
                lineTo(s * 0.5f, s * 0.625f)
                lineTo(s * 0.75f, s * 0.375f)
            } else {
                moveTo(s * 0.375f, s * 0.25f)
                lineTo(s * 0.625f, s * 0.5f)
                lineTo(s * 0.375f, s * 0.75f)
            }
        }
        drawPath(path, tint, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun FilterField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = Glass.colors
    val shape = RoundedCornerShape(8.dp)
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = colors.text, fontSize = 12.sp, fontFamily = page.ui.UiFontFamily),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
        modifier = modifier.testTag("parts-filter"),
        decorationBox = { inner ->
            Box(
                Modifier.height(28.dp).fillMaxWidth().clip(shape).background(colors.background)
                    .border(1.dp, colors.separator, shape).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text("Filter files", color = colors.faint, fontSize = 12.sp)
                inner()
            }
        },
    )
}
