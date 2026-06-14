package page.atlas

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import page.atlas.render.VcsMark
import page.atlas.render.atlasColorsFrom
import page.atlas.render.vcsColor
import page.atlas.render.vcsImpactColor
import page.ui.GlassPalette
import page.ui.glassTokensFor

class AtlasColorsTest {

    private val signature = glassTokensFor(GlassPalette.Signature).color

    @Test
    fun `atlas colors source from glass signature tokens`() {
        val atlas = atlasColorsFrom(signature)
        assertEquals(signature.surfaceL2, atlas.surface)
        assertEquals(signature.surfaceL3, atlas.surfaceVariant)
        assertEquals(signature.primary, atlas.focus)
        assertEquals(signature.accent, atlas.module)
        assertEquals(signature.warn, atlas.relation)
        assertEquals(signature.outline, atlas.outline)
        assertEquals(signature.muted, atlas.label)
        assertEquals(signature.text, atlas.text)
        assertEquals(signature.danger, atlas.cycle)
    }

    @Test
    fun `vcs colors keep their semantic values`() {
        assertEquals(Color(0xFFCC7832), vcsImpactColor)
        assertEquals(Color(0xFF6897BB), vcsColor(VcsMark.MODIFIED))
        assertEquals(Color(0xFF629755), vcsColor(VcsMark.ADDED))
        assertEquals(Color(0xFF808080), vcsColor(VcsMark.DELETED))
    }
}
