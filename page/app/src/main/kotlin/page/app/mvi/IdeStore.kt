package page.app.mvi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class IdeStore(initial: AppState = AppState()) {

    var layout by mutableStateOf(initial.layout)
        private set

    var chrome by mutableStateOf(initial.chrome)
        private set

    fun snapshot(): AppState = AppState(layout = layout, chrome = chrome)

    fun apply(next: AppState) {
        if (next.layout != layout) layout = next.layout
        if (next.chrome != chrome) chrome = next.chrome
    }

    fun updateLayout(transform: (LayoutState) -> LayoutState) {
        val next = transform(layout)
        if (next != layout) layout = next
    }

    fun updateChrome(transform: (ChromeState) -> ChromeState) {
        val next = transform(chrome)
        if (next != chrome) chrome = next
    }
}

internal class IdeDispatcher(
    private val store: IdeStore,
    private val effects: IdeEffectHandler,
) {
    val onEvent: (IdeEvent) -> Unit = { event ->
        val prev = store.snapshot()
        val next = reduce(prev, event)
        store.apply(next)
        effects.handle(event, prev, next)
    }
}
