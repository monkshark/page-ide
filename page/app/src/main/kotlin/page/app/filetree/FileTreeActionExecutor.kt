package page.app.filetree

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import page.language.LspController
import page.app.utils.isKotlinSource
import page.workspace.FileOpHistory
import page.workspace.FileTreeActions
import page.workspace.FileTreeClipboard
import page.workspace.FolderPackageRename
import page.workspace.LargeCopyExecutor
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal data class PasteEntryDialogState(
    val remaining: List<Path>,
    val destParent: Path,
    val mode: FileTreeClipboard.Mode,
    val error: String? = null,
    val createdSoFar: List<FileOpHistory.CopyEntry> = emptyList(),
    val movesSoFar: List<Pair<Path, Path>> = emptyList(),
    val rewriteOriginalsSoFar: List<FileOpHistory.RewriteEntry> = emptyList(),
    val overwriteForAll: Boolean = false,
)

internal data class LargeCopyDialogState(
    val sourceName: String,
    val destName: String,
    val totalBytes: Long,
    val fileCount: Int,
    val bytesCopied: Long,
    val filesCopied: Int,
    val cancelToken: AtomicBoolean,
)

internal fun dropDestLabel(rootDir: Path?, dest: Path): String {
    val rel = FileTreeActions.relativeTo(rootDir, dest)
    return if (rel.isEmpty() || rel == ".") (dest.fileName?.toString() ?: dest.toString()) else rel
}

internal fun dropResultMessage(verb: String, count: Int, destLabel: String): String =
    if (count == 1) "$verb 1 item into $destLabel"
    else "$verb $count items into $destLabel"

internal class FileTreeActionExecutor(
    private val scope: CoroutineScope,
    private val getPasteDialog: () -> PasteEntryDialogState?,
    private val setPasteDialog: (PasteEntryDialogState?) -> Unit,
    private val getLargeCopyState: () -> LargeCopyDialogState?,
    private val setLargeCopyState: (LargeCopyDialogState?) -> Unit,
    private val rootDir: () -> Path?,
    private val readFileText: (Path) -> String?,
    private val applyFolderPackageSync: (Path, Path, Map<String, String>) -> List<FileOpHistory.RewriteEntry>,
    private val applySingleFileMoveSync: (Path, FolderPackageRename.SingleFileMovePlan) -> List<FileOpHistory.RewriteEntry>,
    private val remapTabsAfterRename: (Path, Path) -> Unit,
    private val remapTreeStateAfterRename: (Path, Path) -> Unit,
    private val controllerFor: (Path) -> LspController?,
    private val withFileTreeWatcherClosed: (() -> Unit) -> Unit,
    private val fileOpHistory: FileOpHistory.Stack,
    private val bumpHistoryVersion: () -> Unit,
    private val bumpTreeRevision: () -> Unit,
    private val showInfoToast: (String, (() -> Unit)?) -> Unit,
    private val onUndoFileOp: () -> Unit,
) {
    fun finalizePasteHistory(cur: PasteEntryDialogState) {
        val pushedMoves = cur.movesSoFar
        val pushedCopies = cur.createdSoFar
        if (pushedMoves.isNotEmpty()) {
            fileOpHistory.push(FileOpHistory.PasteCutOp(pushedMoves))
            bumpHistoryVersion()
            FileTreeClipboard.clearCutMarking()
            showInfoToast(dropResultMessage("Moved", pushedMoves.size, dropDestLabel(rootDir(), cur.destParent))) { onUndoFileOp() }
        } else if (pushedCopies.isNotEmpty()) {
            fileOpHistory.push(FileOpHistory.PasteCopyOp(pushedCopies))
            bumpHistoryVersion()
            showInfoToast(dropResultMessage("Copied", pushedCopies.size, dropDestLabel(rootDir(), cur.destParent))) { onUndoFileOp() }
        }
    }

    fun performPaste(newName: String, overwriteOnce: Boolean) {
        val current = getPasteDialog() ?: return
        val src = current.remaining.first()
        val rest = current.remaining.drop(1)
        val overwriteFlag = current.overwriteForAll || overwriteOnce
        when (current.mode) {
            FileTreeClipboard.Mode.Copy -> {
                val estimate = LargeCopyExecutor.estimate(src)
                if (estimate.isLarge) {
                    val cancelToken = AtomicBoolean(false)
                    val destLabel = current.destParent.fileName?.toString() ?: current.destParent.toString()
                    setLargeCopyState(
                        LargeCopyDialogState(
                            sourceName = src.fileName?.toString() ?: src.toString(),
                            destName = destLabel,
                            totalBytes = estimate.totalBytes,
                            fileCount = estimate.fileCount,
                            bytesCopied = 0L,
                            filesCopied = 0,
                            cancelToken = cancelToken,
                        ),
                    )
                    setPasteDialog(null)
                    val capturedCurrent = current
                    val capturedRest = rest
                    val capturedName = newName
                    val capturedOverwrite = overwriteFlag
                    val bytesAcc = AtomicLong(0L)
                    val filesAcc = AtomicInteger(0)
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            LargeCopyExecutor.copyWithProgress(
                                source = src,
                                destParent = capturedCurrent.destParent,
                                newName = capturedName,
                                isCancelled = { cancelToken.get() },
                                onProgress = { deltaBytes, deltaFiles ->
                                    val nb = bytesAcc.addAndGet(deltaBytes)
                                    val nf = filesAcc.addAndGet(deltaFiles)
                                    getLargeCopyState()?.let { snap ->
                                        setLargeCopyState(snap.copy(bytesCopied = nb, filesCopied = nf))
                                    }
                                },
                                overwriteExisting = capturedOverwrite,
                            )
                        }
                        setLargeCopyState(null)
                        when (outcome) {
                            is LargeCopyExecutor.CopyOutcome.Ok -> {
                                bumpTreeRevision()
                                val nextCreated = capturedCurrent.createdSoFar +
                                    FileOpHistory.CopyEntry(source = src, dest = outcome.target)
                                if (capturedRest.isEmpty()) {
                                    fileOpHistory.push(FileOpHistory.PasteCopyOp(nextCreated))
                                    bumpHistoryVersion()
                                    val destLbl = dropDestLabel(rootDir(), capturedCurrent.destParent)
                                    showInfoToast(dropResultMessage("Copied", nextCreated.size, destLbl)) { onUndoFileOp() }
                                } else {
                                    setPasteDialog(
                                        capturedCurrent.copy(
                                            remaining = capturedRest,
                                            error = null,
                                            createdSoFar = nextCreated,
                                        ),
                                    )
                                }
                            }
                            is LargeCopyExecutor.CopyOutcome.Cancelled -> {
                                bumpTreeRevision()
                                if (capturedCurrent.createdSoFar.isNotEmpty()) {
                                    fileOpHistory.push(FileOpHistory.PasteCopyOp(capturedCurrent.createdSoFar))
                                    bumpHistoryVersion()
                                }
                            }
                            is LargeCopyExecutor.CopyOutcome.Err -> {
                                setPasteDialog(capturedCurrent.copy(error = outcome.message))
                            }
                        }
                    }
                } else {
                    when (val result = FileTreeActions.copyFile(src, current.destParent, newName, overwriteFlag)) {
                        is FileTreeActions.CopyResult.Ok -> {
                            bumpTreeRevision()
                            val nextCreated = current.createdSoFar +
                                FileOpHistory.CopyEntry(source = src, dest = result.path)
                            if (rest.isEmpty()) {
                                fileOpHistory.push(FileOpHistory.PasteCopyOp(nextCreated))
                                bumpHistoryVersion()
                                setPasteDialog(null)
                                val destLbl = dropDestLabel(rootDir(), current.destParent)
                                showInfoToast(dropResultMessage("Copied", nextCreated.size, destLbl)) { onUndoFileOp() }
                            } else {
                                setPasteDialog(current.copy(remaining = rest, error = null, createdSoFar = nextCreated))
                            }
                        }
                        is FileTreeActions.CopyResult.Err -> {
                            setPasteDialog(current.copy(error = result.message))
                        }
                    }
                }
            }
            FileTreeClipboard.Mode.Cut -> {
                val srcIsDir = Files.isDirectory(src)
                val intendedNewPath = current.destParent.resolve(newName)
                val packageMap = if (srcIsDir) {
                    val movingToDifferentParent = src.parent?.toAbsolutePath()?.normalize() !=
                        current.destParent.toAbsolutePath().normalize()
                    if (movingToDifferentParent) {
                        FolderPackageRename.computePackageMapForMove(
                            oldFolder = src,
                            newFolder = intendedNewPath,
                            workspaceRoot = rootDir(),
                            readText = readFileText,
                        )
                    } else {
                        FolderPackageRename.computePackageMap(src, newName, readFileText)
                    }
                } else emptyMap()
                val singleFilePlan = if (!srcIsDir && isKotlinSource(src) &&
                    src.parent?.toAbsolutePath()?.normalize() !=
                    current.destParent.toAbsolutePath().normalize()
                ) {
                    FolderPackageRename.planSingleFileMove(
                        oldFile = src,
                        newParent = current.destParent,
                        workspaceRoot = rootDir(),
                        readText = readFileText,
                    )
                } else null
                var moveOriginals: List<FileOpHistory.RewriteEntry> = emptyList()
                val performMove: () -> FileTreeActions.MoveResult = {
                    val txRoot = if (srcIsDir) rootDir() else null
                    val r = FileTreeActions.moveFile(src, current.destParent, newName, txRoot, overwriteFlag)
                    if (r is FileTreeActions.MoveResult.Ok) {
                        remapTabsAfterRename(src, r.path)
                        remapTreeStateAfterRename(src, r.path)
                        if (srcIsDir) {
                            moveOriginals = applyFolderPackageSync(src, r.path, packageMap)
                        } else if (singleFilePlan != null) {
                            moveOriginals = applySingleFileMoveSync(r.path, singleFilePlan)
                        }
                    }
                    r
                }
                val result: FileTreeActions.MoveResult = if (srcIsDir) {
                    var captured: FileTreeActions.MoveResult? = null
                    val moveCtrl = controllerFor(src)
                    val doMove = {
                        withFileTreeWatcherClosed {
                            captured = performMove()
                        }
                    }
                    if (moveCtrl != null) {
                        moveCtrl.runWithClientDown("folder move: ${src.fileName} → ${current.destParent.fileName}/$newName") { doMove() }
                    } else {
                        doMove()
                    }
                    captured ?: FileTreeActions.MoveResult.Err("move did not run")
                } else {
                    performMove()
                }
                when (result) {
                    is FileTreeActions.MoveResult.Ok -> {
                        bumpTreeRevision()
                        val nextMoves = current.movesSoFar + (src to result.path)
                        val nextRewriteOriginals = current.rewriteOriginalsSoFar + moveOriginals
                        if (rest.isEmpty()) {
                            val cutOp = FileOpHistory.PasteCutOp(nextMoves)
                            val composedOp: FileOpHistory.Op = if (nextRewriteOriginals.isEmpty()) cutOp
                            else FileOpHistory.CompositeOp(listOf(FileOpHistory.ReferenceRewriteOp(nextRewriteOriginals), cutOp))
                            fileOpHistory.push(composedOp)
                            bumpHistoryVersion()
                            FileTreeClipboard.clearCutMarking()
                            setPasteDialog(null)
                            val destLbl = dropDestLabel(rootDir(), current.destParent)
                            showInfoToast(dropResultMessage("Moved", nextMoves.size, destLbl)) { onUndoFileOp() }
                        } else {
                            setPasteDialog(
                                current.copy(
                                    remaining = rest,
                                    error = null,
                                    movesSoFar = nextMoves,
                                    rewriteOriginalsSoFar = nextRewriteOriginals,
                                ),
                            )
                        }
                    }
                    is FileTreeActions.MoveResult.Err -> {
                        setPasteDialog(current.copy(error = result.message))
                    }
                }
            }
        }
    }
}
