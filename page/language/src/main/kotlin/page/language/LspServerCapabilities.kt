package page.language

internal fun detectPrepareRenameSupport(caps: org.eclipse.lsp4j.ServerCapabilities?): Boolean {
    val rp = caps?.renameProvider ?: return false
    return when {
        rp.isLeft -> false
        rp.isRight -> rp.right?.prepareProvider == true
        else -> false
    }
}

internal fun detectInlayHintSupport(caps: org.eclipse.lsp4j.ServerCapabilities?): Boolean {
    val ih = caps?.inlayHintProvider ?: return false
    return when {
        ih.isLeft -> ih.left == true
        ih.isRight -> ih.right != null
        else -> false
    }
}

internal fun detectCallHierarchySupport(caps: org.eclipse.lsp4j.ServerCapabilities?): Boolean {
    val ch = caps?.callHierarchyProvider ?: return false
    return when {
        ch.isLeft -> ch.left == true
        ch.isRight -> ch.right != null
        else -> false
    }
}

internal fun detectCompletionResolveSupport(caps: org.eclipse.lsp4j.ServerCapabilities?): Boolean =
    caps?.completionProvider?.resolveProvider == true

internal fun detectExecuteCommandSupport(caps: org.eclipse.lsp4j.ServerCapabilities?): Boolean =
    caps?.executeCommandProvider != null
