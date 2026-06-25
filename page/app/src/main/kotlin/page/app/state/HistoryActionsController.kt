package page.app.state

import page.workspace.HistoryFile
import page.workspace.HistoryStore
import page.workspace.pushMru
import java.nio.file.Path

internal class HistoryActionsController(
    private val history: () -> HistoryFile,
    private val setHistory: (HistoryFile) -> Unit,
) {
    fun addRecentFile(path: Path) {
        setHistory(
            history().copy(
                recentFiles = pushMru(history().recentFiles, path.toString(), HistoryStore.MAX_RECENT_FILES),
            ),
        )
    }

    fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        setHistory(
            history().copy(
                searchHistory = pushMru(history().searchHistory, query, HistoryStore.MAX_SEARCH_HISTORY),
            ),
        )
    }

    fun addReplaceText(text: String) {
        if (text.isEmpty()) return
        setHistory(
            history().copy(
                replaceHistory = pushMru(history().replaceHistory, text, HistoryStore.MAX_REPLACE_HISTORY),
            ),
        )
    }
}
