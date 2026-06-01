package page.lsp

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeActionsTest {

    @Test
    fun commandOnlyActionIsExecutableAndKeepsArguments() {
        val command = Command("Organize imports", "java.apply.organizeImports", listOf("file:///A.java"))
        val entry = CodeActionEntry.fromLspCommand(command)!!
        assertFalse(entry.hasEdit)
        assertTrue(entry.hasCommand)
        assertTrue(entry.isExecutable)
        assertEquals("java.apply.organizeImports", entry.command)
        assertEquals(listOf<Any?>("file:///A.java"), entry.commandArguments)
    }

    @Test
    fun codeActionWithCommandButNoEditIsExecutable() {
        val action = CodeAction("Generate getters").apply {
            command = Command("Generate getters", "java.action.generateAccessors", listOf(42))
        }
        val entry = CodeActionEntry.fromLspCodeAction(action)!!
        assertFalse(entry.hasEdit)
        assertTrue(entry.isExecutable)
        assertEquals("java.action.generateAccessors", entry.command)
        assertEquals(listOf<Any?>(42), entry.commandArguments)
    }

    @Test
    fun codeActionWithNeitherEditNorCommandIsNotExecutable() {
        val action = CodeAction("empty")
        val entry = CodeActionEntry.fromLspCodeAction(action)!!
        assertFalse(entry.hasEdit)
        assertFalse(entry.hasCommand)
        assertFalse(entry.isExecutable)
        assertNull(entry.command)
        assertTrue(entry.commandArguments.isEmpty())
    }

    @Test
    fun blankCommandIsNotExecutable() {
        val entry = CodeActionEntry(
            title = "blank",
            kind = null,
            isPreferred = false,
            edit = RenameWorkspaceEdit.EMPTY,
            command = "",
        )
        assertFalse(entry.hasCommand)
        assertFalse(entry.isExecutable)
    }
}
