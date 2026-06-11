package page.workspace

import page.runtime.*

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import page.editor.TreeNode
import java.awt.datatransfer.DataFlavor
import java.nio.file.Files
import java.nio.file.Path

private val RowHeight = 22.dp
private val ChevronSlot = 18.dp
private val IndentStep = 14.dp
private val EdgePadding = 8.dp

private val TreeCenterTight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TreeRow(
    node: TreeNode,
    isExpanded: Boolean,
    isSelected: Boolean,
    isRoot: Boolean,
    selection: Set<Path>,
    onToggle: (Path, Boolean) -> Unit,
    onPrimaryClick: (Path, ClickModifiers) -> Unit,
    onDoubleClick: (Path, ClickModifiers) -> Unit,
    onSecondaryDown: (Path) -> Unit,
    onCreateFile: (Path) -> Unit,
    onCreateFolder: (Path) -> Unit,
    onRename: (Path) -> Unit,
    onDeleteOne: (Path) -> Unit,
    onDeleteMany: (Set<Path>) -> Unit,
    onReveal: (Path) -> Unit,
    onCopyPath: (Path) -> Unit,
    onCopyRelativePath: (Path) -> Unit,
    onCutFiles: (Set<Path>) -> Unit,
    onCopyFiles: (Set<Path>) -> Unit,
    onPasteInto: (Path) -> Unit,
    onOpenMany: (Set<Path>) -> Unit,
    onOpenInAtlas: ((Path) -> Unit)? = null,
    dragState: TreeDragState? = null,
    onDragStartFrom: ((Path, TreeDragController.Mode) -> Unit)? = null,
    onDragRelease: (() -> Unit)? = null,
    onNodeDrop: ((Path, DragAndDropEvent) -> Boolean)? = null,
    onSpringLoadExpand: ((Path) -> Unit)? = null,
    isDropTarget: Boolean = false,
    isDropAllowed: Boolean = true,
    workspaceRoot: Path? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val name = node.path.fileName?.toString() ?: node.path.toString()
    val isCut = FileTreeClipboard.isCut(node.path)
    val dragActive = dragState?.isActive == true
    val isBeingDragged = dragActive && (dragState?.active?.sources?.contains(node.path) == true)
    val isDragHover = dragActive && dragState?.active?.hovered == node.path

    LaunchedEffect(dragActive, isHovered, node.path) {
        if (dragActive && isHovered) {
            dragState?.hover(node.path)
        }
    }

    if (dragActive && isDragHover && node.isDirectory && onSpringLoadExpand != null && !isExpanded) {
        LaunchedEffect(node.path, isExpanded, isDragHover, dragActive) {
            delay(500)
            if (dragState?.active?.hovered == node.path && dragState.isActive) {
                onSpringLoadExpand(node.path)
                dragState.hover(node.path)
            }
        }
    }

    val rowColor = when {
        isDropTarget && isDropAllowed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        isDropTarget && !isDropAllowed -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = when {
        isBeingDragged -> 0.45f
        isCut -> 0.45f
        else -> 1f
    })

    val createParent = if (node.isDirectory) node.path else node.path.parent
    val pasteTarget = createParent?.takeIf { Files.isDirectory(it) }

    val onNodeDropState = rememberUpdatedState(onNodeDrop)
    val rowDropTarget = remember(node.path, dragState) {
        object : DragAndDropTarget {
            private fun syncModeFromOs(event: DragAndDropEvent) {
                val native = runCatching { event.nativeEvent }.getOrNull() ?: return
                val action = runCatching {
                    when (native) {
                        is java.awt.dnd.DropTargetDragEvent -> native.dropAction
                        is java.awt.dnd.DropTargetDropEvent -> native.dropAction
                        else -> null
                    }
                }.getOrNull() ?: return
                val mode = when (action) {
                    java.awt.dnd.DnDConstants.ACTION_COPY -> TreeDragController.Mode.Copy
                    java.awt.dnd.DnDConstants.ACTION_MOVE -> TreeDragController.Mode.Move
                    else -> return
                }
                dragState?.setMode(mode)
            }
            override fun onEntered(event: DragAndDropEvent) {
                dragState?.hover(node.path)
                syncModeFromOs(event)
            }
            override fun onMoved(event: DragAndDropEvent) {
                dragState?.hover(node.path)
                syncModeFromOs(event)
            }
            override fun onChanged(event: DragAndDropEvent) {
                syncModeFromOs(event)
            }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                syncModeFromOs(event)
                return onNodeDropState.value?.invoke(node.path, event) ?: false
            }
        }
    }
    ContextMenuArea(
        items = {
            val effective: Set<Path> = if (node.path in selection) selection else setOf(node.path)
            val multi = effective.size > 1
            val copyable: Set<Path> = effective.filter { !isRoot || it != node.path }.toSet()
            val openable: Set<Path> = effective.filter { !Files.isDirectory(it) }.toSet()
            buildList {
                if (multi && openable.isNotEmpty()) {
                    add(ContextMenuItem("Open ${openable.size} files\tEnter") { onOpenMany(openable) })
                }
                if (!multi && createParent != null && Files.isDirectory(createParent)) {
                    add(ContextMenuItem("New file…") { onCreateFile(createParent) })
                    add(ContextMenuItem("New folder…") { onCreateFolder(createParent) })
                }
                if (copyable.isNotEmpty()) {
                    add(ContextMenuItem("Cut\tCtrl+X") { onCutFiles(copyable) })
                    add(ContextMenuItem("Copy\tCtrl+C") { onCopyFiles(copyable) })
                }
                if (pasteTarget != null && FileTreeClipboard.hasContentNow()) {
                    add(ContextMenuItem("Paste\tCtrl+V") { onPasteInto(pasteTarget) })
                }
                if (!isRoot) {
                    if (!multi) add(ContextMenuItem("Rename…\tF2") { onRename(node.path) })
                    val deleteLabel = if (multi) "Delete ${effective.size} items…\tDel" else "Delete…\tDel"
                    add(ContextMenuItem(deleteLabel) {
                        if (multi) onDeleteMany(effective) else onDeleteOne(node.path)
                    })
                }
                if (!multi) {
                    if (onOpenInAtlas != null && !node.isDirectory) {
                        add(ContextMenuItem("Open in Atlas") { onOpenInAtlas(node.path) })
                    }
                    add(ContextMenuItem("Show in Explorer") { onReveal(node.path) })
                    add(ContextMenuItem("Copy path") { onCopyPath(node.path) })
                    add(ContextMenuItem("Copy relative path") { onCopyRelativePath(node.path) })
                }
            }
        },
    ) {
        val rowMod = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .background(rowColor)
            .drawBehind {
                for (level in 1 until node.depth) {
                    val x = (EdgePadding + IndentStep * level + ChevronSlot / 2).toPx()
                    drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                }
            }
            .hoverable(interactionSource)
            .treeRowGestures(
                key = node.path,
                onPrimaryClick = { mods -> onPrimaryClick(node.path, mods) },
                onPrimaryDoubleClick = { mods -> onDoubleClick(node.path, mods) },
                onSecondaryDown = { onSecondaryDown(node.path) },
            )
            .let { base ->
                if (!isRoot && onDragStartFrom != null && onDragRelease != null) {
                    base.treeRowDragSource(
                        key = node.path,
                        path = node.path,
                        enabled = true,
                        selectionProvider = { selection },
                        rootProvider = { workspaceRoot },
                        callbacks = DragGestureCallbacks(
                            onDragStart = { p, mode -> onDragStartFrom(p, mode) },
                            onDragEnd = { _ -> onDragRelease() },
                        ),
                    )
                } else base
            }
            .let { base ->
                if (onNodeDrop != null) {
                    base.dragAndDropTarget(
                        shouldStartDragAndDrop = { event ->
                            runCatching {
                                val tx = event.awtTransferable
                                tx.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                                    tx.isDataFlavorSupported(TreeOutboundTransferable.PageInternalFlavor)
                            }.getOrDefault(false)
                        },
                        target = rowDropTarget,
                    )
                } else base
            }
            .padding(
                start = EdgePadding + IndentStep * node.depth,
                end = 8.dp,
            )
        Row(
            modifier = rowMod,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(ChevronSlot),
                contentAlignment = Alignment.Center,
            ) {
                if (node.isDirectory) {
                    Box(
                        modifier = Modifier
                            .hoverable(interactionSource)
                            .chevronToggleGesture(node.path) { recursive ->
                                onToggle(node.path, recursive)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        TreeChevron(expanded = isExpanded)
                    }
                }
            }
            Spacer(Modifier.width(2.dp))
            Text(
                text = name,
                style = LocalTextStyle.current.copy(
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (node.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    lineHeight = 12.sp,
                    lineHeightStyle = TreeCenterTight,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
