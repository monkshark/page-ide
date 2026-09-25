package page.atlas.render

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import page.atlas.export.CodeBundle
import page.atlas.export.CodeBundles
import page.atlas.graph.GraphSlice
import page.ui.EditorFontFamily

@Composable
internal fun CodeBundlePanel(
    slice: GraphSlice,
    focusId: String,
    visibleIds: Set<String>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paths = remember(slice) { CodeBundles.paths(slice) }
    var selected by remember(slice, focusId) { mutableStateOf(CodeBundles.candidates(slice, focusId, true, false)) }
    var result by remember { mutableStateOf<Pair<Set<String>, CodeBundle>?>(null) }
    var copied by remember(selected) { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedOnly by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf(false) }
    val matching = remember(paths, query, selectedOnly, selected) {
        paths.entries.filter { it.value.contains(query, true) && (!selectedOnly || it.key in selected) }.sortedBy { it.value }
    }
    val bundle = result?.takeIf { it.first == selected }?.second
    val clipboard = LocalClipboardManager.current
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(slice, selected) {
        result = null
        val prepared = withContext(Dispatchers.IO) { CodeBundles.build(slice, selected) }
        result = selected to prepared
    }
    Box(modifier.fillMaxSize().background(colors.background)
        .focusRequester(focus).focusable().onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onClose(); true } else false
        }.clickable {}, contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxSize(), color = colors.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Code context", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Choose a scope, review the saved code, then copy it together.", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                    ExploreAction("Back to graph", description = "Close", onClick = onClose)
                }
                Divider()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val fileList: @Composable (Modifier) -> Unit = { pane ->
                        Column(pane.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("1  CHOOSE FILES", fontSize = 10.sp, letterSpacing = 1.sp, color = colors.onSurfaceVariant)
                            val focused = setOf(focusId).intersect(paths.keys)
                            val dependencies = CodeBundles.candidates(slice, focusId, true, false)
                            val dependents = CodeBundles.candidates(slice, focusId, false, true)
                            val visible = visibleIds.intersect(paths.keys)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ExploreAction("Selected file", selected = selected == focused, modifier = Modifier.weight(1f)) { selected = focused }
                                ExploreAction("Dependencies", selected = selected == dependencies, modifier = Modifier.weight(1f)) { selected = dependencies }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ExploreAction("Dependents", selected = selected == dependents, modifier = Modifier.weight(1f)) { selected = dependents }
                                ExploreAction("Visible graph", selected = selected == visible, modifier = Modifier.weight(1f)) { selected = visible }
                            }
                            Divider()
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${selected.size} files selected", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f))
                                ExploreAction("Selected", selected = selectedOnly, description = "Show selected files only") { selectedOnly = !selectedOnly }
                            }
                            Box(Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(6.dp))
                                .border(1.dp, colors.outlineVariant, RoundedCornerShape(6.dp)).padding(10.dp)) {
                                if (query.isEmpty()) Text("Find files to include…", fontSize = 12.sp, color = colors.onSurfaceVariant)
                                BasicTextField(query, { query = it }, singleLine = true,
                                    textStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface), cursorBrush = SolidColor(colors.primary),
                                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Find files to include" })
                            }
                            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                                if (matching.isEmpty()) item {
                                    Text("No matching files", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                                }
                                items(matching, key = { it.key }) { (id, path) ->
                                    Row(Modifier.fillMaxWidth().toggleable(id in selected, role = Role.Checkbox) {
                                        selected = if (it) selected + id else selected - id
                                    }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(id in selected, onCheckedChange = null)
                                        Text(path, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                    val preview: @Composable (Modifier) -> Unit = { pane ->
                        Column(pane.background(colors.background).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("2  REVIEW CONTEXT · SAVED CONTENTS", fontSize = 10.sp, letterSpacing = 1.sp, color = colors.onSurfaceVariant)
                            if (bundle == null) Text("Preparing context…", fontSize = 12.sp)
                            else {
                                val notices = bundle.files.count { it.notice != null }
                                if (notices > 0) Text("$notices files shortened or unavailable. Details are included below.",
                                    fontSize = 11.sp, color = colors.error)
                                SelectionContainer(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                                    Text(bundle.text.take(12_000), fontFamily = EditorFontFamily, fontSize = 11.sp,
                                        lineHeight = 18.sp, color = colors.onSurface)
                                }
                                if (bundle.text.length > 12_000) Text("Preview shortened. Copy includes the full prepared context.",
                                    fontSize = 11.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                    if (maxWidth >= 700.dp) Row(Modifier.fillMaxSize()) {
                        fileList(Modifier.width(300.dp).fillMaxHeight())
                        Divider(vertical = true)
                        preview(Modifier.weight(1f).fillMaxHeight())
                    } else Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExploreAction("Choose files", selected = !review, modifier = Modifier.weight(1f)) { review = false }
                            ExploreAction("Review context", selected = review, modifier = Modifier.weight(1f)) { review = true }
                        }
                        Divider()
                        if (review) preview(Modifier.fillMaxWidth().weight(1f))
                        else fileList(Modifier.fillMaxWidth().weight(1f))
                    }
                }
                Divider()
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(bundle?.let { "${it.includedCount} readable file${if (it.includedCount == 1) "" else "s"} · ${it.characterCount.toString().reversed().chunked(3).joinToString(",").reversed()} characters" }
                        ?: "Reading selected files…", fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    ExploreAction(if (copied) "Copied" else "Copy context", enabled = bundle != null && bundle.includedCount > 0, accent = true) {
                        bundle?.let { clipboard.setText(AnnotatedString(it.text)); copied = true }
                    }
                }
            }
        }
    }
}
