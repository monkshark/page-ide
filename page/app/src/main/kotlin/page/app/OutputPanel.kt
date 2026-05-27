package page.app

import page.runtime.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Cursor

class OutputPanelState {
    private val buffer = TerminalBuffer(maxLines = 8000)
    var lines by mutableStateOf<List<TerminalLine>>(buffer.snapshot)
        private set
    var running by mutableStateOf(false)
        private set
    var commandLabel by mutableStateOf<String?>(null)
        private set
    var workingDir by mutableStateOf<String?>(null)
        private set
    var lastExitCode by mutableStateOf<Int?>(null)
        private set
    var lastDurationMs by mutableStateOf<Long?>(null)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    fun onEvent(event: RunEvent) {
        when (event) {
            is RunEvent.Started -> {
                buffer.feed(Char(0x1B).toString() + "[2J")
                buffer.feed(formatStarted(event))
                commandLabel = buildString {
                    append(event.command)
                    if (event.args.isNotEmpty()) {
                        append(' ')
                        append(event.args.joinToString(" "))
                    }
                }
                workingDir = event.workingDir
                running = true
                lastExitCode = null
                lastDurationMs = null
                lastError = null
                lines = buffer.snapshot
            }
            is RunEvent.Stdout -> {
                buffer.feed(event.text)
                lines = buffer.snapshot
            }
            is RunEvent.Stderr -> {
                buffer.feed(event.text)
                lines = buffer.snapshot
            }
            is RunEvent.Exited -> {
                buffer.feed(formatExited(event))
                running = false
                lastExitCode = event.code
                lastDurationMs = event.durationMs
                lines = buffer.snapshot
            }
            is RunEvent.Failed -> {
                buffer.feed(formatFailed(event))
                running = false
                lastError = event.message
                lines = buffer.snapshot
            }
        }
    }

    fun clear() {
        buffer.feed(Char(0x1B).toString() + "[2J")
        lastExitCode = null
        lastDurationMs = null
        lastError = null
        lines = buffer.snapshot
    }

    private fun formatStarted(event: RunEvent.Started): String {
        val esc = Char(0x1B).toString()
        val dim = esc + "[2m"
        val reset = esc + "[0m"
        val argsText = if (event.args.isEmpty()) "" else " " + event.args.joinToString(" ")
        val cwd = event.workingDir?.let { " (cwd: $it)" } ?: ""
        return "${dim}> Run — ${event.command}$argsText$cwd$reset\n"
    }

    private fun formatExited(event: RunEvent.Exited): String {
        val esc = Char(0x1B).toString()
        val reset = esc + "[0m"
        val color = if (event.code == 0) esc + "[32m" else esc + "[31m"
        val label = if (event.code == 0) "Finished" else "Exited with error"
        val seconds = String.format("%.2f", event.durationMs / 1000.0)
        return "\n$color$label · exit ${event.code} · ${seconds}s$reset\n"
    }

    private fun formatFailed(event: RunEvent.Failed): String {
        val esc = Char(0x1B).toString()
        val reset = esc + "[0m"
        val red = esc + "[31m"
        return "${red}Failed — ${event.message}$reset\n"
    }
}

@Composable
fun OutputPanel(
    state: OutputPanelState,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onStop: () -> Unit,
    height: Dp,
    onResizeDelta: (Dp) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(height),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            VerticalResizeHandle(onDeltaDp = onResizeDelta)
            OutputHeader(
                state = state,
                onClose = onClose,
                onClear = onClear,
                onStop = onStop,
            )
            OutputBody(lines = state.lines, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun OutputHeader(
    state: OutputPanelState,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Output",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        val label = state.commandLabel
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        StatusChip(state = state)
        Spacer(Modifier.width(8.dp))
        HeaderAction(
            label = "Stop",
            enabled = state.running,
            onClick = onStop,
        )
        HeaderAction(label = "Clear", enabled = true, onClick = onClear)
        HeaderAction(label = "Close", enabled = true, onClick = onClose)
    }
}

@Composable
private fun StatusChip(state: OutputPanelState) {
    val (text, color) = when {
        state.running -> "Running" to MaterialTheme.colorScheme.primary
        state.lastError != null -> "Failed" to MaterialTheme.colorScheme.error
        state.lastExitCode != null -> {
            val code = state.lastExitCode
            val ms = state.lastDurationMs ?: 0L
            val seconds = String.format("%.2f", ms / 1000.0)
            val text = "exit $code · ${seconds}s"
            val tint = if (code == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            text to tint
        }
        else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    if (text.isNotEmpty()) {
        Surface(
            color = color.copy(alpha = 0.14f),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun HeaderAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    val fg = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Surface(
        color = Color.Transparent,
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

@Composable
private fun OutputBody(
    lines: List<TerminalLine>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val stickToBottom = remember { mutableStateOf(true) }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .collect { (value, max) ->
                stickToBottom.value = max == 0 || value >= max - 4
            }
    }
    LaunchedEffect(lines.size) {
        if (stickToBottom.value) scrollState.scrollTo(scrollState.maxValue)
    }
    Box(modifier = modifier) {
        SelectionContainer(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                for (line in lines) {
                    Text(
                        text = line.toOutputAnnotated(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun TerminalLine.toOutputAnnotated(): AnnotatedString = buildAnnotatedString {
    for (span in spans) {
        val deco = if (span.style.underline) TextDecoration.Underline else null
        val style = SpanStyle(
            color = span.style.fg ?: Color.Unspecified,
            background = span.style.bg ?: Color.Unspecified,
            fontWeight = if (span.style.bold) FontWeight.Bold else null,
            fontStyle = if (span.style.italic) FontStyle.Italic else null,
            textDecoration = deco,
        )
        pushStyle(style)
        append(span.text)
        pop()
    }
}

@Composable
private fun VerticalResizeHandle(onDeltaDp: (Dp) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
            .hoverable(interactionSource)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dy ->
                    onDeltaDp(with(density) { (-dy).toDp() })
                }
            }
            .background(
                if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else Color.Transparent,
            ),
    )
}
