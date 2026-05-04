package page.editor

import java.nio.file.Files
import java.nio.file.Path

data class TreeNode(
    val path: Path,
    val depth: Int,
    val isDirectory: Boolean,
)

object FileTree {

    fun listTree(root: Path, expanded: Set<Path>): List<TreeNode> {
        val result = mutableListOf<TreeNode>()
        val rootIsDir = isDirectorySafe(root)
        result += TreeNode(root, depth = 0, isDirectory = rootIsDir)
        if (rootIsDir && root in expanded) {
            appendChildren(root, depth = 1, expanded, result)
        }
        return result
    }

    private fun appendChildren(
        dir: Path,
        depth: Int,
        expanded: Set<Path>,
        out: MutableList<TreeNode>,
    ) {
        val children = listChildrenSorted(dir) ?: return
        for (child in children) {
            val isDir = isDirectorySafe(child)
            out += TreeNode(child, depth, isDir)
            if (isDir && child in expanded) {
                appendChildren(child, depth + 1, expanded, out)
            }
        }
    }

    private fun listChildrenSorted(dir: Path): List<Path>? {
        val stream = try {
            Files.list(dir)
        } catch (_: Exception) {
            return null
        }
        return try {
            stream.use { s ->
                s.sorted(compareBy(
                    { !isDirectorySafe(it) },
                    { it.fileName?.toString()?.lowercase() ?: "" },
                )).toList()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isDirectorySafe(path: Path): Boolean = try {
        Files.isDirectory(path)
    } catch (_: Exception) {
        false
    }
}
