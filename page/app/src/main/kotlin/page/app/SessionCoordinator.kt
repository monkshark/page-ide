package page.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import page.app.state.EditorWorkspaceState
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.atlas.interaction.OverviewSelection
import page.atlas.render.AtlasViewState
import page.atlas.render.AtlasViewTab
import page.atlas.render.MapFilterState
import page.atlas.render.MapViewState
import page.atlas.render.OverviewViewState
import page.editor.SplitOrientation
import page.editor.SplitPaneState
import page.runtime.TerminalManager
import java.nio.file.Path

internal class SessionCoordinator(
    private val editorWorkspace: EditorWorkspaceState,
    private val layoutUiState: LayoutUiState,
    private val workspaceState: WorkspaceState,
    private val terminalManagerProvider: () -> TerminalManager,
    private val atlasMapView: MapViewState = MapViewState(),
    private val atlasView: AtlasViewState = AtlasViewState(),
    private val atlasOverviewState: OverviewViewState = OverviewViewState(),
) {
    private val terminalManager: TerminalManager get() = terminalManagerProvider()

    suspend fun restore(root: Path) {
        val session = withContext(Dispatchers.IO) { runCatching { SessionStore.load(root) }.getOrNull() }
        if (session == null) {
            editorWorkspace.foldByPath = emptyMap()
            return
        }
        val primaryBook = withContext(Dispatchers.IO) { restoreTabBook(session.primary) }
        val secondaryBook = withContext(Dispatchers.IO) { restoreTabBook(session.secondary) }
        val restoredExpanded = withContext(Dispatchers.IO) { restoreExpandedDirs(session.expandedDirs) }
        editorWorkspace.primaryPane = editorWorkspace.primaryPane.copy(book = primaryBook)
        editorWorkspace.secondaryPane = editorWorkspace.secondaryPane.copy(book = secondaryBook)
        editorWorkspace.focusedPane = runCatching { PaneSide.valueOf(session.focusedPane) }
            .getOrDefault(PaneSide.PRIMARY)
        editorWorkspace.splitEnabled = session.splitEnabled
        editorWorkspace.splitOrientation = runCatching { SplitOrientation.valueOf(session.splitOrientation) }
            .getOrDefault(SplitOrientation.HORIZONTAL)
        editorWorkspace.splitState = SplitPaneState(ratio = session.splitRatio.coerceIn(0.1f, 0.9f))
        layoutUiState.sidebarWidth = session.sidebarWidth.coerceIn(160f, 600f).dp
        layoutUiState.problemsOpen = session.problemsOpen
        layoutUiState.problemsHeight = session.problemsHeight.coerceIn(120f, 600f).dp
        layoutUiState.problemsCollapsed = session.problemsCollapsed.toSet()
        layoutUiState.problemsFileOrder = session.problemsFileOrder
        layoutUiState.todoOpen = session.todoOpen
        layoutUiState.todoHeight = session.todoHeight.coerceIn(120f, 600f).dp
        layoutUiState.todoCollapsed = session.todoCollapsed.toSet()
        layoutUiState.todoFileOrder = session.todoFileOrder
        layoutUiState.terminalOpen = session.terminalOpen
        layoutUiState.terminalHeight = session.terminalHeight.coerceIn(120f, 600f).dp
        layoutUiState.outputOpen = session.outputOpen
        layoutUiState.outputHeight = session.outputHeight.coerceIn(120f, 1200f).dp
        if (session.terminalTabs.isNotEmpty()) {
            terminalManager.restoreFrom(
                names = session.terminalTabs.map { it.name },
                activeIndex = session.terminalActiveIndex,
                autoStart = true,
            )
        }
        editorWorkspace.foldByPath = session.foldedStartLinesByPath.mapValues { it.value.toSet() }
        if (restoredExpanded.isNotEmpty()) workspaceState.expanded = restoredExpanded
        editorWorkspace.editorScrollByPath = session.editorScrollByPath
            .mapNotNull { (s, snap) ->
                val p = runCatching { Path.of(s) }.getOrNull() ?: return@mapNotNull null
                p to EditorScrollSnapshot(vertical = snap.vertical, horizontal = snap.horizontal)
            }
            .toMap()
        layoutUiState.atlasFollowActive = session.atlasFollow
        layoutUiState.atlasViewTab = runCatching { AtlasViewTab.valueOf(session.atlasViewTab) }
            .getOrDefault(AtlasViewTab.GRAPH)
        session.atlasMap?.let { restoreAtlasMap(it) }
    }

    private fun restoreAtlasMap(atlas: SessionAtlasMap) {
        atlasMapView.pan = Offset(atlas.panX, atlas.panY)
        atlasMapView.scale = atlas.scale.coerceIn(0f, 10f)
        atlasMapView.fitted = atlasMapView.scale > 0f
        atlasMapView.userOffsets.clear()
        for ((id, p) in atlas.boxOffsets) atlasMapView.userOffsets[id] = Offset(p.x, p.y)
        atlasMapView.expandOrder.clear()
        atlasMapView.expandOrder.addAll(atlas.expandOrder)
        atlasMapView.expandedDirs = atlas.expandedDirs?.toSet()
        atlasMapView.filter = MapFilterState(
            focusDir = atlas.focusDir,
            hiddenDirs = atlas.hiddenDirs.toSet(),
            mutedDirs = atlas.mutedDirs.toSet(),
        )
        atlasMapView.pinnedIds = atlas.pinned.toSet()
        atlasView.yaw = atlas.graphYaw
        atlasView.pitch = atlas.graphPitch
        atlasView.zoomUser = atlas.graphZoom
        atlasOverviewState.selection = OverviewSelection(drillPath = atlas.overviewDrill)
        atlasOverviewState.camera.savedViews.clear()
        for ((k, v) in atlas.overviewViews) {
            atlasOverviewState.camera.savedViews[k] = Offset(v.panX, v.panY) to v.scale
        }
        val scope = atlas.overviewDrill.joinToString(" ")
        atlas.overviewViews[scope]?.let {
            atlasOverviewState.camera.pan = Offset(it.panX, it.panY)
            atlasOverviewState.camera.scale = it.scale.coerceIn(0f, 10f)
            atlasOverviewState.camera.fitted = atlasOverviewState.camera.scale > 0f
        }
    }

    fun snapshot(): SessionFile = SessionFile(
        primary = paneSnapshot(editorWorkspace.primaryPane),
        secondary = paneSnapshot(editorWorkspace.secondaryPane),
        focusedPane = editorWorkspace.focusedPane.name,
        splitEnabled = editorWorkspace.splitEnabled,
        splitOrientation = editorWorkspace.splitOrientation.name,
        splitRatio = editorWorkspace.splitState.ratio,
        sidebarWidth = layoutUiState.sidebarWidth.value,
        problemsOpen = layoutUiState.problemsOpen,
        problemsHeight = layoutUiState.problemsHeight.value,
        problemsCollapsed = layoutUiState.problemsCollapsed.toList().sorted(),
        problemsFileOrder = layoutUiState.problemsFileOrder,
        todoOpen = layoutUiState.todoOpen,
        todoHeight = layoutUiState.todoHeight.value,
        todoCollapsed = layoutUiState.todoCollapsed.toList().sorted(),
        todoFileOrder = layoutUiState.todoFileOrder,
        terminalOpen = layoutUiState.terminalOpen,
        terminalHeight = layoutUiState.terminalHeight.value,
        terminalTabs = terminalManager.snapshotNames().map { SessionTerminalTab(name = it) },
        terminalActiveIndex = terminalManager.activeIndex(),
        outputOpen = layoutUiState.outputOpen,
        outputHeight = layoutUiState.outputHeight.value,
        foldedStartLinesByPath = editorWorkspace.foldByPath.mapValues { it.value.toList().sorted() },
        expandedDirs = workspaceState.expanded.map { it.toString() }.sorted(),
        editorScrollByPath = editorWorkspace.editorScrollByPath
            .mapKeys { it.key.toString() }
            .mapValues { SessionScrollSnapshot(vertical = it.value.vertical, horizontal = it.value.horizontal) },
        atlasMap = snapshotAtlasMap(),
        atlasFollow = layoutUiState.atlasFollowActive,
        atlasViewTab = layoutUiState.atlasViewTab.name,
    )

    private fun snapshotAtlasMap(): SessionAtlasMap = SessionAtlasMap(
        panX = atlasMapView.pan.x,
        panY = atlasMapView.pan.y,
        scale = atlasMapView.scale,
        boxOffsets = atlasMapView.userOffsets.mapValues { SessionPoint(it.value.x, it.value.y) },
        expandOrder = atlasMapView.expandOrder.toList(),
        expandedDirs = atlasMapView.expandedDirs?.toList()?.sorted(),
        focusDir = atlasMapView.filter.focusDir,
        hiddenDirs = atlasMapView.filter.hiddenDirs.toList().sorted(),
        mutedDirs = atlasMapView.filter.mutedDirs.toList().sorted(),
        pinned = atlasMapView.pinnedIds.toList().sorted(),
        overviewDrill = atlasOverviewState.selection.drillPath,
        overviewViews = snapshotOverviewViews(),
        graphYaw = atlasView.yaw,
        graphPitch = atlasView.pitch,
        graphZoom = atlasView.zoomUser,
    )

    private fun snapshotOverviewViews(): Map<String, SessionView> {
        val out = HashMap<String, SessionView>()
        for ((k, v) in atlasOverviewState.camera.savedViews) {
            out[k] = SessionView(v.first.x, v.first.y, v.second)
        }
        if (atlasOverviewState.camera.scale > 0f) {
            val key = atlasOverviewState.selection.drillPath.joinToString(" ")
            out[key] = SessionView(
                atlasOverviewState.camera.pan.x,
                atlasOverviewState.camera.pan.y,
                atlasOverviewState.camera.scale,
            )
        }
        return out
    }
}
