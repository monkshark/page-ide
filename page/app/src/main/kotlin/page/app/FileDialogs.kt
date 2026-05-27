package page.app

import page.runtime.*

import java.awt.Frame
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser

object FileDialogs {

    fun open(parent: Frame): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Open"
            fileSelectionMode = JFileChooser.FILES_ONLY
        }
        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.toPath()
        } else null
    }

    fun saveAs(parent: Frame, suggested: String? = null): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Save As"
            fileSelectionMode = JFileChooser.FILES_ONLY
            if (suggested != null) selectedFile = File(suggested)
        }
        return if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.toPath()
        } else null
    }

    fun openDirectory(parent: Frame): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Open Folder"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.toPath()
        } else null
    }
}
