package page.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

class TerminalTab(
    val id: String,
    initialName: String,
    val controller: TerminalController,
) {
    var name: String by mutableStateOf(initialName)
        private set

    fun rename(newName: String) {
        val trimmed = newName.trim()
        name = if (trimmed.isEmpty()) "Terminal" else trimmed
    }
}

class TerminalManager(
    private val workspaceRoot: Path,
    private val scope: CoroutineScope,
) {
    private var nextSeq: Int = 1

    val availableShells: List<ShellOption> = TerminalSession.detectShells()

    var tabs: List<TerminalTab> by mutableStateOf(emptyList())
        private set

    var activeId: String? by mutableStateOf(null)
        private set

    val activeTab: TerminalTab? get() = tabs.firstOrNull { it.id == activeId }

    fun newTab(name: String? = null, autoStart: Boolean = true): TerminalTab {
        val seq = nextSeq++
        val id = "term-$seq"
        val displayName = name?.trim().takeUnless { it.isNullOrEmpty() } ?: "Terminal $seq"
        val controller = TerminalController(workspaceRoot, scope)
        val tab = TerminalTab(id, displayName, controller)
        tabs = tabs + tab
        activeId = id
        if (autoStart) controller.start()
        return tab
    }

    fun selectTab(id: String) {
        if (tabs.any { it.id == id }) activeId = id
    }

    fun closeTab(id: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        tab.controller.stop()
        val idx = tabs.indexOf(tab)
        val remaining = tabs.filter { it.id != id }
        tabs = remaining
        if (activeId == id) {
            activeId = when {
                remaining.isEmpty() -> null
                idx < remaining.size -> remaining[idx].id
                else -> remaining.last().id
            }
        }
    }

    fun renameTab(id: String, newName: String) {
        tabs.firstOrNull { it.id == id }?.rename(newName)
    }

    fun closeAll() {
        for (tab in tabs) tab.controller.stop()
        tabs = emptyList()
        activeId = null
    }

    fun snapshotNames(): List<String> = tabs.map { it.name }

    fun activeIndex(): Int = tabs.indexOfFirst { it.id == activeId }

    fun restoreFrom(names: List<String>, activeIndex: Int, autoStart: Boolean = true) {
        if (tabs.isNotEmpty()) return
        if (names.isEmpty()) return
        for (name in names) newTab(name = name, autoStart = autoStart)
        if (activeIndex in tabs.indices) activeId = tabs[activeIndex].id
    }
}
