package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.rememberDialogState
import java.nio.file.Path
import page.ui.GlassTheme

@Composable
fun RunConfigDialog(
    state: RunConfigsState,
    workspaceRoot: Path?,
    onSave: (RunConfigsState) -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(width = 760.dp, height = 480.dp)
    var draft by remember(state) { mutableStateOf(state) }
    var selected by remember(state) { mutableStateOf(state.activeId ?: state.configs.firstOrNull()?.id) }

    fun selectedConfig(): RunConfig? = draft.configs.firstOrNull { it.id == selected }

    fun updateSelected(transform: (RunConfig) -> RunConfig) {
        val current = selectedConfig() ?: return
        draft = draft.update(transform(current))
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "Run Configurations",
        resizable = true,
        undecorated = true,
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) false
            else when (event.key) {
                Key.Escape -> { onDismiss(); true }
                else -> false
            }
        },
    ) {
        GlassTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        ) {
            Column(Modifier.fillMaxSize()) {
                DialogTitleBar(title = "Run Configurations", onClose = onDismiss)
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.Top,
                ) {
                    ConfigList(
                        configs = draft.configs,
                        selectedId = selected,
                        onSelect = { selected = it },
                        modifier = Modifier.width(240.dp).fillMaxHeight(),
                    )
                    Spacer(Modifier.width(12.dp))
                    ConfigForm(
                        config = selectedConfig(),
                        onChange = { updated -> updateSelected { updated } },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                ListActions(
                    canDelete = selectedConfig() != null,
                    onAdd = {
                        val cfg = RunConfig(
                            id = "cfg-${System.nanoTime()}",
                            name = "New configuration",
                            command = "",
                            args = emptyList(),
                            workingDir = workspaceRoot?.toString(),
                        )
                        draft = draft.add(cfg)
                        selected = cfg.id
                    },
                    onDuplicate = {
                        val current = selectedConfig() ?: return@ListActions
                        val cfg = current.copy(
                            id = "cfg-${System.nanoTime()}",
                            name = "${current.name} (copy)",
                        )
                        draft = draft.add(cfg)
                        selected = cfg.id
                    },
                    onDelete = {
                        val current = selectedConfig() ?: return@ListActions
                        draft = draft.remove(current.id)
                        selected = draft.activeId
                    },
                )
                Spacer(Modifier.height(12.dp))
                DialogFooter(
                    onCancel = onDismiss,
                    onSave = {
                        val sanitized = draft.copy(activeId = selected ?: draft.activeId)
                        onSave(sanitized)
                    },
                )
                }
            }
        }
        }
    }
}

@Composable
private fun DialogWindowScope.DialogTitleBar(
    title: String,
    onClose: () -> Unit,
) {
    WindowDraggableArea(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.clickable { onClose() }.padding(horizontal = 2.dp),
            ) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}

@Composable
private fun ConfigList(
    configs: List<RunConfig>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(4.dp)) {
            if (configs.isEmpty()) {
                Text(
                    text = "No configurations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
            for (cfg in configs) {
                val isSelected = cfg.id == selectedId
                val bg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent
                val fg = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .clickable { onSelect(cfg.id) },
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = cfg.name.ifBlank { "(unnamed)" },
                            style = MaterialTheme.typography.labelMedium,
                            color = fg,
                        )
                        val subtitle = if (cfg.command.isBlank()) "no command set"
                        else "${cfg.command} ${cfg.args.joinToString(" ")}".trim()
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigForm(
    config: RunConfig?,
    onChange: (RunConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (config == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Select a configuration or add a new one",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val scroll = rememberScrollState()
    Column(modifier = modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FormField(
            label = "Name",
            value = config.name,
            onValueChange = { onChange(config.copy(name = it)) },
        )
        FormField(
            label = "Command",
            value = config.command,
            placeholder = "e.g. python · npx · cargo",
            onValueChange = { onChange(config.copy(command = it)) },
            mono = true,
        )
        FormField(
            label = "Arguments (space-separated)",
            value = config.args.joinToString(" "),
            placeholder = "e.g. run main.py",
            onValueChange = { raw -> onChange(config.copy(args = parseArgs(raw))) },
            mono = true,
        )
        FormField(
            label = "Working directory",
            value = config.workingDir.orEmpty(),
            placeholder = "Leave empty to use workspace root",
            onValueChange = { onChange(config.copy(workingDir = it.ifBlank { null })) },
            mono = true,
        )
        FormField(
            label = "Environment variables (KEY=VALUE, one per line)",
            value = envToText(config.env),
            placeholder = "PORT=8080\nNODE_ENV=development",
            onValueChange = { raw -> onChange(config.copy(env = parseEnv(raw))) },
            mono = true,
            minLines = 3,
        )
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    mono: Boolean = false,
    minLines: Int = 1,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        var text by remember(value) { mutableStateOf(TextFieldValue(value)) }
        LaunchedEffect(value) {
            if (text.text != value) text = TextFieldValue(value)
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
        ) {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                if (text.text.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onValueChange(it.text)
                    },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = minLines == 1,
                    minLines = minLines,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ListActions(
    canDelete: Boolean,
    onAdd: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ActionButton(label = "+ Add", enabled = true, onClick = onAdd)
        ActionButton(label = "Duplicate", enabled = canDelete, onClick = onDuplicate)
        ActionButton(label = "Delete", enabled = canDelete, onClick = onDelete)
    }
}

@Composable
private fun DialogFooter(onCancel: () -> Unit, onSave: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        ActionButton(label = "Cancel", enabled = true, onClick = onCancel)
        ActionButton(label = "Save", enabled = true, emphasized = true, onClick = onSave)
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> Color.Transparent
        emphasized -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        emphasized -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .let { if (enabled) it.clickable { onClick() } else it },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

internal fun parseArgs(raw: String): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    var inSingle = false
    var inDouble = false
    var i = 0
    while (i < raw.length) {
        val ch = raw[i]
        when {
            ch == '\\' && i + 1 < raw.length -> {
                sb.append(raw[i + 1])
                i += 2
                continue
            }
            ch == '\'' && !inDouble -> inSingle = !inSingle
            ch == '"' && !inSingle -> inDouble = !inDouble
            ch.isWhitespace() && !inSingle && !inDouble -> {
                if (sb.isNotEmpty()) {
                    out.add(sb.toString())
                    sb.clear()
                }
            }
            else -> sb.append(ch)
        }
        i++
    }
    if (sb.isNotEmpty()) out.add(sb.toString())
    return out
}

internal fun parseEnv(raw: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (line in raw.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
        val eq = trimmed.indexOf('=')
        if (eq <= 0) continue
        val key = trimmed.substring(0, eq).trim()
        val value = trimmed.substring(eq + 1).trim()
        if (key.isNotEmpty()) out[key] = value
    }
    return out
}

internal fun envToText(env: Map<String, String>): String =
    env.entries.joinToString("\n") { "${it.key}=${it.value}" }
