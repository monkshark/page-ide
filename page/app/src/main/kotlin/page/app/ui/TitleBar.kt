package page.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
internal fun TitleBar(
    path: Path?,
    terminalOpen: Boolean,
    onTerminalToggle: () -> Unit,
    runState: RunConfigsState,
    activeFilePath: Path?,
    onSelectRunConfig: (String) -> Unit,
    runIsRunning: Boolean,
    onStartRun: () -> Unit,
    onStopRun: () -> Unit,
    onOpenRunDialog: () -> Unit,
    outputOpen: Boolean,
    onOutputToggle: () -> Unit,
    atlasOpen: Boolean,
    onAtlasToggle: () -> Unit,
    settingsOpen: Boolean,
    onToggleSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = PageIdentity.NAME,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "v${PageIdentity.VERSION}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(20.dp))
            Text(
                text = path?.toString() ?: "untitled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            TitleBarAction(
                label = "Run",
                enabled = canStart,
                onClick = onStartRun,
                shortcut = "Shift+F10",
                icon = { tint: Color -> PlayGlyph(tint = tint) },
                enabledIconTint = Glass.colors.success,
            )
            TitleBarAction(
                label = "Stop",
                enabled = runIsRunning,
                onClick = onStopRun,
                shortcut = "Ctrl+F2",
                icon = { tint: Color -> StopGlyph(tint = tint) },
                enabledIconTint = Glass.colors.danger,
            )
            Spacer(Modifier.width(8.dp))
            TitleBarToggle(
                label = "Atlas",
                selected = atlasOpen,
                onClick = onAtlasToggle,
                icon = { tint: Color -> AtlasGlyph(tint = tint) },
            )
            TitleBarToggle(
                label = "Output",
                selected = outputOpen,
                onClick = onOutputToggle,
                icon = { tint: Color -> OutputGlyph(tint = tint) },
            )
            TitleBarToggle(
                label = "Terminal",
                selected = terminalOpen,
                onClick = onTerminalToggle,
                shortcut = "Ctrl+`",
                icon = { tint: Color -> TerminalGlyph(tint = tint) },
            )
            Spacer(Modifier.width(8.dp))
            TitleBarToggle(
                label = "Settings",
                selected = settingsOpen,
                onClick = onToggleSettings,
                shortcut = "Ctrl+Alt+S",
                icon = { tint: Color -> SettingsGlyph(tint = tint) },
            )
        }
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
            shape = RoundedCornerShape(4.dp),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
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
                            .background(MaterialTheme.colorScheme.outlineVariant),
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
                        .background(MaterialTheme.colorScheme.outlineVariant),
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
private fun TitleBarAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    shortcut: String? = null,
    icon: (@Composable (tint: Color) -> Unit)? = null,
    enabledIconTint: Color? = null,
) {
    val disabledTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val fg = if (enabled) MaterialTheme.colorScheme.onSurface else disabledTint
    val iconTint = when {
        !enabled -> disabledTint
        enabledIconTint != null -> enabledIconTint
        else -> MaterialTheme.colorScheme.onSurface
    }
    val tooltipText = when {
        icon != null && !shortcut.isNullOrBlank() -> "$label · $shortcut"
        icon != null -> label
        shortcut.isNullOrBlank() -> ""
        else -> "$label · $shortcut"
    }
    GlassTooltip(text = tooltipText) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .let { if (enabled) it.clickable { onClick() } else it },
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon(iconTint)
                }
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TitleBarToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    shortcut: String? = null,
    icon: (@Composable (tint: Color) -> Unit)? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipText = when {
        icon != null && !shortcut.isNullOrBlank() -> "$label · $shortcut"
        icon != null -> label
        shortcut.isNullOrBlank() -> ""
        else -> "$label · $shortcut"
    }
    GlassTooltip(text = tooltipText) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 2.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon(fg)
                }
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
