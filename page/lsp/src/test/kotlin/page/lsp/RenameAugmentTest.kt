package page.lsp

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RenameAugmentTest {

    private val tmpDir: Path = Paths.get(System.getProperty("java.io.tmpdir")).resolve("page-rename-aug")
    private val fooPath: Path = tmpDir.resolve("Foo.kt")
    private val barPath: Path = tmpDir.resolve("Bar.kt")
    private val fooUri = fooPath.toUri().toString()
    private val barUri = barPath.toUri().toString()

    @Test
    fun `references already covered by edit are not duplicated`() {
        val edit = RenameWorkspaceEdit(
            listOf(RenameFileChange(fooUri, listOf(RenameEdit(0, 6, 0, 9, "New"))))
        )
        val refs = listOf(ReferenceLocation(fooUri, 0, 6, 0, 9))
        val result = RenameAugment.augment(edit, refs, "Bar", "New") { "class Bar" }
        assertEquals(1, result.changes.size)
        assertEquals(1, result.changes[0].edits.size)
    }

    @Test
    fun `missing reference whose slice equals oldName is added`() {
        val text = "package x\nval r = Bar.named(\"x\")\n"
        val refs = listOf(ReferenceLocation(barUri, 1, 8, 1, 11))
        val result = RenameAugment.augment(RenameWorkspaceEdit.EMPTY, refs, "Bar", "New") { text }
        assertEquals(1, result.changes.size)
        val e = result.changes[0].edits.single()
        assertEquals("New", e.newText)
        assertEquals(1, e.startLine)
        assertEquals(8, e.startCharacter)
        assertEquals(11, e.endCharacter)
    }

    @Test
    fun `reference whose slice does not equal oldName is skipped`() {
        val text = "package x\nval r = Foo.named\n"
        val refs = listOf(ReferenceLocation(barUri, 1, 8, 1, 11))
        val result = RenameAugment.augment(RenameWorkspaceEdit.EMPTY, refs, "Bar", "New") { text }
        assertEquals(0, result.changes.size)
    }

    @Test
    fun `multi-line reference is skipped`() {
        val refs = listOf(ReferenceLocation(barUri, 0, 0, 1, 3))
        val result = RenameAugment.augment(RenameWorkspaceEdit.EMPTY, refs, "Bar", "New") { "Bar\nBar" }
        assertEquals(0, result.changes.size)
    }

    @Test
    fun `length mismatch reference is skipped`() {
        val refs = listOf(ReferenceLocation(barUri, 0, 0, 0, 5))
        val result = RenameAugment.augment(RenameWorkspaceEdit.EMPTY, refs, "Bar", "New") { "Bar.named" }
        assertEquals(0, result.changes.size)
    }

    @Test
    fun `empty references returns the original edit instance`() {
        val edit = RenameWorkspaceEdit(
            listOf(RenameFileChange(fooUri, listOf(RenameEdit(0, 0, 0, 3, "New"))))
        )
        val result = RenameAugment.augment(edit, emptyList(), "Bar", "New") { null }
        assertSame(edit, result)
    }

    @Test
    fun `empty oldName or newName or same name is a no-op`() {
        val refs = listOf(ReferenceLocation(barUri, 0, 0, 0, 3))
        val edit = RenameWorkspaceEdit.EMPTY
        assertSame(edit, RenameAugment.augment(edit, refs, "", "New") { "Bar" })
        assertSame(edit, RenameAugment.augment(edit, refs, "Bar", "") { "Bar" })
        assertSame(edit, RenameAugment.augment(edit, refs, "Bar", "Bar") { "Bar" })
    }

    @Test
    fun `appended edit merges into existing file change sorted descending`() {
        val edit = RenameWorkspaceEdit(
            listOf(RenameFileChange(barUri, listOf(RenameEdit(5, 0, 5, 3, "New"))))
        )
        val text = buildString {
            for (line in 0..15) {
                if (line == 10) appendLine("    Bar.named(\"a\")") else appendLine("// line $line")
            }
        }
        val refs = listOf(
            ReferenceLocation(barUri, 5, 0, 5, 3),
            ReferenceLocation(barUri, 10, 4, 10, 7),
        )
        val result = RenameAugment.augment(edit, refs, "Bar", "New") { text }
        assertEquals(1, result.changes.size)
        val edits = result.changes[0].edits
        assertEquals(2, edits.size)
        assertEquals(10, edits[0].startLine)
        assertEquals(5, edits[1].startLine)
    }

    @Test
    fun `augmentTextually adds missed companion receiver in existing edit-set file`() {
        val text = """
            package x
            class Other {
                private val bar = Bar("a")
                fun viaCompanion(): String = Bar.named("b").greet()
            }
        """.trimIndent() + "\n"
        val existing = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    barUri,
                    listOf(RenameEdit(2, 22, 2, 25, "New")),
                )
            )
        )
        val result = RenameAugment.augmentTextually(existing, "Bar", "New") { text }
        assertEquals(1, result.changes.size)
        assertEquals(2, result.changes[0].edits.size)
        val added = result.changes[0].edits.first { it.startLine == 3 }
        assertEquals("New", added.newText)
    }

    @Test
    fun `augmentTextually skips files not in edit set`() {
        val text = "val x = Bar.named()\n"
        val existing = RenameWorkspaceEdit.EMPTY
        val result = RenameAugment.augmentTextually(existing, "Bar", "New") { text }
        assertSame(existing, result)
    }

    @Test
    fun `augmentTextually skips covered positions`() {
        val text = "val x = Bar.named()\n"
        val existing = RenameWorkspaceEdit(
            listOf(RenameFileChange(barUri, listOf(RenameEdit(0, 8, 0, 11, "New"))))
        )
        val result = RenameAugment.augmentTextually(existing, "Bar", "New") { text }
        assertEquals(1, result.changes[0].edits.size)
    }

    @Test
    fun `augmentImports adds import line edit to candidate file not in edit set`() {
        val text = "package x\n\nimport refscan.Bar\n\nfun f() = New(\"a\")\n"
        val existing = RenameWorkspaceEdit.EMPTY
        val result = RenameAugment.augmentImports(existing, listOf(fooPath), "Bar", "New") { p ->
            if (p == fooPath) text else null
        }
        assertEquals(1, result.changes.size)
        val edit = result.changes[0].edits.single()
        assertEquals(2, edit.startLine)
        assertEquals(15, edit.startCharacter)
        assertEquals(18, edit.endCharacter)
        assertEquals("New", edit.newText)
    }

    @Test
    fun `augmentImports merges into existing file change`() {
        val text = "package x\nimport refscan.Bar\nval r = Bar(\"x\")\n"
        val existing = RenameWorkspaceEdit(
            listOf(RenameFileChange(fooUri, listOf(RenameEdit(2, 8, 2, 11, "New"))))
        )
        val result = RenameAugment.augmentImports(existing, listOf(fooPath), "Bar", "New") { text }
        assertEquals(1, result.changes.size)
        assertEquals(2, result.changes[0].edits.size)
    }

    @Test
    fun `augmentImports skips already covered positions`() {
        val text = "import refscan.Bar\n"
        val existing = RenameWorkspaceEdit(
            listOf(RenameFileChange(fooUri, listOf(RenameEdit(0, 15, 0, 18, "New"))))
        )
        val result = RenameAugment.augmentImports(existing, listOf(fooPath), "Bar", "New") { text }
        assertEquals(1, result.changes[0].edits.size)
    }

    @Test
    fun `augmentImports no-op when candidate has no import`() {
        val existing = RenameWorkspaceEdit.EMPTY
        val result = RenameAugment.augmentImports(existing, listOf(fooPath), "Bar", "New") { "package x\n" }
        assertSame(existing, result)
    }

    @Test
    fun `augmentImports with declarationPackage ignores same-name import from another package`() {
        val text = "package consumer\nimport other.Bar as OtherBar\nfun f(): OtherBar = OtherBar(\"x\")\n"
        val existing = RenameWorkspaceEdit.EMPTY
        val result = RenameAugment.augmentImports(
            existing,
            listOf(fooPath),
            "Bar",
            "New",
            readFileText = { text },
            declarationPackage = "refscan",
        )
        assertSame(existing, result)
    }

    @Test
    fun `augmentImports with declarationPackage keeps import from matching package`() {
        val text = "package consumer\nimport refscan.Bar\nfun f(): Bar = Bar()\n"
        val existing = RenameWorkspaceEdit.EMPTY
        val result = RenameAugment.augmentImports(
            existing,
            listOf(fooPath),
            "Bar",
            "New",
            readFileText = { text },
            declarationPackage = "refscan",
        )
        assertEquals(1, result.changes.size)
        assertEquals(1, result.changes[0].edits.size)
        assertEquals(1, result.changes[0].edits[0].startLine)
        assertEquals(15, result.changes[0].edits[0].startCharacter)
    }

    @Test
    fun `augmentDeclarationFile adds class declaration and self-reference constructor`() {
        val text = """
            package x

            class Bar(val name: String = "bar") {
                fun greet(): String = "Hello from ${'$'}name"

                companion object {
                    fun named(name: String): Bar = Bar(name)
                }
            }
        """.trimIndent() + "\n"
        val existing = RenameWorkspaceEdit(
            listOf(
                RenameFileChange(
                    barUri,
                    listOf(RenameEdit(6, 33, 6, 36, "New")),
                )
            )
        )
        val result = RenameAugment.augmentDeclarationFile(existing, barPath, "Bar", "New") { text }
        assertEquals(1, result.changes.size)
        val edits = result.changes[0].edits
        val lines = edits.map { it.startLine }.toSet()
        assertEquals(true, 2 in lines)
        assertEquals(true, 6 in lines)
        val classDecl = edits.first { it.startLine == 2 }
        assertEquals("New", classDecl.newText)
    }

    @Test
    fun `augmentDeclarationFile is a no-op when declaration file has no occurrences`() {
        val existing = RenameWorkspaceEdit(
            listOf(RenameFileChange(barUri, listOf(RenameEdit(0, 0, 0, 3, "New"))))
        )
        val result = RenameAugment.augmentDeclarationFile(existing, barPath, "Bar", "New") { "package x\n" }
        assertSame(existing, result)
    }

    @Test
    fun `augmentDeclarationFile creates new file change when declaration file not yet in edit set`() {
        val text = "class Bar { fun mk(): Bar = Bar() }\n"
        val existing = RenameWorkspaceEdit.EMPTY
        val result = RenameAugment.augmentDeclarationFile(existing, barPath, "Bar", "New") { text }
        assertEquals(1, result.changes.size)
        assertEquals(3, result.changes[0].edits.size)
    }

    @Test
    fun `augmentDeclarationFile skips already-covered positions`() {
        val text = "class Bar\n"
        val existing = RenameWorkspaceEdit(
            listOf(RenameFileChange(barUri, listOf(RenameEdit(0, 6, 0, 9, "New"))))
        )
        val result = RenameAugment.augmentDeclarationFile(existing, barPath, "Bar", "New") { text }
        assertEquals(1, result.changes[0].edits.size)
    }

    @Test
    fun `augmentDeclarationFile ignores occurrences inside strings`() {
        val text = """
            class Bar {
                val s = "Bar from Bar"
            }
        """.trimIndent() + "\n"
        val existing = RenameWorkspaceEdit.EMPTY
        val result = RenameAugment.augmentDeclarationFile(existing, barPath, "Bar", "New") { text }
        assertEquals(1, result.changes.size)
        assertEquals(1, result.changes[0].edits.size)
    }

    @Test
    fun `references in a new file create a new file change`() {
        val text = "Bar"
        val refs = listOf(ReferenceLocation(barUri, 0, 0, 0, 3))
        val existing = RenameFileChange(fooUri, listOf(RenameEdit(0, 0, 0, 3, "New")))
        val result = RenameAugment.augment(RenameWorkspaceEdit(listOf(existing)), refs, "Bar", "New") { text }
        assertEquals(2, result.changes.size)
        val newChange = result.changes.first { it.uri == barUri }
        assertEquals(1, newChange.edits.size)
        assertEquals("New", newChange.edits[0].newText)
    }
}
