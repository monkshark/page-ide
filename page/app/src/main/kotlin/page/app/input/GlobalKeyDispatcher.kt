package page.app.input

import page.lsp.CodeActionEntry

internal class GlobalKeyDispatcher(
    private val isWindowFocused: () -> Boolean,
    private val codeActionOpen: () -> Boolean,
    private val codeActionList: () -> List<CodeActionEntry>,
    private val codeActionSelected: () -> Int,
    private val setCodeActionOpen: (Boolean) -> Unit,
    private val setCodeActionSelected: (Int) -> Unit,
    private val applyCodeAction: (CodeActionEntry) -> Unit,
    private val requestEditorRefocus: () -> Unit,
    private val openSettings: () -> Unit,
    private val toggleTerminal: () -> Unit,
    private val openWorkspaceSymbol: () -> Unit,
    private val openDocumentSymbol: () -> Unit,
    private val triggerFormat: () -> Unit,
    private val triggerCodeAction: () -> Unit,
    private val startActiveRun: () -> Unit,
    private val stopActiveRun: () -> Unit,
    private val openRunDialog: () -> Unit,
) : java.awt.KeyEventDispatcher {
    override fun dispatchKeyEvent(e: java.awt.event.KeyEvent): Boolean {
        if (e.id != java.awt.event.KeyEvent.KEY_PRESSED) return false
        if (!isWindowFocused()) return false
        val ctrl = (e.modifiersEx and java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0
        val alt = (e.modifiersEx and java.awt.event.InputEvent.ALT_DOWN_MASK) != 0
        val shift = (e.modifiersEx and java.awt.event.InputEvent.SHIFT_DOWN_MASK) != 0
        if (codeActionOpen() && !ctrl && !alt && !shift) {
            val list = codeActionList()
            val sel = codeActionSelected()
            when (e.keyCode) {
                java.awt.event.KeyEvent.VK_ESCAPE -> {
                    setCodeActionOpen(false)
                    requestEditorRefocus()
                    return true
                }
                java.awt.event.KeyEvent.VK_UP -> {
                    if (list.isNotEmpty()) setCodeActionSelected(((sel - 1) + list.size) % list.size)
                    return true
                }
                java.awt.event.KeyEvent.VK_DOWN -> {
                    if (list.isNotEmpty()) setCodeActionSelected((sel + 1) % list.size)
                    return true
                }
                java.awt.event.KeyEvent.VK_ENTER -> {
                    val pick = list.getOrNull(sel)
                    setCodeActionOpen(false)
                    if (pick != null && pick.isExecutable) applyCodeAction(pick)
                    requestEditorRefocus()
                    return true
                }
            }
        }
        return when {
            ctrl && alt && !shift && e.keyCode == java.awt.event.KeyEvent.VK_S -> { openSettings(); true }
            ctrl && !alt && shift && e.keyCode == java.awt.event.KeyEvent.VK_T -> { toggleTerminal(); true }
            ctrl && !alt && !shift && e.keyCode == java.awt.event.KeyEvent.VK_BACK_QUOTE -> { toggleTerminal(); true }
            ctrl && !alt && !shift && e.keyCode == java.awt.event.KeyEvent.VK_T -> { openWorkspaceSymbol(); true }
            ctrl && e.keyCode == java.awt.event.KeyEvent.VK_F12 -> { openDocumentSymbol(); true }
            !ctrl && alt && shift && e.keyCode == java.awt.event.KeyEvent.VK_F -> { triggerFormat(); true }
            !ctrl && alt && !shift && e.keyCode == java.awt.event.KeyEvent.VK_ENTER -> { triggerCodeAction(); true }
            !ctrl && !alt && shift && e.keyCode == java.awt.event.KeyEvent.VK_F10 -> { startActiveRun(); true }
            ctrl && !alt && !shift && e.keyCode == java.awt.event.KeyEvent.VK_F2 -> { stopActiveRun(); true }
            ctrl && alt && !shift && e.keyCode == java.awt.event.KeyEvent.VK_R -> { openRunDialog(); true }
            else -> false
        }
    }
}
