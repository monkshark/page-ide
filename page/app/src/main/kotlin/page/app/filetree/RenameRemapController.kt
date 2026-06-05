package page.app.filetree

import page.app.EditorPaneState
import page.app.PaneSide
import page.language.LspController
import java.nio.file.Path

internal class RenameRemapController(
    private val getExpanded: () -> Set<Path>,
    private val setExpanded: (Set<Path>) -> Unit,
    private val getTreeSelection: () -> Set<Path>,
    private val setTreeSelection: (Set<Path>) -> Unit,
    private val mutatePane: (PaneSide, (EditorPaneState) -> EditorPaneState) -> Unit,
    private val primaryPane: () -> EditorPaneState,
    private val secondaryPane: () -> EditorPaneState,
    private val controllerFor: (Path) -> LspController?,
    private val languageIdFor: (Path) -> String?,
) {
    private fun remapPathSet(paths: Set<Path>, old: Path, new: Path): Set<Path> {
        return if (paths.isEmpty()) paths
        else paths.map { p ->
            when {
                p == old -> new
                p.startsWith(old) -> new.resolve(old.relativize(p))
                else -> p
            }
        }.toSet()
    }

    fun remapTreeStateAfterRename(old: Path, new: Path) {
        setExpanded(remapPathSet(getExpanded(), old, new))
        setTreeSelection(remapPathSet(getTreeSelection(), old, new))
    }

    fun remapTabsAfterRename(old: Path, new: Path) {
        listOf(PaneSide.PRIMARY, PaneSide.SECONDARY).forEach { side ->
            mutatePane(side) { pane ->
                val mapped = pane.book.tabs.map { tab ->
                    val newPath = when {
                        tab.path == old -> new
                        tab.path.startsWith(old) -> new.resolve(old.relativize(tab.path))
                        else -> null
                    }
                    if (newPath != null) tab.copy(path = newPath) else tab
                }
                if (mapped == pane.book.tabs) pane
                else pane.copy(book = pane.book.copy(tabs = mapped))
            }
        }
        val affectedOldPaths = mutableListOf<Path>()
        val affectedNewPaths = mutableListOf<Pair<Path, String>>()
        listOf(primaryPane(), secondaryPane()).forEach { pane ->
            pane.book.tabs.forEach { tab ->
                if (tab.path == new || tab.path.startsWith(new)) {
                    val origin = if (tab.path == new) old else old.resolve(new.relativize(tab.path))
                    affectedOldPaths.add(origin)
                    affectedNewPaths.add(tab.path to tab.text)
                }
            }
        }
        affectedOldPaths.distinct().forEach { controllerFor(it)?.didClose(it) }
        affectedNewPaths.distinctBy { it.first }.forEach { (p, text) ->
            languageIdFor(p)?.let { langId -> controllerFor(p)?.didOpen(p, langId, text) }
        }
    }
}
