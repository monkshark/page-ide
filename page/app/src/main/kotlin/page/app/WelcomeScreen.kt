package page.app

import page.runtime.*
import page.workspace.*

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import page.app.ui.titleBarDrag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.core.PageIdentity
import page.ui.CompactContextMenuRepresentation
import page.ui.EditorFontFamily
import page.ui.IconContextMenuItem
import page.ui.Glass
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Path

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun WelcomeScreen(
    onOpenFolder: () -> Unit,
    onNewProject: () -> Unit,
    onDropPaths: (List<Path>) -> Unit = {},
    recents: List<Path> = emptyList(),
    onOpenRecent: (Path) -> Unit = {},
    onForgetRecent: (Path) -> Unit = {},
    onClearRecents: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var dragHovered by remember { mutableStateOf(false) }
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dragHovered = true }
            override fun onExited(event: DragAndDropEvent) { dragHovered = false }
            override fun onEnded(event: DragAndDropEvent) { dragHovered = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragHovered = false
                val tx = runCatching { event.awtTransferable }.getOrNull() ?: return false
                if (!tx.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                @Suppress("UNCHECKED_CAST")
                val files = runCatching {
                    tx.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                }.getOrNull() ?: return false
                if (files.isEmpty()) return false
                onDropPaths(files.map { it.toPath() })
                return true
            }
        }
    }
    val bg = if (dragHovered) Glass.colors.primarySoft else Glass.colors.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    runCatching {
                        event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    }.getOrDefault(false)
                },
                target = dropTarget,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 52.dp),
        ) {
            Wordmark()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${PageIdentity.ACRONYM}  ·  v${PageIdentity.VERSION}",
                fontFamily = EditorFontFamily,
                fontSize = 11.5.sp,
                letterSpacing = 0.6.sp,
                color = Glass.colors.muted,
            )

            Spacer(Modifier.height(26.dp))
            Divider()
            Spacer(Modifier.height(26.dp))

            SectionLabel("Start")
            ActionRow(
                icon = Icons.Outlined.FolderOpen,
                title = "Open folder…",
                accentHover = false,
                onClick = onOpenFolder,
            ) {}
            ActionRow(
                icon = Icons.Outlined.CreateNewFolder,
                title = "New project",
                accentHover = false,
                onClick = onNewProject,
            ) {}

            if (recents.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                SectionLabel("Recent")
                CompositionLocalProvider(
                    LocalContextMenuRepresentation provides CompactContextMenuRepresentation,
                ) {
                    recents.forEach { project ->
                        ContextMenuArea(
                            items = {
                                listOf(
                                    IconContextMenuItem(
                                        label = "Remove from recent",
                                        icon = Icons.Outlined.Close,
                                    ) { onForgetRecent(project) },
                                    IconContextMenuItem(
                                        label = "Clear recent projects",
                                        icon = Icons.Outlined.DeleteSweep,
                                        danger = true,
                                    ) { onClearRecents() },
                                )
                            },
                        ) {
                            ActionRow(
                                icon = Icons.Outlined.Folder,
                                title = project.fileName?.toString() ?: project.toString(),
                                accentHover = true,
                                onClick = { onOpenRecent(project) },
                            ) {
                                Text(
                                    text = displayPath(project),
                                    fontFamily = EditorFontFamily,
                                    fontSize = 11.5.sp,
                                    color = Glass.colors.faint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ArrowDownward,
                    contentDescription = null,
                    tint = Glass.colors.faint,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "Drop a folder anywhere to open",
                    fontFamily = EditorFontFamily,
                    fontSize = 11.sp,
                    color = Glass.colors.faint,
                )
                Spacer(Modifier.weight(1f))
                DocsLink()
            }
        }
        val chrome = page.app.ui.LocalWindowChrome.current
        if (chrome != null) {
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp).align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .titleBarDrag(chrome.window, chrome.onToggleMaximize),
                )
                page.app.ui.WindowControls(chrome, Modifier.fillMaxHeight())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun OpeningScreen(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Glass.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val spin = rememberInfiniteTransition(label = "opening")
            val angle by spin.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "spin",
            )
            val ringColor = Glass.colors.accent
            Box(
                modifier = Modifier.size(104.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize().rotate(angle)) {
                    val stroke = 3.dp.toPx()
                    drawArc(
                        color = ringColor,
                        startAngle = 0f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Image(
                    painter = painterResource("logo_transparent.svg"),
                    contentDescription = null,
                    modifier = Modifier.size(62.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = if (name.isNotBlank()) "Opening $name…" else "Opening…",
                fontFamily = EditorFontFamily,
                fontSize = 12.sp,
                color = Glass.colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val chrome = page.app.ui.LocalWindowChrome.current
        if (chrome != null) {
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp).align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .titleBarDrag(chrome.window, chrome.onToggleMaximize),
                )
                page.app.ui.WindowControls(chrome, Modifier.fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "PAGE",
            fontFamily = FontFamily.SansSerif,
            fontSize = 44.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
            color = Glass.colors.text,
        )
        Box(
            modifier = Modifier
                .padding(start = 9.dp, bottom = 7.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Glass.colors.accent),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocsLink() {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        text = "Documentation",
        fontFamily = EditorFontFamily,
        fontSize = 11.sp,
        color = if (hovered) Glass.colors.accent else Glass.colors.muted,
        modifier = Modifier
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
            .clickable {
                runCatching {
                    java.awt.Desktop.getDesktop().browse(java.net.URI("https://monkshark.github.io/page-ide/"))
                }
            },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = EditorFontFamily,
        fontSize = 10.sp,
        letterSpacing = 2.6.sp,
        color = Glass.colors.faint,
        modifier = Modifier.padding(start = 2.dp, bottom = 11.dp),
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Glass.colors.separator))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    accentHover: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val railHeight by animateDpAsState(if (hovered) 20.dp else 0.dp, tween(140))
    val hoverTint = if (accentHover) Glass.colors.accent else Glass.colors.primary
    val tileBg = if (hovered) hoverTint.copy(alpha = 0.14f) else Glass.colors.surface
    val tileBorder = if (hovered) hoverTint else Glass.colors.outline
    val iconTint = if (hovered) hoverTint else Glass.colors.muted
    val rowBg = if (hovered) Glass.colors.text.copy(alpha = 0.04f) else Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 3.dp)
                .width(2.dp)
                .height(railHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(Glass.colors.accent),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tileBg)
                    .border(1.dp, tileBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Text(
                text = title,
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                color = Glass.colors.text,
            )
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

private fun displayPath(project: Path): String {
    val raw = project.toString().replace('\\', '/')
    val home = runCatching { System.getProperty("user.home") }.getOrNull()?.replace('\\', '/')
    return if (home != null && raw.startsWith(home)) "~" + raw.removePrefix(home) else raw
}
