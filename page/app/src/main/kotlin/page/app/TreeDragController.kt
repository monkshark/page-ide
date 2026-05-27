package page.app

import page.runtime.*

import java.nio.file.Files
import java.nio.file.Path

object TreeDragController {

    enum class Mode { Move, Copy }
    enum class Source { Internal, External }

    data class DropPlan(
        val sources: List<Path>,
        val target: Path,
        val mode: Mode,
        val source: Source,
    )

    enum class Reason {
        NoSources,
        TargetNotDirectory,
        TargetReadOnly,
        SelfOrDescendant,
        SameParentSameMode,
        ContainsRoot,
    }

    sealed interface Decision {
        data class Allow(val plan: DropPlan) : Decision
        data class Reject(val reason: Reason) : Decision
    }

    fun resolveTargetFolder(node: Path): Path? = when {
        Files.isDirectory(node) -> node
        else -> node.parent?.takeIf { Files.isDirectory(it) }
    }

    fun canDropOn(sources: List<Path>, target: Path): Boolean {
        if (sources.isEmpty()) return false
        val absTarget = target.toAbsolutePath().normalize()
        return sources.none { src ->
            val absSrc = src.toAbsolutePath().normalize()
            absSrc == absTarget || absTarget.startsWith(absSrc)
        }
    }

    fun plan(
        sources: List<Path>,
        targetNode: Path,
        mode: Mode,
        source: Source,
        workspaceRoot: Path? = null,
    ): Decision {
        if (sources.isEmpty()) return Decision.Reject(Reason.NoSources)
        val target = resolveTargetFolder(targetNode) ?: return Decision.Reject(Reason.TargetNotDirectory)
        if (workspaceRoot != null && sources.any { it == workspaceRoot }) {
            return Decision.Reject(Reason.ContainsRoot)
        }
        if (!canDropOn(sources, target)) return Decision.Reject(Reason.SelfOrDescendant)
        if (!Files.isWritable(target)) return Decision.Reject(Reason.TargetReadOnly)
        if (mode == Mode.Move && sources.all { it.parent == target }) {
            return Decision.Reject(Reason.SameParentSameMode)
        }
        return Decision.Allow(DropPlan(sources = sources, target = target, mode = mode, source = source))
    }

    fun effectiveDragPaths(grabbedPath: Path, currentSelection: Set<Path>): List<Path> {
        val inSelection = grabbedPath in currentSelection
        return if (inSelection) currentSelection.toList() else listOf(grabbedPath)
    }
}
