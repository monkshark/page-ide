package page.atlas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.render.SceneModel
import page.atlas.render.buildScene
import page.atlas.render.projectScene
import page.atlas.render.sceneRadius

class Scene3DTest {

    private fun node(id: String, kind: NodeKind = NodeKind.WORKSPACE_FILE) = GraphNode(id, id, null, kind)

    private fun slice(nodes: List<String>, edges: List<Pair<String, String>>) = GraphSlice(
        nodes.map { node(it) },
        edges.map { (from, to) -> GraphEdge(from, to) },
    )

    @Test
    fun `single node sits at origin and projects to canvas center`() {
        val model = buildScene(slice(listOf("a"), emptyList()))
        val n = model.nodes.single()
        assertEquals(Triple(0f, 0f, 0f), Triple(n.x, n.y, n.z))
        val p = projectScene(model, yaw = 0.7f, pitch = 0.4f, zoom = 1f, width = 400f, height = 600f).single()
        assertEquals(200f, p.x)
        assertEquals(300f, p.y)
    }

    @Test
    fun `importer is placed above its dependency`() {
        val model = buildScene(slice(listOf("top", "leaf"), listOf("top" to "leaf")))
        val byId = model.nodes.associateBy { it.id }
        assertTrue(byId.getValue("top").y > byId.getValue("leaf").y)
    }

    @Test
    fun `same layer nodes occupy distinct positions`() {
        val model = buildScene(
            slice(listOf("hub", "a", "b", "c"), listOf("hub" to "a", "hub" to "b", "hub" to "c")),
        )
        val leaves = model.nodes.filter { it.id != "hub" }
        assertEquals(3, leaves.map { it.x to it.z }.distinct().size)
    }

    @Test
    fun `cycle terminates and still assigns layers`() {
        val model = buildScene(slice(listOf("a", "b"), listOf("a" to "b", "b" to "a")))
        assertEquals(2, model.nodes.size)
        assertTrue(sceneRadius(model) >= 1f)
    }

    @Test
    fun `yaw rotation by pi mirrors x around center`() {
        val model = buildScene(
            slice(listOf("hub", "a", "b"), listOf("hub" to "a", "hub" to "b")),
        )
        val width = 500f
        val front = projectScene(model, 0f, 0f, 1f, width, 500f).associateBy { it.id }
        val back = projectScene(model, PI.toFloat(), 0f, 1f, width, 500f).associateBy { it.id }
        for (id in listOf("a", "b")) {
            val f = front.getValue(id)
            val k = back.getValue(id)
            assertTrue(abs((f.x - width / 2f) / f.scale + (k.x - width / 2f) / k.scale) < 0.5f)
        }
    }

    @Test
    fun `projection is sorted far to near`() {
        val model = buildScene(
            slice(listOf("hub", "a", "b", "c"), listOf("hub" to "a", "hub" to "b", "hub" to "c")),
        )
        val depths = projectScene(model, 0.3f, 0.4f, 1f, 400f, 400f).map { it.depth }
        assertEquals(depths.sortedDescending(), depths)
    }

    @Test
    fun `closer nodes render larger`() {
        val model = buildScene(
            slice(listOf("hub", "a", "b", "c"), listOf("hub" to "a", "hub" to "b", "hub" to "c")),
        )
        val projected = projectScene(model, 0.9f, 0.3f, 1f, 400f, 400f)
        assertTrue(projected.first().scale < projected.last().scale)
    }

    @Test
    fun `deterministic for same input`() {
        val s = slice(listOf("x", "y", "z"), listOf("x" to "y", "y" to "z"))
        assertEquals(buildScene(s), buildScene(s))
    }

    @Test
    fun `rings cover every layer`() {
        val model = buildScene(slice(listOf("top", "mid", "leaf"), listOf("top" to "mid", "mid" to "leaf")))
        assertEquals(3, model.rings.size)
        assertEquals(model.nodes.map { it.y }.distinct().sorted(), model.rings.map { it.y }.sorted())
    }

    @Test
    fun `empty slice yields empty scene`() {
        assertEquals(SceneModel.EMPTY, buildScene(GraphSlice.EMPTY))
    }
}
