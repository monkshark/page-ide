package page.editor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TodoScannerTest {

    private val uri = "file:///proj/Foo.kt"

    @Test
    fun `scans TODO from line comment`() {
        val text = "fun f() {\n    // TODO: refactor later\n    val x = 1\n}\n"
        val items = TodoScanner.scanText(uri, text, KotlinLexer)
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("TODO", item.keyword)
        assertEquals("refactor later", item.message)
        assertEquals(1, item.line)
        assertEquals(7, item.column)
        assertTrue(item.rawLine.contains("TODO: refactor later"))
    }

    @Test
    fun `scans FIXME and HACK keywords`() {
        val text = """
            |// FIXME: bug here
            |// HACK: temporary
            |""".trimMargin()
        val items = TodoScanner.scanText(uri, text, KotlinLexer)
        assertEquals(2, items.size)
        assertEquals(listOf("FIXME", "HACK"), items.map { it.keyword })
        assertEquals(listOf("bug here", "temporary"), items.map { it.message })
    }

    @Test
    fun `scans NOTE keyword`() {
        val text = "// NOTE: 캐시 정책 참고\n"
        val items = TodoScanner.scanText(uri, text, KotlinLexer)
        assertEquals(1, items.size)
        assertEquals("NOTE", items.single().keyword)
        assertEquals("캐시 정책 참고", items.single().message)
    }

    @Test
    fun `bare TODO without colon has empty message`() {
        val text = "// TODO\nval x = 1\n"
        val items = TodoScanner.scanText(uri, text, KotlinLexer)
        assertEquals(1, items.size)
        assertEquals("TODO", items.single().keyword)
        assertEquals("", items.single().message)
    }

    @Test
    fun `TODO inside KDoc is captured`() {
        val text = "/** TODO: doc-level note */\nfun f() {}\n"
        val items = TodoScanner.scanText(uri, text, KotlinLexer)
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("TODO", item.keyword)
        assertTrue(item.message.startsWith("doc-level note"))
    }

    @Test
    fun `TODO inside string literal is not captured`() {
        val text = "val s = \"TODO: not a real one\"\n"
        val items = TodoScanner.scanText(uri, text, KotlinLexer)
        assertTrue(items.isEmpty(), "got $items")
    }

    @Test
    fun `empty text yields no items`() {
        val items = TodoScanner.scanText(uri, "", KotlinLexer)
        assertTrue(items.isEmpty())
    }

    @Test
    fun `scanFile walks a Kotlin file`(@TempDir tmp: Path) {
        val f = tmp.resolve("Sample.kt")
        Files.writeString(f, "// TODO: file scan\nfun f() {}\n")
        val items = TodoScanner.scanFile(f)
        assertEquals(1, items.size)
        assertEquals("TODO", items.single().keyword)
        assertEquals("file scan", items.single().message)
        assertEquals(f.toUri().toString(), items.single().uri)
    }

    @Test
    fun `scanFile skips unsupported extensions`(@TempDir tmp: Path) {
        val f = tmp.resolve("notes.txt")
        Files.writeString(f, "// TODO: still skipped\n")
        val items = TodoScanner.scanFile(f)
        assertTrue(items.isEmpty())
    }

    @Test
    fun `scanWorkspace walks subdirectories`(@TempDir tmp: Path) {
        val sub = Files.createDirectory(tmp.resolve("src"))
        Files.writeString(sub.resolve("A.kt"), "// TODO: a\n")
        Files.writeString(sub.resolve("B.kt"), "// FIXME: b\n")
        val items = TodoScanner.scanWorkspace(tmp)
        assertEquals(2, items.size)
        val keywords = items.map { it.keyword }.sorted()
        assertEquals(listOf("FIXME", "TODO"), keywords)
    }

    @Test
    fun `scanWorkspace excludes build directory by default`(@TempDir tmp: Path) {
        val src = Files.createDirectory(tmp.resolve("src"))
        val build = Files.createDirectory(tmp.resolve("build"))
        Files.writeString(src.resolve("Keep.kt"), "// TODO: keep\n")
        Files.writeString(build.resolve("Skip.kt"), "// TODO: skip\n")
        val items = TodoScanner.scanWorkspace(tmp)
        assertEquals(1, items.size)
        assertEquals("keep", items.single().message)
    }

    @Test
    fun `scanWorkspace returns empty for non-directory`(@TempDir tmp: Path) {
        val items = TodoScanner.scanWorkspace(tmp.resolve("does-not-exist"))
        assertTrue(items.isEmpty())
    }

    @Test
    fun `column is start of keyword inside comment`() {
        val text = "        // TODO: indented\n"
        val items = TodoScanner.scanText(uri, text, KotlinLexer)
        val item = items.single()
        assertEquals(0, item.line)
        assertEquals(11, item.column)
    }

    @Test
    fun `rawLine has trailing CR stripped`() {
        val text = "// TODO: crlf line\r\nval x = 1\r\n"
        val items = TodoScanner.scanText(uri, text, KotlinLexer)
        assertEquals(1, items.size)
        val raw = items.single().rawLine
        assertNotNull(raw)
        assertTrue(!raw.endsWith("\r"), "rawLine ends with CR: '$raw'")
    }
}
