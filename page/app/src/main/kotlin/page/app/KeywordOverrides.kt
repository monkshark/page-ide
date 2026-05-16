package page.app

import androidx.compose.ui.graphics.Color
import com.google.gson.reflect.TypeToken
import java.nio.file.Path

data class KeywordOverridesFile(
    val version: Int = 1,
    val colors: Map<String, String> = emptyMap(),
)

object KeywordOverridesStore {
    const val FILE_NAME = "keywords.json"

    fun load(workspaceRoot: Path): KeywordOverridesFile {
        val type = object : TypeToken<KeywordOverridesFile>() {}.type
        return PageIdeStore.readType<KeywordOverridesFile>(workspaceRoot, FILE_NAME, type)
            ?: KeywordOverridesFile()
    }

    fun save(workspaceRoot: Path, file: KeywordOverridesFile) {
        PageIdeStore.write(workspaceRoot, FILE_NAME, file)
    }

    fun setColor(workspaceRoot: Path, keyword: String, hex: String): KeywordOverridesFile {
        val current = load(workspaceRoot)
        val updated = current.copy(colors = current.colors + (keyword.uppercase() to hex))
        save(workspaceRoot, updated)
        return updated
    }

    fun removeColor(workspaceRoot: Path, keyword: String): KeywordOverridesFile {
        val current = load(workspaceRoot)
        val updated = current.copy(colors = current.colors - keyword.uppercase())
        if (updated.colors != current.colors) save(workspaceRoot, updated)
        return updated
    }
}

internal fun parseHexColor(hex: String): Color? {
    val s = hex.trim().removePrefix("#")
    if (s.length != 6 && s.length != 8) return null
    return runCatching {
        val value = s.toLong(16)
        val argb = if (s.length == 6) 0xFF000000L or value else value
        Color(argb.toInt())
    }.getOrNull()
}
