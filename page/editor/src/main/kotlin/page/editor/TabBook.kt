package page.editor

import java.nio.file.Path

data class OpenTab(
    val path: Path,
    val text: String,
    val savedText: String = text,
    val caret: Int = 0,
    val history: EditHistory = EditHistory(),
    val isPinned: Boolean = false,
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

    fun appendTab(tab: OpenTab): TabBook {
        val existing = tabs.indexOfFirst { it.path == tab.path }
        if (existing >= 0) {
            return copy(activeIndex = existing)
        }
        return copy(tabs = tabs + tab, activeIndex = tabs.size)
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

    fun undoGroupOnNonActive(groupId: Long): TabBook {
        val updated = tabs.mapIndexed { idx, tab ->
            if (idx == activeIndex) return@mapIndexed tab
            val top = tab.history.past.lastOrNull() ?: return@mapIndexed tab
            if (top.groupId != groupId) return@mapIndexed tab
            val tabCurrent = EditSnapshot(tab.text, tab.caret, groupId)
            val (newHistory, restored) = tab.history.undo(tabCurrent) ?: return@mapIndexed tab
            tab.copy(text = restored.text, caret = restored.caret, history = newHistory)
        }
        return copy(tabs = updated)
    }

    fun redoGroupOnNonActive(groupId: Long): TabBook {
        val updated = tabs.mapIndexed { idx, tab ->
            if (idx == activeIndex) return@mapIndexed tab
            val top = tab.history.future.lastOrNull() ?: return@mapIndexed tab
            if (top.groupId != groupId) return@mapIndexed tab
            val tabCurrent = EditSnapshot(tab.text, tab.caret, groupId)
            val (newHistory, restored) = tab.history.redo(tabCurrent) ?: return@mapIndexed tab
            tab.copy(text = restored.text, caret = restored.caret, history = newHistory)
        }
        return copy(tabs = updated)
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

    fun markPathSaved(path: Path, savedText: String): TabBook {
        var changed = false
        val updated = tabs.map { t ->
            if (t.path == path && t.savedText != savedText) {
                changed = true
                t.copy(savedText = savedText)
            } else t
        }
        return if (changed) copy(tabs = updated) else this
    }

    fun togglePinned(index: Int): TabBook {
        if (index !in tabs.indices) return this
        val tab = tabs[index]
        val updated = tabs.toMutableList().also {
            it[index] = tab.copy(isPinned = !tab.isPinned)
        }
        return copy(tabs = updated)
    }

    fun closeMany(indices: Collection<Int>): TabBook {
        if (indices.isEmpty()) return this
        val drop = indices.toSet().filter { it in tabs.indices }.toSet()
        if (drop.isEmpty()) return this
        val newTabs = tabs.filterIndexed { i, _ -> i !in drop }
        if (newTabs.isEmpty()) return TabBook()
        val newActive = when {
            activeIndex !in tabs.indices -> -1
            activeIndex !in drop -> activeIndex - drop.count { it < activeIndex }
            else -> {
                val above = (activeIndex + 1 until tabs.size).firstOrNull { it !in drop }
                val below = (activeIndex - 1 downTo 0).firstOrNull { it !in drop }
                val target = above ?: below ?: return TabBook()
                target - drop.count { it < target }
            }
        }
        return TabBook(tabs = newTabs, activeIndex = newActive.coerceIn(0, newTabs.lastIndex))
    }

    fun closeOthers(keepIndex: Int, keepPinned: Boolean = true): TabBook {
        if (keepIndex !in tabs.indices) return this
        val toClose = tabs.indices.filter { i ->
            i != keepIndex && !(keepPinned && tabs[i].isPinned)
        }
        return closeMany(toClose)
    }

    fun closeToLeft(of: Int, keepPinned: Boolean = true): TabBook {
        if (of !in tabs.indices) return this
        val toClose = (0 until of).filter { i -> !(keepPinned && tabs[i].isPinned) }
        return closeMany(toClose)
    }

    fun closeToRight(of: Int, keepPinned: Boolean = true): TabBook {
        if (of !in tabs.indices) return this
        val toClose = ((of + 1) until tabs.size).filter { i -> !(keepPinned && tabs[i].isPinned) }
        return closeMany(toClose)
    }

    fun closeAll(keepPinned: Boolean = true): TabBook {
        val toClose = tabs.indices.filter { i -> !(keepPinned && tabs[i].isPinned) }
        return closeMany(toClose)
    }

    fun closeUnmodified(keepPinned: Boolean = true): TabBook {
        val toClose = tabs.indices.filter { i ->
            !tabs[i].dirty && !(keepPinned && tabs[i].isPinned)
        }
        return closeMany(toClose)
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
