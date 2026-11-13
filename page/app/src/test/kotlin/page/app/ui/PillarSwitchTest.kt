package page.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import page.ui.GlassPalette
import page.ui.GlassTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PillarSwitchTest {

    @Test
    fun `palette names read as words`() {
        assertEquals("Signature light", paletteLabel(GlassPalette.SignatureLight))
        assertEquals("Graphite", paletteLabel(GlassPalette.Graphite))
    }

    @Test
    fun `only the pillars that exist are offered and atlas toggles`() = runComposeUiTest {
        var atlas = 0
        var commands = 0
        setContent {
            GlassTheme {
                PillarSwitch(
                    atlasActive = false,
                    onAtlasToggle = { atlas++ },
                    currentPalette = GlassPalette.Signature,
                    onSelectPalette = {},
                    onCommandPalette = { commands++ },
                )
            }
        }
        onNodeWithText("Glass").assertIsDisplayed()
        onNodeWithTag("pillar-Atlas").performClick()
        onNodeWithTag("pillar-Commands").performClick()
        assertEquals(1, atlas)
        assertEquals(1, commands)
        assertTrue(onAllPair().isEmpty())
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllPair() =
        onAllNodes(androidx.compose.ui.test.hasText("Pair") or androidx.compose.ui.test.hasText("Echo") or androidx.compose.ui.test.hasText("AI"))
            .fetchSemanticsNodes()
}
