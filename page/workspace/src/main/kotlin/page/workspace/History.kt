package page.workspace

import page.runtime.*

import com.google.gson.reflect.TypeToken
import java.nio.file.Path

data class HistoryFile(
    val version: Int = 1,
    val recentFiles: List<String> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val replaceHistory: List<String> = emptyList(),
)

object HistoryStore {
    const val FILE_NAME = "history.json"
    const val MAX_RECENT_FILES = 50
    const val MAX_SEARCH_HISTORY = 20
    const val MAX_REPLACE_HISTORY = 20

    fun load(workspaceRoot: Path): HistoryFile {
        val type = object : TypeToken<HistoryFile>() {}.type
        return PageIdeStore.readType<HistoryFile>(workspaceRoot, FILE_NAME, type) ?: HistoryFile()
    }

    fun save(workspaceRoot: Path, file: HistoryFile) {
        PageIdeStore.write(workspaceRoot, FILE_NAME, file)
    }
}

fun pushMru(list: List<String>, value: String, max: Int): List<String> {
    if (value.isBlank()) return list
    val filtered = list.filterNot { it == value }
    val next = listOf(value) + filtered
    return if (next.size > max) next.take(max) else next
}
