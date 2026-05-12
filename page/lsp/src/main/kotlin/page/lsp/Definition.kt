package page.lsp

import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.jsonrpc.messages.Either

data class DefinitionTarget(
    val uri: String,
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int,
) {
    companion object {
        fun fromLsp(
            either: Either<MutableList<out Location>, MutableList<out LocationLink>>?,
        ): List<DefinitionTarget> {
            if (either == null) return emptyList()
            return when {
                either.isLeft -> either.left.orEmpty().mapNotNull { fromLocation(it) }
                either.isRight -> either.right.orEmpty().mapNotNull { fromLocationLink(it) }
                else -> emptyList()
            }
        }

        private fun fromLocation(loc: Location?): DefinitionTarget? {
            if (loc == null) return null
            val uri = loc.uri ?: return null
            val range = loc.range ?: return null
            return DefinitionTarget(
                uri = uri,
                startLine = range.start.line,
                startCharacter = range.start.character,
                endLine = range.end.line,
                endCharacter = range.end.character,
            )
        }

        private fun fromLocationLink(link: LocationLink?): DefinitionTarget? {
            if (link == null) return null
            val uri = link.targetUri ?: return null
            val range = link.targetSelectionRange ?: link.targetRange ?: return null
            return DefinitionTarget(
                uri = uri,
                startLine = range.start.line,
                startCharacter = range.start.character,
                endLine = range.end.line,
                endCharacter = range.end.character,
            )
        }
    }
}
