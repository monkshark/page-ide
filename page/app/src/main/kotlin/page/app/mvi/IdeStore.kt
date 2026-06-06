package page.app.mvi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class IdeStore(initial: AppState = AppState()) {

    var layout by mutableStateOf(initial.layout)
        private set

    var chrome by mutableStateOf(initial.chrome)
        private set

    var tree by mutableStateOf(initial.tree)
        private set

    var editorLayout by mutableStateOf(initial.editorLayout)
        private set

    var editorScroll by mutableStateOf(initial.editorScroll)
        private set

    fun snapshot(): AppState = AppState(
        layout = layout,
        chrome = chrome,
        tree = tree,
        editorLayout = editorLayout,
        editorScroll = editorScroll,
    )

    fun apply(next: AppState) {
        if (next.layout != layout) layout = next.layout
        if (next.chrome != chrome) chrome = next.chrome
        if (next.tree != tree) tree = next.tree
        if (next.editorLayout != editorLayout) editorLayout = next.editorLayout
        if (next.editorScroll != editorScroll) editorScroll = next.editorScroll
    }

    fun updateLayout(transform: (LayoutState) -> LayoutState) {
        val next = transform(layout)
        if (next != layout) layout = next
    }

    fun updateChrome(transform: (ChromeState) -> ChromeState) {
        val next = transform(chrome)
        if (next != chrome) chrome = next
    }

    fun updateTree(transform: (TreeState) -> TreeState) {
        val next = transform(tree)
        if (next != tree) tree = next
    }

    fun updateEditorLayout(transform: (EditorLayoutState) -> EditorLayoutState) {
        val next = transform(editorLayout)
        if (next != editorLayout) editorLayout = next
    }

    fun updateEditorScroll(transform: (EditorScrollState) -> EditorScrollState) {
        val next = transform(editorScroll)
        if (next != editorScroll) editorScroll = next
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
