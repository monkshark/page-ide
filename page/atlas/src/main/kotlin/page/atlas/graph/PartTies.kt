package page.atlas.graph

import page.atlas.toNioPath

data class FileFacts(val usedBy: Int, val inLoop: Boolean)

data class PartTie(val users: Int, val needed: Boolean)

fun partTies(map: ProjectMap, margins: FileMargins): Map<String, PartTie> {
    fun partId(node: GraphNode): String? = node.path?.toNioPath()?.let { map.partOf(it)?.id }
    val users = margins.usedBy.mapNotNull(::partId).groupingBy { it }.eachCount()
    val needed = margins.needs.mapNotNull { partId(it.node) }.toSet()
    return (users.keys + needed).associateWith { PartTie(users[it] ?: 0, it in needed) }
}
