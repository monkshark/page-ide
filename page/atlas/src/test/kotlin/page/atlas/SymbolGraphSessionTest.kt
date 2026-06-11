package page.atlas

import page.atlas.graph.CallHierarchySource
import page.atlas.graph.EdgeKind
import page.atlas.graph.GraphEdge
import page.atlas.graph.NodeKind
import page.atlas.graph.SymbolGraphSession
import page.atlas.graph.SymbolSpec
import page.atlas.graph.symbolNodeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SymbolGraphSessionTest {

    private class FakeSource : CallHierarchySource {
        val incomingBy = HashMap<String, List<SymbolSpec>>()
        val outgoingBy = HashMap<String, List<SymbolSpec>>()
        val requests = mutableListOf<String>()

        override fun incoming(symbol: SymbolSpec): List<SymbolSpec> {
            requests += "in:${symbol.name}"
            return incomingBy[symbol.name].orEmpty()
        }

        override fun outgoing(symbol: SymbolSpec): List<SymbolSpec> {
            requests += "out:${symbol.name}"
            return outgoingBy[symbol.name].orEmpty()
        }
    }

    private fun spec(name: String, line: Int = 1) =
        SymbolSpec(name = name, detail = null, uri = "file:///c:/ws/$name.kt", line = line)

    @Test
    fun `start expands the root one depth with calls edges in both directions`() {
        val source = FakeSource()
        source.incomingBy["target"] = listOf(spec("caller"))
        source.outgoingBy["target"] = listOf(spec("callee"))
        val session = SymbolGraphSession(source)

        val slice = session.start(spec("target"))

        val rootId = symbolNodeId(spec("target"))
        assertEquals(rootId, session.rootId)
        assertEquals(setOf("target", "caller", "callee"), slice.nodes.map { it.label }.toSet())
        assertTrue(slice.nodes.all { it.kind == NodeKind.SYMBOL })
        val callerId = symbolNodeId(spec("caller"))
        val calleeId = symbolNodeId(spec("callee"))
        assertEquals(
            setOf(
                GraphEdge(callerId, rootId, EdgeKind.CALLS),
                GraphEdge(rootId, calleeId, EdgeKind.CALLS),
            ),
            slice.edges.toSet(),
        )
    }

    @Test
    fun `symbol nodes carry a path derived from the uri`() {
        val source = FakeSource()
        val session = SymbolGraphSession(source)
        val slice = session.start(spec("target"))
        assertNotNull(slice.nodes.single().path)
        assertEquals("target.kt", slice.nodes.single().path?.fileName?.toString())
    }

    @Test
    fun `expand grows exactly one depth per request`() {
        val source = FakeSource()
        source.incomingBy["target"] = listOf(spec("caller"))
        source.incomingBy["caller"] = listOf(spec("grandCaller"))
        val session = SymbolGraphSession(source)
        session.start(spec("target"))
        assertFalse(session.slice.nodes.any { it.label == "grandCaller" })

        val callerId = symbolNodeId(spec("caller"))
        val slice = session.expand(callerId)

        assertTrue(slice.nodes.any { it.label == "grandCaller" })
        val grandId = symbolNodeId(spec("grandCaller"))
        assertTrue(GraphEdge(grandId, callerId, EdgeKind.CALLS) in slice.edges)
    }

    @Test
    fun `nodes at max depth are not expanded`() {
        val source = FakeSource()
        source.incomingBy["target"] = listOf(spec("d1"))
        source.incomingBy["d1"] = listOf(spec("d2"))
        source.incomingBy["d2"] = listOf(spec("d3"))
        val session = SymbolGraphSession(source)
        session.start(spec("target"))
        session.expand(symbolNodeId(spec("d1")))
        val before = session.slice

        val d2Id = symbolNodeId(spec("d2"))
        assertFalse(session.canExpand(d2Id))
        val after = session.expand(d2Id)

        assertEquals(before, after)
        assertFalse(source.requests.contains("in:d2"))
    }

    @Test
    fun `expanding the same node twice hits the source once`() {
        val source = FakeSource()
        source.incomingBy["target"] = listOf(spec("caller"))
        val session = SymbolGraphSession(source)
        session.start(spec("target"))

        val callerId = symbolNodeId(spec("caller"))
        session.expand(callerId)
        session.expand(callerId)

        assertEquals(1, source.requests.count { it == "in:caller" })
        assertFalse(session.canExpand(callerId))
    }

    @Test
    fun `node cap stops adding neighbors and their edges`() {
        val source = FakeSource()
        source.outgoingBy["target"] = (1..5).map { spec("callee$it") }
        val session = SymbolGraphSession(source, maxNodes = 3)

        val slice = session.start(spec("target"))

        assertEquals(3, slice.nodes.size)
        assertEquals(setOf("target", "callee1", "callee2"), slice.nodes.map { it.label }.toSet())
        assertEquals(2, slice.edges.size)
    }

    @Test
    fun `recursive calls do not duplicate nodes or create self edges`() {
        val source = FakeSource()
        source.incomingBy["target"] = listOf(spec("target"), spec("caller"))
        source.outgoingBy["caller"] = listOf(spec("target"))
        val session = SymbolGraphSession(source)
        session.start(spec("target"))
        val slice = session.expand(symbolNodeId(spec("caller")))

        assertEquals(2, slice.nodes.size)
        val rootId = symbolNodeId(spec("target"))
        assertFalse(slice.edges.any { it.from == it.to })
        assertEquals(1, slice.edges.count { it.from == symbolNodeId(spec("caller")) && it.to == rootId })
    }

    @Test
    fun `start resets state from a previous session`() {
        val source = FakeSource()
        source.incomingBy["first"] = listOf(spec("a"))
        source.incomingBy["second"] = listOf(spec("b"))
        val session = SymbolGraphSession(source)
        session.start(spec("first"))

        val slice = session.start(spec("second"))

        assertEquals(setOf("second", "b"), slice.nodes.map { it.label }.toSet())
        assertEquals(symbolNodeId(spec("second")), session.rootId)
    }

    @Test
    fun `canExpand is false for unknown ids and true for fresh depth-1 nodes`() {
        val source = FakeSource()
        source.incomingBy["target"] = listOf(spec("caller"))
        val session = SymbolGraphSession(source)
        session.start(spec("target"))

        assertFalse(session.canExpand("missing"))
        assertTrue(session.canExpand(symbolNodeId(spec("caller"))))
        assertFalse(session.canExpand(symbolNodeId(spec("target"))))
    }
}
