package page.lsp

enum class DiagnosticSeverity {
    ERROR, WARNING, INFO, HINT;

    companion object {
        fun fromLsp(value: org.eclipse.lsp4j.DiagnosticSeverity?): DiagnosticSeverity = when (value) {
            org.eclipse.lsp4j.DiagnosticSeverity.Error -> ERROR
            org.eclipse.lsp4j.DiagnosticSeverity.Warning -> WARNING
            org.eclipse.lsp4j.DiagnosticSeverity.Information -> INFO
            org.eclipse.lsp4j.DiagnosticSeverity.Hint -> HINT
            null -> ERROR
        }
    }
}

data class DiagnosticPosition(val line: Int, val character: Int)

data class Diagnostic(
    val start: DiagnosticPosition,
    val end: DiagnosticPosition,
    val severity: DiagnosticSeverity,
    val message: String,
    val source: String? = null,
    val code: String? = null,
) {
    companion object {
        fun fromLsp(d: org.eclipse.lsp4j.Diagnostic): Diagnostic = Diagnostic(
            start = DiagnosticPosition(d.range.start.line, d.range.start.character),
            end = DiagnosticPosition(d.range.end.line, d.range.end.character),
            severity = DiagnosticSeverity.fromLsp(d.severity),
            message = d.message ?: "",
            source = d.source,
            code = d.code?.let { e ->
                when {
                    e.isLeft -> e.left
                    e.isRight -> e.right?.toString()
                    else -> null
                }
            },
        )
    }
}
