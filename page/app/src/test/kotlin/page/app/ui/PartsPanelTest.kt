package page.app.ui

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import page.atlas.graph.FileFacts
import page.atlas.graph.FileMargins
import page.atlas.graph.GraphNode
import page.atlas.graph.Need
import page.atlas.graph.NodeKind
import page.shared.path.FilePath
import page.atlas.graph.MapPart
import page.atlas.graph.PartTie
import page.atlas.graph.ProjectMap
import page.atlas.graph.ProjectMapBuilder
import page.ui.GlassTheme
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class PartsPanelTest {

    private val root = Path.of("/proj").toAbsolutePath()
    private fun file(rel: String): Path = root.resolve(rel)
    private fun part(id: String, layer: Int, vararg files: String): MapPart =
        MapPart(id, id, "Descriptions are not shown", root.resolve("page/$id"), files.map { file(it) }, layer)

    private val project: ProjectMap = ProjectMap(
        "IDE",
        listOf(
            part("app", 2, "app/Main.kt"),
            part("language", 1, "language/LspRouter.kt"),
            part("lsp", 1, "lsp/LspTransport.kt", "lsp/LspClient.kt", "lsp/JsonRpc.kt"),
            part("core", 0, "core/Env.kt", "core/Paths.kt"),
            MapPart(ProjectMapBuilder.OTHER_ID, "Other", "", null, listOf(file("tools/gen.kt")), 0),
        ),
        emptyList(),
    )

    @Test
    fun `tiers run from the top layer down and leave other out`() {
        assertEquals(listOf(listOf("app"), listOf("lsp", "language"), listOf("core")), partTiers(project).map { tier -> tier.map { it.id } })
    }

    @Test
    fun `relations name the open file in plain words`() {
        assertEquals("Uses this file", relationLabel(PartTie(1, false)))
        assertEquals("Uses this file", relationLabel(PartTie(2, false)))
        assertEquals("Used by this file", relationLabel(PartTie(0, true)))
        assertEquals("Uses this file · Used by this file", relationLabel(PartTie(2, true)))
        assertNull(relationLabel(null))
        assertNull(relationLabel(PartTie(0, false)))
    }

    @Test
    fun `file rows show their users as a number or the loop`() {
        assertEquals("loop", factCaption(FileFacts(4, true)))
        assertEquals("6", factCaption(FileFacts(6, false)))
        assertNull(factCaption(null))
    }

    @Test
    fun `a part is described by its folder`() {
        assertEquals("page/lsp", partFolder(project.part("lsp")!!, root))
        assertNull(partFolder(project.part(ProjectMapBuilder.OTHER_ID)!!, root))
    }

    @Test
    fun `the open file's part is listed and another part can be picked`() = runComposeUiTest {
        var opened: Path? = null
        setContent {
            GlassTheme {
                PartsPanel(
                    map = project,
                    currentFile = file("lsp/LspTransport.kt"),
                    ties = mapOf("app" to PartTie(1, false), "core" to PartTie(0, true), "lsp" to PartTie(1, true)),
                    loadFacts = { files -> files.associateWith { FileFacts(2, it.fileName.toString() == "LspClient.kt") } },
                    onOpenFile = { opened = it },
                    root = root,
                )
            }
        }
        onNodeWithTag("parts-all").performClick()
        onNodeWithTag("relation-app", useUnmergedTree = true).assertTextEquals("Uses this file")
        onNodeWithTag("relation-core", useUnmergedTree = true).assertTextEquals("Used by this file")
        onNodeWithText("Other files").assertIsDisplayed()
        assertEquals(0, onAllNodes(hasText("Descriptions are not shown")).fetchSemanticsNodes().size)
        waitUntil(timeoutMillis = 3_000) { onAllNodes(hasText("loop")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("JsonRpc.kt").performClick()
        assertEquals(file("lsp/JsonRpc.kt"), opened)
        onNodeWithTag("part-core").performClick()
        onNodeWithText("page/core").assertIsDisplayed()
        onNodeWithText("Env.kt").assertIsDisplayed()
    }

    @Test
    fun `an empty map points to the files view`() = runComposeUiTest {
        var browsed = false
        setContent {
            GlassTheme {
                PartsPanel(map = ProjectMap.EMPTY, currentFile = null, ties = emptyMap(), loadFacts = { emptyMap() }, onOpenFile = {}, onBrowseFiles = { browsed = true })
            }
        }
        onNodeWithText("No parts found").assertIsDisplayed()
        onNodeWithText("Browse in Files").performClick()
        assertEquals(true, browsed)
    }

    private fun node(rel: String) = GraphNode(file(rel).toString(), rel.substringAfterLast('/'), FilePath.of(file(rel).toString()), NodeKind.WORKSPACE_FILE)
    private val margins = FileMargins(
        needs = listOf(Need(node("core/Env.kt"), 3)),
        usedBy = listOf(node("app/Main.kt"), node("lsp/LspClient.kt")),
        cycle = emptyList(),
    )

    @Test
    fun `related files are the open file's users and needs inside one part`() {
        assertEquals(RelatedFiles(listOf(file("app/Main.kt")), emptyList()), relatedFiles(project, margins, "app"))
        assertEquals(RelatedFiles(emptyList(), listOf(file("core/Env.kt"))), relatedFiles(project, margins, "core"))
        assertEquals(RelatedFiles(emptyList(), emptyList()), relatedFiles(project, null, "app"))
    }

    @Test
    fun `a relation opens its only file or pins several`() = runComposeUiTest {
        var opened: Path? = null
        val linked = FileMargins(
            needs = listOf(Need(node("core/Env.kt"), 3), Need(node("core/Paths.kt"), 4)),
            usedBy = listOf(node("app/Main.kt")),
            cycle = emptyList(),
        )
        setContent {
            GlassTheme {
                PartsPanel(
                    map = project,
                    currentFile = file("lsp/LspTransport.kt"),
                    ties = mapOf("app" to PartTie(1, false), "core" to PartTie(0, true)),
                    loadFacts = { emptyMap() },
                    onOpenFile = { opened = it },
                    root = root,
                    margins = linked,
                )
            }
        }
        onNodeWithTag("relation-action-app").assertTextEquals("Open Main.kt")
        onNodeWithTag("relation-action-app").performClick()
        assertEquals(file("app/Main.kt"), opened)
        onNodeWithTag("relation-action-core").assertTextEquals("Show 2 files")
        onNodeWithTag("relation-action-core").performClick()
        onNodeWithText("page/core").assertIsDisplayed()
        onNodeWithTag("pinned-label").assertTextEquals("Used by this file, 2 files")
    }

    @Test
    fun `picking another part offers the way back to the open file's part`() = runComposeUiTest {
        setContent {
            GlassTheme {
                PartsPanel(map = project, currentFile = file("lsp/LspTransport.kt"), ties = emptyMap(), loadFacts = { emptyMap() }, onOpenFile = {}, root = root)
            }
        }
        onNodeWithTag("parts-all").performClick()
        assertEquals(0, onAllNodes(hasText("Back to lsp")).fetchSemanticsNodes().size)
        onNodeWithTag("part-core").performClick()
        onNodeWithTag("parts-back").assertTextEquals("Back to lsp")
        onNodeWithTag("parts-back").performClick()
        onNodeWithText("page/lsp").assertIsDisplayed()
        assertEquals(0, onAllNodes(hasText("Back to lsp")).fetchSemanticsNodes().size)
    }

    @Test
    fun `the stack opens on the parts linked to the open file`() = runComposeUiTest {
        setContent {
            GlassTheme {
                PartsPanel(map = project, currentFile = file("lsp/LspTransport.kt"), ties = mapOf("core" to PartTie(0, true)), loadFacts = { emptyMap() }, onOpenFile = {}, root = root)
            }
        }
        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasTestTag("part-app")).fetchSemanticsNodes().size)
        onNodeWithTag("part-core").assertIsDisplayed()
        onNodeWithTag("part-lsp").assertIsDisplayed()
        onNodeWithText("Show 3 more parts").assertIsDisplayed()
        onNodeWithTag("parts-more").performClick()
        onNodeWithTag("part-app").assertIsDisplayed()
        onNodeWithTag("parts-linked").performClick()
        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasTestTag("part-app")).fetchSemanticsNodes().size)
    }

    @Test
    fun `files sort by their users and narrow with the filter`() = runComposeUiTest {
        val counts = mapOf("LspTransport.kt" to 1, "LspClient.kt" to 9, "JsonRpc.kt" to 4)
        setContent {
            GlassTheme {
                PartsPanel(
                    map = project,
                    currentFile = file("lsp/LspTransport.kt"),
                    ties = emptyMap(),
                    loadFacts = { files -> files.associateWith { FileFacts(counts.getValue(it.fileName.toString()), false) } },
                    onOpenFile = {},
                    root = root,
                )
            }
        }
        waitUntil(timeoutMillis = 3_000) { onAllNodes(hasText("9")).fetchSemanticsNodes().isNotEmpty() }
        fun order() = onAllNodes(hasText(".kt", substring = true) and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("part-files"))).fetchSemanticsNodes()
            .mapNotNull { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.firstOrNull()?.text }
            .filter { it in counts.keys }
        assertEquals(listOf("JsonRpc.kt", "LspClient.kt", "LspTransport.kt"), order())
        onNodeWithTag("parts-sort-most").performClick()
        assertEquals(listOf("LspClient.kt", "JsonRpc.kt", "LspTransport.kt"), order())
        onNodeWithTag("parts-filter").performTextInput("client")
        assertEquals(listOf("LspClient.kt"), order())
    }

    @Test
    fun `returning to a part shows its last numbers while new ones load`() = runComposeUiTest {
        var calls = 0
        setContent {
            GlassTheme {
                PartsPanel(
                    map = project,
                    currentFile = file("lsp/LspTransport.kt"),
                    ties = emptyMap(),
                    loadFacts = { files ->
                        calls++
                        if (calls >= 3) Thread.sleep(2_000)
                        files.associateWith { FileFacts(7, false) }
                    },
                    onOpenFile = {},
                    root = root,
                )
            }
        }
        onNodeWithTag("parts-all").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodes(hasText("7")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("part-core").performClick()
        waitUntil(timeoutMillis = 3_000) { calls >= 2 }
        onNodeWithTag("parts-back").performClick()
        onNodeWithTag("file-users-JsonRpc.kt", useUnmergedTree = true).assertTextEquals("7")
    }

    @Test
    fun `the keyboard moves through rows, picks with enter and jumps to the filter`() = runComposeUiTest {
        var opened: Path? = null
        setContent {
            GlassTheme {
                PartsPanel(map = project, currentFile = file("lsp/LspTransport.kt"), ties = emptyMap(), loadFacts = { emptyMap() }, onOpenFile = { opened = it }, root = root)
            }
        }
        onNodeWithTag("parts-all").performClick()
        onNodeWithTag("part-core").requestFocus()
        onNodeWithTag("part-core").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }
        onNodeWithText("page/core").assertIsDisplayed()
        onNodeWithTag("part-core").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionDown) }
        onNodeWithTag("part-other").assertIsFocused()
        onNodeWithTag("part-other").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Slash) }
        onNodeWithTag("parts-filter").assertIsFocused()
        onNodeWithTag("file-row-Env.kt").requestFocus()
        onNodeWithTag("file-row-Env.kt").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }
        assertEquals(file("core/Env.kt"), opened)
    }

    @Test
    fun `the spine explains each dot with real file names`() {
        val app = project.part("app")!!
        val core = project.part("core")!!
        val none = RelatedFiles(emptyList(), emptyList())
        assertEquals("LspTransport.kt is in lsp", spineTip(project.part("lsp")!!, true, "LspTransport.kt", none))
        assertEquals("app depends on LspTransport.kt through Main.kt", spineTip(app, false, "LspTransport.kt", RelatedFiles(listOf(file("app/Main.kt")), emptyList())))
        assertEquals("LspTransport.kt depends on core through Env.kt", spineTip(core, false, "LspTransport.kt", RelatedFiles(emptyList(), listOf(file("core/Env.kt")))))
        assertEquals("core has no link to LspTransport.kt", spineTip(core, false, "LspTransport.kt", none))
        val many = (1..5).map { file("app/F$it.kt") }
        assertEquals("app depends on LspTransport.kt through F1.kt, F2.kt, F3.kt and 2 more", spineTip(app, false, "LspTransport.kt", RelatedFiles(many, emptyList())))
        assertEquals("Files outside every part", spineTip(project.part(ProjectMapBuilder.OTHER_ID)!!, false, "LspTransport.kt", none))
    }

    @Test
    fun `the context line breaks the users down by part`() {
        val m = FileMargins(
            needs = listOf(Need(node("core/Env.kt"), 3)),
            usedBy = listOf(node("app/Main.kt"), node("lsp/LspClient.kt"), node("lsp/JsonRpc.kt")),
            cycle = emptyList(),
        )
        assertEquals("Used by 3 files (2 in lsp, 1 in app). Uses 1 file", contextSummary(project, m))
        assertEquals("Used by no files. Uses 0 files", contextSummary(project, FileMargins(emptyList(), emptyList(), emptyList())))
        assertNull(contextSummary(project, null))
    }

    @Test
    fun `the picked part is marked selected and the open file's part carries a tag`() = runComposeUiTest {
        setContent {
            GlassTheme {
                PartsPanel(map = project, currentFile = file("lsp/LspTransport.kt"), ties = emptyMap(), loadFacts = { emptyMap() }, onOpenFile = {}, root = root)
            }
        }
        onNodeWithTag("part-here-tag", useUnmergedTree = true).assertTextEquals("open file here")
        onNodeWithTag("part-lsp").assertIsNotSelected()
        onNodeWithTag("parts-all").performClick()
        onNodeWithTag("part-core").performClick()
        onNodeWithTag("part-core").assertIsSelected()
    }

    @Test
    fun `a filter with no match says so, offers a way out and survives a part switch`() = runComposeUiTest {
        setContent {
            GlassTheme {
                PartsPanel(map = project, currentFile = file("lsp/LspTransport.kt"), ties = emptyMap(), loadFacts = { emptyMap() }, onOpenFile = {}, root = root)
            }
        }
        onNodeWithTag("parts-filter").performTextInput("zzz")
        onNodeWithText("No file in lsp matches “zzz”").assertIsDisplayed()
        onNodeWithTag("parts-all").performClick()
        onNodeWithTag("part-core").performClick()
        onNodeWithText("No file in core matches “zzz”").assertIsDisplayed()
        onNodeWithTag("parts-clear").performClick()
        onNodeWithText("Env.kt").assertIsDisplayed()
    }
}
