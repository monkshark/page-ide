package page.editor

import java.nio.file.Path
import java.util.Locale

enum class FileKind {
    TEXT,
    IMAGE,
    SVG;

    val isEditableAsText: Boolean
        get() = this == TEXT || this == SVG
}

object FileKinds {
    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    fun classify(path: Path): FileKind {
        val name = path.fileName?.toString()?.lowercase(Locale.ROOT) ?: return FileKind.TEXT
        val ext = name.substringAfterLast('.', missingDelimiterValue = "")
        return when {
            ext == "svg" -> FileKind.SVG
            ext in IMAGE_EXTS -> FileKind.IMAGE
            else -> FileKind.TEXT
        }
    }
}
