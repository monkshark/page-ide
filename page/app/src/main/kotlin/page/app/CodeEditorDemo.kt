package page.app

import page.runtime.*

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.singleWindowApplication
import page.editor.SplitOrientation
import page.editor.SplitPaneState
import page.ui.CodeEditor
import page.ui.SplitPane

fun main() = singleWindowApplication(title = "CodeEditor Demo") {
    var leftValue by remember {
        mutableStateOf(
            TextFieldValue(
                """
                fun main() {
                    println("hello, code editor")
                    val items = listOf("foo", "bar", "baz")
                    items.forEach { println(it) }
                }
                """.trimIndent(),
            ),
        )
    }
    var rightValue by remember {
        mutableStateOf(
            TextFieldValue(
                """
                class Tree(val value: Int) {
                    var left: Tree? = null
                    var right: Tree? = null
                }
                """.trimIndent(),
            ),
        )
    }
    var split by remember { mutableStateOf(true) }
    var orientation by remember { mutableStateOf(SplitOrientation.HORIZONTAL) }
    var splitState by remember { mutableStateOf(SplitPaneState(ratio = 0.5f)) }

    val onShortcut: (KeyEvent) -> Boolean = handler@{ event ->
        if (event.type != KeyEventType.KeyDown) return@handler false
        if (!event.isCtrlPressed) return@handler false
        when {
            event.key == Key.Backslash && event.isShiftPressed -> {
                orientation = if (orientation == SplitOrientation.HORIZONTAL)
                    SplitOrientation.VERTICAL else SplitOrientation.HORIZONTAL
                true
            }
            event.key == Key.Backslash -> {
                split = !split
                true
            }
            else -> false
        }
    }

    if (split) {
        SplitPane(
            state = splitState,
            onStateChange = { splitState = it },
            orientation = orientation,
            modifier = Modifier.fillMaxSize(),
            first = {
                ScrollableEditor(value = leftValue, onValueChange = { leftValue = it }, onShortcut = onShortcut)
            },
            second = {
                ScrollableEditor(value = rightValue, onValueChange = { rightValue = it }, onShortcut = onShortcut)
            },
        )
    } else {
        ScrollableEditor(value = leftValue, onValueChange = { leftValue = it }, onShortcut = onShortcut)
    }
}

@androidx.compose.runtime.Composable
private fun ScrollableEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onShortcut: (KeyEvent) -> Boolean,
) {
    val v = rememberScrollState()
    val h = rememberScrollState()
    CodeEditor(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(v)
            .horizontalScroll(h),
        onPreviewKeyEvent = onShortcut,
    )
}
