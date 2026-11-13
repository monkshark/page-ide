package page.app.input

interface ActionHost {
    val hasSearch: Boolean

    fun openFile()
    fun openFolder()
    fun saveFile()
    fun closeActiveTab()
    fun openSettings()
    fun refreshTree()

    fun requestUndo()
    fun requestRedo()
    fun openSearch()
    fun openReplace()
    fun closeSearch()

    fun openQuickOpen()
    fun openWorkspaceSymbol()
    fun openDocumentSymbol()
    fun toggleFindInFiles()
    fun jumpProblemRelative(forward: Boolean)
    fun activateAdjacentTab(delta: Int)

    fun triggerFormat()
    fun triggerCodeAction()

    fun toggleProblems()
    fun toggleTodo()
    fun toggleSidebar()
    fun toggleSplit()
    fun toggleSplitOrientation()

    fun showAllActions()
    fun cyclePalette()
    fun toggleProjectSummary()
}
