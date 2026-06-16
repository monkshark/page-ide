package page.atlas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.render.EgoColumn
import page.atlas.render.EgoEdge
import page.atlas.render.EgoTheme
import page.atlas.render.columnColor
import page.atlas.render.edgeColor
import page.atlas.render.egoEdgePath

class EgoThemeTest {

    private val theme = EgoTheme(
        canvas = Color(0xFF0A0D14),
        focus = Color(0xFF3D7BFF),
        dependent = Color(0xFF6E8BFF),
        importNode = Color(0xFF4FD3C7),
        external = Color(0xFF6B7689),
        edgeDependent = Color(0xFF6E8BFF),
        edgeImport = Color(0xFF4FD3C7),
        edgeExternal = Color(0xFF6B7689),
        highlight = Color.White,
        shadow = Color.Black,
        label = Color(0xFFAAAAAA),
        grid = Color(0xFF1B2233),
    )

    @Test
    fun `column color maps each column to its directional hue`() {
        assertEquals(theme.focus, theme.columnColor(EgoColumn.FOCUS))
        assertEquals(theme.dependent, theme.columnColor(EgoColumn.DEPENDENT))
        assertEquals(theme.importNode, theme.columnColor(EgoColumn.IMPORT))
        assertEquals(theme.external, theme.columnColor(EgoColumn.EXTERNAL))
    }

    @Test
    fun `edge color follows direction and target column`() {
        assertEquals(theme.edgeDependent, theme.edgeColor(toFocus = true, targetColumn = EgoColumn.FOCUS))
        assertEquals(theme.edgeImport, theme.edgeColor(toFocus = false, targetColumn = EgoColumn.IMPORT))
        assertEquals(theme.edgeExternal, theme.edgeColor(toFocus = false, targetColumn = EgoColumn.EXTERNAL))
    }

    @Test
    fun `edge path spans from start to end`() {
        val edge = EgoEdge(
            from = "a", to = "b", toFocus = false,
            start = Offset(100f, 200f),
            c1 = Offset(250f, 200f),
            c2 = Offset(250f, 400f),
            end = Offset(400f, 400f),
        )
        val bounds = egoEdgePath(edge).getBounds()
        assertEquals(100f, bounds.left)
        assertEquals(400f, bounds.right)
        assertTrue(bounds.top <= 200f && bounds.bottom >= 400f)
    }
}
