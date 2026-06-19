package page.app

import page.runtime.*
import page.workspace.*

import com.google.gson.reflect.TypeToken
import page.editor.FileDocument
import page.editor.OpenTab
import page.editor.TabBook
import java.nio.file.Files
import java.nio.file.Path

data class SessionTabState(
    val path: String,
    val caret: Int = 0,
    val pinned: Boolean = false,
)

data class SessionTerminalTab(
    val name: String,
)

data class SessionScrollSnapshot(
    val vertical: Int = 0,
    val horizontal: Int = 0,
)

data class SessionPane(
    val tabs: List<SessionTabState> = emptyList(),
    val activeIndex: Int = -1,
)

data class SessionPoint(
    val x: Float = 0f,
    val y: Float = 0f,
)

data class SessionView(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val scale: Float = 0f,
)

data class SessionAtlasMap(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val scale: Float = 0f,
    val boxOffsets: Map<String, SessionPoint> = emptyMap(),
    val expandOrder: List<String> = emptyList(),
    val expandedDirs: List<String>? = null,
    val focusDir: String? = null,
    val hiddenDirs: List<String> = emptyList(),
    val mutedDirs: List<String> = emptyList(),
    val pinned: List<String> = emptyList(),
    val overviewDrill: List<String> = emptyList(),
    val overviewViews: Map<String, SessionView> = emptyMap(),
    val graphYaw: Float = 0.6f,
    val graphPitch: Float = 0.5f,
    val graphZoom: Float = 1f,
)

data class SessionFile(
    val version: Int = 1,
    val primary: SessionPane = SessionPane(),
    val secondary: SessionPane = SessionPane(),
    val focusedPane: String = "PRIMARY",
    val splitEnabled: Boolean = false,
    val splitOrientation: String = "HORIZONTAL",
    val splitRatio: Float = 0.5f,
    val sidebarWidth: Float = 260f,
    val problemsOpen: Boolean = false,
    val problemsHeight: Float = 220f,
    val problemsCollapsed: List<String> = emptyList(),
    val problemsFileOrder: List<String> = emptyList(),
    val todoOpen: Boolean = false,
    val todoHeight: Float = 220f,
    val todoCollapsed: List<String> = emptyList(),
    val todoFileOrder: List<String> = emptyList(),
    val terminalOpen: Boolean = false,
    val terminalHeight: Float = 240f,
    val terminalTabs: List<SessionTerminalTab> = emptyList(),
    val terminalActiveIndex: Int = -1,
    val outputOpen: Boolean = false,
    val outputHeight: Float = 220f,
    val foldedStartLinesByPath: Map<String, List<Int>> = emptyMap(),
    val expandedDirs: List<String> = emptyList(),
    val editorScrollByPath: Map<String, SessionScrollSnapshot> = emptyMap(),
    val atlasMap: SessionAtlasMap? = null,
    val atlasFollow: Boolean = false,
    val atlasViewTab: String = "RELATIONS",
)

internal fun restoreExpandedDirs(snapshot: List<String>): Set<Path> {
    if (snapshot.isEmpty()) return emptySet()
    return snapshot.mapNotNull { s ->
        val path = runCatching { Path.of(s) }.getOrNull() ?: return@mapNotNull null
        if (!Files.isDirectory(path)) null else path
    }.toSet()
}

object SessionStore {
    const val FILE_NAME = "session.json"

    fun load(workspaceRoot: Path): SessionFile? {
        val type = object : TypeToken<SessionFile>() {}.type
        return PageIdeStore.readType<SessionFile>(workspaceRoot, FILE_NAME, type)
    }

    fun save(workspaceRoot: Path, file: SessionFile) {
        PageIdeStore.write(workspaceRoot, FILE_NAME, file)
    }
}

internal fun paneSnapshot(pane: EditorPaneState): SessionPane =
    SessionPane(
        tabs = pane.book.tabs.map {
            SessionTabState(
                path = it.path.toString(),
                caret = it.caret,
                pinned = it.isPinned,
            )
        },
        activeIndex = pane.book.activeIndex,
    )

internal fun restoreTabBook(snapshot: SessionPane): TabBook {
    if (snapshot.tabs.isEmpty()) return TabBook()
    val restored = snapshot.tabs.mapNotNull { ts ->
        val path = runCatching { Path.of(ts.path) }.getOrNull() ?: return@mapNotNull null
        if (!Files.exists(path)) return@mapNotNull null
        val text = FileDocument.loadOrNull(path) ?: return@mapNotNull null
        val caret = ts.caret.coerceIn(0, text.length)
        OpenTab(path = path, text = text, savedText = text, caret = caret, isPinned = ts.pinned)
    }
    if (restored.isEmpty()) return TabBook()
    val active = snapshot.activeIndex.coerceIn(0, restored.lastIndex)
    return TabBook(tabs = restored, activeIndex = active)
}
