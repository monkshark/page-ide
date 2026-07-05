package page.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import page.shared.docs.DocRef
import page.shared.docs.buildDocHash
import page.shared.docs.buildDocTree
import page.shared.docs.koVariant
import page.shared.docs.parseDocHash
import page.shared.docs.parseDocIndex
import page.shared.docs.variantFor
import page.shared.md.Document
import page.shared.md.MdParser

private const val LANDING = "guides/overview.md"

private fun landingPath(index: List<String>): String =
    if (LANDING in index) LANDING else index.firstOrNull() ?: "sample.md"

@Composable
fun DocsApp() {
    var index by remember { mutableStateOf<List<String>?>(null) }
    var english by remember { mutableStateOf(false) }
    var ref by remember { mutableStateOf(parseDocHash(currentRawHash())) }

    LaunchedEffect(Unit) {
        index = try {
            parseDocIndex(fetchText("docs-index.json"))
        } catch (e: Throwable) {
            listOf("sample.md")
        }
    }
    LaunchedEffect(Unit) {
        onHashChange { ref = parseDocHash(currentRawHash()) }
    }

    Box(modifier = Modifier.fillMaxSize().background(DocsTheme.background)) {
        val idx = index
        if (idx == null) {
            Text("Loading…", color = DocsTheme.muted, modifier = Modifier.align(Alignment.Center))
        } else {
            val available = remember(idx) { idx.toSet() }
            val tree = remember(idx) { buildDocTree(idx) }
            val requested = koVariant(ref.path ?: landingPath(idx))
            val path = variantFor(requested, english, available)

            Row(modifier = Modifier.fillMaxSize()) {
                DocTreeSidebar(
                    tree = tree,
                    activePath = requested,
                    english = english,
                    onSelect = { p ->
                        ref = DocRef(p, null)
                        replaceRawHash(buildDocHash(p, null))
                    },
                    onToggleLang = { english = !english },
                )
                key(path) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        DocBody(
                            path = path,
                            basePath = requested,
                            initialHeading = ref.heading,
                            onHeadingActive = { slug -> replaceRawHash(buildDocHash(requested, slug)) },
                        )
                    }
                }
            }
        }
        ThemeToggle(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
    }
}

@Composable
private fun DocBody(
    path: String,
    basePath: String,
    initialHeading: String?,
    onHeadingActive: (String) -> Unit,
) {
    var doc by remember { mutableStateOf<Document?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        doc = null
        error = null
        try {
            doc = MdParser.parse(fetchText("docs/$path"))
        } catch (e: Throwable) {
            error = "Failed to load $path"
        }
    }
    val d = doc
    when {
        error != null -> Box(Modifier.fillMaxSize()) {
            Text(error!!, color = DocsTheme.danger, modifier = Modifier.align(Alignment.Center))
        }
        d == null -> Box(Modifier.fillMaxSize()) {
            Text("Loading…", color = DocsTheme.muted, modifier = Modifier.align(Alignment.Center))
        }
        else -> DocContent(d, basePath, initialHeading, onHeadingActive)
    }
}

@Composable
private fun DocContent(
    doc: Document,
    basePath: String,
    initialHeading: String?,
    onHeadingActive: (String) -> Unit,
) {
    val entries = remember(doc) { tocEntries(doc) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val offsets = remember(doc) { mutableStateMapOf<String, Int>() }
    var viewportCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var activeSlug by remember { mutableStateOf<String?>(null) }
    var suppressSpy by remember { mutableStateOf(false) }
    var initialNavDone by remember(doc) { mutableStateOf(initialHeading == null) }

    fun scrollToSlug(slug: String) {
        val target = offsets[slug] ?: return
        scope.launch {
            suppressSpy = true
            activeSlug = slug
            scrollState.animateScrollTo(target)
            suppressSpy = false
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .onGloballyPositioned { viewportCoords = it },
        ) {
            Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    Article(doc) { slug, coords ->
                        val vp = viewportCoords
                        if (vp != null && vp.isAttached && coords.isAttached) {
                            val y = vp.localPositionOf(coords, Offset.Zero).y
                            offsets[slug] = (scrollState.value + y).roundToInt()
                        }
                    }
                }
            }
        }
        if (entries.size >= 3) HeadingRail(entries, activeSlug) { slug ->
            replaceRawHash(buildDocHash(basePath, slug))
            scrollToSlug(slug)
        }
    }

    LaunchedEffect(offsets.size) {
        if (initialNavDone) return@LaunchedEffect
        val slug = initialHeading ?: return@LaunchedEffect
        if (offsets.containsKey(slug)) {
            scrollToSlug(slug)
            initialNavDone = true
        }
    }

    LaunchedEffect(entries) {
        snapshotFlow { scrollState.value }.collect { sv ->
            if (suppressSpy) return@collect
            val active = entries.lastOrNull { (offsets[it.slug] ?: Int.MAX_VALUE) <= sv + 140 }?.slug
                ?: entries.firstOrNull()?.slug
            if (active != null && active != activeSlug) {
                activeSlug = active
                onHeadingActive(active)
            }
        }
    }
}

@Composable
private fun RowScope.HeadingRail(entries: List<TocEntry>, activeSlug: String?, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier.width(220.dp).fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(top = 56.dp, bottom = 28.dp, start = 8.dp, end = 20.dp),
    ) {
        Text(
            "On this page",
            color = DocsTheme.faint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp, bottom = 12.dp),
        )
        for (e in entries) {
            val active = e.slug == activeSlug
            Text(
                e.title,
                color = if (active) DocsTheme.primary else DocsTheme.muted,
                fontSize = if (e.level == 1) 13.sp else 12.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth()
                    .clickable { onSelect(e.slug) }
                    .padding(start = (10 + (e.level - 1) * 12).dp, top = 4.dp, bottom = 4.dp, end = 6.dp),
            )
        }
    }
}

@Composable
private fun ThemeToggle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DocsTheme.surface)
            .border(1.dp, DocsTheme.outline, RoundedCornerShape(8.dp))
            .clickable { DocsTheme.dark = !DocsTheme.dark }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            if (DocsTheme.dark) "☾  Dark" else "☀  Light",
            color = DocsTheme.muted,
            fontSize = 12.sp,
        )
    }
}
