package page.atlas.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as DrawPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import page.atlas.analyzer.ImportExtractor
import page.atlas.analyzer.ModuleLinks
import page.atlas.analyzer.WorkspaceIndex
import page.atlas.graph.DocsDescriber
import page.atlas.graph.GraphSlice
import page.atlas.graph.MapPart
import page.atlas.graph.NodeKind
import page.atlas.graph.ProjectMap
import page.atlas.graph.ProjectMapBuilder
import page.ui.Glass
import java.nio.file.Path
import java.util.prefs.Preferences

private enum class Tie { FOCUS, SELECTED, USES, USED_BY, DIM, NONE }

private data class MapLoad(val map: ProjectMap, val millis: Long)

fun loadProjectMap(root: Path): ProjectMap {
    val index = WorkspaceIndex(root).also { it.refreshIfStale(0) }
    val files = index.files().filter { ImportExtractor.supports(it) }
    return ProjectMapBuilder.build(root, files, emptyList(), DocsDescriber(root), moduleLinks = ModuleLinks.read(root))
}

@Composable
fun ProjectMapView(
    root: Path?,
    slice: GraphSlice,
    activeFile: Path?,
    onOpenFile: (Path) -> Unit,
    modifier: Modifier = Modifier,
    tourStore: TourStore = remember { TourStore.preferences() },
) {
    val load by produceState<MapLoad?>(null, root) {
        value = null
        if (root == null) return@produceState
        value = withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            MapLoad(loadProjectMap(root), System.currentTimeMillis() - started)
        }
    }
    val imports = remember(slice) {
        val files = slice.nodes.filter { it.kind == NodeKind.WORKSPACE_FILE || it.kind == NodeKind.ACTIVE }.map { it.id }.toSet()
        slice.edges.filter { it.from in files && it.to in files }.map { Path.of(it.from) to Path.of(it.to) }
    }
    var drill by remember(root) { mutableStateOf<String?>(null) }
    var fileListOf by remember(root) { mutableStateOf<MapPart?>(null) }
    var selected by remember(root, drill) { mutableStateOf<String?>(null) }
    var tourStep by remember { mutableStateOf(if (tourStore.done()) -1 else 0) }

    val top = load?.map
    val current = remember(top, drill, imports) {
        if (top == null) null
        else drill?.let { ProjectMapBuilder.inside(top, it, root!!, imports, DocsDescriber(root)) } ?: top
    }
    LaunchedEffect(current, drill) {
        val d = drill
        if (d != null && current != null && current.parts.size <= 1) {
            fileListOf = top?.part(d)
        }
    }

    Row(modifier.fillMaxSize().background(Glass.colors.background)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            MapBar(
                title = top?.title ?: "",
                drillName = drill?.let { top?.part(it)?.name },
                fileListName = fileListOf?.name,
                status = when {
                    root == null -> "No folder open"
                    load == null -> "Reading project…"
                    else -> "${top!!.parts.sumOf { it.fileCount }} files · ${load!!.millis} ms"
                },
                onRoot = { drill = null; fileListOf = null },
                onDrill = { fileListOf = null },
                onHelp = { tourStep = 0 },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    current == null -> Text(
                        if (root == null) "Open a folder to see its map." else "Reading project…",
                        color = Glass.colors.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center),
                    )
                    fileListOf != null -> FileList(fileListOf!!, activeFile, onOpenFile)
                    current.isEmpty() -> Text("No source files here.", color = Glass.colors.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
                    else -> MapCanvas(
                        map = current,
                        focus = selected ?: activeFile?.let { current.partOf(it)?.id },
                        focusIsSelection = selected != null,
                        activeName = activeFile?.fileName?.toString(),
                        onPick = { part ->
                            when {
                                drill == null && top != null && part.id != ProjectMapBuilder.OTHER_ID -> { drill = part.id; selected = null }
                                selected == part.id -> fileListOf = part
                                else -> selected = part.id
                            }
                        },
                    )
                }
                if (tourStep >= 0 && current != null && fileListOf == null) {
                    TourCard(
                        step = tourStep,
                        onNext = { if (tourStep == TOUR.lastIndex) { tourStep = -1; tourStore.markDone() } else tourStep++ },
                        onSkip = { tourStep = -1; tourStore.markDone() },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    )
                }
            }
        }
        SidePanel(
            map = current,
            isTop = drill == null,
            focus = selected ?: activeFile?.let { current?.partOf(it)?.id },
            activeFile = activeFile,
            onPick = { selected = it },
            onOpenFile = onOpenFile,
            onShowFiles = { fileListOf = it },
            onEnter = { id -> drill = id; selected = null },
        )
    }
}

@Composable
private fun MapBar(
    title: String,
    drillName: String?,
    fileListName: String?,
    status: String,
    onRoot: () -> Unit,
    onDrill: () -> Unit,
    onHelp: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Crumb(title.ifEmpty { "Project" }, here = drillName == null && fileListName == null, onClick = onRoot)
        if (drillName != null) {
            Text("›", color = Glass.colors.faint, fontSize = 12.sp)
            Crumb(drillName, here = fileListName == null, onClick = onDrill)
        }
        if (fileListName != null && fileListName != drillName) {
            Text("›", color = Glass.colors.faint, fontSize = 12.sp)
            Crumb(fileListName, here = true, onClick = {})
        }
        Spacer(Modifier.weight(1f))
        Text(status, color = Glass.colors.faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Box(
            Modifier.size(20.dp).clip(CircleShape).border(1.dp, Glass.colors.outline, CircleShape).clickable(onClick = onHelp).testTag("map-help"),
            contentAlignment = Alignment.Center,
        ) { Text("?", color = Glass.colors.muted, fontSize = 11.sp) }
    }
}

@Composable
private fun Crumb(label: String, here: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (here) Glass.colors.text else Glass.colors.muted,
        fontSize = 13.sp,
        fontWeight = if (here) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable(enabled = !here, onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun MapCanvas(
    map: ProjectMap,
    focus: String?,
    focusIsSelection: Boolean,
    activeName: String?,
    onPick: (MapPart) -> Unit,
) {
    val placement = remember(map) { ProjectMapLayout.place(map) }
    val rel = focus?.let { map.relationsOf(it) }
    fun tie(id: String): Tie = when {
        focus == null -> Tie.NONE
        id == focus -> if (focusIsSelection) Tie.SELECTED else Tie.FOCUS
        rel!!.uses.contains(id) -> Tie.USES
        rel.usedBy.contains(id) -> Tie.USED_BY
        else -> Tie.DIM
    }
    val colors = Glass.colors
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
        Box(Modifier.size(placement.width.dp, placement.height.dp)) {
            Canvas(Modifier.size(placement.width.dp, placement.height.dp)) {
                val px = density
                for (link in map.links) {
                    val a = placement.parts[link.from] ?: continue
                    val b = placement.parts[link.to] ?: continue
                    val start = Offset((a.x + ProjectMapLayout.BOX_W / 2) * px, (a.y + ProjectMapLayout.BOX_H) * px)
                    val end = Offset((b.x + ProjectMapLayout.BOX_W / 2) * px, b.y * px)
                    val (color, width, alpha) = when {
                        focus == null -> Triple(colors.outline, 1.2f, 1f)
                        link.from == focus -> Triple(colors.success, 2f, 1f)
                        link.to == focus -> Triple(colors.warn, 2f, 1f)
                        else -> Triple(colors.outline, 1.2f, 0.15f)
                    }
                    val midY = (start.y + end.y) / 2
                    val path = DrawPath().apply {
                        moveTo(start.x, start.y)
                        cubicTo(start.x, midY, end.x, midY, end.x, end.y)
                    }
                    drawPath(path, color.copy(alpha = color.alpha * alpha), style = Stroke(width = width * px))
                }
            }
            for (part in map.parts) {
                val at = placement.parts.getValue(part.id)
                PartBox(
                    part = part,
                    tie = tie(part.id),
                    activeName = activeName,
                    onClick = { onPick(part) },
                    modifier = Modifier.offset(at.x.dp, at.y.dp),
                )
            }
        }
    }
}

@Composable
private fun PartBox(part: MapPart, tie: Tie, activeName: String?, onClick: () -> Unit, modifier: Modifier) {
    val colors = Glass.colors
    val border = when (tie) {
        Tie.FOCUS, Tie.SELECTED -> colors.primary
        Tie.USES -> colors.success
        Tie.USED_BY -> colors.warn
        else -> colors.outline
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .size(ProjectMapLayout.BOX_W.dp, ProjectMapLayout.BOX_H.dp)
            .alpha(if (tie == Tie.DIM) 0.3f else 1f)
            .clip(shape)
            .background(if (tie == Tie.SELECTED || tie == Tie.FOCUS) colors.surfaceOverlay else colors.surfaceRaised)
            .border(if (tie == Tie.NONE || tie == Tie.DIM) 1.dp else 1.5.dp, border, shape)
            .clickable(onClick = onClick)
            .testTag("part-${part.id}")
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(part.name, color = colors.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${part.fileCount}", color = colors.faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        val tag = when (tie) {
            Tie.FOCUS -> activeName?.let { "$it is here" }
            Tie.USES -> "relies on this"
            Tie.USED_BY -> "breaks if it breaks"
            else -> null
        }
        if (tag != null) {
            Text(tag, color = border, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
        Text(part.description, color = colors.muted, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = if (tag != null) 2 else 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SidePanel(
    map: ProjectMap?,
    isTop: Boolean,
    focus: String?,
    activeFile: Path?,
    onPick: (String) -> Unit,
    onOpenFile: (Path) -> Unit,
    onShowFiles: (MapPart) -> Unit,
    onEnter: (String) -> Unit,
) {
    val colors = Glass.colors
    Column(
        Modifier.width(300.dp).fillMaxHeight().background(colors.surface).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (map == null) return@Column
        val part = focus?.let { map.part(it) }
        if (part == null) {
            Text(map.title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${map.parts.size} parts · ${map.parts.sumOf { it.fileCount }} files. " +
                    if (isTop) "Top row is what you use, bottom row is what everything stands on." else "Each box is a folder inside this part.",
                color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            Legend()
            Text("Click a box to see what it touches.", color = colors.faint, fontSize = 12.sp)
            return@Column
        }
        Text(part.name, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(part.description, color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp)
        val rel = map.relationsOf(part.id)
        Group("Relies on") { Chips(rel.uses.mapNotNull { map.part(it) }, colors.success, onPick) }
        Group("Breaks if this breaks") { Chips(rel.usedBy.mapNotNull { map.part(it) }, colors.warn, onPick) }
        Group("Files") {
            val base = part.dir
            part.files.take(8).forEach { f ->
                val label = base?.let { runCatching { it.relativize(f).toString() }.getOrNull() } ?: f.fileName.toString()
                Text(
                    label.replace('\\', '/'),
                    color = if (f == activeFile) colors.text else colors.muted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { onOpenFile(f) }.padding(vertical = 2.dp),
                )
            }
            if (part.fileCount > 8) {
                Text("All ${part.fileCount} files →", color = colors.primary, fontSize = 12.sp,
                    modifier = Modifier.clickable { onShowFiles(part) }.padding(vertical = 2.dp))
            }
        }
        if (isTop && part.id != ProjectMapBuilder.OTHER_ID) {
            Text("Look inside ${part.name} →", color = colors.primary, fontSize = 12.5.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, colors.outline, RoundedCornerShape(6.dp)).clickable { onEnter(part.id) }.padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem(Glass.colors.success, "relies on")
        LegendItem(Glass.colors.warn, "breaks if it breaks")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(width = 12.dp, height = 2.dp).background(color))
        Text(label, color = Glass.colors.muted, fontSize = 11.5.sp)
    }
}

@Composable
private fun Group(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), color = Glass.colors.faint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        content()
    }
}

@Composable
private fun Chips(parts: List<MapPart>, color: Color, onPick: (String) -> Unit) {
    if (parts.isEmpty()) {
        Text("nothing", color = Glass.colors.faint, fontSize = 12.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEach { p ->
            Text(
                p.name, color = Glass.colors.text, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable { onPick(p.id) }.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun FileList(part: MapPart, activeFile: Path?, onOpenFile: (Path) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(part.description, color = Glass.colors.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
        part.files.forEach { f ->
            val label = part.dir?.let { runCatching { it.relativize(f).toString() }.getOrNull() } ?: f.toString()
            Text(
                label.replace('\\', '/'),
                color = if (f == activeFile) Glass.colors.text else Glass.colors.muted,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { onOpenFile(f) }.padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

private data class TourStep(val title: String, val body: String)

private val TOUR = listOf(
    TourStep("Your project, in a few parts", "Each box is one job the code does, not one folder. The top row is what you use; the bottom row is what everything stands on. Lines show which part relies on which."),
    TourStep("Click a part", "The panel on the right says what it relies on and what breaks if it breaks. Click a part on this screen to look inside it; go back with the path at the top."),
    TourStep("It follows your editor", "The part holding the file you're editing lights up. What it relies on turns green, what could break turns orange, everything else fades."),
)

@Composable
private fun TourCard(step: Int, onNext: () -> Unit, onSkip: () -> Unit, modifier: Modifier) {
    val colors = Glass.colors
    val s = TOUR[step]
    Column(
        modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceOverlay)
            .border(1.dp, colors.primary, RoundedCornerShape(10.dp))
            .testTag("map-tour")
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${step + 1} / ${TOUR.size}", color = colors.primary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(s.title, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(s.body, color = colors.muted, fontSize = 12.5.sp, lineHeight = 18.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Skip", color = colors.faint, fontSize = 12.5.sp, modifier = Modifier.clickable(onClick = onSkip).padding(4.dp))
            Spacer(Modifier.weight(1f))
            Text(
                if (step == TOUR.lastIndex) "Got it" else "Next",
                color = colors.onPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primary).clickable(onClick = onNext).testTag("map-tour-next").padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

interface TourStore {
    fun done(): Boolean
    fun markDone()

    companion object {
        fun preferences(): TourStore = object : TourStore {
            private val node = runCatching { Preferences.userRoot().node("page/atlas") }.getOrNull()
            override fun done(): Boolean = node?.getBoolean("mapTourDone", false) ?: true
            override fun markDone() { runCatching { node?.putBoolean("mapTourDone", true) } }
        }

        fun memory(initiallyDone: Boolean = false): TourStore = object : TourStore {
            private var done = initiallyDone
            override fun done(): Boolean = done
            override fun markDone() { done = true }
        }
    }
}
