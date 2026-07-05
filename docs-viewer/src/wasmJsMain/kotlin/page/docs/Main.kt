package page.docs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import page.docs.widgets.PageWidgets
import page.docs.widgets.registerPageWidgets
import page.shared.md.WidgetRef

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    registerPageWidgets()
    ComposeViewport(document.body!!) {
        MaterialTheme {
            DocsApp()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@JsExport
fun mountPageWidget(containerId: String, name: String) {
    val el = document.getElementById(containerId) as? HTMLElement ?: return
    ComposeViewport(el) {
        MaterialTheme {
            PageWidgets.Render(WidgetRef(name, emptyMap()))
        }
    }
}
