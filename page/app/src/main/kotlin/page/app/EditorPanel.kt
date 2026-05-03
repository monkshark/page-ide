package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.editor.TextBuffer

@Composable
fun EditorPanel(modifier: Modifier = Modifier) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val buffer = remember(value.text) { TextBuffer(value.text) }
    val caretOffset = value.selection.start.coerceIn(0, buffer.length)
    val caret = buffer.lineColOf(caretOffset)

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
        EditorStatusBar(
            line = caret.line,
            col = caret.col,
            lineCount = buffer.lineCount,
            charCount = buffer.length,
        )
    }
}

@Composable
private fun EditorStatusBar(line: Int, col: Int, lineCount: Int, charCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StatusItem("Ln ${line + 1}, Col ${col + 1}")
            StatusItem("$lineCount lines")
            StatusItem("$charCount chars")
        }
    }
}

@Composable
private fun StatusItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
