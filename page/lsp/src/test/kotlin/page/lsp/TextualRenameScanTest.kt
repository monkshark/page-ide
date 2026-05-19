package page.lsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextualRenameScanTest {

    @Test
    fun `finds simple qualified receiver`() {
        val text = "val x = Bar.named()\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertEquals(1, m.size)
        assertEquals(0, m[0].line)
        assertEquals(8, m[0].column)
    }

    @Test
    fun `finds receiver after colon-colon`() {
        val text = "val x = Bar::class\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertEquals(1, m.size)
        assertEquals(8, m[0].column)
    }

    @Test
    fun `ignores match inside line comment`() {
        val text = "// Bar.named is fine\nval x = 1\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `ignores match inside block comment`() {
        val text = "/* Bar.named */\nval x = 1\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `ignores match inside string literal`() {
        val text = "val s = \"Bar.named\"\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `ignores match inside raw string`() {
        val text = "val s = \"\"\"\n  Bar.named\n\"\"\"\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `ignores match preceded by dot`() {
        val text = "val x = pkg.Bar.named()\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `ignores match preceded by underscore`() {
        val text = "val x = _Bar.named()\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `ignores identifier continuation`() {
        val text = "val x = Barr.named()\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `ignores receiver not followed by identifier`() {
        val text = "val x = Bar.5\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `does not match constructor call`() {
        val text = "val x = Bar(\"y\")\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `multiple matches in same file`() {
        val text = """
            val a = Bar.x()
            val b = pkg.Bar.y()
            val c = Bar.z()
        """.trimIndent() + "\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertEquals(2, m.size)
        assertEquals(0, m[0].line)
        assertEquals(2, m[1].line)
    }

    @Test
    fun `class declaration is not matched`() {
        val text = """
            class Bar(val name: String = "bar") {
                fun greet(): String = "hi"
                companion object {
                    fun named(n: String): Bar = Bar(n)
                }
            }
        """.trimIndent() + "\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertTrue(m.isEmpty(), "expected no matches, got $m")
    }

    @Test
    fun `companion call after non-identifier prefix is matched`() {
        val text = "fun f(): String = Bar.named(\"x\").greet()\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertEquals(1, m.size)
    }

    @Test
    fun `findImportMatches catches simple import last segment`() {
        val text = "package x\n\nimport refscan.Bar\nimport refscan.util.Helper\n"
        val m = TextualRenameScan.findImportMatches(text, "Bar")
        assertEquals(1, m.size)
        assertEquals(2, m[0].line)
        assertEquals(15, m[0].column)
    }

    @Test
    fun `findImportMatches catches middle segment`() {
        val text = "package x\nimport refscan.Bar.named\n"
        val m = TextualRenameScan.findImportMatches(text, "Bar")
        assertEquals(1, m.size)
        assertEquals(1, m[0].line)
        assertEquals(15, m[0].column)
    }

    @Test
    fun `findImportMatches ignores non-import lines`() {
        val text = "val x = refscan.Bar\nclass Bar\n"
        val m = TextualRenameScan.findImportMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `findImportMatches ignores partial-name segment`() {
        val text = "import refscan.Barr\nimport refscan.MyBar\n"
        val m = TextualRenameScan.findImportMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `findImportMatches handles indented import`() {
        val text = "package x\n    import refscan.Bar\n"
        val m = TextualRenameScan.findImportMatches(text, "Bar")
        assertEquals(1, m.size)
        assertEquals(1, m[0].line)
        assertEquals(19, m[0].column)
    }

    @Test
    fun `findImportMatches with expectedPackage filters out same-name imports from other packages`() {
        val text = "import refscan.Bar\nimport other.Bar as OtherBar\n"
        val m = TextualRenameScan.findImportMatches(text, "Bar", expectedPackage = "refscan")
        assertEquals(1, m.size)
        assertEquals(0, m[0].line)
        assertEquals(15, m[0].column)
    }

    @Test
    fun `findImportMatches with expectedPackage matches nested-companion import`() {
        val text = "import refscan.Bar.Companion.LABEL\n"
        val m = TextualRenameScan.findImportMatches(text, "Bar", expectedPackage = "refscan")
        assertEquals(1, m.size)
        assertEquals(15, m[0].column)
    }

    @Test
    fun `findImportMatches with expectedPackage skips import when package prefix differs`() {
        val text = "import other.refscan.Bar\n"
        val m = TextualRenameScan.findImportMatches(text, "Bar", expectedPackage = "refscan")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `findImportMatches without expectedPackage retains legacy permissive behavior`() {
        val text = "import other.Bar as OtherBar\n"
        val m = TextualRenameScan.findImportMatches(text, "Bar")
        assertEquals(1, m.size)
    }

    @Test
    fun `findAllMatches catches declaration constructor and return type`() {
        val text = """
            class Bar(val name: String = "bar") {
                companion object {
                    fun named(n: String): Bar = Bar(n)
                }
            }
        """.trimIndent() + "\n"
        val m = TextualRenameScan.findAllMatches(text, "Bar")
        assertEquals(3, m.size)
        assertEquals(0, m[0].line)
        assertEquals(2, m[1].line)
        assertEquals(2, m[2].line)
    }

    @Test
    fun `findAllMatches ignores strings and comments`() {
        val text = """
            // Bar in comment
            /* Bar in block */
            class Bar {
                val s = "Bar here"
            }
        """.trimIndent() + "\n"
        val m = TextualRenameScan.findAllMatches(text, "Bar")
        assertEquals(1, m.size)
        assertEquals(2, m[0].line)
    }

    @Test
    fun `findAllMatches ignores dotted occurrence`() {
        val text = "val x = pkg.Bar.y\n"
        val m = TextualRenameScan.findAllMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `findAllMatches ignores identifier continuation`() {
        val text = "class Barr\n"
        val m = TextualRenameScan.findAllMatches(text, "Bar")
        assertTrue(m.isEmpty())
    }

    @Test
    fun `line tracking is correct across multi-line strings`() {
        val text = "val s = \"\"\"\n  ignored\n\"\"\"\nval z = Bar.q()\n"
        val m = TextualRenameScan.findQualifiedReceiverMatches(text, "Bar")
        assertEquals(1, m.size)
        assertEquals(3, m[0].line)
        assertEquals(8, m[0].column)
    }
}
