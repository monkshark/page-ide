package page.atlas

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.NodeKind
import page.atlas.render.MapDrilldown
import page.atlas.render.mapDrilldown

class MapDrilldownTest {

    private fun node(id: String, external: Boolean = false) = GraphNode(
        id = id,
        label = id.substringAfterLast('\\'),
        path = if (external) null else Paths.get(id),
        kind = if (external) NodeKind.EXTERNAL else NodeKind.WORKSPACE_FILE,
    )

    private val providersDir = "ws\\providers"
    private val homeScreen = "ws\\screens\\home.kt"
    private val settingsScreen = "ws\\screens\\settings.kt"
    private val userProvider = "ws\\providers\\user.kt"
    private val authProvider = "ws\\providers\\auth.kt"
    private val logLib = "kotlin.io"

    private val nodes = listOf(
        node(homeScreen), node(settingsScreen), node(userProvider), node(authProvider),
        node(logLib, external = true),
    )
    private val edges = listOf(
        GraphEdge(homeScreen, userProvider),
        GraphEdge(homeScreen, authProvider),
        GraphEdge(settingsScreen, userProvider),
        GraphEdge(userProvider, logLib),
        GraphEdge(userProvider, authProvider),
    )

    @Test
    fun `null selection yields empty drilldown`() {
        assertEquals(MapDrilldown.EMPTY, mapDrilldown(nodes, edges, null))
    }

    @Test
    fun `file selection lists individual counterpart files in both directions`() {
        val out = mapDrilldown(nodes, edges, userProvider)
        assertEquals(listOf("home.kt", "settings.kt"), out.usedBy.map { it.label })
        assertEquals(listOf("auth.kt"), out.uses.map { it.label })
    }

    @Test
    fun `folder selection crosses the boundary and skips internal edges`() {
        val out = mapDrilldown(nodes, edges, providersDir)
        assertEquals(listOf("home.kt", "settings.kt"), out.usedBy.map { it.label })
        assertTrue(out.uses.isEmpty())
    }

    @Test
    fun `folder selection groups counterparts inside the folder per outside file`() {
        val out = mapDrilldown(nodes, edges, providersDir)
        val home = out.usedBy.first { it.label == "home.kt" }
        assertEquals(listOf("auth.kt", "user.kt"), home.counterparts)
        val settings = out.usedBy.first { it.label == "settings.kt" }
        assertEquals(listOf("user.kt"), settings.counterparts)
    }

    @Test
    fun `external counterparts stay out of the drilldown like the map`() {
        val out = mapDrilldown(nodes, edges, userProvider)
        assertTrue(out.uses.none { it.label == "kotlin.io" })
    }

    @Test
    fun `selection without crossing edges is empty`() {
        val out = mapDrilldown(nodes, edges, "ws\\other\\lonely.kt")
        assertTrue(out.usedBy.isEmpty())
        assertTrue(out.uses.isEmpty())
    }
}
