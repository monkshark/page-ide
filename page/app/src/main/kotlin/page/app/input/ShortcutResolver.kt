package page.app.input

import androidx.compose.ui.input.key.Key

internal enum class ShortcutAction {
    NONE,
    CYCLE_PALETTE,
    OPEN_FOLDER,
    OPEN_FILE,
    OPEN_SETTINGS,
    SAVE,
    CLOSE_TAB,
    TOGGLE_PROBLEMS,
    TOGGLE_TODO,
    TOGGLE_FIND_IN_FILES,
    OPEN_SEARCH,
    OPEN_REPLACE,
    OPEN_QUICK_OPEN,
    OPEN_WORKSPACE_SYMBOL,
    OPEN_DOCUMENT_SYMBOL,
    TOGGLE_SPLIT_ORIENTATION,
    TOGGLE_SPLIT,
    UNDO,
    REDO,
    FORMAT,
    CODE_ACTION,
    PREV_TAB,
    NEXT_TAB,
    JUMP_PROBLEM_NEXT,
    JUMP_PROBLEM_PREV,
    REFRESH_TREE,
    CLOSE_SEARCH,
    FOCUS_IN_ATLAS,
}

internal object ShortcutResolver {
    fun resolve(key: Key, ctrl: Boolean, alt: Boolean, shift: Boolean, hasSearch: Boolean): ShortcutAction {
        if (ctrl && alt && key == Key.T) return ShortcutAction.CYCLE_PALETTE
        if (ctrl && alt && key == Key.A) return ShortcutAction.FOCUS_IN_ATLAS
        if (ctrl) {
            return when {
                key == Key.O && shift -> ShortcutAction.OPEN_FOLDER
                key == Key.O -> ShortcutAction.OPEN_FILE
                key == Key.S && alt -> ShortcutAction.OPEN_SETTINGS
                key == Key.S -> ShortcutAction.SAVE
                key == Key.W -> ShortcutAction.CLOSE_TAB
                key == Key.M && shift -> ShortcutAction.TOGGLE_PROBLEMS
                key == Key.Six && shift -> ShortcutAction.TOGGLE_TODO
                key == Key.F && shift -> ShortcutAction.TOGGLE_FIND_IN_FILES
                key == Key.F -> ShortcutAction.OPEN_SEARCH
                key == Key.R -> ShortcutAction.OPEN_REPLACE
                key == Key.P -> ShortcutAction.OPEN_QUICK_OPEN
                key == Key.T -> ShortcutAction.OPEN_WORKSPACE_SYMBOL
                key == Key.F12 -> ShortcutAction.OPEN_DOCUMENT_SYMBOL
                key == Key.Backslash && shift -> ShortcutAction.TOGGLE_SPLIT_ORIENTATION
                key == Key.Backslash -> ShortcutAction.TOGGLE_SPLIT
                key == Key.Z && shift -> if (hasSearch) ShortcutAction.NONE else ShortcutAction.REDO
                key == Key.Z -> if (hasSearch) ShortcutAction.NONE else ShortcutAction.UNDO
                key == Key.Y -> if (hasSearch) ShortcutAction.NONE else ShortcutAction.REDO
                else -> ShortcutAction.NONE
            }
        }
        val enter = key == Key.Enter || key == Key.NumPadEnter
        if (alt && shift && enter) return ShortcutAction.FORMAT
        if (alt && !shift && !hasSearch && enter) return ShortcutAction.CODE_ACTION
        if (alt && !shift && !hasSearch && key == Key.DirectionLeft) return ShortcutAction.PREV_TAB
        if (alt && !shift && !hasSearch && key == Key.DirectionRight) return ShortcutAction.NEXT_TAB
        if (key == Key.F8) return if (shift) ShortcutAction.JUMP_PROBLEM_PREV else ShortcutAction.JUMP_PROBLEM_NEXT
        if (key == Key.F5) return ShortcutAction.REFRESH_TREE
        if (key == Key.Escape && hasSearch) return ShortcutAction.CLOSE_SEARCH
        return ShortcutAction.NONE
    }
}
