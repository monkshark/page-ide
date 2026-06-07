package page.app.state

import page.app.CreateEntryDialogState
import page.app.CreateEntryKind
import page.app.FileOpConfirmState
import page.app.PendingClose
import page.app.mvi.IdeStore
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DialogStateAdapterTest {

    @Test
    fun `layout ui create dialog routes through shared store`() {
        val store = IdeStore()
        val ui = LayoutUiState(store)
        val state = CreateEntryDialogState(Path.of("dir"), CreateEntryKind.FOLDER)

        ui.createDialog = state

        assertEquals(state, store.dialogs.createDialog)
        ui.createDialog = null
        assertNull(store.dialogs.createDialog)
    }

    @Test
    fun `app state find-in-files and pending close route through shared store`() {
        val store = IdeStore()
        val app = IdeAppState(store)

        app.findInFiles = true
        assertTrue(store.dialogs.findInFilesOpen)

        app.pendingClose = PendingClose.App
        assertEquals(PendingClose.App, store.dialogs.pendingClose)
    }

    @Test
    fun `holder getters reflect store updates`() {
        val store = IdeStore()
        val app = IdeAppState(store)
        val op = page.workspace.FileOpHistory.RenameOp(Path.of("a"), Path.of("b"))
        val confirm = FileOpConfirmState(isRedo = true, op = op)

        store.updateDialogs { it.copy(fileOpConfirm = confirm) }

        assertEquals(confirm, app.fileOpConfirm)
    }
}
