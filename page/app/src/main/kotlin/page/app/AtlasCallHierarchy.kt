package page.app

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import page.atlas.graph.CallHierarchySource
import page.atlas.graph.SymbolSpec
import page.language.LspController
import page.lsp.CallHierarchyCall
import page.lsp.CallHierarchyItemInfo

fun CallHierarchyItemInfo.toSymbolSpec(): SymbolSpec = SymbolSpec(
    name = name,
    detail = detail,
    uri = uri,
    line = selectionRange.startLine,
    character = selectionRange.startCharacter,
    handle = this,
)

class LspCallHierarchySource(
    private val controller: LspController,
    private val timeoutSeconds: Long = 8,
) : CallHierarchySource {

    override fun incoming(symbol: SymbolSpec): List<SymbolSpec> = callsOf(symbol) { item ->
        controller.incomingCalls(item)
    }

    override fun outgoing(symbol: SymbolSpec): List<SymbolSpec> = callsOf(symbol) { item ->
        controller.outgoingCalls(item)
    }

    private fun callsOf(
        symbol: SymbolSpec,
        request: (CallHierarchyItemInfo) -> CompletableFuture<List<CallHierarchyCall>>,
    ): List<SymbolSpec> {
        val item = symbol.handle as? CallHierarchyItemInfo ?: return emptyList()
        return runCatching {
            request(item).get(timeoutSeconds, TimeUnit.SECONDS).map { it.item.toSymbolSpec() }
        }.getOrDefault(emptyList())
    }
}
