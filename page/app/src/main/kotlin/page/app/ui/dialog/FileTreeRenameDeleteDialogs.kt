package page.app.ui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import page.app.ConfirmDialog
import page.app.NameInputDialog
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.language.LspRouter
import page.lsp.RenameWorkspaceEdit
import page.workspace.FileOpHistory
import page.workspace.FileSymbolRename
import page.workspace.FileTreeActions
import page.workspace.FolderPackageRename
import page.workspace.ImpactScanState
import page.workspace.ReferenceScanner
import java.nio.file.Path

@Composable
internal fun FileTreeRenameDeleteDialogs(
    workspace: WorkspaceState,
    ui: LayoutUiState,
    lspRouter: LspRouter,
    fileOpHistory: FileOpHistory.Stack,
    jumpToProblem: (Path, Int, Int) -> Unit,
    applyRename: (RenameWorkspaceEdit) -> Unit,
    remapTabsAfterRename: (Path, Path) -> Unit,
    remapTreeStateAfterRename: (Path, Path) -> Unit,
    applyFolderPackageSync: (Path, Path, Map<String, String>) -> List<FileOpHistory.RewriteEntry>,
    withFileTreeWatcherClosed: (() -> Unit) -> Unit,
    readFileText: (Path) -> String?,
    closeTabsUnderPath: (Path) -> Unit,
    onFileOpHistoryChanged: () -> Unit,
) {
    var rootDir by workspace::rootDir
    var treeRevision by workspace::treeRevision
    var renameDialog by ui::renameDialog
    var deleteDialog by ui::deleteDialog
    val currentLspRouter by rememberUpdatedState(lspRouter)

    val activeRenameDialog = renameDialog
    val impactScope = rememberCoroutineScope()
    val impactScanner = remember(currentLspRouter) {
        ReferenceScanner(
            documentSymbols = { p -> currentLspRouter.controllerFor(p)?.documentSymbols(p) ?: java.util.concurrent.CompletableFuture.completedFuture(emptyList()) },
            references = { p, l, c -> currentLspRouter.controllerFor(p)?.references(p, l, c, includeDeclaration = false) ?: java.util.concurrent.CompletableFuture.completedFuture(emptyList()) },
            ensureOpen = { p ->
                val langId = currentLspRouter.languageIdFor(p)
                if (langId != null) {
                    val text = runCatching { java.nio.file.Files.readString(p) }.getOrNull()
                    if (text != null) currentLspRouter.controllerFor(p)?.didOpen(p, langId, text)
                }
            },
            scope = impactScope,
        )
    }
    val renameImpactPath = activeRenameDialog?.path?.takeUnless { java.nio.file.Files.isDirectory(it) }
    val impactTarget: Path? = renameImpactPath ?: deleteDialog?.primary
    val impactState: ImpactScanState = produceState<ImpactScanState>(ImpactScanState.Idle, impactTarget) {
        val t = impactTarget
        if (t == null) {
            value = ImpactScanState.Idle
            return@produceState
        }
        value = ImpactScanState.Scanning(0, 1)
        val (flow, job) = impactScanner.scan(t)
        try {
            flow.collect { value = it }
        } finally {
            job.cancel()
        }
    }.value

    if (activeRenameDialog != null) {
        val currentName = activeRenameDialog.path.fileName?.toString() ?: ""
        val parentRel = activeRenameDialog.path.parent?.let { FileTreeActions.relativeTo(rootDir, it) } ?: ""
        val parentLabel = if (parentRel.isEmpty() || parentRel == ".") "/" else parentRel
        NameInputDialog(
            title = "Rename",
            label = "$parentLabel  /  name (current: $currentName)",
            initial = currentName,
            error = activeRenameDialog.error,
            impact = impactState,
            rootDir = rootDir,
            onJumpToHit = { hit ->
                renameDialog = null
                jumpToProblem(hit.file, hit.line, hit.column)
            },
            onSubmit = { newName ->
                val oldPath = activeRenameDialog.path
                val oldName = oldPath.fileName?.toString().orEmpty()
                val oldStem = FileSymbolRename.stripKotlinExtension(oldName)
                val newStem = FileSymbolRename.stripKotlinExtension(newName)
                val shouldRenameSymbol = oldStem != null && newStem != null &&
                    oldStem != newStem &&
                    FileSymbolRename.isValidKotlinIdentifier(newStem)

                fun finishFileRename() {
                    val isDir = java.nio.file.Files.isDirectory(oldPath)
                    val packageMap = if (isDir) {
                        FolderPackageRename.computePackageMap(oldPath, newName, readFileText)
                    } else emptyMap()
                    var renameOriginals: List<FileOpHistory.RewriteEntry> = emptyList()
                    val performAndProcess: () -> FileTreeActions.RenameResult = {
                        val txRoot = if (isDir) rootDir else null
                        var r = FileTreeActions.rename(oldPath, newName, txRoot)
                        if (isDir && r is FileTreeActions.RenameResult.Err) {
                            for (i in 1..5) {
                                runCatching { Thread.sleep(250L) }
                                r = FileTreeActions.rename(oldPath, newName, txRoot)
                                if (r is FileTreeActions.RenameResult.Ok) break
                            }
                        }
                        val ok = r as? FileTreeActions.RenameResult.Ok
                        if (ok != null) {
                            remapTabsAfterRename(oldPath, ok.path)
                            remapTreeStateAfterRename(oldPath, ok.path)
                            if (isDir) {
                                renameOriginals = applyFolderPackageSync(oldPath, ok.path, packageMap)
                            }
                        }
                        r
                    }
                    val result: FileTreeActions.RenameResult = if (isDir) {
                        var captured: FileTreeActions.RenameResult? = null
                        val dirCtrl = currentLspRouter.controllerFor(oldPath)
                        val doRename = {
                            withFileTreeWatcherClosed {
                                captured = performAndProcess()
                            }
                        }
                        if (dirCtrl != null) {
                            dirCtrl.runWithClientDown("folder rename: ${oldPath.fileName} → $newName") { doRename() }
                        } else {
                            doRename()
                        }
                        captured ?: FileTreeActions.RenameResult.Err("rename did not run")
                    } else {
                        performAndProcess()
                    }
                    when (result) {
                        is FileTreeActions.RenameResult.Ok -> {
                            treeRevision++
                            val renameOp = FileOpHistory.RenameOp(from = oldPath, to = result.path)
                            val composedOp: FileOpHistory.Op = if (renameOriginals.isEmpty()) renameOp
                                else FileOpHistory.CompositeOp(listOf(FileOpHistory.ReferenceRewriteOp(renameOriginals), renameOp))
                            fileOpHistory.push(composedOp)
                            onFileOpHistoryChanged()
                            renameDialog = null
                        }
                        is FileTreeActions.RenameResult.Err -> {
                            renameDialog = activeRenameDialog.copy(error = result.message)
                        }
                    }
                }

                if (shouldRenameSymbol) {
                    impactScope.launch {
                        val symCtrl = currentLspRouter.controllerFor(oldPath)
                        val symLangId = currentLspRouter.languageIdFor(oldPath)
                        val syms = runCatching {
                            symCtrl?.documentSymbols(oldPath)?.await()
                        }.getOrNull().orEmpty()
                        val pick = FileSymbolRename.findRenamableTopLevelSymbol(oldStem!!, syms)
                        if (pick != null && symCtrl != null && symLangId != null) {
                            val fileText = readFileText(oldPath)
                            if (fileText != null) {
                                if (!symCtrl.isOpenAt(oldPath)) {
                                    runCatching { symCtrl.didOpen(oldPath, symLangId, fileText) }
                                }
                                val candidatePaths = mutableListOf<java.nio.file.Path>()
                                rootDir?.let { root ->
                                    runCatching {
                                        java.nio.file.Files.walk(root).use { stream ->
                                            stream
                                                .filter { p -> java.nio.file.Files.isRegularFile(p) }
                                                .filter { p ->
                                                    currentLspRouter.languageIdFor(p) == symLangId
                                                }
                                                .forEach { p ->
                                                    val norm = p.toAbsolutePath().normalize()
                                                    if (norm == oldPath.toAbsolutePath().normalize()) return@forEach
                                                    val text = runCatching {
                                                        java.nio.file.Files.readString(norm)
                                                    }.getOrNull() ?: return@forEach
                                                    if (!text.contains(oldStem)) return@forEach
                                                    candidatePaths.add(norm)
                                                    if (!symCtrl.isOpenAt(norm)) {
                                                        runCatching { symCtrl.didOpen(norm, symLangId, text) }
                                                    }
                                                }
                                        }
                                    }
                                }
                                val edit = runCatching {
                                    symCtrl.rename(
                                        oldPath,
                                        fileText,
                                        pick.selectionRange.startLine,
                                        pick.selectionRange.startCharacter,
                                        newStem!!,
                                    ).await()
                                }.getOrNull()
                                val refs = runCatching {
                                    symCtrl.references(
                                        oldPath,
                                        pick.selectionRange.startLine,
                                        pick.selectionRange.startCharacter,
                                        includeDeclaration = false,
                                    ).await()
                                }.getOrDefault(emptyList())
                                val readText: (java.nio.file.Path) -> String? = readFileText
                                val withRefs = page.lsp.RenameAugment.augment(
                                    edit = edit ?: page.lsp.RenameWorkspaceEdit.EMPTY,
                                    references = refs,
                                    oldName = oldStem,
                                    newName = newStem!!,
                                    readFileText = readText,
                                )
                                val withTextual = page.lsp.RenameAugment.augmentTextually(
                                    edit = withRefs,
                                    oldName = oldStem,
                                    newName = newStem,
                                    readFileText = readText,
                                )
                                val withDecl = page.lsp.RenameAugment.augmentDeclarationFile(
                                    edit = withTextual,
                                    declarationPath = oldPath,
                                    oldName = oldStem,
                                    newName = newStem,
                                    readFileText = readText,
                                )
                                val declarationPackage = FileSymbolRename.readPackageDeclaration(fileText)
                                val augmented = page.lsp.RenameAugment.augmentImports(
                                    edit = withDecl,
                                    candidatePaths = candidatePaths,
                                    oldName = oldStem,
                                    newName = newStem,
                                    readFileText = readText,
                                    declarationPackage = declarationPackage,
                                )
                                if (augmented.changes.isNotEmpty()) {
                                    applyRename(augmented)
                                }
                            }
                        }
                        finishFileRename()
                    }
                } else {
                    finishFileRename()
                }
            },
            onDismiss = { renameDialog = null },
        )
    }

    val activeDeleteDialog = deleteDialog
    if (activeDeleteDialog != null) {
        val multi = activeDeleteDialog.isMulti
        val first = activeDeleteDialog.primary
        val displayName = first.fileName?.toString() ?: first.toString()
        val isDir = java.nio.file.Files.isDirectory(first)
        val rel = FileTreeActions.relativeTo(rootDir, first)
        val message = if (multi) {
            "Delete ${activeDeleteDialog.paths.size} items?"
        } else {
            "Delete ${if (isDir) "folder" else "file"} '$displayName'?"
        }
        val detail = if (multi) {
            val preview = activeDeleteDialog.paths.take(4)
                .joinToString("\n") { FileTreeActions.relativeTo(rootDir, it) }
            if (activeDeleteDialog.paths.size > 4) "$preview\n…" else preview
        } else if (rel.isEmpty()) null else rel
        ConfirmDialog(
            title = "Delete",
            message = message,
            detail = detail,
            impact = if (multi) ImpactScanState.Idle else impactState,
            rootDir = rootDir,
            onJumpToHit = { hit ->
                deleteDialog = null
                jumpToProblem(hit.file, hit.line, hit.column)
            },
            confirmLabel = if (multi) "Delete all" else "Delete",
            danger = true,
            onConfirm = {
                val workspaceRoot = rootDir
                if (workspaceRoot != null) {
                    val trashed = FileTreeActions.deleteToTrash(activeDeleteDialog.paths, workspaceRoot)
                    val entries = when (trashed) {
                        is FileTreeActions.TrashResult.Ok -> trashed.entries
                        is FileTreeActions.TrashResult.Err -> {
                            println("[filetree] trash failed: ${trashed.message}")
                            trashed.partialEntries
                        }
                    }
                    entries.forEach { entry -> closeTabsUnderPath(entry.originalPath) }
                    if (entries.isNotEmpty()) {
                        fileOpHistory.push(FileOpHistory.DeleteOp(entries))
                        onFileOpHistoryChanged()
                        treeRevision++
                        FileTreeActions.purgeTrashOlderThan(workspaceRoot)
                    }
                } else {
                    val outcome = FileTreeActions.deleteBatch(activeDeleteDialog.paths)
                    outcome.results.forEach { (path, result) ->
                        if (result is FileTreeActions.DeleteResult.Ok) {
                            closeTabsUnderPath(path)
                        } else if (result is FileTreeActions.DeleteResult.Err) {
                            println("[filetree] delete failed for $path: ${result.message}")
                        }
                    }
                    if (outcome.successCount > 0) treeRevision++
                }
                deleteDialog = null
            },
            onDismiss = { deleteDialog = null },
        )
    }
}
