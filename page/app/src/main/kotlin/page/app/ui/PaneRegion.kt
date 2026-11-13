package page.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import page.ui.Glass
import androidx.compose.runtime.Composable
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
import page.atlas.analyzer.ImportExtractor
import page.editor.FileKind
import page.editor.FileKinds
import page.editor.SyntaxLexers
import page.language.LspController
import page.language.LspRouter
import page.lsp.RenameWorkspaceEdit
import page.workspace.EditorScrollSnapshot
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
    onJumpToProblem: (Path, Int, Int) -> Unit = { _, _, _ -> },
    onApplyRename: (RenameWorkspaceEdit) -> Unit = {},
    onRequestReferences: (Path, Int, Int, String, ReferencesSurface) -> Unit = { _, _, _, _, _ -> },
    referencesState: ReferencesQueryState? = null,
    codeAction: CodeActionPreviewBinding = CodeActionPreviewBinding(),
    onReferencesClose: () -> Unit = {},
    onReferencesOpenInPanel: () -> Unit = {},
    linePreviewFor: (String, Int) -> String? = { _, _ -> null },
    onShowCallGraph: (Path, Int, Int) -> Unit = { _, _, _ -> },
    margins: MarginsBinding? = null,
    workspaceRoot: Path? = null,
    usedElsewhere: (Path?) -> (String) -> Boolean = { { false } },
    usageRevision: Int = 0,
    editorFocusVersion: Int = 0,
    initialFoldedStartLines: Set<Int> = emptySet(),
    onFoldStartLinesChange: (Path, Set<Int>) -> Unit = { _, _ -> },
    editorScrollFor: (Path) -> EditorScrollSnapshot? = { null },
    onEditorScrollChange: (Path, EditorScrollSnapshot) -> Unit = { _, _ -> },
    tabContextActions: TabContextActions? = null,
    pageSettings: PageSettings = LocalPageSettings.current,
    modifier: Modifier = Modifier,
) {
    val active = pane.book.active
    val kind = active?.let { FileKinds.classify(it.path) }
    val activeLexer = active?.path?.let { SyntaxLexers.forPath(it) }
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
            onEmptyAreaClick = if (editor.splitEnabled) {
                { editor.collapseSplit() }
            } else null,
        )
        if (active != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Breadcrumb(path = active.path, workspaceRoot = workspaceRoot)
            }
        }
        FocusIndicator(visible = isFocused && editor.splitEnabled)
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
                    usedElsewhere = usedElsewhere(active?.path),
                    usageRevision = usageRevision,
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
                        { line, ch, sym, surface -> onRequestReferences(p, line, ch, sym, surface) }
                    },
                    codeAction = codeAction,
                    references = referencesState?.takeIf { query ->
                        query.surface == ReferencesSurface.Popup &&
                            active?.path?.toUri()?.toString() == query.originUri
                    },
                    onReferenceJump = { path, line, character ->
                        onReferencesClose()
                        onJumpToProblem(path, line, character)
                    },
                    onReferencesOpenInPanel = onReferencesOpenInPanel,
                    onReferencesDismiss = onReferencesClose,
                    referenceLinePreview = linePreviewFor,
                    margins = if (isFocused) margins else null,
                    onShowCallGraph = active?.path
                        ?.takeIf { activeCtrl?.supportsCallHierarchy == true || ImportExtractor.supportsStaticCalls(it) }
                        ?.let { p -> { line, ch -> onShowCallGraph(p, line, ch) } },
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = Glass.colors.outline,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = "No file open",
                color = Glass.colors.muted,
                fontSize = 13.sp,
            )
        }
    }
}
