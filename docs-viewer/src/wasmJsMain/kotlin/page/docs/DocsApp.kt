package page.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt
import page.shared.md.Document
import page.shared.md.MdParser

@Composable
fun DocsApp() {
    var doc by remember { mutableStateOf<Document?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            doc = MdParser.parse(fetchText("docs/sample.md"))
        } catch (e: Throwable) {
            error = e.message ?: "Failed to load docs."
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(DocsTheme.background)) {
        val d = doc
        when {
            error != null -> Text(error!!, color = DocsTheme.danger, modifier = Modifier.align(Alignment.Center))
            d == null -> Text("Loading…", color = DocsTheme.muted, modifier = Modifier.align(Alignment.Center))
            else -> DocsContent(d)
        }
    }
}

@Composable
private fun DocsContent(doc: Document) {
    val entries = remember(doc) { tocEntries(doc) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val offsets = remember(doc) { mutableStateMapOf<String, Int>() }
    var viewportCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var activeSlug by remember { mutableStateOf<String?>(null) }
    var suppressSpy by remember { mutableStateOf(false) }
    var initialNavDone by remember(doc) { mutableStateOf(false) }

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
        Sidebar(entries, activeSlug, onSelect = { slug -> replaceSlug(slug); scrollToSlug(slug) })
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
    }

    LaunchedEffect(offsets.size) {
        if (initialNavDone) return@LaunchedEffect
        val slug = currentSlug()
        if (slug.isEmpty()) {
            initialNavDone = true
            return@LaunchedEffect
        }
        if (offsets.containsKey(slug)) {
            scrollToSlug(slug)
            initialNavDone = true
        }
    }

    LaunchedEffect(Unit) {
        onHashChange {
            val slug = currentSlug()
            if (slug.isNotEmpty()) scrollToSlug(slug)
        }
    }

    LaunchedEffect(entries) {
        snapshotFlow { scrollState.value }.collect { sv ->
            if (suppressSpy) return@collect
            val active = entries.lastOrNull { (offsets[it.slug] ?: Int.MAX_VALUE) <= sv + 140 }?.slug
                ?: entries.firstOrNull()?.slug
            if (active != null && active != activeSlug) {
                activeSlug = active
                replaceSlug(active)
            }
        }
    }
}
