package page.app

import page.runtime.*

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.nio.file.Path

object FileTreeClipboard {

    enum class Mode { Cut, Copy }

    data class Content(val paths: List<Path>, val mode: Mode)

    private val internalPaths = mutableStateListOf<Path>()
    private var internalMode by mutableStateOf(Mode.Copy)

    val markedCutPaths: List<Path> get() = if (internalMode == Mode.Cut) internalPaths else emptyList()

    fun isCut(path: Path): Boolean = internalMode == Mode.Cut && path in internalPaths

    fun writeCut(paths: Collection<Path>) = write(paths, Mode.Cut)

    fun writeCopy(paths: Collection<Path>) = write(paths, Mode.Copy)

    private fun write(paths: Collection<Path>, mode: Mode) {
        val snapshot = paths.toList()
        writeToOs(snapshot)
        internalPaths.clear()
        internalPaths.addAll(snapshot)
        internalMode = mode
    }

    fun hasContentNow(): Boolean {
        if (internalPaths.isNotEmpty()) return true
        return runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val contents = clipboard.getContents(null) ?: return@runCatching false
            contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        }.getOrDefault(false)
    }

    fun read(): Content? {
        val osPaths = readFromOs() ?: return null
        if (osPaths.isEmpty()) return null
        val ours = osPaths == internalPaths.toList()
        val mode = if (ours) internalMode else Mode.Copy
        if (!ours && internalMode == Mode.Cut) {
            internalPaths.clear()
            internalMode = Mode.Copy
        }
        return Content(osPaths, mode)
    }

    fun clearCutMarking() {
        if (internalMode == Mode.Cut) {
            internalPaths.clear()
            internalMode = Mode.Copy
        }
    }

    private fun readFromOs(): List<Path>? {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val contents = clipboard.getContents(null) ?: return null
            if (!contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return null
            @Suppress("UNCHECKED_CAST")
            val files = contents.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            files.map { it.toPath() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeToOs(paths: List<Path>) {
        val files = paths.map { it.toFile() }
        val transferable = object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
                flavor == DataFlavor.javaFileListFlavor
            override fun getTransferData(flavor: DataFlavor): Any {
                if (flavor != DataFlavor.javaFileListFlavor) throw UnsupportedFlavorException(flavor)
                return files
            }
        }
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
        } catch (_: Throwable) {
        }
    }
}
