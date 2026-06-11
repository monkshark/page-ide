package page.workspace

import page.runtime.*

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import page.editor.FileTree
import page.editor.TreeNode
import page.ui.CompactContextMenuRepresentation
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun FileTreePanel(
    root: Path?,
    expanded: Set<Path>,
    selection: Set<Path>,
    onToggle: (Path, Boolean) -> Unit,
    onSelectionChange: (Set<Path>) -> Unit,
    onOpenFile: (Path) -> Unit,
    onCreateFile: (Path) -> Unit = {},
    onCreateFolder: (Path) -> Unit = {},
    onRename: (Path) -> Unit = {},
    onDeleteOne: (Path) -> Unit = {},
    onDeleteMany: (Set<Path>) -> Unit = {},
    onReveal: (Path) -> Unit = {},
    onCopyPath: (Path) -> Unit = {},
    onCopyRelativePath: (Path) -> Unit = {},
    onPasteInto: (Path) -> Unit = {},
    onUndo: () -> Boolean = { false },
    canUndo: Boolean = false,
    onDropPlan: (TreeDragController.DropPlan) -> Unit = {},
    onExternalDrop: (List<Path>, Path) -> Unit = { _, _ -> },
    onDropRejected: (String) -> Unit = {},
    onPanelFocusChanged: (Boolean) -> Unit = {},
    revision: Int = 0,
    pendingFocusTick: Int = 0,
    modifier: Modifier = Modifier,
) {
    val anchorState = remember(root) { mutableStateOf<Path?>(null) }
    val lastInteractedState = remember(root) { mutableStateOf<Path?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(pendingFocusTick) {
        if (pendingFocusTick > 0) {
            runCatching { focusRequester.requestFocus() }
        }
    }
    val dragState = rememberTreeDragState()
    val listState = rememberLazyListState()

    LaunchedEffect(dragState, listState) {
        fun edgeDelta(): Float {
            val active = dragState.active ?: return 0f
            val hovered = active.hovered ?: return 0f
            val layout = listState.layoutInfo
            val viewportHeight = layout.viewportEndOffset - layout.viewportStartOffset
            if (viewportHeight <= 0) return 0f
            val item = layout.visibleItemsInfo.firstOrNull { it.key == hovered.toString() } ?: return 0f
            val itemTop = (item.offset - layout.viewportStartOffset).toFloat()
            val itemBottom = itemTop + item.size
            val edge = 16f
            val topGap = itemTop
            val bottomGap = viewportHeight - itemBottom
            return when {
                topGap < edge -> -((edge - topGap).coerceIn(2f, edge) * 1.4f)
                bottomGap < edge -> ((edge - bottomGap).coerceIn(2f, edge) * 1.4f)
                else -> 0f
            }
        }
        snapshotFlow { edgeDelta() }.distinctUntilChanged().collectLatest { delta ->
            if (delta == 0f) return@collectLatest
            while (true) {
                listState.scrollBy(delta)
                delay(16)
                val next = edgeDelta()
                if (next == 0f) break
                if ((delta < 0f) != (next < 0f)) break
            }
        }
    }

    val panelCoordsState = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val mousePanelState = remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(dragState) {
        snapshotFlow { dragState.isActive }.collectLatest { active ->
            if (!active) {
                mousePanelState.value = null
                return@collectLatest
            }
            while (true) {
                val info = java.awt.MouseInfo.getPointerInfo()
                val coords = panelCoordsState.value
                if (info != null && coords != null && coords.isAttached) {
                    val win = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
                        ?: java.awt.Window.getWindows().firstOrNull { it.isShowing }
                    val winOrigin = win?.takeIf { it.isShowing }
                        ?.runCatching { locationOnScreen }?.getOrNull()
                    if (winOrigin != null) {
                        val mouseWinX = info.location.x - winOrigin.x
                        val mouseWinY = info.location.y - winOrigin.y
                        val panelOrigin = coords.positionInWindow()
                        mousePanelState.value = Offset(
                            mouseWinX - panelOrigin.x,
                            mouseWinY - panelOrigin.y,
                        )
                    }
                }
                delay(16)
            }
        }
    }

    val ctrlHeldState = remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val kfm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val dispatcher = java.awt.KeyEventDispatcher { event ->
            if (event.keyCode == java.awt.event.KeyEvent.VK_CONTROL) {
                when (event.id) {
                    java.awt.event.KeyEvent.KEY_PRESSED -> ctrlHeldState.value = true
                    java.awt.event.KeyEvent.KEY_RELEASED -> ctrlHeldState.value = false
                }
            }
            false
        }
        kfm.addKeyEventDispatcher(dispatcher)
        onDispose { kfm.removeKeyEventDispatcher(dispatcher) }
    }
    LaunchedEffect(dragState) {
        snapshotFlow { dragState.isActive to ctrlHeldState.value }
            .distinctUntilChanged()
            .collectLatest { (active, ctrl) ->
                if (active) {
                    dragState.setMode(
                        if (ctrl) TreeDragController.Mode.Copy
                        else TreeDragController.Mode.Move
                    )
                }
            }
    }

    val handleNodeDrop: (Path, DragAndDropEvent) -> Boolean = handleNodeDrop@ { nodePath, event ->
        val transferable = runCatching { event.awtTransferable }.getOrNull()
            ?: return@handleNodeDrop false
        val resolvedTarget = TreeDragController.resolveTargetFolder(nodePath)
            ?: root
            ?: return@handleNodeDrop false
        val internal = TreeOutboundTransferable.extractInternalPayload(transferable)
        if (internal != null) {
            val mode = dragState.active?.currentMode ?: internal.initialMode
            val decision = TreeDragController.plan(
                sources = internal.paths,
                targetNode = resolvedTarget,
                mode = mode,
                source = TreeDragController.Source.Internal,
                workspaceRoot = root,
            )
            dragState.cancel()
            if (decision is TreeDragController.Decision.Allow) {
                onDropPlan(decision.plan)
                return@handleNodeDrop true
            }
            val reason = (decision as? TreeDragController.Decision.Reject)?.reason
            if (reason == TreeDragController.Reason.SameParentSameMode) {
                return@handleNodeDrop false
            }
            val msg = when (reason) {
                TreeDragController.Reason.TargetReadOnly -> "Target folder is read-only"
                TreeDragController.Reason.SelfOrDescendant -> "Cannot drop into itself"
                TreeDragController.Reason.ContainsRoot -> "Cannot move workspace root"
                TreeDragController.Reason.TargetNotDirectory -> "Target is not a folder"
                else -> "Drop not allowed"
            }
            onDropRejected(msg)
            return@handleNodeDrop false
        }
        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return@handleNodeDrop false
        @Suppress("UNCHECKED_CAST")
        val files = runCatching {
            transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
        }.getOrNull() ?: return@handleNodeDrop false
        if (files.isEmpty()) return@handleNodeDrop false
        dragState.cancel()
        onExternalDrop(files.map { it.toPath() }, resolvedTarget)
        true
    }

    val panelDropTarget = remember(root) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val anchor = dragState.active?.hovered ?: root ?: return false
                return handleNodeDrop(anchor, event)
            }

            override fun onEntered(event: DragAndDropEvent) {
                val tx = runCatching { event.awtTransferable }.getOrNull()
                if (tx != null && TreeOutboundTransferable.extractInternalPayload(tx) != null) return
                if (!dragState.isActive) {
                    dragState.start(
                        sources = emptyList(),
                        mode = TreeDragController.Mode.Copy,
                        origin = TreeDragController.Source.External,
                        at = androidx.compose.ui.geometry.Offset.Zero,
                    )
                }
            }

            override fun onExited(event: DragAndDropEvent) {
                val a = dragState.active
                if (a?.origin == TreeDragController.Source.External) dragState.cancel()
            }
        }
    }

    Surface(
        modifier = modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                runCatching {
                    val tx = event.awtTransferable
                    tx.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                        tx.isDataFlavorSupported(TreeOutboundTransferable.PageInternalFlavor)
                }.getOrDefault(false)
            },
            target = panelDropTarget,
        ),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        CompositionLocalProvider(LocalContextMenuRepresentation provides CompactContextMenuRepresentation) {
            Column(modifier = Modifier.fillMaxSize()) {
                SectionHeader()
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp,
                )
                if (root == null) {
                    EmptyTreeHint()
                } else {
                    val rootDir = root
                    val nodes by produceState(
                        initialValue = listOf(TreeNode(rootDir, depth = 0, isDirectory = true)),
                        rootDir,
                        expanded,
                        revision,
                    ) {
                        value = withContext(Dispatchers.IO) { FileTree.listTree(rootDir, expanded) }
                    }
                    val visibleOrder = remember(nodes) { nodes.map { it.path } }

                    fun activate(path: Path, recursive: Boolean = false) {
                        val node = nodes.firstOrNull { it.path == path } ?: return
                        if (node.isDirectory) onToggle(path, recursive) else onOpenFile(path)
                    }

                    val handlePrimaryClick: (Path, ClickModifiers) -> Unit = { path, mods ->
                        focusRequester.requestFocus()
                        lastInteractedState.value = path
                        val newSel: Set<Path> = when {
                            mods.ctrl && mods.shift -> {
                                val range = FileTreeSelection.range(anchorState.value, path, visibleOrder)
                                LinkedHashSet<Path>(selection).apply { addAll(range) }
                            }
                            mods.shift -> FileTreeSelection.range(anchorState.value, path, visibleOrder)
                            mods.ctrl -> FileTreeSelection.toggle(selection, path)
                            else -> FileTreeSelection.single(path)
                        }
                        onSelectionChange(newSel)
                        if (!mods.shift && !mods.ctrl) {
                            anchorState.value = path
                        } else if (mods.ctrl && !mods.shift && anchorState.value == null) {
                            anchorState.value = path
                        }
                    }

                    val handleSecondaryDown: (Path) -> Unit = { path ->
                        focusRequester.requestFocus()
                        if (path !in selection) {
                            onSelectionChange(FileTreeSelection.single(path))
                            anchorState.value = path
                        }
                        lastInteractedState.value = path
                    }

                    val commitDrop: () -> Unit = {
                        dragState.cancel()
                    }

                    val handleDragStartFrom: (Path, TreeDragController.Mode) -> Unit = { grabbed, mode ->
                        val effective = TreeDragController.effectiveDragPaths(grabbed, selection)
                            .filter { it != root }
                        if (effective.isNotEmpty()) {
                            if (grabbed !in selection) {
                                onSelectionChange(FileTreeSelection.single(grabbed))
                            }
                            dragState.start(
                                sources = effective,
                                mode = mode,
                                origin = TreeDragController.Source.Internal,
                                at = androidx.compose.ui.geometry.Offset.Zero,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { panelCoordsState.value = it }
                            .focusRequester(focusRequester)
                            .onFocusChanged { onPanelFocusChanged(it.hasFocus) }
                            .focusable()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.changedToDown() } && event.buttons.isPrimaryPressed) {
                                        focusRequester.requestFocus()
                                    }
                                }
                            }
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                fun tryRenameSelected(): Boolean {
                                    if (selection.size != 1) return false
                                    val target = selection.first()
                                    if (target == root) return false
                                    onRename(target)
                                    return true
                                }
                                fun copyableSelection(): List<Path> {
                                    if (selection.isEmpty()) return emptyList()
                                    return selection.filter { it != root }
                                }
                                fun pasteTarget(): Path? {
                                    val candidate = lastInteractedState.value
                                        ?: selection.firstOrNull()
                                        ?: root
                                    return when {
                                        Files.isDirectory(candidate) -> candidate
                                        else -> candidate.parent
                                    }
                                }
                                if (e.isCtrlPressed && !e.isShiftPressed) {
                                    when (e.key) {
                                        Key.C -> {
                                            val paths = copyableSelection()
                                            if (paths.isEmpty()) return@onPreviewKeyEvent false
                                            FileTreeClipboard.writeCopy(paths)
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.X -> {
                                            val paths = copyableSelection()
                                            if (paths.isEmpty()) return@onPreviewKeyEvent false
                                            FileTreeClipboard.writeCut(paths)
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.V -> {
                                            val target = pasteTarget() ?: return@onPreviewKeyEvent false
                                            onPasteInto(target)
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.Z -> {
                                            if (canUndo) return@onPreviewKeyEvent onUndo()
                                        }
                                        else -> {}
                                    }
                                }
                                when (e.key) {
                                    Key.Delete -> {
                                        if (selection.isEmpty()) false
                                        else {
                                            if (selection.size > 1) onDeleteMany(selection)
                                            else onDeleteOne(selection.first())
                                            true
                                        }
                                    }
                                    Key.Escape -> {
                                        when {
                                            dragState.isActive -> { dragState.cancel(); true }
                                            selection.isEmpty() -> false
                                            else -> { onSelectionChange(emptySet()); true }
                                        }
                                    }
                                    Key.Enter -> {
                                        if (selection.size > 1) {
                                            val files = selection.filter { !Files.isDirectory(it) }
                                            if (files.isNotEmpty()) { files.forEach { onOpenFile(it) }; true } else false
                                        } else {
                                            val last = lastInteractedState.value ?: selection.firstOrNull()
                                            if (last != null) { activate(last, recursive = e.isCtrlPressed); true } else false
                                        }
                                    }
                                    Key.F2 -> tryRenameSelected()
                                    Key.F6 -> if (e.isShiftPressed) tryRenameSelected() else false
                                    else -> false
                                }
                            },
                    ) {
                        ContextMenuArea(
                            items = {
                                buildList {
                                    add(ContextMenuItem("New file…") { onCreateFile(root) })
                                    add(ContextMenuItem("New folder…") { onCreateFolder(root) })
                                    if (FileTreeClipboard.hasContentNow()) {
                                        add(ContextMenuItem("Paste\tCtrl+V") { onPasteInto(root) })
                                    }
                                    if (canUndo) {
                                        add(ContextMenuItem("Undo\tCtrl+Z") { onUndo() })
                                    }
                                }
                            },
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp),
                            ) {
                                items(nodes, key = { it.path.toString() }) { node ->
                                    val active = dragState.active
                                    val targetFolder = TreeDragController.resolveTargetFolder(node.path)
                                    val isHoveredTarget = active != null && active.hovered == node.path
                                    val allowed = if (active != null && targetFolder != null) {
                                        TreeDragController.plan(
                                            sources = active.sources,
                                            targetNode = node.path,
                                            mode = active.currentMode,
                                            source = TreeDragController.Source.Internal,
                                            workspaceRoot = root,
                                        ) is TreeDragController.Decision.Allow
                                    } else true
                                    TreeRow(
                                        node = node,
                                        isExpanded = node.path in expanded,
                                        isSelected = node.path in selection,
                                        isRoot = node.path == root,
                                        selection = selection,
                                        onToggle = onToggle,
                                        onPrimaryClick = handlePrimaryClick,
                                        onDoubleClick = { path, mods -> activate(path, recursive = mods.ctrl) },
                                        onSecondaryDown = handleSecondaryDown,
                                        onCreateFile = onCreateFile,
                                        onCreateFolder = onCreateFolder,
                                        onRename = onRename,
                                        onDeleteOne = onDeleteOne,
                                        onDeleteMany = onDeleteMany,
                                        onReveal = onReveal,
                                        onCopyPath = onCopyPath,
                                        onCopyRelativePath = onCopyRelativePath,
                                        onCutFiles = { paths ->
                                            FileTreeClipboard.writeCut(paths.toList())
                                        },
                                        onCopyFiles = { paths ->
                                            FileTreeClipboard.writeCopy(paths.toList())
                                        },
                                        onPasteInto = onPasteInto,
                                        onOpenMany = { paths ->
                                            paths.filter { !Files.isDirectory(it) }.forEach(onOpenFile)
                                        },
                                        dragState = dragState,
                                        onDragStartFrom = handleDragStartFrom,
                                        onDragRelease = commitDrop,
                                        onNodeDrop = handleNodeDrop,
                                        onSpringLoadExpand = { p -> onToggle(p, false) },
                                        isDropTarget = isHoveredTarget,
                                        isDropAllowed = allowed,
                                        workspaceRoot = root,
                                    )
                                }
                            }
                        }
                        val mp = mousePanelState.value
                        if (mp != null) {
                            FloatingDragLabel(
                                dragState = dragState,
                                modifier = Modifier.offset {
                                    IntOffset(
                                        (mp.x + 12f).toInt(),
                                        (mp.y + 18f).toInt(),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingDragLabel(
    dragState: TreeDragState,
    modifier: Modifier = Modifier,
) {
    val active = dragState.active ?: return
    if (active.sources.isEmpty()) return
    val first = active.sources.first().fileName?.toString() ?: active.sources.first().toString()
    val label = if (active.sources.size > 1) "$first  +${active.sources.size - 1}" else first
    val isCopy = active.currentMode == TreeDragController.Mode.Copy
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
            )
            if (isCopy) {
                Spacer(Modifier.width(6.dp))
                Row(
                    modifier = Modifier
                        .height(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "COPY",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = androidx.compose.material3.LocalTextStyle.current.copy(
                            fontSize = 9.sp,
                            lineHeight = 9.sp,
                            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader() {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PROJECT",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun EmptyTreeHint() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No folder open",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Press Ctrl+Shift+O",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}
