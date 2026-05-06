package page.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.singleWindowApplication
import page.editor.EditorContent
import page.ui.CodeEditor

fun main() = singleWindowApplication(title = "CodeEditor Demo") {
    var content by remember {
        mutableStateOf(
            EditorContent.of(
                """
                fun main() {
                    println("hello, code editor")
                    val items = listOf("foo", "bar", "baz")
                    items.forEach { println(it) }
                }
                """.trimIndent(),
                caret = 0,
            ),
        )
    }
    CodeEditor(
        content = content,
        onContentChange = { content = it },
        modifier = Modifier.fillMaxSize(),
    )
}
