package page.app

import page.runtime.*

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.nio.file.Path

internal object TreeOutboundTransferable {

    val PageInternalFlavor: DataFlavor by lazy {
        DataFlavor(
            "${DataFlavor.javaJVMLocalObjectMimeType};class=${PageInternalPayload::class.java.name}",
            "PAGE internal drag",
            PageInternalPayload::class.java.classLoader,
        )
    }

    data class PageInternalPayload(
        val paths: List<Path>,
        val initialMode: TreeDragController.Mode,
    )

    fun fromPaths(paths: List<Path>, initialMode: TreeDragController.Mode): Transferable {
        val files = paths.mapNotNull { runCatching { it.toFile() }.getOrNull() }
        val payload = PageInternalPayload(paths.toList(), initialMode)
        return object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> =
                arrayOf(PageInternalFlavor, DataFlavor.javaFileListFlavor)

            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
                flavor == PageInternalFlavor || flavor == DataFlavor.javaFileListFlavor

            override fun getTransferData(flavor: DataFlavor): Any = when {
                flavor == PageInternalFlavor -> payload
                flavor == DataFlavor.javaFileListFlavor -> files
                else -> throw UnsupportedFlavorException(flavor)
            }
        }
    }

    fun extractInternalPayload(transferable: Transferable): PageInternalPayload? {
        if (!transferable.isDataFlavorSupported(PageInternalFlavor)) return null
        return runCatching { transferable.getTransferData(PageInternalFlavor) as? PageInternalPayload }
            .getOrNull()
    }
}
