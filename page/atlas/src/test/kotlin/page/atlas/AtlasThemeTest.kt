package page.atlas

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import page.atlas.graph.EdgeKind
import page.atlas.graph.NodeKind
import page.atlas.render.AtlasTheme
import page.atlas.render.edgeStyle
import page.atlas.render.nodeColor

class AtlasThemeTest {
    private val theme = AtlasTheme(
        nodeActive = Color(0xFF000001),
        nodeWorkspace = Color(0xFF000002),
        nodeExternal = Color(0xFF000003),
        nodeSymbol = Color(0xFF000004),
        edgeImport = Color(0xFF000005),
        edgeRelation = Color(0xFF000006),
        label = Color(0xFF000007),
        selectionRing = Color(0xFF000008),
        ring = Color(0xFF000009),
        nodeHighlight = Color.White,
        nodeShadow = Color.Black,
    )

    @Test
    fun nodeColorMapsEachKind() {
        assertEquals(theme.nodeActive, theme.nodeColor(NodeKind.ACTIVE))
        assertEquals(theme.nodeWorkspace, theme.nodeColor(NodeKind.WORKSPACE_FILE))
        assertEquals(theme.nodeExternal, theme.nodeColor(NodeKind.EXTERNAL))
        assertEquals(theme.nodeSymbol, theme.nodeColor(NodeKind.SYMBOL))
    }

    @Test
    fun importEdgeIsThinArrowlessImportColor() {
        val style = theme.edgeStyle(EdgeKind.IMPORT)
        assertEquals(1f, style.strokeWidth)
        assertFalse(style.dashed)
        assertFalse(style.arrow)
        assertEquals(theme.edgeImport, style.color)
    }

    @Test
    fun extendsEdgeIsFilledArrowRelationColor() {
        val style = theme.edgeStyle(EdgeKind.EXTENDS)
        assertEquals(2f, style.strokeWidth)
        assertFalse(style.dashed)
        assertTrue(style.arrow)
        assertTrue(style.filledArrow)
        assertEquals(theme.edgeRelation, style.color)
    }

    @Test
    fun implementsEdgeIsDashedOpenArrowRelationColor() {
        val style = theme.edgeStyle(EdgeKind.IMPLEMENTS)
        assertEquals(1.5f, style.strokeWidth)
        assertTrue(style.dashed)
        assertTrue(style.arrow)
        assertFalse(style.filledArrow)
        assertEquals(theme.edgeRelation, style.color)
    }

    @Test
    fun callsEdgeIsThinFilledArrowRelationColor() {
        val style = theme.edgeStyle(EdgeKind.CALLS)
        assertEquals(1f, style.strokeWidth)
        assertFalse(style.dashed)
        assertTrue(style.arrow)
        assertTrue(style.filledArrow)
        assertEquals(theme.edgeRelation, style.color)
    }
}
