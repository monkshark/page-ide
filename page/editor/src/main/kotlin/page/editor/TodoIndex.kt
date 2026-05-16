package page.editor

import java.util.concurrent.CopyOnWriteArrayList

class TodoIndex {

    private val perFile = LinkedHashMap<String, List<TodoItem>>()
    private val listeners = CopyOnWriteArrayList<(TodoIndex) -> Unit>()
    private val lock = Any()

    fun setFile(uri: String, items: List<TodoItem>) {
        synchronized(lock) {
            val existing = perFile[uri].orEmpty()
            if (existing == items) return
            if (items.isEmpty()) perFile.remove(uri) else perFile[uri] = items
        }
        notifyListeners()
    }

    fun removeFile(uri: String) {
        val changed = synchronized(lock) { perFile.remove(uri) != null }
        if (changed) notifyListeners()
    }

    fun replaceAll(byFile: Map<String, List<TodoItem>>) {
        synchronized(lock) {
            perFile.clear()
            for ((uri, items) in byFile) {
                if (items.isNotEmpty()) perFile[uri] = items
            }
        }
        notifyListeners()
    }

    fun all(): List<TodoItem> = synchronized(lock) {
        perFile.values.flatten().sortedWith(itemOrder)
    }

    fun forFile(uri: String): List<TodoItem> = synchronized(lock) {
        perFile[uri].orEmpty()
    }

    fun fileCount(): Int = synchronized(lock) { perFile.size }

    fun size(): Int = synchronized(lock) { perFile.values.sumOf { it.size } }

    fun addListener(listener: (TodoIndex) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (TodoIndex) -> Unit) {
        listeners -= listener
    }

    private fun notifyListeners() {
        for (l in listeners) l(this)
    }

    private companion object {
        val itemOrder: Comparator<TodoItem> = compareBy(
            { it.uri },
            { it.line },
            { it.column },
        )
    }
}
