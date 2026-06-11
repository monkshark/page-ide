package page.atlas.render

import androidx.compose.ui.graphics.Color

enum class VcsMark { MODIFIED, ADDED, DELETED }

fun vcsColor(mark: VcsMark): Color = when (mark) {
    VcsMark.MODIFIED -> Color(0xFF6897BB)
    VcsMark.ADDED -> Color(0xFF629755)
    VcsMark.DELETED -> Color(0xFF808080)
}

fun vcsFolderCounts(marks: Map<String, VcsMark>, folderIds: Collection<String>): Map<String, Int> {
    if (marks.isEmpty() || folderIds.isEmpty()) return emptyMap()
    val result = HashMap<String, Int>()
    for (folder in folderIds) {
        val count = marks.keys.count { it != folder && belongsTo(it, folder) }
        if (count > 0) result[folder] = count
    }
    return result
}
