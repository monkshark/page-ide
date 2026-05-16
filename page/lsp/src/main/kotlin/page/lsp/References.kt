package page.lsp

import org.eclipse.lsp4j.Location

data class ReferenceLocation(
    val uri: String,
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int,
) {
    fun contains(uri: String, line: Int, character: Int): Boolean {
        if (this.uri != uri) return false
        if (line < startLine || line > endLine) return false
        val afterStart = line > startLine || character >= startCharacter
        val beforeEnd = line < endLine || character <= endCharacter
        return afterStart && beforeEnd
    }

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

fun pickSingleOtherReference(
    results: List<ReferenceLocation>,
    originUri: String,
    line: Int,
    character: Int,
): ReferenceLocation? {
    if (results.size != 2) return null
    val caretMatch = results.firstOrNull { it.contains(originUri, line, character) } ?: return null
    return results.firstOrNull { it !== caretMatch }
}
