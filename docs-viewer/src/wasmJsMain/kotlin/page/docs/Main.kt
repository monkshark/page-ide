package page.docs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import page.docs.widgets.registerPageWidgets

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    registerPageWidgets()
    ComposeViewport(document.body!!) {
        MaterialTheme {
            DocsApp()
        }
    }
}
