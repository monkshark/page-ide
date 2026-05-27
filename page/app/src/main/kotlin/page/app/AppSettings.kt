package page.app

import page.runtime.*

import page.ui.GlassPalette
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Properties

object AppSettings {
    private const val FILE_NAME = "settings.properties"
    private const val DIR_NAME = ".page"
    private const val KEY_PALETTE = "glass.palette"

    private val settingsPath: Path by lazy {
        val home = System.getProperty("user.home")?.let(Path::of)
            ?: Path.of(".")
        home.resolve(DIR_NAME).resolve(FILE_NAME)
    }

    fun loadPalette(default: GlassPalette = GlassPalette.Cool): GlassPalette {
        val path = settingsPath
        if (!Files.exists(path)) return default
        val raw = runCatching {
            Properties().apply {
                Files.newBufferedReader(path).use(::load)
            }.getProperty(KEY_PALETTE)
        }.getOrNull() ?: return default
        return GlassPalette.values().firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: default
    }

    fun savePalette(palette: GlassPalette) {
        runCatching {
            val path = settingsPath
            Files.createDirectories(path.parent)
            val props = Properties()
            if (Files.exists(path)) {
                Files.newBufferedReader(path).use(props::load)
            }
            props.setProperty(KEY_PALETTE, palette.name)
            Files.newBufferedWriter(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { props.store(it, "PAGE settings") }
        }
    }
}
