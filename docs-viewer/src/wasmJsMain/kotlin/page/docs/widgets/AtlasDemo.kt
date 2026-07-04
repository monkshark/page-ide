package page.docs.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.docs.DocsTheme
import page.docs.fetchText
import page.shared.graph.GraphSlice
import page.shared.graph.GraphSnapshot
import page.shared.widget.GraphCanvas
import page.shared.widget.GraphColors

private val docsGraphColors = GraphColors(
    edge = DocsTheme.faint,
    edgeStrong = DocsTheme.accent,
    active = DocsTheme.warn,
    workspace = DocsTheme.primary,
    external = DocsTheme.muted,
    focus = DocsTheme.accent,
    label = DocsTheme.text,
)

@Composable
fun AtlasDemo(args: Map<String, String>) {
    var slice by remember { mutableStateOf<GraphSlice?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            slice = GraphSnapshot.parse(fetchText("atlas-snapshot.json"))
        } catch (e: Throwable) {
            failed = true
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(440.dp).padding(vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, DocsTheme.outline, RoundedCornerShape(14.dp))
            .background(DocsTheme.background),
    ) {
        val s = slice
        when {
            failed -> Text("Graph snapshot unavailable.", color = DocsTheme.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            s == null -> Text("Loading graph…", color = DocsTheme.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            s.nodes.isEmpty() -> Text("No dependencies found.", color = DocsTheme.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            else -> {
                GraphCanvas(s, docsGraphColors, Modifier.fillMaxSize())
                Text(
                    "${s.nodes.size} files · ${s.edges.size} imports · hover a node",
                    color = DocsTheme.faint,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                )
            }
        }
    }
}
