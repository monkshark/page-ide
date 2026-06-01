package page.lsp

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.DocumentSymbol
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
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolLocation
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
    fun `didSave forwards to server with current text`() {
        val uri = "file:///S.rs"
        workspace.didOpen(uri, "rust", "fn main() {}")
        workspace.didSave(uri)

        waitUntil { harness.fakeServer.didSaveCalls.isNotEmpty() }
        val params = harness.fakeServer.didSaveCalls.first()
        assertEquals(uri, params.textDocument.uri)
        assertEquals("fn main() {}", params.text)
    }

    @Test
    fun `didSave on unopened doc is no-op`() {
        workspace.didSave("file:///nope.rs")
        assertTrue(harness.fakeServer.didSaveCalls.isEmpty())
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
    fun `references on unopened doc returns empty without server call`() {
        val result = workspace.references("file:///nope.kt", 0, 0).get(2, TimeUnit.SECONDS)
        assertTrue(result.isEmpty())
        assertTrue(harness.fakeServer.referencesCalls.isEmpty())
    }

    @Test
    fun `references forwards params and parses locations`() {
        val uri = "file:///F.kt"
        workspace.didOpen(uri, "kotlin", "fun foo() {} foo()")
        harness.fakeServer.referencesResponse = mutableListOf(
            Location(uri, Range(Position(0, 4), Position(0, 7))),
            Location("file:///G.kt", Range(Position(2, 0), Position(2, 3))),
        )

        val refs = workspace.references(uri, 0, 5, includeDeclaration = true).get(2, TimeUnit.SECONDS)
        assertEquals(2, refs.size)
        assertEquals(uri, refs[0].uri)
        assertEquals(4, refs[0].startCharacter)
        assertEquals("file:///G.kt", refs[1].uri)
        assertEquals(2, refs[1].startLine)

        waitUntil { harness.fakeServer.referencesCalls.isNotEmpty() }
        val sent = harness.fakeServer.referencesCalls.first()
        assertEquals(uri, sent.textDocument.uri)
        assertEquals(0, sent.position.line)
        assertEquals(5, sent.position.character)
        assertTrue(sent.context.isIncludeDeclaration)
    }

    @Test
    fun `references can exclude declaration in context flag`() {
        val uri = "file:///FE.kt"
        workspace.didOpen(uri, "kotlin", "x")
        workspace.references(uri, 0, 0, includeDeclaration = false).get(2, TimeUnit.SECONDS)
        waitUntil { harness.fakeServer.referencesCalls.isNotEmpty() }
        assertFalse(harness.fakeServer.referencesCalls.first().context.isIncludeDeclaration)
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

    @Test
    fun `documentSymbols on unopened doc returns empty without server call`() {
        val result = workspace.documentSymbols("file:///nope.kt").get(2, TimeUnit.SECONDS)
        assertTrue(result.isEmpty())
        assertTrue(harness.fakeServer.documentSymbolCalls.isEmpty())
    }

    @Test
    fun `documentSymbols maps hierarchical DocumentSymbol with children`() {
        val uri = "file:///DS.kt"
        workspace.didOpen(uri, "kotlin", "class A { fun b() {} }")
        val childRange = Range(Position(0, 10), Position(0, 21))
        val child = DocumentSymbol("b", SymbolKind.Method, childRange, childRange)
        val classRange = Range(Position(0, 0), Position(0, 22))
        val parent = DocumentSymbol("A", SymbolKind.Class, classRange, Range(Position(0, 6), Position(0, 7))).apply {
            children = mutableListOf(child)
        }
        harness.fakeServer.documentSymbolResponse = mutableListOf(Either.forRight(parent))

        val syms = workspace.documentSymbols(uri).get(2, TimeUnit.SECONDS)
        assertEquals(1, syms.size)
        val top = syms[0]
        assertEquals("A", top.name)
        assertEquals(SymbolKind.Class, top.kind)
        assertEquals(0, top.range.startLine)
        assertEquals(6, top.selectionRange.startCharacter)
        assertEquals(1, top.children.size)
        assertEquals("b", top.children[0].name)
        assertEquals(SymbolKind.Method, top.children[0].kind)

        val flat = syms.flatMap { it.flatten() }
        assertEquals(2, flat.size)
        assertEquals("A", flat[0].name)
        assertEquals("b", flat[1].name)
        assertEquals("A", flat[1].containerName)

        waitUntil { harness.fakeServer.documentSymbolCalls.isNotEmpty() }
        assertEquals(uri, harness.fakeServer.documentSymbolCalls.first().textDocument.uri)
    }

    @Test
    fun `documentSymbols maps legacy SymbolInformation flat list`() {
        val uri = "file:///DSI.kt"
        workspace.didOpen(uri, "kotlin", "val x = 1")
        @Suppress("DEPRECATION")
        val si = SymbolInformation(
            "x", SymbolKind.Variable,
            Location(uri, Range(Position(0, 4), Position(0, 5))),
            "OuterContainer",
        )
        harness.fakeServer.documentSymbolResponse = mutableListOf(Either.forLeft(si))

        val syms = workspace.documentSymbols(uri).get(2, TimeUnit.SECONDS)
        assertEquals(1, syms.size)
        assertEquals("x", syms[0].name)
        assertEquals(SymbolKind.Variable, syms[0].kind)
        assertEquals("OuterContainer", syms[0].containerName)
        assertEquals(0, syms[0].range.startLine)
        assertEquals(4, syms[0].range.startCharacter)
    }

    @Test
    fun `workspaceSymbolsLocated maps modern WorkspaceSymbol with Location`() {
        val sym = WorkspaceSymbol(
            "Widget", SymbolKind.Class,
            Either.forLeft(Location("file:///pkg/Widget.kt", Range(Position(3, 6), Position(3, 12)))),
            "com.example",
        )
        @Suppress("DEPRECATION")
        harness.fakeServer.workspaceSymbolResponse =
            Either.forRight<MutableList<out SymbolInformation>, MutableList<out WorkspaceSymbol>>(mutableListOf(sym))

        val list = workspace.workspaceSymbolsLocated("Widget").get(2, TimeUnit.SECONDS)
        assertEquals(1, list.size)
        assertEquals("Widget", list[0].name)
        assertEquals("com.example", list[0].containerName)
        assertEquals(SymbolKind.Class, list[0].kind)
        val loc = list[0].location
        assertNotNull(loc)
        assertEquals("file:///pkg/Widget.kt", loc!!.uri)
        assertEquals(3, loc.range.startLine)
        assertEquals(6, loc.range.startCharacter)
        assertEquals(12, loc.range.endCharacter)

        waitUntil { harness.fakeServer.workspaceSymbolCalls.isNotEmpty() }
        assertEquals("Widget", harness.fakeServer.workspaceSymbolCalls.first().query)
    }

    @Test
    fun `workspaceSymbolsLocated maps WorkspaceSymbol with WorkspaceSymbolLocation (uri-only)`() {
        val sym = WorkspaceSymbol(
            "Lazy", SymbolKind.Function,
            Either.forRight(WorkspaceSymbolLocation("file:///pkg/Lazy.kt")),
            "com.example",
        )
        @Suppress("DEPRECATION")
        harness.fakeServer.workspaceSymbolResponse =
            Either.forRight<MutableList<out SymbolInformation>, MutableList<out WorkspaceSymbol>>(mutableListOf(sym))

        val list = workspace.workspaceSymbolsLocated("Lazy").get(2, TimeUnit.SECONDS)
        assertEquals(1, list.size)
        val loc = list[0].location
        assertNotNull(loc)
        assertEquals("file:///pkg/Lazy.kt", loc!!.uri)
        assertEquals(0, loc.range.startLine)
        assertEquals(0, loc.range.startCharacter)
    }

    @Test
    fun `workspaceSymbolsLocated maps legacy SymbolInformation list`() {
        @Suppress("DEPRECATION")
        val si = SymbolInformation(
            "Helper", SymbolKind.Class,
            Location("file:///pkg/Helper.kt", Range(Position(1, 0), Position(1, 6))),
            "com.example",
        )
        @Suppress("DEPRECATION")
        harness.fakeServer.workspaceSymbolResponse =
            Either.forLeft<MutableList<out SymbolInformation>, MutableList<out WorkspaceSymbol>>(mutableListOf(si))

        val list = workspace.workspaceSymbolsLocated("Helper").get(2, TimeUnit.SECONDS)
        assertEquals(1, list.size)
        assertEquals("Helper", list[0].name)
        assertEquals("com.example", list[0].containerName)
        val loc = list[0].location
        assertNotNull(loc)
        assertEquals("file:///pkg/Helper.kt", loc!!.uri)
        assertEquals(1, loc.range.startLine)
    }

    @Test
    fun `workspaceSymbolsLocated returns empty for null server response`() {
        harness.fakeServer.workspaceSymbolResponse = null
        val list = workspace.workspaceSymbolsLocated("X").get(2, TimeUnit.SECONDS)
        assertTrue(list.isEmpty())
    }

    @Test
    fun `formatting on unopened doc returns empty without server call`() {
        val edits = workspace.formatting("file:///nope.kt").get(2, TimeUnit.SECONDS)
        assertTrue(edits.isEmpty())
        assertTrue(harness.fakeServer.formattingCalls.isEmpty())
    }

    @Test
    fun `formatting forwards params and maps text edits`() {
        val uri = "file:///FMT.kt"
        workspace.didOpen(uri, "kotlin", "fun  a()  {}")
        harness.fakeServer.formattingResponse = mutableListOf(
            TextEdit(Range(Position(0, 3), Position(0, 5)), " "),
            TextEdit(Range(Position(0, 7), Position(0, 9)), " "),
        )

        val edits = workspace.formatting(uri, tabSize = 2, insertSpaces = true).get(2, TimeUnit.SECONDS)
        assertEquals(2, edits.size)
        assertEquals(0, edits[0].startLine)
        assertEquals(3, edits[0].startCharacter)
        assertEquals(5, edits[0].endCharacter)
        assertEquals(" ", edits[0].newText)
        assertEquals(7, edits[1].startCharacter)

        waitUntil { harness.fakeServer.formattingCalls.isNotEmpty() }
        val sent = harness.fakeServer.formattingCalls.first()
        assertEquals(uri, sent.textDocument.uri)
        assertEquals(2, sent.options.tabSize)
        assertTrue(sent.options.isInsertSpaces)
    }

    @Test
    fun `formatting passes tabs option when insertSpaces is false`() {
        val uri = "file:///FMT2.kt"
        workspace.didOpen(uri, "kotlin", "x")
        workspace.formatting(uri, tabSize = 4, insertSpaces = false).get(2, TimeUnit.SECONDS)
        waitUntil { harness.fakeServer.formattingCalls.isNotEmpty() }
        val sent = harness.fakeServer.formattingCalls.first()
        assertEquals(4, sent.options.tabSize)
        assertFalse(sent.options.isInsertSpaces)
    }

    @Test
    fun `codeAction on unopened doc returns empty without server call`() {
        val list = workspace.codeAction("file:///nope.kt", 0, 0, 0, 0).get(2, TimeUnit.SECONDS)
        assertTrue(list.isEmpty())
        assertTrue(harness.fakeServer.codeActionCalls.isEmpty())
    }

    @Test
    fun `codeAction maps CodeAction with workspace edit and isPreferred`() {
        val uri = "file:///CA.kt"
        workspace.didOpen(uri, "kotlin", "val x = 1")
        val we = WorkspaceEdit().apply {
            changes = mutableMapOf(
                uri to mutableListOf(TextEdit(Range(Position(0, 4), Position(0, 5)), "y"))
            )
        }
        val action = CodeAction("Rename to y").apply {
            kind = "quickfix"
            isPreferred = true
            edit = we
        }
        harness.fakeServer.codeActionResponse = mutableListOf(Either.forRight(action))

        val list = workspace.codeAction(uri, 0, 4, 0, 5).get(2, TimeUnit.SECONDS)
        assertEquals(1, list.size)
        assertEquals("Rename to y", list[0].title)
        assertEquals("quickfix", list[0].kind)
        assertTrue(list[0].isPreferred)
        assertTrue(list[0].hasEdit)
        assertTrue(list[0].isExecutable)
        assertEquals(1, list[0].edit.changes.size)
        assertEquals("y", list[0].edit.changes[0].edits[0].newText)
    }

    @Test
    fun `codeAction maps Command as executable command-only entry`() {
        val uri = "file:///CC.kt"
        workspace.didOpen(uri, "kotlin", "x")
        val cmd = Command("Show in panel", "page.showPanel", listOf("arg1"))
        harness.fakeServer.codeActionResponse = mutableListOf(Either.forLeft(cmd))

        val list = workspace.codeAction(uri, 0, 0, 0, 0).get(2, TimeUnit.SECONDS)
        assertEquals(1, list.size)
        assertEquals("Show in panel", list[0].title)
        assertNull(list[0].kind)
        assertFalse(list[0].hasEdit)
        assertTrue(list[0].hasCommand)
        assertTrue(list[0].isExecutable)
        assertEquals("page.showPanel", list[0].command)
        assertEquals(1, list[0].commandArguments.size)
        assertTrue(list[0].commandArguments[0].toString().contains("arg1"))
    }

    @Test
    fun `codeAction forwards range and diagnostics in context`() {
        val uri = "file:///CD.kt"
        workspace.didOpen(uri, "kotlin", "val z = 1")
        val diag = org.eclipse.lsp4j.Diagnostic(
            Range(Position(0, 4), Position(0, 5)),
            "unused variable",
        )
        harness.fakeServer.codeActionResponse = mutableListOf()
        workspace.codeAction(uri, 0, 2, 0, 6, listOf(diag)).get(2, TimeUnit.SECONDS)

        waitUntil { harness.fakeServer.codeActionCalls.isNotEmpty() }
        val sent = harness.fakeServer.codeActionCalls.first()
        assertEquals(uri, sent.textDocument.uri)
        assertEquals(0, sent.range.start.line)
        assertEquals(2, sent.range.start.character)
        assertEquals(6, sent.range.end.character)
        assertEquals(1, sent.context.diagnostics.size)
        assertEquals("unused variable", sent.context.diagnostics[0].message)
    }

    @Test
    fun `codeAction returns empty for null server response`() {
        val uri = "file:///CE.kt"
        workspace.didOpen(uri, "kotlin", "x")
        harness.fakeServer.codeActionResponse = null
        val list = workspace.codeAction(uri, 0, 0, 0, 0).get(2, TimeUnit.SECONDS)
        assertTrue(list.isEmpty())
    }

    @Test
    fun `inlayHints on unopened doc returns empty without server call`() {
        val list = workspace.inlayHints("file:///nope.kt", 0, 0, 5, 0).get(2, TimeUnit.SECONDS)
        assertTrue(list.isEmpty())
        assertTrue(harness.fakeServer.inlayHintCalls.isEmpty())
    }

    @Test
    fun `inlayHints forwards range and parses Parameter and Type hints`() {
        val uri = "file:///IH.kt"
        workspace.didOpen(uri, "kotlin", "fun greet(name: String) = name\nval x = greet(\"page\")")
        val paramHint = org.eclipse.lsp4j.InlayHint(
            Position(1, 14),
            Either.forLeft<String, MutableList<org.eclipse.lsp4j.InlayHintLabelPart>>("name:"),
        ).apply {
            kind = org.eclipse.lsp4j.InlayHintKind.Parameter
            paddingRight = true
        }
        val typeHint = org.eclipse.lsp4j.InlayHint(
            Position(1, 5),
            Either.forLeft<String, MutableList<org.eclipse.lsp4j.InlayHintLabelPart>>(": String"),
        ).apply {
            kind = org.eclipse.lsp4j.InlayHintKind.Type
            paddingLeft = true
        }
        harness.fakeServer.inlayHintResponse = mutableListOf(paramHint, typeHint)

        val list = workspace.inlayHints(uri, 0, 0, 2, 0).get(2, TimeUnit.SECONDS)
        assertEquals(2, list.size)

        val param = list.first { it.kind == InlayHintItem.Kind.PARAMETER }
        assertEquals(1, param.line)
        assertEquals(14, param.character)
        assertEquals("name:", param.label)
        assertTrue(param.paddingRight)
        assertFalse(param.paddingLeft)

        val type = list.first { it.kind == InlayHintItem.Kind.TYPE }
        assertEquals(": String", type.label)
        assertTrue(type.paddingLeft)
        assertFalse(type.paddingRight)

        waitUntil { harness.fakeServer.inlayHintCalls.isNotEmpty() }
        val sent = harness.fakeServer.inlayHintCalls.first()
        assertEquals(uri, sent.textDocument.uri)
        assertEquals(0, sent.range.start.line)
        assertEquals(0, sent.range.start.character)
        assertEquals(2, sent.range.end.line)
    }

    @Test
    fun `inlayHints joins label part list into single string`() {
        val uri = "file:///IH2.kt"
        workspace.didOpen(uri, "kotlin", "x")
        val parts = mutableListOf(
            org.eclipse.lsp4j.InlayHintLabelPart("name"),
            org.eclipse.lsp4j.InlayHintLabelPart(":"),
        )
        val hint = org.eclipse.lsp4j.InlayHint(
            Position(0, 3),
            Either.forRight<String, MutableList<org.eclipse.lsp4j.InlayHintLabelPart>>(parts),
        ).apply { kind = org.eclipse.lsp4j.InlayHintKind.Parameter }
        harness.fakeServer.inlayHintResponse = mutableListOf(hint)

        val list = workspace.inlayHints(uri, 0, 0, 1, 0).get(2, TimeUnit.SECONDS)
        assertEquals(1, list.size)
        assertEquals("name:", list[0].label)
    }

    @Test
    fun `inlayHints drops blank and missing-position entries`() {
        val uri = "file:///IH3.kt"
        workspace.didOpen(uri, "kotlin", "x")
        val blank = org.eclipse.lsp4j.InlayHint(
            Position(0, 0),
            Either.forLeft<String, MutableList<org.eclipse.lsp4j.InlayHintLabelPart>>("   "),
        )
        val noPos = org.eclipse.lsp4j.InlayHint().apply {
            label = Either.forLeft<String, MutableList<org.eclipse.lsp4j.InlayHintLabelPart>>("name:")
        }
        val good = org.eclipse.lsp4j.InlayHint(
            Position(0, 1),
            Either.forLeft<String, MutableList<org.eclipse.lsp4j.InlayHintLabelPart>>("ok"),
        )
        harness.fakeServer.inlayHintResponse = mutableListOf(blank, noPos, good)

        val list = workspace.inlayHints(uri, 0, 0, 1, 0).get(2, TimeUnit.SECONDS)
        assertEquals(1, list.size)
        assertEquals("ok", list[0].label)
    }

    @Test
    fun `inlayHints returns empty for null server response`() {
        val uri = "file:///IH4.kt"
        workspace.didOpen(uri, "kotlin", "x")
        harness.fakeServer.inlayHintResponse = null
        val list = workspace.inlayHints(uri, 0, 0, 1, 0).get(2, TimeUnit.SECONDS)
        assertTrue(list.isEmpty())
    }

    @Test
    fun `completion on unopened doc returns empty without server call`() {
        val list = workspace.completion("file:///nope.kt", 0, 0).get(2, TimeUnit.SECONDS)
        assertTrue(list.items.isEmpty())
        assertTrue(harness.fakeServer.completionCalls.isEmpty())
    }

    @Test
    fun `completion assigns resolve tokens to items`() {
        val uri = "file:///CP.kt"
        workspace.didOpen(uri, "kotlin", "x")
        harness.fakeServer.completionResponse = Either.forLeft(
            mutableListOf(
                CompletionItem("foo").apply { detail = "fun foo()" },
                CompletionItem("bar").apply { detail = "fun bar()" },
            ),
        )

        val list = workspace.completion(uri, 0, 0).get(2, TimeUnit.SECONDS)
        assertEquals(2, list.items.size)
        val tokens = list.items.mapNotNull { it.resolveToken }
        assertEquals(2, tokens.size)
        assertEquals(2, tokens.toSet().size)
    }

    @Test
    fun `resolveCompletionItem returns documentation and additional edits`() {
        val uri = "file:///CR.kt"
        workspace.didOpen(uri, "kotlin", "x")
        harness.fakeServer.completionResponse = Either.forLeft(
            mutableListOf(CompletionItem("HashMap")),
        )
        val list = workspace.completion(uri, 0, 0).get(2, TimeUnit.SECONDS)
        val token = list.items.first().resolveToken
        assertNotNull(token)

        harness.fakeServer.resolveCompletionResponse = CompletionItem("HashMap").apply {
            documentation = Either.forRight(MarkupContent(MarkupKind.MARKDOWN, "A hash-based map."))
            detail = "java.util.HashMap"
            additionalTextEdits = mutableListOf(
                TextEdit(Range(Position(0, 0), Position(0, 0)), "import java.util.HashMap;\n"),
            )
        }

        val resolved = workspace.resolveCompletionItem(token!!).get(2, TimeUnit.SECONDS)
        assertNotNull(resolved)
        assertEquals("A hash-based map.", resolved!!.documentation)
        assertEquals("java.util.HashMap", resolved.detail)
        assertEquals(1, resolved.additionalEdits.size)
        assertEquals("import java.util.HashMap;\n", resolved.additionalEdits[0].newText)
        assertEquals(0, resolved.additionalEdits[0].startLine)

        waitUntil { harness.fakeServer.resolveCompletionCalls.isNotEmpty() }
        assertEquals("HashMap", harness.fakeServer.resolveCompletionCalls.first().label)
    }

    @Test
    fun `resolveCompletionItem with unknown token returns null without server call`() {
        val resolved = workspace.resolveCompletionItem(987654L).get(2, TimeUnit.SECONDS)
        assertNull(resolved)
        assertTrue(harness.fakeServer.resolveCompletionCalls.isEmpty())
    }

    @Test
    fun `new completion call clears prior resolve registry`() {
        val uri = "file:///CL.kt"
        workspace.didOpen(uri, "kotlin", "x")
        harness.fakeServer.completionResponse = Either.forLeft(mutableListOf(CompletionItem("first")))
        val firstToken = workspace.completion(uri, 0, 0).get(2, TimeUnit.SECONDS).items.first().resolveToken
        assertNotNull(firstToken)

        harness.fakeServer.completionResponse = Either.forLeft(mutableListOf(CompletionItem("second")))
        workspace.completion(uri, 0, 1).get(2, TimeUnit.SECONDS)

        val stale = workspace.resolveCompletionItem(firstToken!!).get(2, TimeUnit.SECONDS)
        assertNull(stale)
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
