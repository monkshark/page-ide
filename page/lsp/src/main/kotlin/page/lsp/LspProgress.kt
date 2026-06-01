package page.lsp

sealed interface LspProgress {
    val token: String

    data class Begin(
        override val token: String,
        val title: String,
        val message: String?,
        val percentage: Int?,
    ) : LspProgress

    data class Report(
        override val token: String,
        val message: String?,
        val percentage: Int?,
    ) : LspProgress

    data class End(
        override val token: String,
        val message: String?,
    ) : LspProgress
}

fun parseLspProgress(params: org.eclipse.lsp4j.ProgressParams?): LspProgress? {
    params ?: return null
    val token = params.token?.let { if (it.isLeft) it.left else it.right?.toString() } ?: return null
    val value = params.value ?: return null
    if (!value.isLeft) return null
    return when (val notif = value.left) {
        is org.eclipse.lsp4j.WorkDoneProgressBegin ->
            LspProgress.Begin(token, notif.title.orEmpty(), notif.message, notif.percentage)
        is org.eclipse.lsp4j.WorkDoneProgressReport ->
            LspProgress.Report(token, notif.message, notif.percentage)
        is org.eclipse.lsp4j.WorkDoneProgressEnd ->
            LspProgress.End(token, notif.message)
        else -> null
    }
}
