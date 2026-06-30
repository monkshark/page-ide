package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import page.atlas.graph.GraphNode
import page.atlas.graph.Neighbor
import page.atlas.graph.Neighborhood
import page.atlas.graph.NodeKind
import page.atlas.render.CallsBand
import page.atlas.render.buildCallsBand
import page.atlas.render.symbolDisplayLine

class CallsBandTest {

    private fun node(id: String) = GraphNode(id, id, null, NodeKind.SYMBOL)

    private fun neighborhood(incoming: Int, outgoing: Int, total: Pair<Int, Int> = incoming to outgoing) =
        Neighborhood(
            focus = node("focus"),
            incoming = (0 until incoming).map { Neighbor(node("c$it"), 0) },
            outgoing = (0 until outgoing).map { Neighbor(node("o$it"), 0) },
            incomingTotal = total.first,
            outgoingTotal = total.second,
            inCycle = false,
        )

    @Test
    fun `caps each side at three and folds the tail into one overflow card`() {
        val band = buildCallsBand(336f, 156f, neighborhood(incoming = 5, outgoing = 5))

        val callers = band.cards.filter { it.side < 0 }
        val callees = band.cards.filter { it.side > 0 }
        assertEquals(4, callers.size, "three real callers plus one overflow card")
        assertEquals(4, callees.size, "three real callees plus one overflow card")
        assertEquals(3, callers.count { it.node != null })
        assertEquals(1, callers.count { it.node == null })
        assertEquals(2, callers.first { it.node == null }.overflow, "overflow carries the hidden remainder")
        assertEquals(2, callees.first { it.node == null }.overflow)
    }

    @Test
    fun `no overflow card when a side fits within the cap`() {
        val band = buildCallsBand(336f, 156f, neighborhood(incoming = 2, outgoing = 3))

        val callers = band.cards.filter { it.side < 0 }
        val callees = band.cards.filter { it.side > 0 }
        assertEquals(2, callers.size)
        assertEquals(3, callees.size)
        assertTrue(band.cards.all { it.node != null }, "every card is a real symbol")
    }

    @Test
    fun `callers sit left of the focus and callees sit right`() {
        val band = buildCallsBand(336f, 156f, neighborhood(incoming = 2, outgoing = 2))
        val cx = band.focus.center.x

        assertTrue(band.cards.filter { it.side < 0 }.all { it.rect.right < band.focus.left })
        assertTrue(band.cards.filter { it.side > 0 }.all { it.rect.left > band.focus.right })
        assertTrue(band.cards.filter { it.side < 0 }.all { it.rect.center.x < cx })
        assertTrue(band.cards.filter { it.side > 0 }.all { it.rect.center.x > cx })
    }

    @Test
    fun `focus is centered in the band`() {
        val band = buildCallsBand(336f, 156f, neighborhood(incoming = 1, outgoing = 1))
        assertEquals(168f, band.focus.center.x)
        assertEquals(78f, band.focus.center.y)
    }

    @Test
    fun `wide band centers the cluster around the focus instead of pinning to edges`() {
        val band = buildCallsBand(720f, 156f, neighborhood(incoming = 2, outgoing = 2))
        val cx = band.focus.center.x
        assertEquals(360f, cx, "focus stays centered at half width")

        val callers = band.cards.filter { it.side < 0 }
        val callees = band.cards.filter { it.side > 0 }
        val leftGap = band.focus.left - callers.first().rect.right
        val rightGap = callees.first().rect.left - band.focus.right
        assertEquals(leftGap, rightGap, 0.5f, "cluster is symmetric around the focus")
        assertTrue(callers.first().rect.left > 16f, "caller cluster is pulled in from the left edge")
        assertTrue(callees.first().rect.right < 704f, "callee cluster is pulled in from the right edge")
    }

    @Test
    fun `zero size yields an empty band`() {
        assertEquals(CallsBand.EMPTY, buildCallsBand(0f, 0f, neighborhood(incoming = 3, outgoing = 3)))
    }

    @Test
    fun `empty neighborhood draws only the focus`() {
        val band = buildCallsBand(336f, 156f, neighborhood(incoming = 0, outgoing = 0))
        assertTrue(band.cards.isEmpty())
        assertTrue(band.focus != androidx.compose.ui.geometry.Rect.Zero)
    }

    @Test
    fun `symbol display line is one-based`() {
        assertEquals(42, symbolDisplayLine("file:///a.kt#foo@41"))
        assertEquals(1, symbolDisplayLine("file:///a.kt#bar@0"))
    }

    @Test
    fun `symbol display line is null when unparseable`() {
        assertNull(symbolDisplayLine("file:///a.kt#foo"))
        assertNull(symbolDisplayLine("file:///a.kt#foo@x"))
        assertNull(symbolDisplayLine("file:///a.kt#foo@-1"))
    }
}
