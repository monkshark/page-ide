package page.lsp

import org.eclipse.lsp4j.SymbolKind

data class CallHierarchyItemInfo(
    val name: String,
    val detail: String?,
    val kind: SymbolKind?,
    val uri: String,
    val range: SymbolRange,
    val selectionRange: SymbolRange,
    internal val raw: org.eclipse.lsp4j.CallHierarchyItem,
) {
    companion object {
        fun fromLsp(item: org.eclipse.lsp4j.CallHierarchyItem?): CallHierarchyItemInfo? {
            if (item == null) return null
            val uri = item.uri ?: return null
            val range = item.range?.toSymbolRange() ?: return null
            return CallHierarchyItemInfo(
                name = item.name ?: "",
                detail = item.detail,
                kind = item.kind,
                uri = uri,
                range = range,
                selectionRange = (item.selectionRange ?: item.range).toSymbolRange(),
                raw = item,
            )
        }
    }
}

data class CallHierarchyCall(
    val item: CallHierarchyItemInfo,
    val fromRanges: List<SymbolRange>,
) {
    companion object {
        fun fromIncoming(call: org.eclipse.lsp4j.CallHierarchyIncomingCall?): CallHierarchyCall? {
            if (call == null) return null
            val item = CallHierarchyItemInfo.fromLsp(call.from) ?: return null
            return CallHierarchyCall(item, call.fromRanges.orEmpty().map { it.toSymbolRange() })
        }

        fun fromOutgoing(call: org.eclipse.lsp4j.CallHierarchyOutgoingCall?): CallHierarchyCall? {
            if (call == null) return null
            val item = CallHierarchyItemInfo.fromLsp(call.to) ?: return null
            return CallHierarchyCall(item, call.fromRanges.orEmpty().map { it.toSymbolRange() })
        }
    }
}
