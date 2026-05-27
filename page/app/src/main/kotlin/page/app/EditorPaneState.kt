package page.app

import page.runtime.*

import androidx.compose.ui.text.input.TextFieldValue
import page.editor.SearchState
import page.editor.TabBook

enum class PaneSide { PRIMARY, SECONDARY }

data class EditorPaneState(
    val book: TabBook = TabBook(),
    val editorValue: TextFieldValue = TextFieldValue(""),
    val search: SearchState? = null,
)
