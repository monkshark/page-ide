package page.workspace

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class LargeCopyExecutorTest {

    @Test
    fun `estimate returns zero for missing source`(@TempDir tmp: Path) {
        val e = LargeCopyExecutor.estimate(tmp.resolve("missing"))
        assertEquals(0L, e.totalBytes)
        assertEquals(0, e.fileCount)
        assertFalse(e.isLarge)
    }

    @Test
    fun `estimate aggregates directory tree`(@TempDir tmp: Path) {
        val root = tmp.resolve("root")
        Files.createDirectories(root.resolve("nested"))
        Files.writeString(root.resolve("a.txt"), "abc")
        Files.writeString(root.resolve("nested").resolve("b.txt"), "defgh")
        val e = LargeCopyExecutor.estimate(root)
        assertEquals(8L, e.totalBytes)
        assertEquals(2, e.fileCount)
        assertFalse(e.isLarge)
    }

    @Test
    fun `isLarge flips when byte threshold is crossed`(@TempDir tmp: Path) {
        val big = tmp.resolve("big.bin")
        Files.write(big, ByteArray(LargeCopyExecutor.BYTE_THRESHOLD.toInt() + 1) { 0x7F })
        val e = LargeCopyExecutor.estimate(big)
        assertTrue(e.isLarge)
        assertEquals(1, e.fileCount)
    }

    @Test
    fun `copyWithProgress streams bytes and reports per-file completion`(@TempDir tmp: Path) {
        val src = tmp.resolve("payload.bin")
        Files.write(src, ByteArray(200_000) { (it % 251).toByte() })
        val dest = tmp.resolve("dest")
        Files.createDirectories(dest)
        val bytes = AtomicLong()
        val files = AtomicInteger()
        val outcome = LargeCopyExecutor.copyWithProgress(
            source = src,
            destParent = dest,
            newName = "out.bin",
            isCancelled = { false },
            onProgress = { db, df ->
                bytes.addAndGet(db)
                files.addAndGet(df)
            },
        )
        assertTrue(outcome is LargeCopyExecutor.CopyOutcome.Ok)
        val target = (outcome as LargeCopyExecutor.CopyOutcome.Ok).target
        assertTrue(Files.exists(target))
        assertEquals(200_000L, Files.size(target))
        assertEquals(200_000L, bytes.get())
        assertEquals(1, files.get())
    }

    @Test
    fun `copyWithProgress refuses overwrite`(@TempDir tmp: Path) {
        val src = tmp.resolve("a.txt").also { Files.writeString(it, "x") }
        val dest = tmp.resolve("dest").also { Files.createDirectories(it) }
        Files.writeString(dest.resolve("a.txt"), "existing")
        val outcome = LargeCopyExecutor.copyWithProgress(
            source = src,
            destParent = dest,
            newName = "a.txt",
            isCancelled = { false },
            onProgress = { _, _ -> },
        )
        assertTrue(outcome is LargeCopyExecutor.CopyOutcome.Err)
    }

    @Test
    fun `copyWithProgress replaces target when overwriteExisting`(@TempDir tmp: Path) {
        val src = tmp.resolve("a.txt").also { Files.writeString(it, "fresh") }
        val dest = tmp.resolve("dest").also { Files.createDirectories(it) }
        val target = dest.resolve("a.txt").also { Files.writeString(it, "stale") }
        val outcome = LargeCopyExecutor.copyWithProgress(
            source = src,
            destParent = dest,
            newName = "a.txt",
            isCancelled = { false },
            onProgress = { _, _ -> },
            overwriteExisting = true,
        )
        assertTrue(outcome is LargeCopyExecutor.CopyOutcome.Ok)
        assertEquals("fresh", Files.readString(target))
    }

    @Test
    fun `copyWithProgress refuses to overwrite a directory`(@TempDir tmp: Path) {
        val src = tmp.resolve("a.txt").also { Files.writeString(it, "x") }
        val dest = tmp.resolve("dest").also { Files.createDirectories(it) }
        Files.createDirectories(dest.resolve("a.txt"))
        val outcome = LargeCopyExecutor.copyWithProgress(
            source = src,
            destParent = dest,
            newName = "a.txt",
            isCancelled = { false },
            onProgress = { _, _ -> },
            overwriteExisting = true,
        )
        assertTrue(outcome is LargeCopyExecutor.CopyOutcome.Err)
    }

    @Test
    fun `copyWithProgress cleans up partial output on cancel`(@TempDir tmp: Path) {
        val src = tmp.resolve("tree")
        Files.createDirectories(src.resolve("nested"))
        repeat(6) { i ->
            Files.write(src.resolve("nested").resolve("file-$i.bin"), ByteArray(40_000) { it.toByte() })
        }
        val dest = tmp.resolve("dest").also { Files.createDirectories(it) }
        val cancel = AtomicBoolean(false)
        val files = AtomicInteger()
        val outcome = LargeCopyExecutor.copyWithProgress(
            source = src,
            destParent = dest,
            newName = "tree-copy",
            isCancelled = { cancel.get() },
            onProgress = { _, df ->
                if (df > 0) {
                    val seen = files.addAndGet(df)
                    if (seen >= 2) cancel.set(true)
                }
            },
        )
        assertTrue(outcome is LargeCopyExecutor.CopyOutcome.Cancelled)
        val target = (outcome as LargeCopyExecutor.CopyOutcome.Cancelled).target
        assertFalse(Files.exists(target))
    }

    @Test
    fun `copyWithProgress rejects folder-into-itself`(@TempDir tmp: Path) {
        val src = tmp.resolve("folder").also { Files.createDirectories(it) }
        Files.writeString(src.resolve("a.txt"), "x")
        val outcome = LargeCopyExecutor.copyWithProgress(
            source = src,
            destParent = src,
            newName = "copy",
            isCancelled = { false },
            onProgress = { _, _ -> },
        )
        assertTrue(outcome is LargeCopyExecutor.CopyOutcome.Err)
    }
}
