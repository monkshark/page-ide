package page.editor

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class FileKindTest {

    @Test
    fun `png is image`() {
        assertEquals(FileKind.IMAGE, FileKinds.classify(Paths.get("/tmp/icon.png")))
    }

    @Test
    fun `jpg and jpeg are image`() {
        assertEquals(FileKind.IMAGE, FileKinds.classify(Paths.get("/tmp/photo.jpg")))
        assertEquals(FileKind.IMAGE, FileKinds.classify(Paths.get("/tmp/photo.jpeg")))
    }

    @Test
    fun `gif bmp webp are image`() {
        assertEquals(FileKind.IMAGE, FileKinds.classify(Paths.get("/tmp/x.gif")))
        assertEquals(FileKind.IMAGE, FileKinds.classify(Paths.get("/tmp/x.bmp")))
        assertEquals(FileKind.IMAGE, FileKinds.classify(Paths.get("/tmp/x.webp")))
    }

    @Test
    fun `uppercase extension is recognized`() {
        assertEquals(FileKind.IMAGE, FileKinds.classify(Paths.get("/tmp/Foo.PNG")))
        assertEquals(FileKind.SVG, FileKinds.classify(Paths.get("/tmp/Foo.SVG")))
    }

    @Test
    fun `svg is svg not image`() {
        assertEquals(FileKind.SVG, FileKinds.classify(Paths.get("/tmp/icon.svg")))
    }

    @Test
    fun `xml is text`() {
        assertEquals(FileKind.TEXT, FileKinds.classify(Paths.get("/tmp/pom.xml")))
    }

    @Test
    fun `kotlin is text`() {
        assertEquals(FileKind.TEXT, FileKinds.classify(Paths.get("/tmp/Main.kt")))
    }

    @Test
    fun `no extension is text`() {
        assertEquals(FileKind.TEXT, FileKinds.classify(Paths.get("/tmp/Makefile")))
    }

    @Test
    fun `text and svg are editable as text, image is not`() {
        assertEquals(true, FileKind.TEXT.isEditableAsText)
        assertEquals(true, FileKind.SVG.isEditableAsText)
        assertEquals(false, FileKind.IMAGE.isEditableAsText)
    }
}
