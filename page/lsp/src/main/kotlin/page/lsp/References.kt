package page.lsp

import org.eclipse.lsp4j.Location

data class ReferenceLocation(
    val uri: String,
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int,
) {
    companion object {
        fun fromLsp(locations: List<Location?>?): List<ReferenceLocation> {
            if (locations == null) return emptyList()
            return locations.mapNotNull { fromLocation(it) }
        }

        private fun fromLocation(loc: Location?): ReferenceLocation? {
            if (loc == null) return null
            val uri = loc.uri ?: return null
            val range = loc.range ?: return null
            return ReferenceLocation(
                uri = uri,
                startLine = range.start.line,
                startCharacter = range.start.character,
                endLine = range.end.line,
                endCharacter = range.end.character,
            )
        }
    }
}
