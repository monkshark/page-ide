package page.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import page.ui.GlassPalette

class AppSettingsUiPaletteTest {

    private lateinit var dir: Path
    private var prevDir: String? = null

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("page-ui-palette-")
        prevDir = System.getProperty("page.settings.dir")
        System.setProperty("page.settings.dir", dir.toString())
    }

    @AfterTest
    fun tearDown() {
        if (prevDir != null) System.setProperty("page.settings.dir", prevDir) else System.clearProperty("page.settings.dir")
        dir.toFile().deleteRecursively()
    }

    @Test
    fun defaultPaletteIsSignature() {
        assertEquals(GlassPalette.Signature, UiOptions.DEFAULT.palette)
        assertEquals(GlassPalette.Signature, AppSettings.loadUi().palette)
        assertEquals(GlassPalette.Signature, AppSettings.loadPalette())
    }

    @Test
    fun signaturePalettesRoundTrip() {
        AppSettings.saveUi(UiOptions(palette = GlassPalette.SignatureLight))
        assertEquals(GlassPalette.SignatureLight, AppSettings.loadUi().palette)

        AppSettings.saveUi(UiOptions(palette = GlassPalette.Signature))
        assertEquals(GlassPalette.Signature, AppSettings.loadUi().palette)
    }
}
