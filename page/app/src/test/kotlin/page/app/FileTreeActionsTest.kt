package page.app

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
}
