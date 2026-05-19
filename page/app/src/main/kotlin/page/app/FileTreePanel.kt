package page.app

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.editor.FileTree
import page.editor.TreeNode
import page.ui.CompactContextMenuRepresentation
import java.nio.file.Files
import java.nio.file.Path

private val RowHeight = 22.dp
private val ChevronWidth = 14.dp
private val IndentStep = 14.dp
private val EdgePadding = 8.dp

private val CenterTight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

@Composable
fun FileTreePanel(
    root: Path?,
    expanded: Set<Path>,
    selectedFile: Path?,
    onToggle: (Path) -> Unit,
    onOpenFile: (Path) -> Unit,
    onCreateFile: (Path) -> Unit = {},
    onCreateFolder: (Path) -> Unit = {},
    onRename: (Path) -> Unit = {},
    onDelete: (Path) -> Unit = {},
    onReveal: (Path) -> Unit = {},
    onCopyPath: (Path) -> Unit = {},
    onCopyRelativePath: (Path) -> Unit = {},
    revision: Int = 0,
    modifier: Modifier = Modifier,
) {
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
                                    isSelected = node.path == selectedFile,
                                    isRoot = node.path == root,
                                    onToggle = onToggle,
                                    onOpenFile = onOpenFile,
                                    onCreateFile = onCreateFile,
                                    onCreateFolder = onCreateFolder,
                                    onRename = onRename,
                                    onDelete = onDelete,
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

@Composable
private fun SectionHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PROJECT",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun TreeRow(
    node: TreeNode,
    isExpanded: Boolean,
    isSelected: Boolean,
    isRoot: Boolean,
    onToggle: (Path) -> Unit,
    onOpenFile: (Path) -> Unit,
    onCreateFile: (Path) -> Unit,
    onCreateFolder: (Path) -> Unit,
    onRename: (Path) -> Unit,
    onDelete: (Path) -> Unit,
    onReveal: (Path) -> Unit,
    onCopyPath: (Path) -> Unit,
    onCopyRelativePath: (Path) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val name = node.path.fileName?.toString() ?: node.path.toString()

    val rowColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    val createParent = if (node.isDirectory) node.path else node.path.parent
    ContextMenuArea(
        items = {
            buildList {
                if (createParent != null && Files.isDirectory(createParent)) {
                    add(ContextMenuItem("New file…") { onCreateFile(createParent) })
                    add(ContextMenuItem("New folder…") { onCreateFolder(createParent) })
                }
                if (!isRoot) {
                    add(ContextMenuItem("Rename…") { onRename(node.path) })
                    add(ContextMenuItem("Delete…") { onDelete(node.path) })
                }
                add(ContextMenuItem("Reveal in file manager") { onReveal(node.path) })
                add(ContextMenuItem("Copy path") { onCopyPath(node.path) })
                add(ContextMenuItem("Copy relative path") { onCopyRelativePath(node.path) })
            }
        },
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .background(rowColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                if (node.isDirectory) onToggle(node.path) else onOpenFile(node.path)
            }
            .padding(
                start = EdgePadding + IndentStep * node.depth,
                end = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(ChevronWidth),
            contentAlignment = Alignment.Center,
        ) {
            if (node.isDirectory) {
                Text(
                    text = if (isExpanded) "▾" else "▸",
                    style = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        lineHeightStyle = CenterTight,
                    ),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = name,
            style = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = if (node.isDirectory) FontWeight.Medium else FontWeight.Normal,
                lineHeight = 12.sp,
                lineHeightStyle = CenterTight,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
