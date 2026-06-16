package page.app.input

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

internal class ShortcutDispatchController(
    private val hasSearch: () -> Boolean,
    private val cyclePalette: () -> Unit,
    private val openFolder: () -> Unit,
    private val openFile: () -> Unit,
    private val openSettings: () -> Unit,
    private val saveFile: () -> Unit,
    private val closeActiveTab: () -> Unit,
    private val toggleProblems: () -> Unit,
    private val toggleTodo: () -> Unit,
    private val toggleFindInFiles: () -> Unit,
    private val openSearch: () -> Unit,
    private val openReplace: () -> Unit,
    private val openQuickOpen: () -> Unit,
    private val openWorkspaceSymbol: () -> Unit,
    private val openDocumentSymbol: () -> Unit,
    private val toggleSplitOrientation: () -> Unit,
    private val toggleSplit: () -> Unit,
    private val requestUndo: () -> Unit,
    private val requestRedo: () -> Unit,
    private val triggerFormat: () -> Unit,
    private val triggerCodeAction: () -> Unit,
    private val activateAdjacentTab: (Int) -> Unit,
    private val jumpProblemRelative: (Boolean) -> Unit,
    private val refreshTree: () -> Unit,
    private val closeSearch: () -> Unit,
    private val focusActiveInAtlas: () -> Unit,
) {
    fun handle(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (
            ShortcutResolver.resolve(
                key = event.key,
                ctrl = event.isCtrlPressed,
                alt = event.isAltPressed,
                shift = event.isShiftPressed,
                hasSearch = hasSearch(),
            )
        ) {
            ShortcutAction.CYCLE_PALETTE -> { cyclePalette(); true }
            ShortcutAction.OPEN_FOLDER -> { openFolder(); true }
            ShortcutAction.OPEN_FILE -> { openFile(); true }
            ShortcutAction.OPEN_SETTINGS -> { openSettings(); true }
            ShortcutAction.SAVE -> { saveFile(); true }
            ShortcutAction.CLOSE_TAB -> { closeActiveTab(); true }
            ShortcutAction.TOGGLE_PROBLEMS -> { toggleProblems(); true }
            ShortcutAction.TOGGLE_TODO -> { toggleTodo(); true }
            ShortcutAction.TOGGLE_FIND_IN_FILES -> { toggleFindInFiles(); true }
            ShortcutAction.OPEN_SEARCH -> { openSearch(); true }
            ShortcutAction.OPEN_REPLACE -> { openReplace(); true }
            ShortcutAction.OPEN_QUICK_OPEN -> { openQuickOpen(); true }
            ShortcutAction.OPEN_WORKSPACE_SYMBOL -> { openWorkspaceSymbol(); true }
            ShortcutAction.OPEN_DOCUMENT_SYMBOL -> { openDocumentSymbol(); true }
            ShortcutAction.TOGGLE_SPLIT_ORIENTATION -> { toggleSplitOrientation(); true }
            ShortcutAction.TOGGLE_SPLIT -> { toggleSplit(); true }
            ShortcutAction.UNDO -> { requestUndo(); true }
            ShortcutAction.REDO -> { requestRedo(); true }
            ShortcutAction.FORMAT -> { triggerFormat(); true }
            ShortcutAction.CODE_ACTION -> { triggerCodeAction(); true }
            ShortcutAction.PREV_TAB -> { activateAdjacentTab(-1); true }
            ShortcutAction.NEXT_TAB -> { activateAdjacentTab(1); true }
            ShortcutAction.JUMP_PROBLEM_NEXT -> { jumpProblemRelative(true); true }
            ShortcutAction.JUMP_PROBLEM_PREV -> { jumpProblemRelative(false); true }
            ShortcutAction.REFRESH_TREE -> { refreshTree(); true }
            ShortcutAction.CLOSE_SEARCH -> { closeSearch(); true }
            ShortcutAction.FOCUS_IN_ATLAS -> { focusActiveInAtlas(); true }
            ShortcutAction.NONE -> false
        }
    }
}
