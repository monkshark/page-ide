package page.app

import page.runtime.*

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileOpHistoryTest {

    private fun rewriteEntry(path: Path, original: String, rewritten: String = original) =
        FileOpHistory.RewriteEntry(path, original, rewritten)

    private fun copyEntries(vararg dests: Path) =
        dests.map { FileOpHistory.CopyEntry(source = it, dest = it) }

    @Test
    fun `stack push and pop in LIFO order`() {
        val stack = FileOpHistory.Stack()
        val a = FileOpHistory.CreateOp(Path.of("a"))
        val b = FileOpHistory.CreateOp(Path.of("b"))
        stack.push(a)
        stack.push(b)
        assertEquals(2, stack.size)
        assertEquals(b, stack.pop())
        assertEquals(a, stack.pop())
        assertNull(stack.pop())
    }

    @Test
    fun `stack enforces max history`() {
        val stack = FileOpHistory.Stack()
        repeat(100) { i -> stack.push(FileOpHistory.CreateOp(Path.of("p$i"))) }
        assertEquals(64, stack.size)
    }

    @Test
    fun `popForUndo moves op to redo stack and popForRedo restores it`() {
        val stack = FileOpHistory.Stack()
        val a = FileOpHistory.CreateOp(Path.of("a"))
        val b = FileOpHistory.CreateOp(Path.of("b"))
        stack.push(a)
        stack.push(b)
        assertEquals(b, stack.popForUndo())
        assertEquals(1, stack.size)
        assertEquals(1, stack.redoSize)
        assertEquals(b, stack.peekRedo())
        assertEquals(b, stack.popForRedo())
        assertEquals(2, stack.size)
        assertEquals(0, stack.redoSize)
    }

    @Test
    fun `push clears redo stack`() {
        val stack = FileOpHistory.Stack()
        val a = FileOpHistory.CreateOp(Path.of("a"))
        val b = FileOpHistory.CreateOp(Path.of("b"))
        val c = FileOpHistory.CreateOp(Path.of("c"))
        stack.push(a)
        stack.push(b)
        stack.popForUndo()
        assertEquals(1, stack.redoSize)
        stack.push(c)
        assertEquals(0, stack.redoSize)
    }

    @Test
    fun `CreateOp undo deletes the created file`(@TempDir tmp: Path) {
        val target = tmp.resolve("created.txt")
        Files.createFile(target)
        val op = FileOpHistory.CreateOp(target)
        val result = op.undo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertFalse(Files.exists(target))
    }

    @Test
    fun `CreateOp undo deletes the created folder recursively`(@TempDir tmp: Path) {
        val target = tmp.resolve("folder")
        Files.createDirectories(target.resolve("nested"))
        Files.createFile(target.resolve("nested/inside.txt"))
        val op = FileOpHistory.CreateOp(target, isDirectory = true)
        val result = op.undo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertFalse(Files.exists(target))
    }

    @Test
    fun `CreateOp redo recreates the file`(@TempDir tmp: Path) {
        val target = tmp.resolve("recreated.txt")
        val op = FileOpHistory.CreateOp(target, isDirectory = false)
        val result = op.redo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertTrue(Files.exists(target))
        assertFalse(Files.isDirectory(target))
    }

    @Test
    fun `CreateOp redo recreates the folder`(@TempDir tmp: Path) {
        val target = tmp.resolve("recreated-folder")
        val op = FileOpHistory.CreateOp(target, isDirectory = true)
        val result = op.redo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertTrue(Files.isDirectory(target))
    }

    @Test
    fun `RenameOp undo restores the original name`(@TempDir tmp: Path) {
        val from = tmp.resolve("Old.txt")
        val to = tmp.resolve("New.txt")
        Files.createFile(from)
        Files.move(from, to)
        val op = FileOpHistory.RenameOp(from = from, to = to)
        val result = op.undo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertTrue(Files.exists(from))
        assertFalse(Files.exists(to))
    }

    @Test
    fun `RenameOp undo fails when original path is occupied`(@TempDir tmp: Path) {
        val from = tmp.resolve("Old.txt")
        val to = tmp.resolve("New.txt")
        Files.createFile(from)
        Files.createFile(to)
        val op = FileOpHistory.RenameOp(from = from, to = to)
        val result = op.undo()
        assertTrue(result is FileOpHistory.UndoResult.Err)
    }

    @Test
    fun `RenameOp redo re-renames`(@TempDir tmp: Path) {
        val from = tmp.resolve("Old.txt")
        val to = tmp.resolve("New.txt")
        Files.createFile(from)
        val op = FileOpHistory.RenameOp(from = from, to = to)
        val result = op.redo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertFalse(Files.exists(from))
        assertTrue(Files.exists(to))
    }

    @Test
    fun `DeleteOp undo restores from trash`(@TempDir tmp: Path) {
        val original = tmp.resolve("doomed.txt")
        Files.writeString(original, "alive")
        val trashedPath = tmp.resolve("trash/0-doomed.txt")
        Files.createDirectories(trashedPath.parent)
        Files.move(original, trashedPath)
        val op = FileOpHistory.DeleteOp(
            listOf(FileOpHistory.TrashEntry(originalPath = original, trashedPath = trashedPath)),
        )
        val result = op.undo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertTrue(Files.exists(original))
        assertEquals("alive", Files.readString(original))
        assertFalse(Files.exists(trashedPath))
    }

    @Test
    fun `DeleteOp redo moves file back to trash`(@TempDir tmp: Path) {
        val original = tmp.resolve("doomed.txt")
        Files.writeString(original, "alive")
        val trashedPath = tmp.resolve("trash/0-doomed.txt")
        val op = FileOpHistory.DeleteOp(
            listOf(FileOpHistory.TrashEntry(originalPath = original, trashedPath = trashedPath)),
        )
        val result = op.redo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertFalse(Files.exists(original))
        assertTrue(Files.exists(trashedPath))
        assertEquals("alive", Files.readString(trashedPath))
    }

    @Test
    fun `PasteCopyOp undo removes copied entries`(@TempDir tmp: Path) {
        val copyA = tmp.resolve("copied-a.txt").also { Files.createFile(it) }
        val copyDir = tmp.resolve("copied-folder").also {
            Files.createDirectories(it)
            Files.createFile(it.resolve("inner.txt"))
        }
        val op = FileOpHistory.PasteCopyOp(copyEntries(copyA, copyDir))
        val result = op.undo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertFalse(Files.exists(copyA))
        assertFalse(Files.exists(copyDir))
    }

    @Test
    fun `PasteCopyOp redo re-copies file from source to dest`(@TempDir tmp: Path) {
        val src = tmp.resolve("src.txt").also { Files.writeString(it, "payload") }
        val dst = tmp.resolve("dest/copied.txt")
        val op = FileOpHistory.PasteCopyOp(listOf(FileOpHistory.CopyEntry(source = src, dest = dst)))
        val result = op.redo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertTrue(Files.exists(dst))
        assertEquals("payload", Files.readString(dst))
    }

    @Test
    fun `PasteCopyOp redo re-copies directory recursively`(@TempDir tmp: Path) {
        val srcDir = tmp.resolve("source-folder").also { Files.createDirectories(it) }
        Files.writeString(srcDir.resolve("leaf.txt"), "L")
        Files.createDirectories(srcDir.resolve("nested"))
        Files.writeString(srcDir.resolve("nested/inner.txt"), "I")
        val dstDir = tmp.resolve("dest-folder")
        val op = FileOpHistory.PasteCopyOp(listOf(FileOpHistory.CopyEntry(source = srcDir, dest = dstDir)))
        val result = op.redo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertTrue(Files.isDirectory(dstDir))
        assertEquals("L", Files.readString(dstDir.resolve("leaf.txt")))
        assertEquals("I", Files.readString(dstDir.resolve("nested/inner.txt")))
    }

    @Test
    fun `PasteCutOp undo moves files back to origin`(@TempDir tmp: Path) {
        val origin = tmp.resolve("origin/note.txt")
        val current = tmp.resolve("moved/note.txt")
        Files.createDirectories(origin.parent)
        Files.createDirectories(current.parent)
        Files.writeString(current, "hello")
        val op = FileOpHistory.PasteCutOp(listOf(origin to current))
        val result = op.undo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertTrue(Files.exists(origin))
        assertEquals("hello", Files.readString(origin))
        assertFalse(Files.exists(current))
    }

    @Test
    fun `PasteCutOp redo moves files back to destination`(@TempDir tmp: Path) {
        val origin = tmp.resolve("origin/note.txt")
        val current = tmp.resolve("moved/note.txt")
        Files.createDirectories(origin.parent)
        Files.writeString(origin, "hello")
        val op = FileOpHistory.PasteCutOp(listOf(origin to current))
        val result = op.redo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertFalse(Files.exists(origin))
        assertTrue(Files.exists(current))
        assertEquals("hello", Files.readString(current))
    }

    @Test
    fun `ReferenceRewriteOp undo restores file contents`(@TempDir tmp: Path) {
        val a = tmp.resolve("A.kt").also { Files.writeString(it, "import old.pkg.Foo\nclass A") }
        val b = tmp.resolve("B.kt").also { Files.writeString(it, "import old.pkg.Bar\nclass B") }
        val rewrites = listOf(
            rewriteEntry(a, "import old.pkg.Foo\nclass A", "import new.pkg.Foo\nclass A"),
            rewriteEntry(b, "import old.pkg.Bar\nclass B", "import new.pkg.Bar\nclass B"),
        )
        Files.writeString(a, "import new.pkg.Foo\nclass A")
        Files.writeString(b, "import new.pkg.Bar\nclass B")
        val op = FileOpHistory.ReferenceRewriteOp(rewrites)
        val result = op.undo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertEquals("import old.pkg.Foo\nclass A", Files.readString(a))
        assertEquals("import old.pkg.Bar\nclass B", Files.readString(b))
    }

    @Test
    fun `ReferenceRewriteOp redo restores rewritten contents`(@TempDir tmp: Path) {
        val a = tmp.resolve("A.kt").also { Files.writeString(it, "import old.pkg.Foo\nclass A") }
        val rewrites = listOf(rewriteEntry(a, "import old.pkg.Foo\nclass A", "import new.pkg.Foo\nclass A"))
        val op = FileOpHistory.ReferenceRewriteOp(rewrites)
        val result = op.redo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertEquals("import new.pkg.Foo\nclass A", Files.readString(a))
    }

    @Test
    fun `ReferenceRewriteOp undo with empty list returns Ok`() {
        val op = FileOpHistory.ReferenceRewriteOp(emptyList())
        val result = op.undo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertEquals(0, (result as FileOpHistory.UndoResult.Ok).touched.size)
    }

    @Test
    fun `CompositeOp undoes parts in reverse order`(@TempDir tmp: Path) {
        val refFile = tmp.resolve("Ref.kt").also { Files.writeString(it, "import old.pkg.Moved\n") }
        val origin = tmp.resolve("from/Moved.kt")
        val current = tmp.resolve("to/Moved.kt")
        Files.createDirectories(origin.parent)
        Files.createDirectories(current.parent)
        Files.writeString(current, "package new.pkg\nclass Moved")

        Files.writeString(refFile, "import new.pkg.Moved\n")

        val rewriteOp = FileOpHistory.ReferenceRewriteOp(
            listOf(rewriteEntry(refFile, "import old.pkg.Moved\n", "import new.pkg.Moved\n")),
        )
        val cutOp = FileOpHistory.PasteCutOp(listOf(origin to current))
        val composite = FileOpHistory.CompositeOp(listOf(rewriteOp, cutOp))
        val result = composite.undo()

        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertEquals("import old.pkg.Moved\n", Files.readString(refFile))
        assertTrue(Files.exists(origin))
        assertEquals("package new.pkg\nclass Moved", Files.readString(origin))
        assertFalse(Files.exists(current))
    }

    @Test
    fun `CompositeOp redoes parts in forward order`(@TempDir tmp: Path) {
        val refFile = tmp.resolve("Ref.kt").also { Files.writeString(it, "import old.pkg.Moved\n") }
        val origin = tmp.resolve("from/Moved.kt")
        val current = tmp.resolve("to/Moved.kt")
        Files.createDirectories(origin.parent)
        Files.createDirectories(current.parent)
        Files.writeString(origin, "package new.pkg\nclass Moved")

        val rewriteOp = FileOpHistory.ReferenceRewriteOp(
            listOf(rewriteEntry(refFile, "import old.pkg.Moved\n", "import new.pkg.Moved\n")),
        )
        val cutOp = FileOpHistory.PasteCutOp(listOf(origin to current))
        val composite = FileOpHistory.CompositeOp(listOf(rewriteOp, cutOp))
        val result = composite.redo()

        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertEquals("import new.pkg.Moved\n", Files.readString(refFile))
        assertFalse(Files.exists(origin))
        assertEquals("package new.pkg\nclass Moved", Files.readString(current))
    }

    @Test
    fun `CompositeOp empty undo returns Ok with no touched paths`() {
        val composite = FileOpHistory.CompositeOp(emptyList())
        val result = composite.undo()
        assertTrue(result is FileOpHistory.UndoResult.Ok)
        assertEquals(0, (result as FileOpHistory.UndoResult.Ok).touched.size)
    }

    @Test
    fun `CompositeOp surfaces part errors`(@TempDir tmp: Path) {
        val present = tmp.resolve("here.txt").also { Files.writeString(it, "x") }
        val missingOrigin = tmp.resolve("origin/missing.txt")
        val missingCurrent = tmp.resolve("moved/missing.txt")
        val rewriteOp = FileOpHistory.ReferenceRewriteOp(listOf(rewriteEntry(present, "y")))
        val cutOp = FileOpHistory.PasteCutOp(listOf(missingOrigin to missingCurrent))
        val composite = FileOpHistory.CompositeOp(listOf(rewriteOp, cutOp))
        val result = composite.undo()
        assertTrue(result is FileOpHistory.UndoResult.Err)
    }

    @Test
    fun `FileTreeActions deleteToTrash followed by DeleteOp undo restores entries`(@TempDir tmp: Path) {
        val workspace = tmp.resolve("ws").also { Files.createDirectories(it) }
        val a = workspace.resolve("a.txt").also { Files.writeString(it, "A") }
        val b = workspace.resolve("b.txt").also { Files.writeString(it, "B") }
        val trash = FileTreeActions.deleteToTrash(listOf(a, b), workspace)
        assertTrue(trash is FileTreeActions.TrashResult.Ok)
        val entries = (trash as FileTreeActions.TrashResult.Ok).entries
        assertFalse(Files.exists(a))
        assertFalse(Files.exists(b))
        val undo = FileOpHistory.DeleteOp(entries).undo()
        assertTrue(undo is FileOpHistory.UndoResult.Ok)
        assertEquals("A", Files.readString(a))
        assertEquals("B", Files.readString(b))
    }
}
