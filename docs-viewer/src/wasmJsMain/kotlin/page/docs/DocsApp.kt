package page.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            else -> Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(modifier = Modifier.align(Alignment.TopCenter)) { Article(d) }
            }
        }
    }
}
