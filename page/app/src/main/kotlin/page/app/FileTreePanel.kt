package page.app

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.editor.FileTree
import page.ui.CompactContextMenuRepresentation
import java.nio.file.Path

@Composable
fun FileTreePanel(
    root: Path?,
    expanded: Set<Path>,
    selection: Set<Path>,
    onToggle: (Path) -> Unit,
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
    revision: Int = 0,
    modifier: Modifier = Modifier,
) {
    val anchorState = remember(root) { mutableStateOf<Path?>(null) }
    val lastInteractedState = remember(root) { mutableStateOf<Path?>(null) }
    val focusRequester = remember { FocusRequester() }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
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
                    val nodes = remember(root, expanded, revision) { FileTree.listTree(root, expanded) }
                    val visibleOrder = remember(nodes) { nodes.map { it.path } }

                    fun activate(path: Path) {
                        val node = nodes.firstOrNull { it.path == path } ?: return
                        if (node.isDirectory) onToggle(path) else onOpenFile(path)
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

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .focusable()
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
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
                                        if (selection.isEmpty()) false
                                        else { onSelectionChange(emptySet()); true }
                                    }
                                    Key.Enter -> {
                                        val last = lastInteractedState.value ?: selection.firstOrNull()
                                        if (last != null) { activate(last); true } else false
                                    }
                                    else -> false
                                }
                            },
                    ) {
                        ContextMenuArea(
                            items = {
                                listOf(
                                    ContextMenuItem("New file…") { onCreateFile(root) },
                                    ContextMenuItem("New folder…") { onCreateFolder(root) },
                                )
                            },
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                                items(nodes, key = { it.path.toString() }) { node ->
                                    TreeRow(
                                        node = node,
                                        isExpanded = node.path in expanded,
                                        isSelected = node.path in selection,
                                        isRoot = node.path == root,
                                        selection = selection,
                                        onToggle = onToggle,
                                        onPrimaryClick = handlePrimaryClick,
                                        onDoubleClick = ::activate,
                                        onSecondaryDown = handleSecondaryDown,
                                        onCreateFile = onCreateFile,
                                        onCreateFolder = onCreateFolder,
                                        onRename = onRename,
                                        onDeleteOne = onDeleteOne,
                                        onDeleteMany = onDeleteMany,
                                        onReveal = onReveal,
                                        onCopyPath = onCopyPath,
                                        onCopyRelativePath = onCopyRelativePath,
                                    )
                                }
                            }
                        }
                    }
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
