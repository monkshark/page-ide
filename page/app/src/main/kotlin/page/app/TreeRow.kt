package page.app

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import page.editor.TreeNode
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

@Composable
internal fun TreeRow(
    node: TreeNode,
    isExpanded: Boolean,
    isSelected: Boolean,
    isRoot: Boolean,
    selection: Set<Path>,
    onToggle: (Path) -> Unit,
    onPrimaryClick: (Path, ClickModifiers) -> Unit,
    onDoubleClick: (Path) -> Unit,
    onSecondaryDown: (Path) -> Unit,
    onCreateFile: (Path) -> Unit,
    onCreateFolder: (Path) -> Unit,
    onRename: (Path) -> Unit,
    onDeleteOne: (Path) -> Unit,
    onDeleteMany: (Set<Path>) -> Unit,
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
            val effective: Set<Path> = if (node.path in selection) selection else setOf(node.path)
            val multi = effective.size > 1
            buildList {
                if (!multi && createParent != null && Files.isDirectory(createParent)) {
                    add(ContextMenuItem("New file…") { onCreateFile(createParent) })
                    add(ContextMenuItem("New folder…") { onCreateFolder(createParent) })
                }
                if (!isRoot) {
                    if (!multi) add(ContextMenuItem("Rename…") { onRename(node.path) })
                    val deleteLabel = if (multi) "Delete ${effective.size} items…" else "Delete…"
                    add(ContextMenuItem(deleteLabel) {
                        if (multi) onDeleteMany(effective) else onDeleteOne(node.path)
                    })
                }
                if (!multi) {
                    add(ContextMenuItem("Reveal in file manager") { onReveal(node.path) })
                    add(ContextMenuItem("Copy path") { onCopyPath(node.path) })
                    add(ContextMenuItem("Copy relative path") { onCopyRelativePath(node.path) })
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .background(rowColor)
                .treeRowGestures(
                    key = node.path,
                    onPrimaryClick = { mods -> onPrimaryClick(node.path, mods) },
                    onPrimaryDoubleClick = { onDoubleClick(node.path) },
                    onSecondaryDown = { onSecondaryDown(node.path) },
                )
                .padding(
                    start = EdgePadding + IndentStep * node.depth,
                    end = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(ChevronSlot),
                contentAlignment = Alignment.Center,
            ) {
                if (node.isDirectory) {
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) { onToggle(node.path) },
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
                    color = MaterialTheme.colorScheme.onSurface,
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
