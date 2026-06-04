package page.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.app.*
import page.app.state.EditorWorkspaceState
import page.editor.FileKind
import page.editor.FileKinds
import page.editor.SyntaxLexers
import page.language.LspController
import page.language.LspRouter
import page.lsp.RenameWorkspaceEdit
import java.nio.file.Path

@Composable
internal fun PaneRegion(
    pane: EditorPaneState,
    side: PaneSide,
    editor: EditorWorkspaceState,
    lspRouter: LspRouter,
    isFocused: Boolean,
    onPaneFocus: (PaneSide) -> Unit,
    onCloseTab: (PaneSide, Int) -> Unit,
    onQueryChange: (PaneSide, String) -> Unit,
    onReplaceChange: (PaneSide, String) -> Unit,
    onToggleCase: (PaneSide) -> Unit,
    onSearchNext: (PaneSide) -> Unit,
    onSearchPrev: (PaneSide) -> Unit,
    onReplace: (PaneSide) -> Unit,
    onReplaceAll: (PaneSide) -> Unit,
    onSearchClose: (PaneSide) -> Unit,
    onWindowShortcut: (KeyEvent) -> Boolean,
    onTabDragStart: () -> Unit = {},
    onTabDragEnd: () -> Unit = {},
    onProblemsToggle: () -> Unit = {},
    onJumpToProblem: (Path, Int, Int) -> Unit = { _, _, _ -> },
    onApplyRename: (RenameWorkspaceEdit) -> Unit = {},
    onRequestReferences: (Path, Int, Int, String) -> Unit = { _, _, _, _ -> },
    todoCount: Int = 0,
    onTodoToggle: () -> Unit = {},
    workspaceRoot: Path? = null,
    editorFocusVersion: Int = 0,
    initialFoldedStartLines: Set<Int> = emptySet(),
    onFoldStartLinesChange: (Path, Set<Int>) -> Unit = { _, _ -> },
    editorScrollFor: (Path) -> EditorScrollSnapshot? = { null },
    onEditorScrollChange: (Path, EditorScrollSnapshot) -> Unit = { _, _ -> },
    tabContextActions: TabContextActions? = null,
    runtimeVersions: Map<String, String> = emptyMap(),
    runtimeSources: Map<String, String> = emptyMap(),
    runtimeBuildFileVersions: Map<String, String> = emptyMap(),
    onRuntimeClick: ((String) -> Unit)? = null,
    pageSettings: PageSettings = PageSettings(),
    modifier: Modifier = Modifier,
) {
    val active = pane.book.active
    val kind = active?.let { FileKinds.classify(it.path) }
    val activeLexer = active?.path?.let { SyntaxLexers.forPath(it) }
    val activeExt = remember(active?.path) {
        active?.path?.fileName?.toString()?.lowercase()?.substringAfterLast('.', "") ?: ""
    }
    val runtimeInfo: Triple<String, String, String?>? = remember(activeExt, runtimeVersions, runtimeSources, runtimeBuildFileVersions) {
        fun build(name: String, key: String, id: String): Triple<String, String, String?> {
            val ver = runtimeVersions[key] ?: "?"
            val bfVer = runtimeBuildFileVersions[key]
            val src = runtimeSources[key]
            val mismatch = bfVer != null && ver != "?" && !ver.startsWith(bfVer)
            val label = if (mismatch) "$name $ver ⚠" else "$name $ver"
            val tooltip = when {
                mismatch -> "Project requires $bfVer ($src), using $ver"
                src != null -> "from $src"
                else -> null
            }
            return Triple(label, id, tooltip)
        }
        when (activeExt) {
            "java" -> build("JDK", "java", "jdk")
            "js", "mjs", "cjs", "ts" -> build("Node", "js", "node")
            "py" -> build("Python", "py", "python-runtime")
            "go" -> build("Go", "go", "go-sdk")
            "c", "cpp", "cc", "cxx", "h", "hpp" -> build("Clang", "cpp", "cpp-toolchain")
            "rs" -> build("Rust", "rs", "rust-runtime")
            "cs" -> build(".NET", "cs", "dotnet-runtime")
            else -> null
        }
    }
    Column(
        modifier = modifier.fillMaxSize()
            .pointerInput(side) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Press) onPaneFocus(side)
                    }
                }
            },
    ) {
        TabBar(
            book = pane.book,
            onActivate = { idx ->
                onPaneFocus(side)
                editor.activateTab(side, idx)
            },
            onClose = { idx -> onCloseTab(side, idx) },
            onMove = { from, to -> editor.moveTab(side, from, to) },
            onMoveToOtherPane = if (editor.splitEnabled) {
                { idx -> editor.moveTabAcross(side, idx) }
            } else null,
            crossPaneSide = if (!editor.splitEnabled) null else when (side) {
                PaneSide.PRIMARY -> CrossPaneSide.RIGHT
                PaneSide.SECONDARY -> CrossPaneSide.LEFT
            },
            onDragStart = onTabDragStart,
            onDragEnd = onTabDragEnd,
            contextActions = tabContextActions,
            showCloseButton = pageSettings.ui.showTabCloseButton,
        )
        FocusIndicator(visible = isFocused)
        when (kind) {
            FileKind.IMAGE -> PreviewPanel(
                path = active.path,
                kind = kind,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            FileKind.SVG -> SvgEditPanel(
                path = active.path,
                value = pane.editorValue,
                onValueChange = { v -> editor.handleEditorChange(side, v) },
                search = pane.search,
                onQueryChange = { q -> onQueryChange(side, q) },
                onReplaceChange = { v -> onReplaceChange(side, v) },
                onToggleCase = { onToggleCase(side) },
                onSearchNext = { onSearchNext(side) },
                onSearchPrev = { onSearchPrev(side) },
                onReplace = { onReplace(side) },
                onReplaceAll = { onReplaceAll(side) },
                onSearchClose = { onSearchClose(side) },
                onWindowShortcut = onWindowShortcut,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            else -> if (active == null) {
                EmptyPanePlaceholder(modifier = Modifier.fillMaxWidth().weight(1f))
            } else {
                val activeCtrl = lspRouter.controllerFor(active.path)
                val activeDiagnostics = activeCtrl?.diagnosticsFor(active.path).orEmpty()
                val lspStatusText = lspStatusLineText(lspRouter, active?.path)
                val ctrlActivities = activeCtrl?.activities?.values
                    ?.sortedBy { it.startedAtMs }
                    ?.toList().orEmpty()
                val globalStarting = lspRouter.startingActivities
                val installActivities = InstallProgressRegistry.entries.values.map { e ->
                    val frac = (e.progress as? page.runtime.LspInstaller.Progress.Downloading)
                        ?.takeIf { it.total > 0 }
                        ?.let { (it.bytesRead.toFloat() / it.total.toFloat()).coerceIn(0f, 1f) }
                    LspController.Activity(
                        kind = "install",
                        label = "${e.displayName} (installing)",
                        startedAtMs = e.startedAtMs,
                        progress = frac,
                        installerId = e.installerId,
                    )
                }
                val lspActivities = (globalStarting + ctrlActivities + installActivities)
                    .distinctBy { it.kind + it.label }
                    .sortedBy { it.startedAtMs }
                EditorPanel(
                    value = pane.editorValue,
                    onValueChange = { v -> editor.handleEditorChange(side, v) },
                    search = pane.search,
                    onQueryChange = { q -> onQueryChange(side, q) },
                    onReplaceChange = { v -> onReplaceChange(side, v) },
                    onToggleCase = { onToggleCase(side) },
                    onSearchNext = { onSearchNext(side) },
                    onSearchPrev = { onSearchPrev(side) },
                    onReplace = { onReplace(side) },
                    onReplaceAll = { onReplaceAll(side) },
                    onSearchClose = { onSearchClose(side) },
                    onWindowShortcut = onWindowShortcut,
                    lexer = activeLexer,
                    activePath = active?.path,
                    diagnostics = activeDiagnostics,
                    lspStatusText = lspStatusText,
                    lspActivities = lspActivities,
                    onLspStatusClick = { activeCtrl?.openInstallGuide() },
                    onActivityClick = { act ->
                        act.installerId?.let { onRuntimeClick?.invoke(it) }
                    },
                    onProblemsToggle = onProblemsToggle,
                    todoCount = todoCount,
                    onTodoToggle = onTodoToggle,
                    onRequestCompletion = active?.path?.let { p ->
                        activeCtrl?.let { ctrl -> { line, ch, trig -> ctrl.completion(p, pane.editorValue.text, line, ch, trig) } }
                    },
                    onRequestHover = active?.path?.let { p ->
                        activeCtrl?.let { ctrl -> { line, ch -> ctrl.hover(p, line, ch) } }
                    },
                    onRequestDefinition = active?.path?.let { p ->
                        activeCtrl?.let { ctrl -> { line, ch -> ctrl.definition(p, line, ch) } }
                    },
                    onRequestSignatureHelp = active?.path?.let { p ->
                        activeCtrl?.let { ctrl -> { line, ch, trig, retrig -> ctrl.signatureHelp(p, pane.editorValue.text, line, ch, trig, retrig) } }
                    },
                    onResolveCompletion = activeCtrl?.let { ctrl -> { token -> ctrl.resolveCompletion(token) } },
                    onGoToDefinition = { target ->
                        val path = runCatching {
                            java.nio.file.Paths.get(java.net.URI(target.uri))
                        }.getOrNull()
                        if (path != null) onJumpToProblem(path, target.startLine, target.startCharacter)
                    },
                    onRequestPrepareRename = active?.path?.let { p ->
                        activeCtrl?.let { ctrl -> { line, ch -> ctrl.prepareRename(p, line, ch) } }
                    },
                    onRequestRename = active?.path?.let { p ->
                        activeCtrl?.let { ctrl -> { line, ch, name -> ctrl.rename(p, pane.editorValue.text, line, ch, name) } }
                    },
                    onApplyRename = onApplyRename,
                    onRequestReferences = active?.path?.let { p ->
                        { line, ch, sym -> onRequestReferences(p, line, ch, sym) }
                    },
                    onRequestInlayHints = active?.path
                        ?.takeIf { activeCtrl?.status?.value == LspController.Status.READY }
                        ?.let { p ->
                            activeCtrl?.let { ctrl -> { sl, sc, el, ec -> ctrl.inlayHints(p, sl, sc, el, ec) } }
                        },
                    workspaceRoot = workspaceRoot,
                    editorFocusVersion = editorFocusVersion,
                    initialFoldedStartLines = initialFoldedStartLines,
                    onFoldStartLinesChange = { lines ->
                        active.path.let { p -> onFoldStartLinesChange(p, lines) }
                    },
                    initialVScroll = editorScrollFor(active.path)?.vertical ?: 0,
                    initialHScroll = editorScrollFor(active.path)?.horizontal ?: 0,
                    onScrollChange = { v, h ->
                        onEditorScrollChange(active.path, EditorScrollSnapshot(v, h))
                    },
                    jdkVersion = runtimeInfo?.first,
                    jdkVersionTooltip = runtimeInfo?.third?.let { "from $it" },
                    onJdkVersionClick = runtimeInfo?.let { (_, id, _) -> { onRuntimeClick?.invoke(id) } },
                    pageSettings = pageSettings,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FocusIndicator(visible: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                if (visible) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else Color.Transparent,
            ),
    )
}

@Composable
private fun EmptyPanePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No file open",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 12.sp,
        )
    }
}
