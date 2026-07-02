package page.atlas.render

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import page.atlas.graph.ModuleGraph
import page.atlas.graph.ModuleLink
import page.atlas.graph.ModuleNode
import page.atlas.graph.moduleDependsOn
import page.atlas.graph.modulePath
import page.atlas.graph.moduleUsedBy

private const val FILE_LIMIT = 12
private const val MODULE_LIMIT = 8

@Composable
internal fun OverviewInspector(
    graph: ModuleGraph,
    module: ModuleNode,
    onSelectModule: (String) -> Unit,
    onOpenFile: (FilePath) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dependsOn = remember(graph, module.id) { moduleDependsOn(graph, module.id) }
    val usedBy = remember(graph, module.id) { moduleUsedBy(graph, module.id) }
    val langLine = remember(module) { languageLine(module) }
    val roles = atlasRoleColors()

    Column(
        modifier = modifier
            .width(210.dp)
            .heightIn(max = 360.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = "MODULE",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Text(
            text = module.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
        )
        Text(
            text = langLine,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        LinkSection("Depends on", dependsOn, roles.dependency, onSelectModule)
        LinkSection("Used by", usedBy, roles.usedBy, onSelectModule)

        if (module.files.isNotEmpty()) {
            SectionDivider()
            Text(
                text = "Files (${module.fileCount})",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
            )
            for (file in module.files.take(FILE_LIMIT)) {
                Text(
                    text = file.name,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFile(file.path) }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
            if (module.files.size > FILE_LIMIT) {
                Text(
                    text = "+${module.files.size - FILE_LIMIT} more",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
internal fun OverviewPathPanel(
    graph: ModuleGraph,
    from: String,
    to: String,
    onSelectModule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val path = remember(graph, from, to) { modulePath(graph, from, to) }
    val byId = remember(graph) { graph.nodes.associateBy { it.id } }
    val roles = atlasRoleColors()

    Column(
        modifier = modifier
            .width(210.dp)
            .heightIn(max = 360.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(roles.path))
            Text(
                text = "  PATH",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = roles.path,
            )
        }
        if (path == null) {
            Text(
                text = "No dependency path",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
            )
            val fromLabel = byId[from]?.label ?: from
            val toLabel = byId[to]?.label ?: to
            Text(
                text = "$fromLabel ⇢ $toLabel",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            return@Column
        }
        val hops = path.size - 1
        Text(
            text = "$hops hop${if (hops == 1) "" else "s"}",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
        )
        SectionDivider()
        for ((index, id) in path.withIndex()) {
            val label = byId[id]?.label ?: id
            Text(
                text = "${index + 1}. $label",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectModule(id) }
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun LinkSection(
    title: String,
    links: List<ModuleLink>,
    accent: Color,
    onSelectModule: (String) -> Unit,
) {
    if (links.isEmpty()) return
    SectionDivider()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(accent))
        Text(
            text = "  $title  ${links.size} modules",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
    for (link in links.take(MODULE_LIMIT)) {
        Text(
            text = link.node.label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectModule(link.node.id) }
                .padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
    if (links.size > MODULE_LIMIT) {
        Text(
            text = "+${links.size - MODULE_LIMIT} more",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

private fun languageLine(module: ModuleNode): String {
    val counts = LinkedHashMap<String, Int>()
    for (file in module.files) {
        val ext = file.name.substringAfterLast('.', "")
        if (ext.isNotEmpty()) counts.merge(ext.lowercase(), 1, Int::plus)
    }
    val top = counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(3)
        .joinToString(" · ") { "${it.key} ${it.value}" }
    return if (top.isEmpty()) "${module.fileCount} files" else "${module.fileCount} files · $top"
}
