package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import page.core.PageIdentity
import page.editor.FileDocument
import page.ui.GlassTheme
import java.awt.Cursor
import java.nio.file.Path

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    var path: Path? by remember { mutableStateOf(null) }
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var rootDir: Path? by remember { mutableStateOf(null) }
    var expanded: Set<Path> by remember { mutableStateOf(emptySet()) }
    var sidebarWidth: Dp by remember { mutableStateOf(260.dp) }

    val openFile: (java.awt.Frame) -> Unit = { parent ->
        FileDialogs.open(parent)?.let { picked ->
            FileDocument.loadOrNull(picked)?.let { text ->
                value = TextFieldValue(text)
                path = picked
            }
        }
    }
    val saveFile: (java.awt.Frame) -> Unit = { parent ->
        val target = path ?: FileDialogs.saveAs(parent)
        if (target != null) {
            FileDocument.save(target, value.text)
            path = target
        }
    }
    val openFolder: (java.awt.Frame) -> Unit = { parent ->
        FileDialogs.openDirectory(parent)?.let { picked ->
            rootDir = picked
            expanded = setOf(picked)
        }
    }
    val loadFromTree: (Path) -> Unit = { picked ->
        FileDocument.loadOrNull(picked)?.let { text ->
            value = TextFieldValue(text)
            path = picked
        }
    }
    val toggleExpanded: (Path) -> Unit = { p ->
        expanded = if (p in expanded) expanded - setOf(p) else expanded + setOf(p)
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = windowTitle(path),
    ) {
        val frame = window
        GlassTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                            when {
                                event.key == Key.O && event.isShiftPressed -> { openFolder(frame); true }
                                event.key == Key.O -> { openFile(frame); true }
                                event.key == Key.S -> { saveFile(frame); true }
                                else -> false
                            }
                        } else false
                    },
                color = MaterialTheme.colorScheme.background,
            ) {
                Shell(
                    path = path,
                    value = value,
                    onValueChange = { value = it },
                    rootDir = rootDir,
                    expanded = expanded,
                    sidebarWidth = sidebarWidth,
                    onSidebarResize = { delta ->
                        sidebarWidth = (sidebarWidth + delta).coerceIn(160.dp, 600.dp)
                    },
                    onToggle = toggleExpanded,
                    onOpenFile = loadFromTree,
                )
            }
        }
    }
}

private fun windowTitle(path: Path?): String {
    val name = path?.fileName?.toString() ?: "untitled"
    return "$name — ${PageIdentity.NAME}"
}

@Composable
private fun Shell(
    path: Path?,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    rootDir: Path?,
    expanded: Set<Path>,
    sidebarWidth: Dp,
    onSidebarResize: (Dp) -> Unit,
    onToggle: (Path) -> Unit,
    onOpenFile: (Path) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TitleBar(path = path)
        Row(modifier = Modifier.fillMaxSize()) {
            FileTreePanel(
                root = rootDir,
                expanded = expanded,
                selectedFile = path,
                onToggle = onToggle,
                onOpenFile = onOpenFile,
                modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
            )
            ResizeHandle(onSidebarResize)
            EditorPanel(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun TitleBar(path: Path?) {
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
        }
    }
}

@Composable
private fun ResizeHandle(onDeltaDp: (Dp) -> Unit) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(6.dp)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
            .hoverable(interactionSource)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dx ->
                    onDeltaDp(with(density) { dx.toDp() })
                }
            }
            .background(
                if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else Color.Transparent,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}
