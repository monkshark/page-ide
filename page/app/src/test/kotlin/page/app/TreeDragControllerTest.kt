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

class TreeDragControllerTest {

    @Test
    fun `effectiveDragPaths returns selection when grabbed is part of it`() {
        val a = Path.of("a")
        val b = Path.of("b")
        val c = Path.of("c")
        val sel = linkedSetOf(a, b)
        val effective = TreeDragController.effectiveDragPaths(a, sel)
        assertTrue(effective.containsAll(listOf(a, b)))
        assertEquals(2, effective.size)
    }

    @Test
    fun `effectiveDragPaths returns single when grabbed not in selection`() {
        val a = Path.of("a")
        val b = Path.of("b")
        val sel = linkedSetOf(b)
        assertEquals(listOf(a), TreeDragController.effectiveDragPaths(a, sel))
    }

    @Test
    fun `canDropOn rejects descendant of source`() {
        val folder = Path.of("/x/parent")
        val sub = Path.of("/x/parent/child")
        assertFalse(TreeDragController.canDropOn(listOf(folder), sub))
    }

    @Test
    fun `canDropOn rejects same as source`() {
        val folder = Path.of("/x/parent")
        assertFalse(TreeDragController.canDropOn(listOf(folder), folder))
    }

    @Test
    fun `canDropOn allows sibling target`() {
        val a = Path.of("/x/a")
        val b = Path.of("/x/b")
        assertTrue(TreeDragController.canDropOn(listOf(a), b))
    }

    @Test
    fun `plan rejects when sources contain root`(@TempDir tmp: Path) {
        val root = tmp.toAbsolutePath().normalize()
        val target = Files.createDirectory(tmp.resolve("sub"))
        val decision = TreeDragController.plan(
            sources = listOf(root),
            targetNode = target,
            mode = TreeDragController.Mode.Move,
            source = TreeDragController.Source.Internal,
            workspaceRoot = root,
        )
        assertTrue(decision is TreeDragController.Decision.Reject)
        assertEquals(TreeDragController.Reason.ContainsRoot, (decision as TreeDragController.Decision.Reject).reason)
    }

    @Test
    fun `plan rejects move into same parent`(@TempDir tmp: Path) {
        val parent = Files.createDirectory(tmp.resolve("p"))
        val a = Files.createFile(parent.resolve("a.txt"))
        val decision = TreeDragController.plan(
            sources = listOf(a),
            targetNode = parent,
            mode = TreeDragController.Mode.Move,
            source = TreeDragController.Source.Internal,
        )
        assertTrue(decision is TreeDragController.Decision.Reject)
        assertEquals(
            TreeDragController.Reason.SameParentSameMode,
            (decision as TreeDragController.Decision.Reject).reason,
        )
    }

    @Test
    fun `plan allows move into different folder`(@TempDir tmp: Path) {
        val from = Files.createDirectory(tmp.resolve("from"))
        val to = Files.createDirectory(tmp.resolve("to"))
        val file = Files.createFile(from.resolve("a.txt"))
        val decision = TreeDragController.plan(
            sources = listOf(file),
            targetNode = to,
            mode = TreeDragController.Mode.Move,
            source = TreeDragController.Source.Internal,
        )
        assertTrue(decision is TreeDragController.Decision.Allow)
        val plan = (decision as TreeDragController.Decision.Allow).plan
        assertEquals(to, plan.target)
        assertEquals(listOf(file), plan.sources)
        assertEquals(TreeDragController.Mode.Move, plan.mode)
    }

    @Test
    fun `plan allows copy into same parent`(@TempDir tmp: Path) {
        val parent = Files.createDirectory(tmp.resolve("p"))
        val a = Files.createFile(parent.resolve("a.txt"))
        val decision = TreeDragController.plan(
            sources = listOf(a),
            targetNode = parent,
            mode = TreeDragController.Mode.Copy,
            source = TreeDragController.Source.Internal,
        )
        assertTrue(decision is TreeDragController.Decision.Allow)
    }

    @Test
    fun `resolveTargetFolder returns parent when node is a file`(@TempDir tmp: Path) {
        val parent = Files.createDirectory(tmp.resolve("d"))
        val file = Files.createFile(parent.resolve("a.txt"))
        assertEquals(parent, TreeDragController.resolveTargetFolder(file))
    }

    @Test
    fun `resolveTargetFolder returns directory itself`(@TempDir tmp: Path) {
        val d = Files.createDirectory(tmp.resolve("d"))
        assertEquals(d, TreeDragController.resolveTargetFolder(d))
    }

    @Test
    fun `resolveTargetFolder returns null when neither dir nor parent`() {
        assertNull(TreeDragController.resolveTargetFolder(Path.of("/nonexistent/file.txt")))
    }
}
