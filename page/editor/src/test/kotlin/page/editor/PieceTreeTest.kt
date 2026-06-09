package page.editor

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class PieceTreeTest {

    @Test
    fun `multi-piece text returns concatenation in order`() {
        val tree = PieceTree("hello")
        tree.insert(5, " world")
        tree.insert(0, ">> ")
        assertEquals(">> hello world", tree.text())
        assertEquals(14, tree.length)
        assertEquals(1, tree.lineCount)
    }

    @Test
    fun `inserting LFs across pieces updates line count`() {
        val tree = PieceTree("abc")
        tree.insert(1, "\n")
        tree.insert(3, "\n")
        assertEquals("a\nb\nc", tree.text())
        assertEquals(3, tree.lineCount)
        assertEquals("a", tree.lineAt(0))
        assertEquals("b", tree.lineAt(1))
        assertEquals("c", tree.lineAt(2))
    }

    @Test
    fun `lineColOf is correct at piece boundaries`() {
        val tree = PieceTree("abc\n")
        tree.insert(4, "def")
        for (offset in 0..tree.length) {
            val lc = tree.lineColOf(offset)
            assertEquals(offset, tree.offsetOf(lc.line, lc.col), "round-trip at $offset")
        }
    }

    @Test
    fun `delete spanning multiple pieces`() {
        val tree = PieceTree("abc")
        tree.insert(1, "XYZ")
        tree.insert(4, "QQ")
        assertEquals("aXYZQQbc", tree.text())
        tree.delete(1, 7)
        assertEquals("ac", tree.text())
        assertEquals(2, tree.length)
    }

    @Test
    fun `delete entire buffer leaves empty`() {
        val tree = PieceTree("hello\nworld")
        tree.delete(0, tree.length)
        assertEquals("", tree.text())
        assertEquals(0, tree.length)
        assertEquals(1, tree.lineCount)
    }

    @Test
    fun `delete then insert maintains correctness`() {
        val tree = PieceTree("abcdef")
        tree.delete(2, 4)
        assertEquals("abef", tree.text())
        tree.insert(2, "CD")
        assertEquals("abCDef", tree.text())
    }

    @Test
    fun `large initial buffer with many inserts still matches reference`() {
        val tree = PieceTree("0123456789".repeat(100))
        val ref = StringBuilder("0123456789".repeat(100))
        for (i in 0 until 50) {
            val pos = (i * 17) % (tree.length + 1)
            val text = "[$i]"
            tree.insert(pos, text)
            ref.insert(pos, text)
        }
        assertEquals(ref.toString(), tree.text())
        assertEquals(ref.length, tree.length)
    }

    @Test
    fun `random edit fuzz matches StringBuilder reference`() {
        val rng = Random(0xC0FFEE)
        val tree = PieceTree("seed\n")
        val ref = StringBuilder("seed\n")
        repeat(400) {
            when (rng.nextInt(3)) {
                0 -> {
                    val pos = rng.nextInt(ref.length + 1)
                    val text = randomText(rng, rng.nextInt(1, 8))
                    tree.insert(pos, text)
                    ref.insert(pos, text)
                }
                1 -> if (ref.isNotEmpty()) {
                    val start = rng.nextInt(ref.length)
                    val end = (start + rng.nextInt(1, 5)).coerceAtMost(ref.length)
                    tree.delete(start, end)
                    ref.delete(start, end)
                }
                2 -> {
                    val expectedLines = countLines(ref)
                    assertEquals(expectedLines, tree.lineCount, "lineCount mismatch")
                }
            }
        }
        assertEquals(ref.toString(), tree.text())
        assertEquals(ref.length, tree.length)
        assertEquals(countLines(ref), tree.lineCount)
        for (line in 0 until tree.lineCount) {
            val expected = expectedLineAt(ref, line)
            assertEquals(expected, tree.lineAt(line), "lineAt($line) mismatch")
        }
        for (offset in 0..tree.length) {
            val lc = tree.lineColOf(offset)
            assertEquals(offset, tree.offsetOf(lc.line, lc.col), "round-trip at $offset")
        }
    }

    @Test
    fun `lineAt with random LFs matches reference`() {
        val rng = Random(0xBEEF)
        val tree = PieceTree("")
        val ref = StringBuilder()
        repeat(150) {
            val pos = rng.nextInt(ref.length + 1)
            val text = if (rng.nextInt(4) == 0) "\n" else randomText(rng, rng.nextInt(1, 4))
            tree.insert(pos, text)
            ref.insert(pos, text)
        }
        assertEquals(ref.toString(), tree.text())
        for (line in 0 until tree.lineCount) {
            assertEquals(expectedLineAt(ref, line), tree.lineAt(line), "lineAt($line) mismatch")
        }
    }

    private fun randomText(rng: Random, len: Int): String {
        val alphabet = "abcdefghij"
        val sb = StringBuilder()
        repeat(len) { sb.append(alphabet[rng.nextInt(alphabet.length)]) }
        return sb.toString()
    }

    private fun countLines(s: CharSequence): Int {
        var count = 1
        for (i in 0 until s.length) if (s[i] == '\n') count++
        return count
    }

    private fun expectedLineAt(s: CharSequence, line: Int): String {
        var current = 0
        var start = 0
        for (i in 0 until s.length) {
            if (s[i] == '\n') {
                if (current == line) return s.substring(start, i)
                current++
                start = i + 1
            }
        }
        return if (current == line) s.substring(start, s.length) else ""
    }
}
