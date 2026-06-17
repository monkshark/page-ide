package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import page.atlas.render.atlasRoleColors
import page.ui.GlassPalette
import page.ui.glassTokensFor

class AtlasColorsTest {
    private val cool = glassTokensFor(GlassPalette.Cool).color

    @Test
    fun rolesMapToGlassSemanticTokens() {
        val roles = atlasRoleColors(cool)
        assertEquals(cool.primary, roles.dependency)
        assertEquals(cool.accent, roles.usedBy)
        assertEquals(cool.warn, roles.cycle)
        assertEquals(cool.error, roles.hub)
        assertEquals(cool.warn, roles.path)
        assertEquals(cool.muted, roles.neutral)
    }

    @Test
    fun cycleAndHubAndDependencyAreDistinct() {
        val roles = atlasRoleColors(cool)
        assertNotEquals(roles.cycle, roles.hub)
        assertNotEquals(roles.dependency, roles.usedBy)
        assertNotEquals(roles.dependency, roles.cycle)
    }
}
