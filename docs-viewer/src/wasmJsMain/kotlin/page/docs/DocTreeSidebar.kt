package page.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.shared.docs.DocTreeNode

@Composable
fun DocTreeSidebar(
    tree: List<DocTreeNode>,
    activePath: String,
    english: Boolean,
    onSelect: (String) -> Unit,
    onToggleLang: () -> Unit,
) {
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    Column(
        modifier = Modifier.width(260.dp).fillMaxHeight()
            .background(DocsTheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp, horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("PAGE Docs", color = DocsTheme.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            LangToggle(english, onToggleLang)
        }
        for (node in tree) TreeNode(node, activePath, collapsed, depth = 0, onSelect = onSelect)
    }
}

@Composable
private fun LangToggle(english: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(7.dp))
            .border(1.dp, DocsTheme.outline, RoundedCornerShape(7.dp))
            .clickable { onToggle() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("KO", color = if (!english) DocsTheme.primary else DocsTheme.faint, fontSize = 11.sp, fontWeight = if (!english) FontWeight.SemiBold else FontWeight.Normal)
        Text("EN", color = if (english) DocsTheme.primary else DocsTheme.faint, fontSize = 11.sp, fontWeight = if (english) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun TreeNode(
    node: DocTreeNode,
    activePath: String,
    collapsed: MutableMap<String, Boolean>,
    depth: Int,
    onSelect: (String) -> Unit,
) {
    val indent = (6 + depth * 12).dp
    if (node.isLeaf) {
        val active = node.path == activePath
        Text(
            node.name,
            color = if (active) DocsTheme.primary else DocsTheme.muted,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.fillMaxWidth()
                .clickable { node.path?.let(onSelect) }
                .padding(start = indent, top = 4.dp, bottom = 4.dp, end = 6.dp),
        )
    } else {
        val key = "$depth/${node.name}"
        val isCollapsed = collapsed[key] == true
        Text(
            (if (isCollapsed) "▸  " else "▾  ") + node.name,
            color = DocsTheme.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
                .clickable { collapsed[key] = !isCollapsed }
                .padding(start = indent, top = 8.dp, bottom = 4.dp, end = 6.dp),
        )
        if (!isCollapsed) for (child in node.children) TreeNode(child, activePath, collapsed, depth + 1, onSelect)
    }
}
