package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import page.atlas.analyzer.FileAnalysis
import page.atlas.analyzer.ImportExtractor
import page.atlas.analyzer.StaticCallHierarchySource
import page.atlas.analyzer.WorkspaceIndex
import page.atlas.graph.SymbolSpec

class StaticCallHierarchySourceTest {

    private fun write(root: Path, relative: String, text: String): Path {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return file.toAbsolutePath().normalize()
    }

    private fun source(root: Path): StaticCallHierarchySource =
        StaticCallHierarchySource(WorkspaceIndex(root)) { file ->
            ImportExtractor.analyze(file, Files.readString(file))
        }

    @Test
    fun `outgoing resolves cross-file callee`(@TempDir root: Path) {
        val a = write(root, "a/A.kt", "package p\n\nfun caller() {\n    target()\n}\n")
        val b = write(root, "b/B.kt", "package p\n\nfun target() {}\n")
        val src = source(root)
        val caller = src.rootAt(a, 2) ?: error("caller not found")
        val out = src.outgoing(caller)
        assertEquals(1, out.size)
        assertEquals("target", out[0].name)
        assertEquals(b.toUri().toString(), out[0].uri)
        assertEquals(2, out[0].line)
    }

    @Test
    fun `incoming resolves cross-file caller`(@TempDir root: Path) {
        val a = write(root, "a/A.kt", "package p\n\nfun caller() {\n    target()\n}\n")
        val b = write(root, "b/B.kt", "package p\n\nfun target() {}\n")
        val src = source(root)
        val target = src.rootAt(b, 2) ?: error("target not found")
        val incoming = src.incoming(target)
        assertEquals(1, incoming.size)
        assertEquals("caller", incoming[0].name)
        assertEquals(a.toUri().toString(), incoming[0].uri)
        assertEquals(2, incoming[0].line)
    }

    @Test
    fun `ambiguous callee name produces no edge`(@TempDir root: Path) {
        write(root, "a/Dup.kt", "package p\n\nfun dup() {}\n")
        write(root, "b/Dup2.kt", "package q\n\nfun dup() {}\n")
        val caller = write(root, "c/Caller.kt", "package r\n\nfun c() {\n    dup()\n}\n")
        val src = source(root)
        val sym = src.rootAt(caller, 2) ?: error("caller not found")
        assertTrue(src.outgoing(sym).isEmpty())
    }

    @Test
    fun `same-file declaration wins over workspace duplicate`(@TempDir root: Path) {
        val a = write(root, "a/A.kt", "package p\n\nfun a() {\n    b()\n}\n\nfun b() {}\n")
        write(root, "c/C.kt", "package q\n\nfun b() {}\n")
        val src = source(root)
        val caller = src.rootAt(a, 2) ?: error("a not found")
        val out = src.outgoing(caller)
        assertEquals(1, out.size)
        assertEquals("b", out[0].name)
        assertEquals(a.toUri().toString(), out[0].uri)
        assertEquals(6, out[0].line)
    }

    @Test
    fun `missing callee does not crash`(@TempDir root: Path) {
        val a = write(root, "a/A.kt", "package p\n\nfun caller() {\n    nowhere()\n}\n")
        val src = source(root)
        val caller = src.rootAt(a, 2) ?: error("caller not found")
        assertTrue(src.outgoing(caller).isEmpty())
    }

    @Test
    fun `empty workspace is safe`(@TempDir root: Path) {
        val src = source(root)
        val phantom = SymbolSpec("x", null, root.resolve("X.kt").toUri().toString(), 0)
        assertTrue(src.outgoing(phantom).isEmpty())
        assertTrue(src.incoming(phantom).isEmpty())
    }

    @Test
    fun `rootAt finds enclosing top-level declaration`(@TempDir root: Path) {
        val a = write(root, "a/A.kt", "package p\n\nfun caller() {\n    target()\n}\n")
        val src = source(root)
        val sym = src.rootAt(a, 3) ?: error("enclosing not found")
        assertEquals("caller", sym.name)
        assertEquals(2, sym.line)
    }
}
