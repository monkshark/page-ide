package page.app

import page.runtime.*

import java.nio.file.Path

object FileTreeSelection {

    fun single(path: Path): Set<Path> = setOf(path)

    fun toggle(current: Set<Path>, path: Path): Set<Path> {
        return if (path in current) {
            current.filterTo(linkedSetOf()) { it != path }
        } else {
            LinkedHashSet<Path>(current).apply { add(path) }
        }
    }

    fun range(anchor: Path?, target: Path, order: List<Path>): Set<Path> {
        if (order.isEmpty()) return setOf(target)
        val targetIndex = order.indexOf(target)
        if (targetIndex < 0) return setOf(target)
        val anchorIndex = anchor?.let(order::indexOf) ?: -1
        if (anchorIndex < 0) return setOf(target)
        val lo = minOf(anchorIndex, targetIndex)
        val hi = maxOf(anchorIndex, targetIndex)
        return order.subList(lo, hi + 1).toSet()
    }

    fun pruneToDescendantsOf(current: Set<Path>, root: Path): Set<Path> {
        if (current.isEmpty()) return current
        return current.filterTo(linkedSetOf()) { it.startsWith(root) || it == root }
    }
}
