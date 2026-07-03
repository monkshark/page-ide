package page.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import page.core.PageIdentity
import page.runtime.CURRENT_FILE_ID
import page.runtime.LanguageRunDefaults
import page.runtime.LanguageRunTemplate
import page.runtime.RunConfigsState
import page.ui.CompactDropdown
import page.ui.CompactMenuItem
import page.ui.Glass
import page.ui.GlassTooltip
import java.nio.file.Path

@Composable
internal fun TopBar(
    path: Path?,
    workspaceRoot: Path?,
    runState: RunConfigsState,
    activeFilePath: Path?,
    onSelectRunConfig: (String) -> Unit,
    runIsRunning: Boolean,
    onStartRun: () -> Unit,
    onStopRun: () -> Unit,
    onOpenRunDialog: () -> Unit,
) {
    val colors = Glass.colors
    Surface(
        modifier = Modifier.fillMaxWidth().height(34.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppMarkGlyph(size = 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = PageIdentity.NAME,
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
            )
            Spacer(Modifier.width(16.dp))
            Breadcrumb(path = path, workspaceRoot = workspaceRoot)
            Spacer(Modifier.weight(1f))
            val currentFileTemplate = activeFilePath?.let { LanguageRunDefaults.forFile(it) }
            RunDropdown(
                state = runState,
                activeFilePath = activeFilePath,
                currentFileTemplate = currentFileTemplate,
                onSelect = onSelectRunConfig,
                onEdit = onOpenRunDialog,
            )
            Spacer(Modifier.width(4.dp))
            val canStart = !runIsRunning && when {
                runState.isCurrentFileActive -> currentFileTemplate != null
                else -> runState.active != null
            }
            TopBarAction(
                label = "Run",
                enabled = canStart,
                onClick = onStartRun,
                shortcut = "Shift+F10",
                icon = { tint -> PlayGlyph(tint = tint) },
                enabledIconTint = colors.success,
            )
            TopBarAction(
                label = "Stop",
                enabled = runIsRunning,
                onClick = onStopRun,
                shortcut = "Ctrl+F2",
                icon = { tint -> StopGlyph(tint = tint) },
                enabledIconTint = colors.danger,
            )
        }
    }
}

@Composable
private fun Breadcrumb(path: Path?, workspaceRoot: Path?) {
    val colors = Glass.colors
    val segments = breadcrumbSegments(path, workspaceRoot)
    if (segments.isEmpty()) {
        Text(
            text = "untitled",
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        segments.forEachIndexed { index, segment ->
            if (index > 0) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.faint,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            Text(
                text = segment,
                style = MaterialTheme.typography.bodySmall,
                color = if (index == segments.lastIndex) colors.text else colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun breadcrumbSegments(path: Path?, workspaceRoot: Path?): List<String> {
    if (path == null) return emptyList()
    val rootName = workspaceRoot?.fileName?.toString()
    val relative = if (workspaceRoot != null && path.startsWith(workspaceRoot)) {
        runCatching { workspaceRoot.relativize(path) }.getOrNull()
    } else null
    return if (relative != null) {
        buildList {
            if (rootName != null) add(rootName)
            relative.forEach { add(it.toString()) }
        }
    } else {
        listOf(path.fileName?.toString() ?: path.toString())
    }
}

@Composable
private fun RunDropdown(
    state: RunConfigsState,
    activeFilePath: Path?,
    currentFileTemplate: LanguageRunTemplate?,
    onSelect: (String) -> Unit,
    onEdit: () -> Unit,
) {
    val colors = Glass.colors
    var expanded by remember { mutableStateOf(false) }
    val active = state.active
    val activeFileName = activeFilePath?.fileName?.toString()
    val label = when {
        state.isCurrentFileActive -> activeFileName?.let { "Current file · $it" } ?: "Current file"
        active != null -> active.name.takeIf { it.isNotBlank() } ?: "Run config"
        else -> "Run config"
    }
    Box {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(Glass.radius.xs),
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(horizontal = 2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                )
                Spacer(Modifier.width(6.dp))
                ChevronGlyph(tint = colors.faint)
            }
        }
        CompactDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val currentFileLabel = when {
                activeFileName == null -> "Current file (no file open)"
                currentFileTemplate == null -> "Current file (${activeFileName} — unsupported)"
                else -> "Current file · $activeFileName"
            }
            CompactMenuItem(
                label = currentFileLabel,
                onClick = {
                    expanded = false
                    onSelect(CURRENT_FILE_ID)
                },
            )
            if (state.configs.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.separator),
                    )
                }
                for (cfg in state.configs) {
                    CompactMenuItem(
                        label = cfg.name.ifBlank { cfg.command },
                        onClick = {
                            expanded = false
                            onSelect(cfg.id)
                        },
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.separator),
                )
            }
            CompactMenuItem(
                label = "Edit configurations…",
                onClick = { expanded = false; onEdit() },
            )
        }
    }
}

@Composable
private fun TopBarAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    shortcut: String? = null,
    icon: @Composable (tint: Color) -> Unit,
    enabledIconTint: Color,
) {
    val colors = Glass.colors
    val iconTint = if (enabled) enabledIconTint else colors.faint
    val tooltipText = if (!shortcut.isNullOrBlank()) "$label · $shortcut" else label
    GlassTooltip(text = tooltipText) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(Glass.radius.xs),
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .let { if (enabled) it.clickable { onClick() } else it },
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon(iconTint)
            }
        }
    }
}
