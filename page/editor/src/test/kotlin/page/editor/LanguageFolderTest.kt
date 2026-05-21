package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LanguageFolderTest {
    @Test
    fun braceFolderDelegatesToFoldRegions() {
        val text = "class A {\n    val x = 1\n}\n"
        assertEquals(FoldRegions.detect(text), BraceFolder.detect(text))
    }

    @Test
    fun unknownExtensionFallsBackToBraceFolder() {
        assertSame(BraceFolder, LanguageFolders.forExtension(null))
        assertSame(BraceFolder, LanguageFolders.forExtension("xyz"))
    }

    @Test
    fun ktExtensionRoutesToFolder() {
        val folder = LanguageFolders.forExtension("kt")
        val text = "fun f() {\n  val x = 1\n}\n"
        val regions = folder.detect(text)
        assertEquals(1, regions.size)
        assertEquals(0, regions[0].startLine)
        assertEquals(2, regions[0].endLine)
    }
}
