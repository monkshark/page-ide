package page.docs.widgets

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.atlas.graph.AtlasSnapshot
import page.atlas.graph.GraphSlice
import page.atlas.graph.aggregateModules
import page.atlas.interaction.OverviewSelection
import page.atlas.render.AtlasRoleColors
import page.atlas.render.MapViewState
import page.atlas.render.OverviewCanvas
import page.atlas.render.layeredModuleLayout
import page.docs.DocsTheme
import page.docs.fetchText
import page.shared.path.FilePath

@Composable
private fun docsRoleColors(): AtlasRoleColors = AtlasRoleColors(
    dependency = DocsTheme.primary,
    usedBy = DocsTheme.accent,
    cycle = DocsTheme.warn,
    hub = DocsTheme.danger,
    path = DocsTheme.warn,
    neutral = DocsTheme.muted,
)

@Composable
private fun docsColorScheme() = if (DocsTheme.dark) darkColorScheme(
    primary = DocsTheme.primary,
    surface = DocsTheme.surface,
    surfaceVariant = DocsTheme.surfaceRaised,
    outline = DocsTheme.outline,
    onSurface = DocsTheme.text,
    onSurfaceVariant = DocsTheme.muted,
    background = DocsTheme.background,
) else lightColorScheme(
    primary = DocsTheme.primary,
    surface = DocsTheme.surface,
    surfaceVariant = DocsTheme.surfaceRaised,
    outline = DocsTheme.outline,
    onSurface = DocsTheme.text,
    onSurfaceVariant = DocsTheme.muted,
    background = DocsTheme.background,
)

@Composable
fun AtlasOverview(args: Map<String, String>) {
    var slice by remember { mutableStateOf<GraphSlice?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            slice = AtlasSnapshot.parse(fetchText("atlas-snapshot.json"))
        } catch (e: Throwable) {
            failed = true
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(520.dp).padding(vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, DocsTheme.outline, RoundedCornerShape(14.dp))
            .background(DocsTheme.background),
    ) {
        val s = slice
        when {
            failed -> Text("Graph snapshot unavailable.", color = DocsTheme.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            s == null -> Text("Loading graph…", color = DocsTheme.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            s.nodes.isEmpty() -> Text("No dependencies found.", color = DocsTheme.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            else -> OverviewHost(s)
        }
    }
}

@Composable
private fun OverviewHost(slice: GraphSlice) {
    var selection by remember(slice) { mutableStateOf(OverviewSelection()) }
    val view = remember(slice) { MapViewState() }
    val drillScope = remember(selection.drillPath) {
        selection.drillPath.lastOrNull()?.let { FilePath.of(it) }
    }
    val moduleGraph = remember(slice, drillScope) { aggregateModules(slice, scopeRoot = drillScope) }
    val layout = remember(moduleGraph) { layeredModuleLayout(moduleGraph) }
    MaterialTheme(colorScheme = docsColorScheme()) {
        Box(modifier = Modifier.fillMaxSize()) {
            OverviewCanvas(
                graph = moduleGraph,
                layout = layout,
                activeModuleId = null,
                followActive = false,
                view = view,
                selection = selection,
                onSelectionChange = { selection = it },
                onOpenFile = {},
                roles = docsRoleColors(),
            )
            if (selection.drillPath.isNotEmpty()) {
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(DocsTheme.surface.copy(alpha = 0.93f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    OverviewBreadcrumb(selection.drillPath) { depth -> selection = selection.drillUpTo(depth) }
                }
            }
        }
    }
}

@Composable
private fun OverviewBreadcrumb(drillPath: List<String>, onNavigate: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BreadcrumbSegment("root", current = false) { onNavigate(0) }
        drillPath.forEachIndexed { index, id ->
            Text("/", color = DocsTheme.muted, fontSize = 12.sp)
            BreadcrumbSegment(crumbLabel(id), current = index == drillPath.lastIndex) { onNavigate(index + 1) }
        }
    }
}

@Composable
private fun BreadcrumbSegment(label: String, current: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (current) DocsTheme.text else DocsTheme.primary,
        fontSize = 12.sp,
        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        modifier = if (current) Modifier else Modifier.clickable { onClick() },
    )
}

private fun crumbLabel(id: String): String =
    id.substringAfterLast('/').substringAfterLast('\\').ifEmpty { id }
