package page.app

import page.runtime.*

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenameTransactionTest {

    private fun newWorkspace(): Path {
        val dir = Files.createTempDirectory("page-ide-rename-tx-")
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching {
                Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        })
        return dir
    }

    private fun fillSource(root: Path, name: String): Path {
        val src = Files.createDirectories(root.resolve(name))
        Files.writeString(Files.createFile(src.resolve("a.kt")), "alpha")
        val inner = Files.createDirectories(src.resolve("inner"))
        Files.writeString(Files.createFile(inner.resolve("b.kt")), "beta")
        return src
    }

    @Test
    fun `execute moves directory and clears marker on success`() {
        val ws = newWorkspace()
        val source = fillSource(ws, "complex")
        val target = ws.resolve("lattice")

        RenameTransaction.execute(ws, source, target)

        assertTrue(!Files.exists(source))
        assertTrue(Files.isDirectory(target))
        assertEquals("alpha", Files.readString(target.resolve("a.kt")))
        assertEquals("beta", Files.readString(target.resolve("inner/b.kt")))
        assertNull(RenameTransaction.current(ws))
    }

    @Test
    fun `current reads back marker`() {
        val ws = newWorkspace()
        val from = ws.resolve("a")
        val to = ws.resolve("b")
        RenameTransaction.write(ws, RenameTransaction.Marker(from, to, RenameTransaction.Stage.COPY))
        val read = RenameTransaction.current(ws)
        assertNotNull(read)
        assertEquals(from.toAbsolutePath().normalize(), read.from)
        assertEquals(to.toAbsolutePath().normalize(), read.to)
        assertEquals(RenameTransaction.Stage.COPY, read.stage)
    }

    @Test
    fun `recover with no marker returns NoMarker`() {
        val ws = newWorkspace()
        val result = RenameTransaction.recover(ws)
        assertIs<RenameTransaction.RecoveryResult.NoMarker>(result)
    }

    @Test
    fun `recover at copy stage rolls back partial target`() {
        val ws = newWorkspace()
        val source = fillSource(ws, "complex")
        val target = ws.resolve("lattice")
        Files.createDirectories(target)
        Files.writeString(Files.createFile(target.resolve("a.kt")), "alpha")
        RenameTransaction.write(ws, RenameTransaction.Marker(source, target, RenameTransaction.Stage.COPY))

        val result = RenameTransaction.recover(ws)

        assertIs<RenameTransaction.RecoveryResult.RolledBack>(result)
        assertTrue(Files.isDirectory(source))
        assertTrue(!Files.exists(target))
        assertNull(RenameTransaction.current(ws))
    }

    @Test
    fun `recover at copy stage with full target advances to delete and finishes`() {
        val ws = newWorkspace()
        val source = fillSource(ws, "complex")
        val target = ws.resolve("lattice")
        FileTreeActions.copyTree(source, target)
        RenameTransaction.write(ws, RenameTransaction.Marker(source, target, RenameTransaction.Stage.COPY))

        val result = RenameTransaction.recover(ws)

        assertIs<RenameTransaction.RecoveryResult.Resumed>(result)
        assertTrue(!Files.exists(source))
        assertTrue(Files.isDirectory(target))
        assertEquals("alpha", Files.readString(target.resolve("a.kt")))
        assertEquals("beta", Files.readString(target.resolve("inner/b.kt")))
        assertNull(RenameTransaction.current(ws))
    }

    @Test
    fun `recover at delete stage retries source deletion`() {
        val ws = newWorkspace()
        val source = fillSource(ws, "complex")
        val target = ws.resolve("lattice")
        FileTreeActions.copyTree(source, target)
        RenameTransaction.write(ws, RenameTransaction.Marker(source, target, RenameTransaction.Stage.DELETE))

        val result = RenameTransaction.recover(ws)

        assertIs<RenameTransaction.RecoveryResult.Resumed>(result)
        assertTrue(!Files.exists(source))
        assertTrue(Files.isDirectory(target))
        assertNull(RenameTransaction.current(ws))
    }

    @Test
    fun `recover skips when neither source nor target exists`() {
        val ws = newWorkspace()
        val from = ws.resolve("ghost")
        val to = ws.resolve("phantom")
        RenameTransaction.write(ws, RenameTransaction.Marker(from, to, RenameTransaction.Stage.DELETE))

        val result = RenameTransaction.recover(ws)

        assertIs<RenameTransaction.RecoveryResult.Skipped>(result)
        assertNull(RenameTransaction.current(ws))
    }

    @Test
    fun `execute writes marker before each phase`() {
        val ws = newWorkspace()
        val source = fillSource(ws, "complex")
        val target = ws.resolve("lattice")
        RenameTransaction.execute(ws, source, target)
        assertNull(RenameTransaction.current(ws))
    }

    @Test
    fun `execute appends begin copy-done commit lines to log`() {
        val ws = newWorkspace()
        val source = fillSource(ws, "complex")
        val target = ws.resolve("lattice")
        RenameTransaction.execute(ws, source, target)

        val log = RenameTransaction.logFile(ws)
        assertTrue(Files.isRegularFile(log))
        val text = Files.readString(log)
        assertTrue(text.contains(" begin from="))
        assertTrue(text.contains(" copy-done from="))
        assertTrue(text.contains(" commit from="))
    }

    @Test
    fun `recover writes recover-start and outcome lines`() {
        val ws = newWorkspace()
        val source = fillSource(ws, "complex")
        val target = ws.resolve("lattice")
        FileTreeActions.copyTree(source, target)
        RenameTransaction.write(ws, RenameTransaction.Marker(source, target, RenameTransaction.Stage.DELETE))

        val result = RenameTransaction.recover(ws)
        assertIs<RenameTransaction.RecoveryResult.Resumed>(result)

        val text = Files.readString(RenameTransaction.logFile(ws))
        assertTrue(text.contains(" recover-start stage=DELETE"))
        assertTrue(text.contains(" recover-resumed from="))
    }

    @Test
    fun `recover at copy stage with mismatched sizes rolls back`() {
        val ws = newWorkspace()
        val source = fillSource(ws, "complex")
        val target = ws.resolve("lattice")
        Files.createDirectories(target)
        Files.writeString(Files.createFile(target.resolve("a.kt")), "different content here")
        Files.createDirectories(target.resolve("inner"))
        Files.writeString(Files.createFile(target.resolve("inner/b.kt")), "beta")
        RenameTransaction.write(ws, RenameTransaction.Marker(source, target, RenameTransaction.Stage.COPY))

        val result = RenameTransaction.recover(ws)

        assertIs<RenameTransaction.RecoveryResult.RolledBack>(result)
        assertTrue(Files.isDirectory(source))
        assertTrue(!Files.exists(target))
    }
}
