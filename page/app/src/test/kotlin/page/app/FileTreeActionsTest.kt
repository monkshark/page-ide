package page.app

import page.runtime.*

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class FileTreeActionsTest {

    private fun newDir(): Path {
        val dir = Files.createTempDirectory("page-ide-actions-test-")
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        })
        return dir
    }

    @Test
    fun `createFile creates new file in directory`() {
        val root = newDir()
        val result = FileTreeActions.createFile(root, "hello.kt")
        assertIs<FileTreeActions.CreateResult.Ok>(result)
        assertTrue(Files.exists(result.path))
        assertTrue(Files.isRegularFile(result.path))
        assertEquals("hello.kt", result.path.fileName.toString())
    }

    @Test
    fun `createFile rejects when parent is not a directory`() {
        val root = newDir()
        val file = Files.createFile(root.resolve("a.kt"))
        val result = FileTreeActions.createFile(file, "b.kt")
        assertIs<FileTreeActions.CreateResult.Err>(result)
        assertTrue(result.message.contains("not a directory"))
    }

    @Test
    fun `createFile rejects existing target`() {
        val root = newDir()
        Files.createFile(root.resolve("a.kt"))
        val result = FileTreeActions.createFile(root, "a.kt")
        assertIs<FileTreeActions.CreateResult.Err>(result)
        assertTrue(result.message.contains("already exists"))
    }

    @Test
    fun `createFile rejects empty name`() {
        val root = newDir()
        val result = FileTreeActions.createFile(root, "   ")
        assertIs<FileTreeActions.CreateResult.Err>(result)
        assertTrue(result.message.contains("empty"))
    }

    @Test
    fun `createFile rejects dot and dotdot`() {
        val root = newDir()
        assertIs<FileTreeActions.CreateResult.Err>(FileTreeActions.createFile(root, "."))
        assertIs<FileTreeActions.CreateResult.Err>(FileTreeActions.createFile(root, ".."))
    }

    @Test
    fun `createFile rejects invalid characters`() {
        val root = newDir()
        val bad = listOf("a/b.kt", "a\\b.kt", "a:b.kt", "a*b.kt", "a?b.kt", "a\"b.kt", "a<b.kt", "a>b.kt", "a|b.kt")
        for (name in bad) {
            val result = FileTreeActions.createFile(root, name)
            assertIs<FileTreeActions.CreateResult.Err>(result, "expected error for '$name'")
            assertTrue(result.message.contains("Invalid character"), "expected invalid-char error for '$name', got: ${result.message}")
        }
    }

    @Test
    fun `createFolder creates new directory`() {
        val root = newDir()
        val result = FileTreeActions.createFolder(root, "sub")
        assertIs<FileTreeActions.CreateResult.Ok>(result)
        assertTrue(Files.isDirectory(result.path))
    }

    @Test
    fun `createFolder rejects existing target`() {
        val root = newDir()
        Files.createDirectories(root.resolve("sub"))
        val result = FileTreeActions.createFolder(root, "sub")
        assertIs<FileTreeActions.CreateResult.Err>(result)
        assertTrue(result.message.contains("already exists"))
    }

    @Test
    fun `parentDirOf returns same path for directory`() {
        val root = newDir()
        assertEquals(root, FileTreeActions.parentDirOf(root))
    }

    @Test
    fun `parentDirOf returns parent for file`() {
        val root = newDir()
        val file = Files.createFile(root.resolve("a.kt"))
        assertEquals(root, FileTreeActions.parentDirOf(file))
    }

    @Test
    fun `relativeTo returns absolute path when workspace root is null`() {
        val root = newDir()
        val file = Files.createFile(root.resolve("a.kt"))
        assertEquals(file.toString(), FileTreeActions.relativeTo(null, file))
    }

    @Test
    fun `relativeTo returns relative path when workspace root is provided`() {
        val root = newDir()
        Files.createDirectories(root.resolve("sub"))
        val file = Files.createFile(root.resolve("sub/a.kt"))
        val rel = FileTreeActions.relativeTo(root, file)
        assertTrue(rel.endsWith("a.kt"))
        assertTrue(rel.contains("sub"))
        assertTrue(!rel.startsWith(root.toString()))
    }

    @Test
    fun `relativeTo returns empty for root itself`() {
        val root = newDir()
        assertEquals("", FileTreeActions.relativeTo(root, root))
    }

    @Test
    fun `rename moves file to new name`() {
        val root = newDir()
        val file = Files.createFile(root.resolve("old.kt"))
        Files.writeString(file, "content")
        val result = FileTreeActions.rename(file, "new.kt")
        assertIs<FileTreeActions.RenameResult.Ok>(result)
        assertTrue(Files.exists(result.path))
        assertEquals("new.kt", result.path.fileName.toString())
        assertEquals("content", Files.readString(result.path))
        assertTrue(!Files.exists(file))
    }

    @Test
    fun `rename rejects unchanged name`() {
        val root = newDir()
        val file = Files.createFile(root.resolve("a.kt"))
        val result = FileTreeActions.rename(file, "a.kt")
        assertIs<FileTreeActions.RenameResult.Err>(result)
        assertTrue(result.message.contains("unchanged"))
    }

    @Test
    fun `rename rejects when target exists`() {
        val root = newDir()
        val file = Files.createFile(root.resolve("a.kt"))
        Files.createFile(root.resolve("b.kt"))
        val result = FileTreeActions.rename(file, "b.kt")
        assertIs<FileTreeActions.RenameResult.Err>(result)
        assertTrue(result.message.contains("already exists"))
    }

    @Test
    fun `rename rejects invalid name`() {
        val root = newDir()
        val file = Files.createFile(root.resolve("a.kt"))
        val result = FileTreeActions.rename(file, "")
        assertIs<FileTreeActions.RenameResult.Err>(result)
        assertTrue(result.message.contains("empty"))
    }

    @Test
    fun `rename works on directory`() {
        val root = newDir()
        val dir = Files.createDirectories(root.resolve("old"))
        Files.createFile(dir.resolve("inside.kt"))
        val result = FileTreeActions.rename(dir, "renamed")
        assertIs<FileTreeActions.RenameResult.Ok>(result)
        assertTrue(Files.isDirectory(result.path))
        assertTrue(Files.exists(result.path.resolve("inside.kt")))
    }

    @Test
    fun `rename works on nested directory tree`() {
        val root = newDir()
        val dir = Files.createDirectories(root.resolve("parent/inner"))
        Files.writeString(Files.createFile(dir.resolve("a.kt")), "AAA")
        Files.writeString(Files.createFile(root.resolve("parent").resolve("b.kt")), "BBB")
        val result = FileTreeActions.rename(root.resolve("parent"), "lattice")
        assertIs<FileTreeActions.RenameResult.Ok>(result)
        assertTrue(Files.isDirectory(result.path))
        assertTrue(!Files.exists(root.resolve("parent")))
        assertEquals("AAA", Files.readString(result.path.resolve("inner/a.kt")))
        assertEquals("BBB", Files.readString(result.path.resolve("b.kt")))
    }

    @Test
    fun `renameDirectoryByCopy copies tree then deletes source`() {
        val root = newDir()
        val source = Files.createDirectories(root.resolve("complex"))
        Files.writeString(Files.createFile(source.resolve("a.kt")), "alpha")
        val inner = Files.createDirectories(source.resolve("inner"))
        Files.writeString(Files.createFile(inner.resolve("b.kt")), "beta")
        val target = root.resolve("lattice")

        FileTreeActions.renameDirectoryByCopy(source, target)

        assertTrue(!Files.exists(source))
        assertTrue(Files.isDirectory(target))
        assertEquals("alpha", Files.readString(target.resolve("a.kt")))
        assertEquals("beta", Files.readString(target.resolve("inner/b.kt")))
    }

    @Test
    fun `delete removes file`() {
        val root = newDir()
        val file = Files.createFile(root.resolve("a.kt"))
        val result = FileTreeActions.delete(file)
        assertIs<FileTreeActions.DeleteResult.Ok>(result)
        assertTrue(!Files.exists(file))
    }

    @Test
    fun `delete removes folder recursively`() {
        val root = newDir()
        val dir = Files.createDirectories(root.resolve("sub/inner"))
        Files.createFile(dir.resolve("a.kt"))
        Files.createFile(root.resolve("sub").resolve("b.kt"))
        val result = FileTreeActions.delete(root.resolve("sub"))
        assertIs<FileTreeActions.DeleteResult.Ok>(result)
        assertTrue(!Files.exists(root.resolve("sub")))
    }

    @Test
    fun `delete rejects non-existent path`() {
        val root = newDir()
        val result = FileTreeActions.delete(root.resolve("missing.kt"))
        assertIs<FileTreeActions.DeleteResult.Err>(result)
        assertTrue(result.message.contains("not exist"))
    }

    @Test
    fun `pruneRedundantDescendants drops paths covered by an ancestor`() {
        val root = newDir()
        val sub = Files.createDirectories(root.resolve("sub"))
        val inner = Files.createFile(sub.resolve("inner.kt"))
        val sibling = Files.createFile(root.resolve("sibling.kt"))

        val pruned = FileTreeActions.pruneRedundantDescendants(listOf(inner, sub, sibling))
        val absolute = pruned.map { it.toAbsolutePath().normalize() }.toSet()
        assertTrue(sub.toAbsolutePath().normalize() in absolute)
        assertTrue(sibling.toAbsolutePath().normalize() in absolute)
        assertTrue(inner.toAbsolutePath().normalize() !in absolute)
    }

    @Test
    fun `pruneRedundantDescendants keeps unrelated paths`() {
        val root = newDir()
        val a = Files.createFile(root.resolve("a.kt"))
        val b = Files.createFile(root.resolve("b.kt"))
        val pruned = FileTreeActions.pruneRedundantDescendants(listOf(a, b))
        assertEquals(2, pruned.size)
    }

    @Test
    fun `pruneRedundantDescendants removes exact duplicates`() {
        val root = newDir()
        val a = Files.createFile(root.resolve("a.kt"))
        val pruned = FileTreeActions.pruneRedundantDescendants(listOf(a, a, a))
        assertEquals(1, pruned.size)
    }

    @Test
    fun `deleteBatch removes multiple files and reports per-path results`() {
        val root = newDir()
        val a = Files.createFile(root.resolve("a.kt"))
        val b = Files.createFile(root.resolve("b.kt"))
        val outcome = FileTreeActions.deleteBatch(listOf(a, b))
        assertEquals(2, outcome.results.size)
        assertEquals(2, outcome.successCount)
        assertEquals(0, outcome.failureCount)
        assertTrue(outcome.allOk)
        assertTrue(!Files.exists(a))
        assertTrue(!Files.exists(b))
    }

    @Test
    fun `deleteBatch skips children when parent is already in the batch`() {
        val root = newDir()
        val sub = Files.createDirectories(root.resolve("sub"))
        val inner = Files.createFile(sub.resolve("inner.kt"))
        val outcome = FileTreeActions.deleteBatch(listOf(inner, sub))
        assertEquals(1, outcome.results.size)
        assertEquals(sub.toAbsolutePath().normalize(), outcome.results.single().first.toAbsolutePath().normalize())
        assertTrue(outcome.allOk)
        assertTrue(!Files.exists(sub))
    }

    @Test
    fun `deleteBatch reports per-path failure without short-circuiting`() {
        val root = newDir()
        val a = Files.createFile(root.resolve("a.kt"))
        val missing = root.resolve("missing.kt")
        val b = Files.createFile(root.resolve("b.kt"))
        val outcome = FileTreeActions.deleteBatch(listOf(a, missing, b))
        assertEquals(3, outcome.results.size)
        assertEquals(2, outcome.successCount)
        assertEquals(1, outcome.failureCount)
        assertTrue(!outcome.allOk)
        assertTrue(!Files.exists(a))
        assertTrue(!Files.exists(b))
    }

    @Test
    fun `copyFile copies file with new name`() {
        val root = newDir()
        val src = Files.createFile(root.resolve("a.kt"))
        Files.writeString(src, "hello")
        val dest = Files.createDirectories(root.resolve("dst"))
        val result = FileTreeActions.copyFile(src, dest, "b.kt")
        assertIs<FileTreeActions.CopyResult.Ok>(result)
        assertTrue(Files.exists(src))
        assertTrue(Files.exists(result.path))
        assertEquals("hello", Files.readString(result.path))
    }

    @Test
    fun `copyFile copies directory recursively`() {
        val root = newDir()
        val srcDir = Files.createDirectories(root.resolve("src"))
        Files.createFile(srcDir.resolve("a.txt")).also { Files.writeString(it, "A") }
        val inner = Files.createDirectories(srcDir.resolve("inner"))
        Files.createFile(inner.resolve("b.txt")).also { Files.writeString(it, "B") }
        val dest = Files.createDirectories(root.resolve("dst"))
        val result = FileTreeActions.copyFile(srcDir, dest, "src-copy")
        assertIs<FileTreeActions.CopyResult.Ok>(result)
        assertTrue(Files.isDirectory(result.path))
        assertEquals("A", Files.readString(result.path.resolve("a.txt")))
        assertEquals("B", Files.readString(result.path.resolve("inner/b.txt")))
        assertTrue(Files.exists(srcDir))
    }

    @Test
    fun `copyFile rejects existing target`() {
        val root = newDir()
        val src = Files.createFile(root.resolve("a.kt"))
        val dest = Files.createDirectories(root.resolve("dst"))
        Files.createFile(dest.resolve("a.kt"))
        val result = FileTreeActions.copyFile(src, dest, "a.kt")
        assertIs<FileTreeActions.CopyResult.Err>(result)
        assertTrue(result.message.contains("already exists"))
    }

    @Test
    fun `copyFile rejects copying directory into itself`() {
        val root = newDir()
        val dir = Files.createDirectories(root.resolve("dir"))
        val result = FileTreeActions.copyFile(dir, dir, "nested")
        assertIs<FileTreeActions.CopyResult.Err>(result)
        assertTrue(result.message.contains("into itself"))
    }

    @Test
    fun `copyFile rejects when destination is not a directory`() {
        val root = newDir()
        val src = Files.createFile(root.resolve("a.kt"))
        val file = Files.createFile(root.resolve("not-dir.txt"))
        val result = FileTreeActions.copyFile(src, file, "b.kt")
        assertIs<FileTreeActions.CopyResult.Err>(result)
        assertTrue(result.message.contains("not a directory"))
    }

    @Test
    fun `copyFile rejects invalid new name`() {
        val root = newDir()
        val src = Files.createFile(root.resolve("a.kt"))
        val dest = Files.createDirectories(root.resolve("dst"))
        val result = FileTreeActions.copyFile(src, dest, "bad/name")
        assertIs<FileTreeActions.CopyResult.Err>(result)
        assertTrue(result.message.contains("Invalid character"))
    }

    @Test
    fun `moveFile moves and renames file`() {
        val root = newDir()
        val src = Files.createFile(root.resolve("a.kt"))
        Files.writeString(src, "x")
        val dest = Files.createDirectories(root.resolve("dst"))
        val result = FileTreeActions.moveFile(src, dest, "b.kt")
        assertIs<FileTreeActions.MoveResult.Ok>(result)
        assertTrue(!Files.exists(src))
        assertTrue(Files.exists(result.path))
        assertEquals("x", Files.readString(result.path))
    }

    @Test
    fun `moveFile rejects existing target`() {
        val root = newDir()
        val src = Files.createFile(root.resolve("a.kt"))
        val dest = Files.createDirectories(root.resolve("dst"))
        Files.createFile(dest.resolve("a.kt"))
        val result = FileTreeActions.moveFile(src, dest, "a.kt")
        assertIs<FileTreeActions.MoveResult.Err>(result)
        assertTrue(result.message.contains("already exists"))
    }

    @Test
    fun `moveFile rejects moving directory into itself`() {
        val root = newDir()
        val dir = Files.createDirectories(root.resolve("dir"))
        val result = FileTreeActions.moveFile(dir, dir, "nested")
        assertIs<FileTreeActions.MoveResult.Err>(result)
        assertTrue(result.message.contains("into itself"))
    }

    @Test
    fun `multi-paste — two distinct files move to same destination without name bleed`() {
        val root = newDir()
        val srcDir = Files.createDirectories(root.resolve("src"))
        val dst = Files.createDirectories(root.resolve("dst"))
        val a = Files.writeString(srcDir.resolve("Deep.kt"), "deep-body")
        val b = Files.writeString(srcDir.resolve("Movable.kt"), "movable-body")

        val r1 = FileTreeActions.moveFile(a, dst, "Deep.kt")
        assertIs<FileTreeActions.MoveResult.Ok>(r1)
        val r2 = FileTreeActions.moveFile(b, dst, "Movable.kt")
        assertIs<FileTreeActions.MoveResult.Ok>(r2)

        assertTrue(!Files.exists(a))
        assertTrue(!Files.exists(b))
        assertTrue(Files.exists(dst.resolve("Deep.kt")))
        assertTrue(Files.exists(dst.resolve("Movable.kt")))
        assertEquals("deep-body", Files.readString(dst.resolve("Deep.kt")))
        assertEquals("movable-body", Files.readString(dst.resolve("Movable.kt")))
    }

    @Test
    fun `multi-paste — three files move sequentially preserving names and content`() {
        val root = newDir()
        val srcDir = Files.createDirectories(root.resolve("src"))
        val dst = Files.createDirectories(root.resolve("dst"))
        val files = listOf("Alpha.kt" to "α", "Beta.kt" to "β", "Gamma.kt" to "γ")
            .map { (name, body) -> Files.writeString(srcDir.resolve(name), body) to body }

        for ((src, expected) in files) {
            val res = FileTreeActions.moveFile(src, dst, src.fileName.toString())
            assertIs<FileTreeActions.MoveResult.Ok>(res)
            assertEquals(expected, Files.readString(res.path))
        }

        files.forEach { (src, _) -> assertTrue(!Files.exists(src), "source $src should be gone") }
        listOf("Alpha.kt", "Beta.kt", "Gamma.kt").forEach { name ->
            assertTrue(Files.exists(dst.resolve(name)), "$name should land in dst")
        }
    }

    @Test
    fun `multi-paste — second move with same name as first lands as collision`() {
        val root = newDir()
        val srcA = Files.createDirectories(root.resolve("a"))
        val srcB = Files.createDirectories(root.resolve("b"))
        val dst = Files.createDirectories(root.resolve("dst"))
        val fileA = Files.writeString(srcA.resolve("Helper.kt"), "from-a")
        val fileB = Files.writeString(srcB.resolve("Helper.kt"), "from-b")

        val r1 = FileTreeActions.moveFile(fileA, dst, "Helper.kt")
        assertIs<FileTreeActions.MoveResult.Ok>(r1)
        val r2 = FileTreeActions.moveFile(fileB, dst, "Helper.kt")
        assertIs<FileTreeActions.MoveResult.Err>(r2)
        assertTrue(r2.message.contains("already exists"))

        assertEquals("from-a", Files.readString(dst.resolve("Helper.kt")))
        assertTrue(Files.exists(fileB), "second source should remain when collision blocks the move")
    }

    @Test
    fun `multi-paste — second move with rename around collision succeeds`() {
        val root = newDir()
        val srcA = Files.createDirectories(root.resolve("a"))
        val srcB = Files.createDirectories(root.resolve("b"))
        val dst = Files.createDirectories(root.resolve("dst"))
        val fileA = Files.writeString(srcA.resolve("Helper.kt"), "from-a")
        val fileB = Files.writeString(srcB.resolve("Helper.kt"), "from-b")

        val r1 = FileTreeActions.moveFile(fileA, dst, "Helper.kt")
        assertIs<FileTreeActions.MoveResult.Ok>(r1)
        val r2 = FileTreeActions.moveFile(fileB, dst, "Helper2.kt")
        assertIs<FileTreeActions.MoveResult.Ok>(r2)

        assertEquals("from-a", Files.readString(dst.resolve("Helper.kt")))
        assertEquals("from-b", Files.readString(dst.resolve("Helper2.kt")))
        assertTrue(!Files.exists(fileA))
        assertTrue(!Files.exists(fileB))
    }
}
