package page.app

import page.runtime.*
import page.workspace.*

import page.ui.GlassPalette
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Properties

data class AutoSaveOptions(
    val onFocusLost: Boolean = true,
    val idleSeconds: Int = 15,
    val beforeRun: Boolean = true,
    val onClose: Boolean = true,
) {
    companion object { val DEFAULT = AutoSaveOptions() }
}

data class EditorOptions(
    val fontSize: Int = 14,
    val tabSize: Int = 4,
    val useSpacesForTab: Boolean = true,
    val showLineNumbers: Boolean = true,
    val showMinimap: Boolean = false,
    val highlightCurrentLine: Boolean = true,
) {
    companion object { val DEFAULT = EditorOptions() }
}

data class LspOptions(
    val showInlayHints: Boolean = true,
    val triggerCompletionMidWord: Boolean = true,
    val hoverDelayMs: Int = 500,
    val showInlineDiagnostics: Boolean = true,
    val serverPaths: Map<String, String> = emptyMap(),
) {
    companion object { val DEFAULT = LspOptions() }
}

data class AutoInputOptions(
    val pairs: Boolean = true,
    val htmlTags: Boolean = true,
    val backspaceDeletesPair: Boolean = true,
) {
    companion object { val DEFAULT = AutoInputOptions() }
}

data class UiOptions(
    val palette: GlassPalette = GlassPalette.Cool,
    val sidebarWidth: Int = 240,
    val showTabCloseButton: Boolean = true,
) {
    companion object { val DEFAULT = UiOptions() }
}

data class RunOptions(
    val clearOutputOnRun: Boolean = false,
    val openTerminalOnRun: Boolean = false,
) {
    companion object { val DEFAULT = RunOptions() }
}

object AppSettings {
    private const val FILE_NAME = "settings.properties"
    private const val DIR_NAME = ".page"

    private const val KEY_PALETTE = "glass.palette"

    private const val KEY_AS_FOCUS_LOST = "autoSave.onFocusLost"
    private const val KEY_AS_IDLE_SECONDS = "autoSave.idleSeconds"
    private const val KEY_AS_BEFORE_RUN = "autoSave.beforeRun"
    private const val KEY_AS_ON_CLOSE = "autoSave.onClose"

    private const val KEY_ED_FONT_SIZE = "editor.fontSize"
    private const val KEY_ED_TAB_SIZE = "editor.tabSize"
    private const val KEY_ED_USE_SPACES = "editor.useSpacesForTab"
    private const val KEY_ED_LINE_NUMBERS = "editor.showLineNumbers"
    private const val KEY_ED_MINIMAP = "editor.showMinimap"
    private const val KEY_ED_HL_LINE = "editor.highlightCurrentLine"

    private const val KEY_LSP_INLAY = "lsp.showInlayHints"
    private const val KEY_LSP_MIDWORD = "lsp.triggerCompletionMidWord"
    private const val KEY_LSP_HOVER_MS = "lsp.hoverDelayMs"
    private const val KEY_LSP_INLINE_DIAG = "lsp.showInlineDiagnostics"
    private const val KEY_LSP_SERVER_PATH_PREFIX = "lsp.serverPath."

    private const val KEY_AI_PAIRS = "autoInput.pairs"
    private const val KEY_AI_HTML = "autoInput.htmlTags"
    private const val KEY_AI_BACKSPACE_PAIR = "autoInput.backspaceDeletesPair"

    private const val KEY_UI_SIDEBAR_WIDTH = "ui.sidebarWidth"
    private const val KEY_UI_TAB_CLOSE = "ui.showTabCloseButton"

    private const val KEY_RUN_CLEAR_OUTPUT = "run.clearOutputOnRun"
    private const val KEY_RUN_OPEN_TERMINAL = "run.openTerminalOnRun"

    private fun settingsPath(): Path {
        System.getProperty("page.settings.dir")?.takeIf { it.isNotBlank() }?.let {
            return Path.of(it).resolve(FILE_NAME)
        }
        val home = System.getProperty("user.home")?.let(Path::of) ?: Path.of(".")
        return home.resolve(DIR_NAME).resolve(FILE_NAME)
    }

    fun loadPalette(default: GlassPalette = GlassPalette.Cool): GlassPalette {
        val raw = readProperty(KEY_PALETTE) ?: return default
        return GlassPalette.values().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default
    }
    fun savePalette(palette: GlassPalette) = writeProperties(mapOf(KEY_PALETTE to palette.name))

    fun loadAutoSave(default: AutoSaveOptions = AutoSaveOptions.DEFAULT): AutoSaveOptions {
        val p = readAllProperties() ?: return default
        return AutoSaveOptions(
            onFocusLost = p.getBoolean(KEY_AS_FOCUS_LOST, default.onFocusLost),
            idleSeconds = p.getInt(KEY_AS_IDLE_SECONDS, default.idleSeconds, 0, 3600),
            beforeRun = p.getBoolean(KEY_AS_BEFORE_RUN, default.beforeRun),
            onClose = p.getBoolean(KEY_AS_ON_CLOSE, default.onClose),
        )
    }
    fun saveAutoSave(o: AutoSaveOptions) = writeProperties(mapOf(
        KEY_AS_FOCUS_LOST to o.onFocusLost.toString(),
        KEY_AS_IDLE_SECONDS to o.idleSeconds.toString(),
        KEY_AS_BEFORE_RUN to o.beforeRun.toString(),
        KEY_AS_ON_CLOSE to o.onClose.toString(),
    ))

    fun loadEditor(default: EditorOptions = EditorOptions.DEFAULT): EditorOptions {
        val p = readAllProperties() ?: return default
        return EditorOptions(
            fontSize = p.getInt(KEY_ED_FONT_SIZE, default.fontSize, 8, 48),
            tabSize = p.getInt(KEY_ED_TAB_SIZE, default.tabSize, 1, 16),
            useSpacesForTab = p.getBoolean(KEY_ED_USE_SPACES, default.useSpacesForTab),
            showLineNumbers = p.getBoolean(KEY_ED_LINE_NUMBERS, default.showLineNumbers),
            showMinimap = p.getBoolean(KEY_ED_MINIMAP, default.showMinimap),
            highlightCurrentLine = p.getBoolean(KEY_ED_HL_LINE, default.highlightCurrentLine),
        )
    }
    fun saveEditor(o: EditorOptions) = writeProperties(mapOf(
        KEY_ED_FONT_SIZE to o.fontSize.toString(),
        KEY_ED_TAB_SIZE to o.tabSize.toString(),
        KEY_ED_USE_SPACES to o.useSpacesForTab.toString(),
        KEY_ED_LINE_NUMBERS to o.showLineNumbers.toString(),
        KEY_ED_MINIMAP to o.showMinimap.toString(),
        KEY_ED_HL_LINE to o.highlightCurrentLine.toString(),
    ))

    fun loadLsp(default: LspOptions = LspOptions.DEFAULT): LspOptions {
        val p = readAllProperties() ?: return default
        val serverPaths = p.stringPropertyNames()
            .filter { it.startsWith(KEY_LSP_SERVER_PATH_PREFIX) }
            .mapNotNull { key ->
                val id = key.removePrefix(KEY_LSP_SERVER_PATH_PREFIX)
                val value = p.getProperty(key)?.trim().orEmpty()
                if (id.isBlank() || value.isBlank()) null else id to value
            }
            .toMap()
        return LspOptions(
            showInlayHints = p.getBoolean(KEY_LSP_INLAY, default.showInlayHints),
            triggerCompletionMidWord = p.getBoolean(KEY_LSP_MIDWORD, default.triggerCompletionMidWord),
            hoverDelayMs = p.getInt(KEY_LSP_HOVER_MS, default.hoverDelayMs, 0, 5000),
            showInlineDiagnostics = p.getBoolean(KEY_LSP_INLINE_DIAG, default.showInlineDiagnostics),
            serverPaths = serverPaths,
        )
    }
    fun saveLsp(o: LspOptions) = writeProperties(
        updates = mapOf(
            KEY_LSP_INLAY to o.showInlayHints.toString(),
            KEY_LSP_MIDWORD to o.triggerCompletionMidWord.toString(),
            KEY_LSP_HOVER_MS to o.hoverDelayMs.toString(),
            KEY_LSP_INLINE_DIAG to o.showInlineDiagnostics.toString(),
        ) + o.serverPaths
            .filterValues { it.isNotBlank() }
            .mapKeys { (id, _) -> "$KEY_LSP_SERVER_PATH_PREFIX$id" },
        removeKeyPrefix = KEY_LSP_SERVER_PATH_PREFIX,
    )

    fun loadAutoInput(default: AutoInputOptions = AutoInputOptions.DEFAULT): AutoInputOptions {
        val p = readAllProperties() ?: return default
        return AutoInputOptions(
            pairs = p.getBoolean(KEY_AI_PAIRS, default.pairs),
            htmlTags = p.getBoolean(KEY_AI_HTML, default.htmlTags),
            backspaceDeletesPair = p.getBoolean(KEY_AI_BACKSPACE_PAIR, default.backspaceDeletesPair),
        )
    }
    fun saveAutoInput(o: AutoInputOptions) = writeProperties(mapOf(
        KEY_AI_PAIRS to o.pairs.toString(),
        KEY_AI_HTML to o.htmlTags.toString(),
        KEY_AI_BACKSPACE_PAIR to o.backspaceDeletesPair.toString(),
    ))

    fun loadUi(default: UiOptions = UiOptions.DEFAULT): UiOptions {
        val p = readAllProperties() ?: return default
        val palette = p.getProperty(KEY_PALETTE)?.let { raw ->
            GlassPalette.values().firstOrNull { it.name.equals(raw, ignoreCase = true) }
        } ?: default.palette
        return UiOptions(
            palette = palette,
            sidebarWidth = p.getInt(KEY_UI_SIDEBAR_WIDTH, default.sidebarWidth, 120, 800),
            showTabCloseButton = p.getBoolean(KEY_UI_TAB_CLOSE, default.showTabCloseButton),
        )
    }
    fun saveUi(o: UiOptions) = writeProperties(mapOf(
        KEY_PALETTE to o.palette.name,
        KEY_UI_SIDEBAR_WIDTH to o.sidebarWidth.toString(),
        KEY_UI_TAB_CLOSE to o.showTabCloseButton.toString(),
    ))

    fun loadRun(default: RunOptions = RunOptions.DEFAULT): RunOptions {
        val p = readAllProperties() ?: return default
        return RunOptions(
            clearOutputOnRun = p.getBoolean(KEY_RUN_CLEAR_OUTPUT, default.clearOutputOnRun),
            openTerminalOnRun = p.getBoolean(KEY_RUN_OPEN_TERMINAL, default.openTerminalOnRun),
        )
    }
    fun saveRun(o: RunOptions) = writeProperties(mapOf(
        KEY_RUN_CLEAR_OUTPUT to o.clearOutputOnRun.toString(),
        KEY_RUN_OPEN_TERMINAL to o.openTerminalOnRun.toString(),
    ))

    private fun Properties.getBoolean(key: String, default: Boolean): Boolean =
        getProperty(key)?.toBooleanStrictOrNull() ?: default

    private fun Properties.getInt(key: String, default: Int, min: Int, max: Int): Int =
        getProperty(key)?.toIntOrNull()?.coerceIn(min, max) ?: default

    private fun readProperty(key: String): String? = readAllProperties()?.getProperty(key)

    private fun readAllProperties(): Properties? {
        val path = settingsPath()
        if (!Files.exists(path)) return null
        return runCatching {
            Properties().apply { Files.newBufferedReader(path).use(::load) }
        }.getOrNull()
    }

    private fun writeProperties(updates: Map<String, String>, removeKeyPrefix: String? = null) {
        runCatching {
            val path = settingsPath()
            Files.createDirectories(path.parent)
            val props = Properties()
            if (Files.exists(path)) Files.newBufferedReader(path).use(props::load)
            if (removeKeyPrefix != null) {
                props.stringPropertyNames()
                    .filter { it.startsWith(removeKeyPrefix) }
                    .forEach { props.remove(it) }
            }
            for ((k, v) in updates) props.setProperty(k, v)
            Files.newBufferedWriter(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { props.store(it, "PAGE settings") }
        }
    }
}
