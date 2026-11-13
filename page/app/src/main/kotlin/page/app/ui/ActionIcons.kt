package page.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.ui.graphics.vector.ImageVector

internal object ActionIcons {

    fun forId(id: String): ImageVector? = when (id) {
        "file.openFile" -> Icons.Outlined.InsertDriveFile
        "file.openFolder" -> Icons.Outlined.FolderOpen
        "file.save" -> Icons.Outlined.Save
        "file.closeTab" -> Icons.Outlined.Close
        "file.settings" -> Icons.Outlined.Settings
        "file.refreshTree" -> Icons.Outlined.Refresh

        "edit.undo" -> Icons.Outlined.Undo
        "edit.redo", "edit.redoAlternate" -> Icons.Outlined.Redo
        "edit.find" -> Icons.Outlined.Search
        "edit.replace" -> Icons.Outlined.FindReplace
        "edit.closeSearch" -> Icons.Outlined.Close

        "nav.quickOpen" -> Icons.Outlined.InsertDriveFile
        "nav.workspaceSymbol" -> Icons.Outlined.Hub
        "nav.documentSymbol" -> Icons.AutoMirrored.Outlined.ListAlt
        "nav.findInFiles" -> Icons.AutoMirrored.Outlined.ManageSearch
        "nav.nextProblem", "nav.prevProblem" -> Icons.Outlined.ErrorOutline
        "nav.prevTab" -> Icons.AutoMirrored.Outlined.ArrowBack
        "nav.nextTab" -> Icons.AutoMirrored.Outlined.ArrowForward

        "code.format" -> Icons.AutoMirrored.Outlined.FormatAlignLeft
        "code.action" -> Icons.Outlined.Bolt

        "view.problems" -> Icons.Outlined.ErrorOutline
        "view.todo" -> Icons.Outlined.Checklist
        "view.split" -> Icons.Outlined.VerticalSplit
        "view.splitFlip" -> Icons.Outlined.SwapHoriz

        "page.actions" -> Icons.AutoMirrored.Outlined.ListAlt
        "page.cycleTheme" -> Icons.Outlined.Palette

        else -> null
    }
}
