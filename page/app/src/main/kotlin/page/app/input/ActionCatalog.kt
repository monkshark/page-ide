package page.app.input

import androidx.compose.ui.input.key.Key
import page.ui.Binding
import page.ui.Platform
import page.ui.ShortcutLabels

enum class ActionGroup { File, Edit, Navigate, Code, View, Page }

enum class ActionContext { Global, Editor, FileTree }

enum class SearchMode { Any, OnlyWhenOpen, OnlyWhenClosed }

data class ActionSpec(
    val id: String,
    val label: String,
    val group: ActionGroup,
    val binding: Binding?,
    val macBinding: Binding? = binding,
    val searchMode: SearchMode = SearchMode.Any,
    val context: ActionContext = ActionContext.Global,
    val run: ((ActionHost) -> Unit)? = null,
) {
    fun bindingFor(mac: Boolean): Binding? = if (mac) macBinding else binding
}

object ActionCatalog {

    val all: List<ActionSpec> = listOf(
        ActionSpec(
            id = "file.openFile",
            label = "Open File…",
            group = ActionGroup.File,
            binding = Binding(Key.O, primary = true),
        ) { it.openFile() },
        ActionSpec(
            id = "file.openFolder",
            label = "Open Folder…",
            group = ActionGroup.File,
            binding = Binding(Key.O, primary = true, shift = true),
        ) { it.openFolder() },
        ActionSpec(
            id = "file.save",
            label = "Save",
            group = ActionGroup.File,
            binding = Binding(Key.S, primary = true),
        ) { it.saveFile() },
        ActionSpec(
            id = "file.closeTab",
            label = "Close Tab",
            group = ActionGroup.File,
            binding = Binding(Key.W, primary = true),
        ) { it.closeActiveTab() },
        ActionSpec(
            id = "file.settings",
            label = "Settings…",
            group = ActionGroup.File,
            binding = Binding(Key.S, primary = true, alt = true),
            macBinding = Binding(Key.Comma, primary = true),
        ) { it.openSettings() },
        ActionSpec(
            id = "file.refreshTree",
            label = "Refresh File Tree",
            group = ActionGroup.File,
            binding = Binding(Key.F5),
        ) { it.refreshTree() },

        ActionSpec(
            id = "edit.undo",
            label = "Undo",
            group = ActionGroup.Edit,
            binding = Binding(Key.Z, primary = true),
            searchMode = SearchMode.OnlyWhenClosed,
        ) { it.requestUndo() },
        ActionSpec(
            id = "edit.redo",
            label = "Redo",
            group = ActionGroup.Edit,
            binding = Binding(Key.Z, primary = true, shift = true),
            searchMode = SearchMode.OnlyWhenClosed,
        ) { it.requestRedo() },
        ActionSpec(
            id = "edit.redoAlternate",
            label = "Redo",
            group = ActionGroup.Edit,
            binding = Binding(Key.Y, primary = true),
            searchMode = SearchMode.OnlyWhenClosed,
        ) { it.requestRedo() },
        ActionSpec(
            id = "edit.find",
            label = "Find",
            group = ActionGroup.Edit,
            binding = Binding(Key.F, primary = true),
        ) { it.openSearch() },
        ActionSpec(
            id = "edit.replace",
            label = "Replace",
            group = ActionGroup.Edit,
            binding = Binding(Key.R, primary = true),
        ) { it.openReplace() },
        ActionSpec(
            id = "edit.closeSearch",
            label = "Close Search",
            group = ActionGroup.Edit,
            binding = Binding(Key.Escape),
            searchMode = SearchMode.OnlyWhenOpen,
        ) { it.closeSearch() },

        ActionSpec(
            id = "nav.quickOpen",
            label = "Go to File…",
            group = ActionGroup.Navigate,
            binding = Binding(Key.P, primary = true),
        ) { it.openQuickOpen() },
        ActionSpec(
            id = "nav.workspaceSymbol",
            label = "Go to Symbol in Project…",
            group = ActionGroup.Navigate,
            binding = Binding(Key.T, primary = true),
        ) { it.openWorkspaceSymbol() },
        ActionSpec(
            id = "nav.documentSymbol",
            label = "Go to Symbol in File…",
            group = ActionGroup.Navigate,
            binding = Binding(Key.F12, primary = true),
        ) { it.openDocumentSymbol() },
        ActionSpec(
            id = "nav.findInFiles",
            label = "Find in Files",
            group = ActionGroup.Navigate,
            binding = Binding(Key.F, primary = true, shift = true),
        ) { it.toggleFindInFiles() },
        ActionSpec(
            id = "nav.nextProblem",
            label = "Next Problem",
            group = ActionGroup.Navigate,
            binding = Binding(Key.F8),
        ) { it.jumpProblemRelative(true) },
        ActionSpec(
            id = "nav.prevProblem",
            label = "Previous Problem",
            group = ActionGroup.Navigate,
            binding = Binding(Key.F8, shift = true),
        ) { it.jumpProblemRelative(false) },
        ActionSpec(
            id = "nav.prevTab",
            label = "Previous Tab",
            group = ActionGroup.Navigate,
            binding = Binding(Key.DirectionLeft, alt = true),
            searchMode = SearchMode.OnlyWhenClosed,
        ) { it.activateAdjacentTab(-1) },
        ActionSpec(
            id = "nav.nextTab",
            label = "Next Tab",
            group = ActionGroup.Navigate,
            binding = Binding(Key.DirectionRight, alt = true),
            searchMode = SearchMode.OnlyWhenClosed,
        ) { it.activateAdjacentTab(1) },

        ActionSpec(
            id = "code.format",
            label = "Format File",
            group = ActionGroup.Code,
            binding = Binding(Key.Enter, alt = true, shift = true),
        ) { it.triggerFormat() },
        ActionSpec(
            id = "code.action",
            label = "Show Context Actions",
            group = ActionGroup.Code,
            binding = Binding(Key.Enter, alt = true),
            searchMode = SearchMode.OnlyWhenClosed,
        ) { it.triggerCodeAction() },

        ActionSpec(
            id = "view.problems",
            label = "Problems",
            group = ActionGroup.View,
            binding = Binding(Key.M, primary = true, shift = true),
        ) { it.toggleProblems() },
        ActionSpec(
            id = "view.todo",
            label = "Todo",
            group = ActionGroup.View,
            binding = Binding(Key.Six, primary = true, shift = true),
        ) { it.toggleTodo() },
        ActionSpec(
            id = "view.sidebar",
            label = "Toggle Sidebar",
            group = ActionGroup.View,
            binding = Binding(Key.One, alt = true),
            macBinding = Binding(Key.One, primary = true),
        ) { it.toggleSidebar() },
        ActionSpec(
            id = "view.split",
            label = "Split Editor",
            group = ActionGroup.View,
            binding = Binding(Key.Backslash, primary = true),
        ) { it.toggleSplit() },
        ActionSpec(
            id = "view.splitFlip",
            label = "Flip Split Orientation",
            group = ActionGroup.View,
            binding = Binding(Key.Backslash, primary = true, shift = true),
        ) { it.toggleSplitOrientation() },

        ActionSpec(
            id = "page.actions",
            label = "Show All Actions",
            group = ActionGroup.Page,
            binding = Binding(Key.P, primary = true, shift = true),
        ) { it.showAllActions() },
        ActionSpec(
            id = "page.cycleTheme",
            label = "Next Theme",
            group = ActionGroup.Page,
            binding = Binding(Key.T, primary = true, alt = true),
            macBinding = Binding(Key.T, control = true, alt = true),
        ) { it.cyclePalette() },
        ActionSpec(
            id = "page.projectSummary",
            label = "Show Project Summary",
            group = ActionGroup.Page,
            binding = Binding(Key.A, primary = true, alt = true),
            macBinding = Binding(Key.A, control = true, alt = true),
        ) { it.toggleProjectSummary() },
        ActionSpec(
            id = "editor.completion",
            label = "Trigger Completion",
            group = ActionGroup.Code,
            binding = Binding(Key.Spacebar, primary = true),
            macBinding = Binding(Key.I, primary = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.signatureHelp",
            label = "Show Parameter Info",
            group = ActionGroup.Code,
            binding = Binding(Key.Spacebar, primary = true, shift = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.rename",
            label = "Rename Symbol",
            group = ActionGroup.Code,
            binding = Binding(Key.F2),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.declaration",
            label = "Go to Declaration",
            group = ActionGroup.Navigate,
            binding = Binding(Key.B, primary = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.definition",
            label = "Go to Definition",
            group = ActionGroup.Navigate,
            binding = Binding(Key.F12),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.usages",
            label = "Find Usages",
            group = ActionGroup.Navigate,
            binding = Binding(Key.F12, shift = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.toggleComment",
            label = "Comment Lines",
            group = ActionGroup.Edit,
            binding = Binding(Key.Slash, primary = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.indent",
            label = "Indent Selection",
            group = ActionGroup.Edit,
            binding = Binding(Key.Tab),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.unindent",
            label = "Unindent Selection",
            group = ActionGroup.Edit,
            binding = Binding(Key.Tab, shift = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.selectAll",
            label = "Select All",
            group = ActionGroup.Edit,
            binding = Binding(Key.A, primary = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.cut",
            label = "Cut",
            group = ActionGroup.Edit,
            binding = Binding(Key.X, primary = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.copy",
            label = "Copy",
            group = ActionGroup.Edit,
            binding = Binding(Key.C, primary = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.paste",
            label = "Paste",
            group = ActionGroup.Edit,
            binding = Binding(Key.V, primary = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.moveLineUp",
            label = "Move Line Up",
            group = ActionGroup.Edit,
            binding = Binding(Key.DirectionUp, alt = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.moveLineDown",
            label = "Move Line Down",
            group = ActionGroup.Edit,
            binding = Binding(Key.DirectionDown, alt = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.duplicateLineUp",
            label = "Duplicate Line Up",
            group = ActionGroup.Edit,
            binding = Binding(Key.DirectionUp, alt = true, shift = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.duplicateLineDown",
            label = "Duplicate Line Down",
            group = ActionGroup.Edit,
            binding = Binding(Key.DirectionDown, alt = true, shift = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.wordLeft",
            label = "Move to Previous Word",
            group = ActionGroup.Edit,
            binding = Binding(Key.DirectionLeft, primary = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.wordRight",
            label = "Move to Next Word",
            group = ActionGroup.Edit,
            binding = Binding(Key.DirectionRight, primary = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.deleteWordLeft",
            label = "Delete Word Before Caret",
            group = ActionGroup.Edit,
            binding = Binding(Key.Backspace, primary = true),
            context = ActionContext.Editor,
        ),
        ActionSpec(
            id = "editor.deleteWordRight",
            label = "Delete Word After Caret",
            group = ActionGroup.Edit,
            binding = Binding(Key.Delete, primary = true),
            context = ActionContext.Editor,
        ),

        ActionSpec(
            id = "tree.open",
            label = "Open Selected",
            group = ActionGroup.File,
            binding = Binding(Key.Enter),
            context = ActionContext.FileTree,
        ),
        ActionSpec(
            id = "tree.openRecursive",
            label = "Expand Selected Recursively",
            group = ActionGroup.File,
            binding = Binding(Key.Enter, shift = true),
            context = ActionContext.FileTree,
        ),
        ActionSpec(
            id = "tree.rename",
            label = "Rename…",
            group = ActionGroup.File,
            binding = Binding(Key.F2),
            context = ActionContext.FileTree,
        ),
        ActionSpec(
            id = "tree.delete",
            label = "Delete…",
            group = ActionGroup.File,
            binding = Binding(Key.Delete),
            context = ActionContext.FileTree,
        ),
        ActionSpec(
            id = "tree.cut",
            label = "Cut",
            group = ActionGroup.File,
            binding = Binding(Key.X, primary = true),
            context = ActionContext.FileTree,
        ),
        ActionSpec(
            id = "tree.copy",
            label = "Copy",
            group = ActionGroup.File,
            binding = Binding(Key.C, primary = true),
            context = ActionContext.FileTree,
        ),
        ActionSpec(
            id = "tree.paste",
            label = "Paste",
            group = ActionGroup.File,
            binding = Binding(Key.V, primary = true),
            context = ActionContext.FileTree,
        ),
        ActionSpec(
            id = "tree.undo",
            label = "Undo File Operation",
            group = ActionGroup.File,
            binding = Binding(Key.Z, primary = true),
            context = ActionContext.FileTree,
        ),
    )

    fun resolve(
        key: Key,
        primary: Boolean,
        control: Boolean,
        alt: Boolean,
        shift: Boolean,
        hasSearch: Boolean,
        mac: Boolean = Platform.isMac,
    ): ActionSpec? = all.firstOrNull { spec ->
        if (spec.context != ActionContext.Global || spec.run == null) return@firstOrNull false
        val binding = spec.bindingFor(mac) ?: return@firstOrNull false
        if (!binding.matches(key, primary, control, alt, shift)) return@firstOrNull false
        when (spec.searchMode) {
            SearchMode.Any -> true
            SearchMode.OnlyWhenOpen -> hasSearch
            SearchMode.OnlyWhenClosed -> !hasSearch
        }
    }

    fun label(spec: ActionSpec, mac: Boolean = Platform.isMac): String? =
        spec.bindingFor(mac)?.let { ShortcutLabels.of(it, mac) }

    fun labelOf(id: String, mac: Boolean = Platform.isMac): String? =
        all.firstOrNull { it.id == id }?.let { label(it, mac) }
}
