package page.atlas.analyzer

import page.atlas.graph.DocsDescriber
import page.atlas.graph.ProjectMap
import page.atlas.graph.ProjectMapBuilder
import java.nio.file.Path

object ProjectMapLoader {

    fun load(root: Path): ProjectMap {
        val index = WorkspaceIndex(root).also { it.refreshIfStale(0) }
        val files = index.files().filter { ImportExtractor.supports(it) }
        return ProjectMapBuilder.build(root, files, emptyList(), DocsDescriber(root), moduleLinks = ModuleLinks.read(root))
    }
}
