package page.lsp

data class InlayHintItem(
    val line: Int,
    val character: Int,
    val label: String,
    val kind: Kind,
    val paddingLeft: Boolean,
    val paddingRight: Boolean,
) {
    enum class Kind { TYPE, PARAMETER, OTHER }

    companion object {
        fun fromLsp(hint: org.eclipse.lsp4j.InlayHint?): InlayHintItem? {
            if (hint == null) return null
            val pos = hint.position ?: return null
            val label = renderLabel(hint.label).trim()
            if (label.isEmpty()) return null
            val kind = when (hint.kind) {
                org.eclipse.lsp4j.InlayHintKind.Type -> Kind.TYPE
                org.eclipse.lsp4j.InlayHintKind.Parameter -> Kind.PARAMETER
                null -> Kind.OTHER
            }
            return InlayHintItem(
                line = pos.line,
                character = pos.character,
                label = label,
                kind = kind,
                paddingLeft = hint.paddingLeft == true,
                paddingRight = hint.paddingRight == true,
            )
        }

        fun fromLspList(list: List<org.eclipse.lsp4j.InlayHint>?): List<InlayHintItem> {
            if (list.isNullOrEmpty()) return emptyList()
            val out = ArrayList<InlayHintItem>(list.size)
            for (h in list) {
                val item = fromLsp(h) ?: continue
                out += item
            }
            return out
        }

        private fun renderLabel(
            label: org.eclipse.lsp4j.jsonrpc.messages.Either<String, MutableList<org.eclipse.lsp4j.InlayHintLabelPart>>?,
        ): String {
            if (label == null) return ""
            return when {
                label.isLeft -> label.left.orEmpty()
                label.isRight -> label.right.orEmpty().joinToString("") { it.value.orEmpty() }
                else -> ""
            }
        }
    }
}
