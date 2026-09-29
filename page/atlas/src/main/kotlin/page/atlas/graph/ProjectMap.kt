package page.atlas.graph

import java.nio.file.Path

data class MapPart(
    val id: String,
    val name: String,
    val description: String,
    val dir: Path?,
    val files: List<Path>,
    val layer: Int,
) {
    val fileCount: Int get() = files.size
}

data class MapLink(val from: String, val to: String, val weight: Int)

data class PartRelations(val uses: Set<String>, val usedBy: Set<String>)

data class ProjectMap(
    val title: String,
    val parts: List<MapPart>,
    val links: List<MapLink>,
) {
    val layers: Int get() = (parts.maxOfOrNull { it.layer } ?: -1) + 1

    fun part(id: String): MapPart? = parts.firstOrNull { it.id == id }

    fun partOf(file: Path): MapPart? {
        val target = file.toAbsolutePath().normalize()
        return parts.firstOrNull { part -> part.files.any { it == target } }
    }

    fun relationsOf(id: String): PartRelations = PartRelations(
        uses = links.filter { it.from == id }.map { it.to }.toSet(),
        usedBy = links.filter { it.to == id }.map { it.from }.toSet(),
    )

    fun isEmpty(): Boolean = parts.isEmpty()

    companion object {
        val EMPTY = ProjectMap("", emptyList(), emptyList())
    }
}
