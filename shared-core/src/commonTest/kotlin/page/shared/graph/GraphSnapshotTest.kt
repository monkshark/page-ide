package page.shared.graph

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphSnapshotTest {

    @Test
    fun parsesNodesAndEdges() {
        val slice = GraphSnapshot.parse(
            """{"nodes":[{"id":"a","label":"A.kt","kind":"ACTIVE"},{"id":"b","label":"B.kt","kind":"EXTERNAL"}],""" +
                """"edges":[{"from":"a","to":"b","kind":"IMPLEMENTS"}]}""",
        )
        assertEquals(2, slice.nodes.size)
        assertEquals(NodeKind.ACTIVE, slice.nodes[0].kind)
        assertEquals(NodeKind.EXTERNAL, slice.nodes[1].kind)
        assertEquals(EdgeKind.IMPLEMENTS, slice.edges[0].kind)
    }

    @Test
    fun unknownKindDefaults() {
        val slice = GraphSnapshot.parse("""{"nodes":[{"id":"x","label":"x"}],"edges":[{"from":"x","to":"y"}]}""")
        assertEquals(NodeKind.WORKSPACE_FILE, slice.nodes[0].kind)
        assertEquals(EdgeKind.IMPORT, slice.edges[0].kind)
    }

    @Test
    fun labelFallsBackToId() {
        val slice = GraphSnapshot.parse("""{"nodes":[{"id":"only-id"}],"edges":[]}""")
        assertEquals("only-id", slice.nodes[0].label)
    }

    @Test
    fun emptyOnMalformedRoot() {
        assertEquals(GraphSlice.EMPTY, GraphSnapshot.parse("[]"))
    }
}
