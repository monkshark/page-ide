package page.git

import java.nio.file.Path

enum class GitStatusKind { MODIFIED, ADDED, DELETED, RENAMED, UNTRACKED }

interface VcsStatusProvider {
    fun statuses(): Map<Path, GitStatusKind>
    fun refresh()
}

fun parseGitStatus(output: String, root: Path): Map<Path, GitStatusKind> {
    val result = LinkedHashMap<Path, GitStatusKind>()
    val tokens = output.split(0.toChar()).filter { it.isNotEmpty() }
    var i = 0
    while (i < tokens.size) {
        val entry = tokens[i]
        i++
        if (entry.length < 4 || entry[2] != ' ') continue
        val x = entry[0]
        val y = entry[1]
        val path = entry.substring(3)
        if ((x == 'R' || x == 'C') && i < tokens.size) i++
        val kind = when {
            x == '?' || y == '?' -> GitStatusKind.UNTRACKED
            x == 'R' || x == 'C' -> GitStatusKind.RENAMED
            x == 'D' || y == 'D' -> GitStatusKind.DELETED
            x == 'A' -> GitStatusKind.ADDED
            else -> GitStatusKind.MODIFIED
        }
        result[root.resolve(path).toAbsolutePath().normalize()] = kind
    }
    return result
}
