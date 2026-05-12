package page.editor

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileTreeTest {

    @Test
    fun `collapsed root yields only the root node`(@TempDir dir: Path) {
        Files.createFile(dir.resolve("a.txt"))
        val nodes = FileTree.listTree(dir, expanded = emptySet())
        assertEquals(1, nodes.size)
        assertEquals(dir, nodes[0].path)
        assertEquals(0, nodes[0].depth)
        assertTrue(nodes[0].isDirectory)
    }

    @Test
    fun `expanded root lists immediate children`(@TempDir dir: Path) {
        val a = Files.createFile(dir.resolve("a.txt"))
        val b = Files.createFile(dir.resolve("b.txt"))
        val sub = Files.createDirectory(dir.resolve("sub"))
        val nodes = FileTree.listTree(dir, expanded = setOf(dir))
        assertEquals(4, nodes.size)
        assertEquals(dir, nodes[0].path)
        assertEquals(sub, nodes[1].path)
        assertTrue(nodes[1].isDirectory)
        assertEquals(a, nodes[2].path)
        assertEquals(b, nodes[3].path)
    }

    @Test
    fun `directories sort before files`(@TempDir dir: Path) {
        Files.createFile(dir.resolve("a.txt"))
        Files.createDirectory(dir.resolve("z"))
        val nodes = FileTree.listTree(dir, expanded = setOf(dir))
        assertEquals("z", nodes[1].path.fileName.toString())
        assertEquals("a.txt", nodes[2].path.fileName.toString())
    }

    @Test
    fun `name sort is case-insensitive`(@TempDir dir: Path) {
        Files.createFile(dir.resolve("Beta.txt"))
        Files.createFile(dir.resolve("alpha.txt"))
        val nodes = FileTree.listTree(dir, expanded = setOf(dir))
        assertEquals("alpha.txt", nodes[1].path.fileName.toString())
        assertEquals("Beta.txt", nodes[2].path.fileName.toString())
    }

    @Test
    fun `depth increases with nesting`(@TempDir dir: Path) {
        val l1 = Files.createDirectory(dir.resolve("l1"))
        val l2 = Files.createDirectory(l1.resolve("l2"))
        Files.createFile(l2.resolve("deep.txt"))

        val nodes = FileTree.listTree(dir, expanded = setOf(dir, l1, l2))
        assertEquals(0, nodes[0].depth)
        assertEquals(1, nodes[1].depth)
        assertEquals(2, nodes[2].depth)
        assertEquals(3, nodes[3].depth)
    }

    @Test
    fun `unexpanded subdirectory is not descended into`(@TempDir dir: Path) {
        val sub = Files.createDirectory(dir.resolve("sub"))
        Files.createFile(sub.resolve("hidden.txt"))
        val nodes = FileTree.listTree(dir, expanded = setOf(dir))
        assertEquals(2, nodes.size)
        assertEquals(dir, nodes[0].path)
        assertEquals(sub, nodes[1].path)
    }

    @Test
    fun `single file root is treated as a leaf`(@TempDir dir: Path) {
        val file = Files.createFile(dir.resolve("only.txt"))
        val nodes = FileTree.listTree(file, expanded = setOf(file))
        assertEquals(1, nodes.size)
        assertEquals(file, nodes[0].path)
        assertEquals(false, nodes[0].isDirectory)
    }

    @Test
    fun `singleChildChain returns empty for empty dir`(@TempDir dir: Path) {
        assertEquals(emptySet(), FileTree.singleChildChain(dir))
    }

    @Test
    fun `singleChildChain returns empty when sole child is a file`(@TempDir dir: Path) {
        Files.createFile(dir.resolve("only.txt"))
        assertEquals(emptySet(), FileTree.singleChildChain(dir))
    }

    @Test
    fun `singleChildChain returns empty when multiple children`(@TempDir dir: Path) {
        Files.createDirectory(dir.resolve("a"))
        Files.createDirectory(dir.resolve("b"))
        assertEquals(emptySet(), FileTree.singleChildChain(dir))
    }

    @Test
    fun `singleChildChain returns sole directory child`(@TempDir dir: Path) {
        val a = Files.createDirectory(dir.resolve("a"))
        assertEquals(setOf(a), FileTree.singleChildChain(dir))
    }

    @Test
    fun `singleChildChain cascades through single-folder chain`(@TempDir dir: Path) {
        val a = Files.createDirectory(dir.resolve("a"))
        val b = Files.createDirectory(a.resolve("b"))
        val c = Files.createDirectory(b.resolve("c"))
        Files.createFile(c.resolve("leaf.txt"))
        assertEquals(setOf(a, b, c), FileTree.singleChildChain(dir))
    }

    @Test
    fun `singleChildChain stops at branching directory`(@TempDir dir: Path) {
        val a = Files.createDirectory(dir.resolve("a"))
        val b = Files.createDirectory(a.resolve("b"))
        Files.createDirectory(b.resolve("c1"))
        Files.createDirectory(b.resolve("c2"))
        assertEquals(setOf(a, b), FileTree.singleChildChain(dir))
    }

    @Test
    fun `singleChildChain stops when chain hits a file leaf`(@TempDir dir: Path) {
        val a = Files.createDirectory(dir.resolve("a"))
        Files.createFile(a.resolve("leaf.txt"))
        assertEquals(setOf(a), FileTree.singleChildChain(dir))
    }

    @Test
    fun `singleChildChain returns empty for non-directory input`(@TempDir dir: Path) {
        val file = Files.createFile(dir.resolve("f.txt"))
        assertEquals(emptySet(), FileTree.singleChildChain(file))
    }
}
