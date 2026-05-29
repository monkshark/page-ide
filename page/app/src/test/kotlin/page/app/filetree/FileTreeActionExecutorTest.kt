package page.app.filetree

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import page.workspace.FileOpHistory
import page.workspace.FileTreeClipboard
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileTreeActionExecutorTest {

    private lateinit var root: Path
    private val history = FileOpHistory.Stack()
    private var pasteDialog: PasteEntryDialogState? = null
    private var largeCopyState: LargeCopyDialogState? = null
    private val toasts = mutableListOf<String>()
    private var folderRewrites: List<FileOpHistory.RewriteEntry> = emptyList()

    private fun executor() = FileTreeActionExecutor(
        scope = CoroutineScope(Dispatchers.Unconfined),
        getPasteDialog = { pasteDialog },
        setPasteDialog = { pasteDialog = it },
        getLargeCopyState = { largeCopyState },
        setLargeCopyState = { largeCopyState = it },
        rootDir = { root },
        readFileText = { p -> runCatching { Files.readString(p) }.getOrNull() },
        applyFolderPackageSync = { _, _, _ -> folderRewrites },
        applySingleFileMoveSync = { _, _ -> emptyList() },
        remapTabsAfterRename = { _, _ -> },
        remapTreeStateAfterRename = { _, _ -> },
        controllerFor = { null },
        withFileTreeWatcherClosed = { it() },
        fileOpHistory = history,
        bumpHistoryVersion = {},
        bumpTreeRevision = {},
        showInfoToast = { msg, _ -> toasts.add(msg) },
        onUndoFileOp = {},
    )

    @BeforeTest
    fun setup() {
        root = Files.createTempDirectory("ftae-test")
    }

    @AfterTest
    fun cleanup() {
        Files.walk(root).use { s -> s.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    private fun file(name: String, content: String = "x"): Path {
        val p = root.resolve(name)
        Files.writeString(p, content)
        return p
    }

    private fun dir(name: String): Path = Files.createDirectory(root.resolve(name))

    @Test
    fun `copy single pushes PasteCopyOp and creates the file`() {
        val src = file("a.txt", "hello")
        val dest = dir("out")
        pasteDialog = PasteEntryDialogState(remaining = listOf(src), destParent = dest, mode = FileTreeClipboard.Mode.Copy)

        executor().performPaste("copied.txt", false)

        assertNull(pasteDialog)
        val op = history.peek()
        assertIs<FileOpHistory.PasteCopyOp>(op)
        assertEquals(1, op.entries.size)
        assertTrue(Files.exists(dest.resolve("copied.txt")))
        assertEquals(1, toasts.size)
    }

    @Test
    fun `cut single file without rewrites pushes plain PasteCutOp`() {
        val src = file("a.txt", "hello")
        val dest = dir("out")
        pasteDialog = PasteEntryDialogState(remaining = listOf(src), destParent = dest, mode = FileTreeClipboard.Mode.Cut)

        executor().performPaste("moved.txt", false)

        assertNull(pasteDialog)
        val op = history.peek()
        assertIs<FileOpHistory.PasteCutOp>(op)
        assertEquals(1, op.moves.size)
        assertTrue(Files.notExists(src))
        assertTrue(Files.exists(dest.resolve("moved.txt")))
    }

    @Test
    fun `cut folder with reference rewrites composes ReferenceRewriteOp before the move`() {
        val src = dir("pkg")
        Files.writeString(src.resolve("F.kt"), "package pkg")
        val dest = dir("dst")
        folderRewrites = listOf(FileOpHistory.RewriteEntry(root.resolve("Other.kt"), "old", "new"))
        pasteDialog = PasteEntryDialogState(remaining = listOf(src), destParent = dest, mode = FileTreeClipboard.Mode.Cut)

        executor().performPaste("pkg", false)

        val op = history.peek()
        assertIs<FileOpHistory.CompositeOp>(op)
        assertEquals(2, op.parts.size)
        assertIs<FileOpHistory.ReferenceRewriteOp>(op.parts[0])
        assertIs<FileOpHistory.PasteCutOp>(op.parts[1])
        assertTrue(Files.exists(dest.resolve("pkg").resolve("F.kt")))
    }

    @Test
    fun `multi paste keeps the dialog open with accumulated history until the last item`() {
        val a = file("a.txt")
        val b = file("b.txt")
        val dest = dir("out")
        pasteDialog = PasteEntryDialogState(remaining = listOf(a, b), destParent = dest, mode = FileTreeClipboard.Mode.Copy)
        val exec = executor()

        exec.performPaste("a.txt", false)

        val mid = pasteDialog
        assertIs<PasteEntryDialogState>(mid)
        assertEquals(listOf(b), mid.remaining)
        assertEquals(1, mid.createdSoFar.size)
        assertEquals(0, history.size)

        exec.performPaste("b.txt", false)

        assertNull(pasteDialog)
        val op = history.peek()
        assertIs<FileOpHistory.PasteCopyOp>(op)
        assertEquals(2, op.entries.size)
    }
}
