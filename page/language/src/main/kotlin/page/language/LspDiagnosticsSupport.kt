package page.language

import page.lsp.Diagnostic

private val FILE_DRIVE_URI = Regex("^(file:///)([A-Za-z])(:.*)$")

internal fun canonicalUri(uri: String): String {
    val match = FILE_DRIVE_URI.matchEntire(uri) ?: return uri
    return match.groupValues[1] + match.groupValues[2].lowercase() + match.groupValues[3]
}

internal fun shouldReinjectUnnecessary(
    incomingHasUnnecessary: Boolean,
    cachedForCurrentContent: Boolean,
): Boolean = !incomingHasUnnecessary && cachedForCurrentContent

internal fun drainBatch(pending: MutableMap<String, List<Diagnostic>>): List<Pair<String, List<Diagnostic>>> {
    if (pending.isEmpty()) return emptyList()
    val out = ArrayList<Pair<String, List<Diagnostic>>>(pending.size)
    val it = pending.entries.iterator()
    while (it.hasNext()) {
        val e = it.next()
        out.add(e.key to e.value)
        it.remove()
    }
    return out
}

internal fun extractProjectUris(result: Any?): List<String> {
    val items = result as? Iterable<*> ?: return emptyList()
    return items.mapNotNull { el ->
        when (el) {
            null -> null
            is String -> el
            else -> el.toString().trim().removeSurrounding("\"").ifBlank { null }
        }
    }.filter { it.startsWith("file:") }
}

internal fun toLspDiagnostic(d: page.lsp.Diagnostic): org.eclipse.lsp4j.Diagnostic {
    val r = org.eclipse.lsp4j.Range(
        org.eclipse.lsp4j.Position(d.start.line, d.start.character),
        org.eclipse.lsp4j.Position(d.end.line, d.end.character),
    )
    val sev = when (d.severity) {
        page.lsp.DiagnosticSeverity.ERROR -> org.eclipse.lsp4j.DiagnosticSeverity.Error
        page.lsp.DiagnosticSeverity.WARNING -> org.eclipse.lsp4j.DiagnosticSeverity.Warning
        page.lsp.DiagnosticSeverity.INFO -> org.eclipse.lsp4j.DiagnosticSeverity.Information
        page.lsp.DiagnosticSeverity.HINT -> org.eclipse.lsp4j.DiagnosticSeverity.Hint
    }
    return org.eclipse.lsp4j.Diagnostic(r, d.message, sev, d.source.orEmpty()).apply {
        code = org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(d.code ?: "")
    }
}
