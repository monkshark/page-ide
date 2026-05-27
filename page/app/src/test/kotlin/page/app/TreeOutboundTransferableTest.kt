package page.app

import page.runtime.*

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Path

class TreeOutboundTransferableTest {

    @Test
    fun `transferable advertises both file list and internal flavors`() {
        val tx = TreeOutboundTransferable.fromPaths(
            paths = listOf(Path.of("/tmp/a"), Path.of("/tmp/b")),
            initialMode = TreeDragController.Mode.Move,
        )
        assertTrue(tx.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        assertTrue(tx.isDataFlavorSupported(TreeOutboundTransferable.PageInternalFlavor))
        assertFalse(tx.isDataFlavorSupported(DataFlavor.stringFlavor))
    }

    @Test
    fun `internal payload exposes paths and mode`() {
        val paths = listOf(Path.of("/x/a"), Path.of("/x/b"))
        val tx = TreeOutboundTransferable.fromPaths(paths, TreeDragController.Mode.Copy)
        val payload = TreeOutboundTransferable.extractInternalPayload(tx)
        assertNotNull(payload)
        assertEquals(paths, payload!!.paths)
        assertEquals(TreeDragController.Mode.Copy, payload.initialMode)
    }

    @Test
    fun `extractInternalPayload returns null for plain file list`() {
        val plain = object : java.awt.datatransfer.Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean =
                flavor == DataFlavor.javaFileListFlavor
            override fun getTransferData(flavor: DataFlavor?): Any =
                listOf(File("/tmp/foo"))
        }
        assertNull(TreeOutboundTransferable.extractInternalPayload(plain))
    }

    @Test
    fun `file list flavor yields File items`() {
        val tx = TreeOutboundTransferable.fromPaths(
            paths = listOf(Path.of("/tmp/one.txt")),
            initialMode = TreeDragController.Mode.Move,
        )
        @Suppress("UNCHECKED_CAST")
        val files = tx.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
        assertEquals(1, files.size)
        assertEquals("one.txt", files[0].name)
    }
}
