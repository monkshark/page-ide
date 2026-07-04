package page.docs.widgets

import androidx.compose.runtime.Composable
import page.shared.md.WidgetRef

object PageWidgets {
    private val registry = mutableMapOf<String, @Composable (Map<String, String>) -> Unit>()

    fun register(name: String, content: @Composable (Map<String, String>) -> Unit) {
        registry[name] = content
    }

    fun has(name: String): Boolean = registry.containsKey(name)

    @Composable
    fun Render(ref: WidgetRef) {
        registry[ref.name]?.invoke(ref.args)
    }
}

fun registerPageWidgets() {
    PageWidgets.register("AtlasDemo") { args -> AtlasDemo(args) }
}
