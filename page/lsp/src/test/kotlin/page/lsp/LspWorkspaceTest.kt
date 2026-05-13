package page.lsp

import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpTriggerKind
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LspWorkspaceTest {

    private lateinit var harness: LspTestHarness
    private lateinit var workspace: LspWorkspace

    @BeforeTest
    fun setUp() {
        harness = LspTestHarness()
        harness.client.start().get(5, TimeUnit.SECONDS)
        waitUntil { harness.client.state == LspState.INITIALIZED }
        workspace = LspWorkspace(harness.client)
    }

    @AfterTest
    fun tearDown() {
        harness.close()
    }

    @Test
    fun `didOpen forwards to server with version 1`() {
        val uri = "file:///A.kt"
        workspace.didOpen(uri, "kotlin", "fun a() {}")

        waitUntil { harness.fakeServer.didOpenCalls.isNotEmpty() }
        val params = harness.fakeServer.didOpenCalls.first()
        assertEquals(uri, params.textDocument.uri)
        assertEquals("kotlin", params.textDocument.languageId)
        assertEquals(1, params.textDocument.version)
        assertEquals("fun a() {}", params.textDocument.text)
        assertTrue(workspace.isOpen(uri))
        assertEquals(1, workspace.versionOf(uri))
    }

    @Test
    fun `didChange increments version and forwards new text`() {
        val uri = "file:///B.kt"
        workspace.didOpen(uri, "kotlin", "v1")
        workspace.didChange(uri, "v2")
        workspace.didChange(uri, "v3")

        waitUntil { harness.fakeServer.didChangeCalls.size >= 2 }
        val changes = harness.fakeServer.didChangeCalls.toList()
        assertEquals(2, changes.first().textDocument.version)
        assertEquals(3, changes.last().textDocument.version)
        assertEquals("v3", workspace.textOf(uri))
        assertEquals(3, workspace.versionOf(uri))
    }

    @Test
    fun `didClose removes document and forwards to server`() {
        val uri = "file:///C.kt"
        workspace.didOpen(uri, "kotlin", "x")
        workspace.didClose(uri)

        waitUntil { harness.fakeServer.didCloseCalls.isNotEmpty() }
        assertFalse(workspace.isOpen(uri))
        assertNull(workspace.textOf(uri))
    }

    @Test
    fun `didChange on unopened doc errors out`() {
        try {
            workspace.didChange("file:///none.kt", "x")
            error("expected IllegalStateException")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `didClose on unopened doc is no-op`() {
        workspace.didClose("file:///nope.kt")
    }

    @Test
    fun `reopen closes and re-opens with version reset`() {
        val uri = "file:///R.kt"
        workspace.didOpen(uri, "kotlin", "v1")
        workspace.didChange(uri, "v2")
        workspace.didChange(uri, "v3")
        assertEquals(3, workspace.versionOf(uri))

        workspace.reopen(uri, "fresh")

        waitUntil { harness.fakeServer.didCloseCalls.isNotEmpty() }
        assertTrue(workspace.isOpen(uri))
        assertEquals("fresh", workspace.textOf(uri))
        assertEquals(1, workspace.versionOf(uri))
        assertEquals(uri, harness.fakeServer.didCloseCalls.last().textDocument.uri)
        val lastOpen = harness.fakeServer.didOpenCalls.last()
        assertEquals(uri, lastOpen.textDocument.uri)
        assertEquals("fresh", lastOpen.textDocument.text)
        assertEquals("kotlin", lastOpen.textDocument.languageId)
    }

    @Test
    fun `reopen on unopened doc is no-op`() {
        workspace.reopen("file:///none.kt", "ignored")
        assertFalse(workspace.isOpen("file:///none.kt"))
        assertTrue(harness.fakeServer.didOpenCalls.isEmpty())
    }

    @Test
    fun `hover on unopened doc returns null without server call`() {
        val result = workspace.hover("file:///nope.kt", 0, 0).get(2, TimeUnit.SECONDS)
        assertNull(result)
        assertTrue(harness.fakeServer.hoverCalls.isEmpty())
    }

    @Test
    fun `hover forwards params and parses response`() {
        val uri = "file:///H.kt"
        workspace.didOpen(uri, "kotlin", "x")
        harness.fakeServer.hoverResponse = Hover().apply {
            contents = Either.forRight(MarkupContent(MarkupKind.MARKDOWN, "hello"))
            range = Range(Position(0, 0), Position(0, 1))
        }

        val info = workspace.hover(uri, 0, 0).get(2, TimeUnit.SECONDS)
        assertNotNull(info)
        assertEquals("hello", info!!.markdown)

        waitUntil { harness.fakeServer.hoverCalls.isNotEmpty() }
        val sent = harness.fakeServer.hoverCalls.first()
        assertEquals(uri, sent.textDocument.uri)
        assertEquals(0, sent.position.line)
        assertEquals(0, sent.position.character)
    }

    @Test
    fun `definition on unopened doc returns empty`() {
        val result = workspace.definition("file:///nope.kt", 0, 0).get(2, TimeUnit.SECONDS)
        assertTrue(result.isEmpty())
        assertTrue(harness.fakeServer.definitionCalls.isEmpty())
    }

    @Test
    fun `definition returns mapped targets`() {
        val uri = "file:///D.kt"
        workspace.didOpen(uri, "kotlin", "y")
        val loc = Location("file:///Target.kt", Range(Position(5, 2), Position(5, 8)))
        harness.fakeServer.definitionResponse =
            Either.forLeft<MutableList<out Location>, MutableList<out LocationLink>>(mutableListOf(loc))

        val targets = workspace.definition(uri, 3, 4).get(2, TimeUnit.SECONDS)
        assertEquals(1, targets.size)
        assertEquals("file:///Target.kt", targets[0].uri)
        assertEquals(5, targets[0].startLine)
        assertEquals(2, targets[0].startCharacter)
        assertEquals(8, targets[0].endCharacter)
    }

    @Test
    fun `signatureHelp on unopened doc returns null without server call`() {
        val result = workspace.signatureHelp("file:///nope.kt", 0, 0).get(2, TimeUnit.SECONDS)
        assertNull(result)
        assertTrue(harness.fakeServer.signatureHelpCalls.isEmpty())
    }

    @Test
    fun `signatureHelp forwards trigger char context and parses response`() {
        val uri = "file:///S.kt"
        workspace.didOpen(uri, "kotlin", "addInts(")
        harness.fakeServer.signatureHelpResponse = SignatureHelp().apply {
            signatures = mutableListOf(
                SignatureInformation().apply {
                    label = "addInts(x: Int, y: Int): Int"
                    parameters = mutableListOf(
                        ParameterInformation("x: Int"),
                        ParameterInformation("y: Int"),
                    )
                },
            )
            activeSignature = 0
            activeParameter = 0
        }

        val info = workspace.signatureHelp(uri, 0, 8, triggerCharacter = "(").get(2, TimeUnit.SECONDS)
        assertNotNull(info)
        assertEquals(1, info!!.signatures.size)
        assertEquals("addInts(x: Int, y: Int): Int", info.signatures[0].label)
        assertEquals(2, info.signatures[0].parameters.size)
        assertEquals(0, info.activeSignature)

        waitUntil { harness.fakeServer.signatureHelpCalls.isNotEmpty() }
        val sent = harness.fakeServer.signatureHelpCalls.first()
        assertEquals(uri, sent.textDocument.uri)
        assertEquals(0, sent.position.line)
        assertEquals(8, sent.position.character)
        assertEquals(SignatureHelpTriggerKind.TriggerCharacter, sent.context.triggerKind)
        assertEquals("(", sent.context.triggerCharacter)
    }

    @Test
    fun `signatureHelp invoked context when no trigger char`() {
        val uri = "file:///SI.kt"
        workspace.didOpen(uri, "kotlin", "x")
        workspace.signatureHelp(uri, 0, 0).get(2, TimeUnit.SECONDS)
        waitUntil { harness.fakeServer.signatureHelpCalls.isNotEmpty() }
        val sent = harness.fakeServer.signatureHelpCalls.first()
        assertEquals(SignatureHelpTriggerKind.Invoked, sent.context.triggerKind)
    }

    @Test
    fun `signatureHelp retrigger context when isRetrigger`() {
        val uri = "file:///SR.kt"
        workspace.didOpen(uri, "kotlin", "x")
        workspace.signatureHelp(uri, 0, 0, isRetrigger = true).get(2, TimeUnit.SECONDS)
        waitUntil { harness.fakeServer.signatureHelpCalls.isNotEmpty() }
        val sent = harness.fakeServer.signatureHelpCalls.first()
        assertEquals(SignatureHelpTriggerKind.ContentChange, sent.context.triggerKind)
        assertTrue(sent.context.isRetrigger)
    }

    @Test
    fun `prepareRename forwards params and parses placeholder`() {
        val uri = "file:///R.kt"
        workspace.didOpen(uri, "kotlin", "val foo = 1")
        harness.fakeServer.prepareRenameResponse = Either3.forSecond(
            PrepareRenameResult(Range(Position(0, 4), Position(0, 7)), "foo")
        )

        val p = workspace.prepareRename(uri, 0, 5).get(2, TimeUnit.SECONDS)
        assertNotNull(p)
        assertEquals(0, p!!.startLine)
        assertEquals(4, p.startCharacter)
        assertEquals(7, p.endCharacter)
        assertEquals("foo", p.placeholder)

        waitUntil { harness.fakeServer.prepareRenameCalls.isNotEmpty() }
        val sent = harness.fakeServer.prepareRenameCalls.first()
        assertEquals(uri, sent.textDocument.uri)
        assertEquals(0, sent.position.line)
        assertEquals(5, sent.position.character)
    }

    @Test
    fun `prepareRename on unopened doc returns null without server call`() {
        val result = workspace.prepareRename("file:///nope.kt", 0, 0).get(2, TimeUnit.SECONDS)
        assertNull(result)
        assertTrue(harness.fakeServer.prepareRenameCalls.isEmpty())
    }

    @Test
    fun `rename forwards new name and parses workspace edit`() {
        val uri = "file:///RW.kt"
        workspace.didOpen(uri, "kotlin", "val foo = 1")
        harness.fakeServer.renameResponse = WorkspaceEdit().apply {
            changes = mutableMapOf(
                uri to mutableListOf(TextEdit(Range(Position(0, 4), Position(0, 7)), "bar"))
            )
        }

        val r = workspace.rename(uri, 0, 5, "bar").get(2, TimeUnit.SECONDS)
        assertFalse(r.isEmpty)
        assertEquals(1, r.changes.size)
        assertEquals(uri, r.changes[0].uri)
        assertEquals("bar", r.changes[0].edits[0].newText)

        waitUntil { harness.fakeServer.renameCalls.isNotEmpty() }
        val sent = harness.fakeServer.renameCalls.first()
        assertEquals(uri, sent.textDocument.uri)
        assertEquals("bar", sent.newName)
    }

    @Test
    fun `rename on unopened doc returns empty without server call`() {
        val r = workspace.rename("file:///nope.kt", 0, 0, "x").get(2, TimeUnit.SECONDS)
        assertTrue(r.isEmpty)
        assertTrue(harness.fakeServer.renameCalls.isEmpty())
    }

    private fun waitUntil(timeoutMs: Long = 2000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }
}
