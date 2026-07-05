package page.shared.docs

class DocTreeNode(
    val name: String,
    val path: String?,
    val children: List<DocTreeNode>,
) {
    val isLeaf: Boolean get() = path != null
}

fun docTitle(fileName: String): String =
    fileName.substringAfterLast('/').removeSuffix(".md").replace('_', ' ')

fun buildDocTree(paths: List<String>): List<DocTreeNode> {
    val base = paths.asSequence().filter { !isEnVariant(it) }.distinct().sorted().toList()
    return buildLevel(base, "")
}

private fun buildLevel(paths: List<String>, prefix: String): List<DocTreeNode> {
    val dirs = LinkedHashMap<String, MutableList<String>>()
    val files = mutableListOf<String>()
    for (p in paths) {
        val rest = p.removePrefix(prefix)
        val slash = rest.indexOf('/')
        if (slash < 0) files += p
        else dirs.getOrPut(rest.substring(0, slash)) { mutableListOf() } += p
    }
    val nodes = mutableListOf<DocTreeNode>()
    for ((dir, ps) in dirs) nodes += DocTreeNode(dir, null, buildLevel(ps, "$prefix$dir/"))
    for (f in files) nodes += DocTreeNode(docTitle(f), f, emptyList())
    return nodes
}
