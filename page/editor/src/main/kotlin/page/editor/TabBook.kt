package page.editor

import java.nio.file.Path

data class OpenTab(
    val path: Path,
    val text: String,
    val savedText: String = text,
    val caret: Int = 0,
    val history: EditHistory = EditHistory(),
) {
    val dirty: Boolean get() = text != savedText
}

data class TabBook(
    val tabs: List<OpenTab> = emptyList(),
    val activeIndex: Int = -1,
) {
    val active: OpenTab? get() = tabs.getOrNull(activeIndex)

    fun openOrFocus(path: Path, text: String): TabBook {
        val existing = tabs.indexOfFirst { it.path == path }
        if (existing >= 0) {
            return copy(activeIndex = existing)
        }
        val newTab = OpenTab(path = path, text = text, savedText = text, caret = 0)
        return copy(tabs = tabs + newTab, activeIndex = tabs.size)
    }

    fun close(index: Int): TabBook {
        if (index !in tabs.indices) return this
        val newTabs = tabs.toMutableList().also { it.removeAt(index) }
        if (newTabs.isEmpty()) return TabBook()
        val newActive = when {
            index < activeIndex -> activeIndex - 1
            index == activeIndex -> index.coerceAtMost(newTabs.lastIndex)
            else -> activeIndex
        }
        return TabBook(tabs = newTabs, activeIndex = newActive)
    }

    fun closeActive(): TabBook =
        if (activeIndex in tabs.indices) close(activeIndex) else this

    fun activate(index: Int): TabBook =
        if (index in tabs.indices) copy(activeIndex = index) else this

    fun updateActive(text: String, caret: Int): TabBook {
        if (activeIndex !in tabs.indices) return this
        val current = tabs[activeIndex]
        if (current.text == text && current.caret == caret) return this
        val updated = tabs.toMutableList().also {
            it[activeIndex] = current.copy(text = text, caret = caret)
        }
        return copy(tabs = updated)
    }

    fun pushHistoryOnActive(prev: EditSnapshot): TabBook {
        if (activeIndex !in tabs.indices) return this
        val current = tabs[activeIndex]
        val newHistory = current.history.pushBeforeChange(prev)
        if (newHistory === current.history) return this
        val updated = tabs.toMutableList().also {
            it[activeIndex] = current.copy(history = newHistory)
        }
        return copy(tabs = updated)
    }

    fun undoOnActive(current: EditSnapshot): Pair<TabBook, EditSnapshot>? {
        if (activeIndex !in tabs.indices) return null
        val tab = tabs[activeIndex]
        val (newHistory, restored) = tab.history.undo(current) ?: return null
        val updated = tabs.toMutableList().also {
            it[activeIndex] = tab.copy(
                text = restored.text,
                caret = restored.caret,
                history = newHistory,
            )
        }
        return copy(tabs = updated) to restored
    }

    fun redoOnActive(current: EditSnapshot): Pair<TabBook, EditSnapshot>? {
        if (activeIndex !in tabs.indices) return null
        val tab = tabs[activeIndex]
        val (newHistory, restored) = tab.history.redo(current) ?: return null
        val updated = tabs.toMutableList().also {
            it[activeIndex] = tab.copy(
                text = restored.text,
                caret = restored.caret,
                history = newHistory,
            )
        }
        return copy(tabs = updated) to restored
    }

    fun markActiveSaved(): TabBook {
        if (activeIndex !in tabs.indices) return this
        val current = tabs[activeIndex]
        if (current.savedText == current.text) return this
        val updated = tabs.toMutableList().also {
            it[activeIndex] = current.copy(savedText = current.text)
        }
        return copy(tabs = updated)
    }

    fun move(from: Int, to: Int): TabBook {
        if (from == to) return this
        if (from !in tabs.indices || to !in tabs.indices) return this
        val newTabs = tabs.toMutableList()
        val item = newTabs.removeAt(from)
        newTabs.add(to, item)
        val newActive = when {
            activeIndex == from -> to
            from < to && activeIndex in (from + 1)..to -> activeIndex - 1
            from > to && activeIndex in to..(from - 1) -> activeIndex + 1
            else -> activeIndex
        }
        return TabBook(tabs = newTabs, activeIndex = newActive)
    }
}
