package page.editor

import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FileDocumentTest {

    @Test
    fun `save then load yields original text`(@TempDir dir: Path) {
        val path = dir.resolve("a.txt")
        val original = "hello\nworld\n"
        FileDocument.save(path, original)
        assertEquals(original, FileDocument.load(path))
    }

    @Test
    fun `save handles non-ASCII as UTF-8`(@TempDir dir: Path) {
        val path = dir.resolve("ko.txt")
        val original = "한글 — emoji and é ç"
        FileDocument.save(path, original)
        val bytesOnDisk = Files.readAllBytes(path)
        assertEquals(original, String(bytesOnDisk, StandardCharsets.UTF_8))
        assertEquals(original, FileDocument.load(path))
    }

    @Test
    fun `save overwrites existing content`(@TempDir dir: Path) {
        val path = dir.resolve("over.txt")
        FileDocument.save(path, "first")
        FileDocument.save(path, "second")
        assertEquals("second", FileDocument.load(path))
    }

    @Test
    fun `save empty text produces empty file`(@TempDir dir: Path) {
        val path = dir.resolve("empty.txt")
        FileDocument.save(path, "")
        assertEquals(0L, Files.size(path))
        assertEquals("", FileDocument.load(path))
    }

    @Test
    fun `load missing file throws`(@TempDir dir: Path) {
        val path = dir.resolve("missing.txt")
        assertFailsWith<java.nio.file.NoSuchFileException> { FileDocument.load(path) }
    }

    @Test
    fun `loadOrNull returns null for non-UTF-8 bytes`(@TempDir dir: Path) {
        val path = dir.resolve("binary.bin")
        Files.write(path, byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte()))
        assertNull(FileDocument.loadOrNull(path))
    }

    @Test
    fun `loadOrNull returns null for missing file`(@TempDir dir: Path) {
        assertNull(FileDocument.loadOrNull(dir.resolve("missing.txt")))
    }

    @Test
    fun `loadOrNull returns content for valid UTF-8 file`(@TempDir dir: Path) {
        val path = dir.resolve("ok.txt")
        FileDocument.save(path, "hello")
        assertEquals("hello", FileDocument.loadOrNull(path))
    }
}
