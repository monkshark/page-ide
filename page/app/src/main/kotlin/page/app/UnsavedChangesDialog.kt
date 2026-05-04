package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.rememberDialogState
import page.ui.GlassTheme

private const val TITLE = "저장되지 않은 변경 사항"
private const val SAVE = "저장"
private const val DISCARD = "저장 안 함"
private const val CANCEL = "취소"
private const val TAB_QUESTION_PREFIX = "다음 파일에 저장되지 않은 변경 사항이 있습니다."
private const val TAB_QUESTION_SUFFIX = "저장하시겠습니까?"
private const val APP_QUESTION_SUFFIX = "저장하지 않고 종료하시겠습니까?"

@Composable
internal fun UnsavedChangesDialog(
    fileNames: List<String>,
    isAppExit: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    val state = rememberDialogState(width = 460.dp, height = 220.dp)
    DialogWindow(
        onCloseRequest = onCancel,
        state = state,
        title = TITLE,
        resizable = false,
        undecorated = true,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onCancel(); true
            } else false
        },
    ) {
        GlassTheme {
            DialogContent(
                fileNames = fileNames,
                isAppExit = isAppExit,
                onSave = onSave,
                onDiscard = onDiscard,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun DialogWindowScope.DialogContent(
    fileNames: List<String>,
    isAppExit: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = TAB_QUESTION_PREFIX,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                FileList(fileNames)
                Text(
                    text = if (isAppExit) APP_QUESTION_SUFFIX else TAB_QUESTION_SUFFIX,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DialogButton(label = CANCEL, primary = false, onClick = onCancel)
                    Spacer(Modifier.width(8.dp))
                    DialogButton(label = DISCARD, primary = false, onClick = onDiscard)
                    Spacer(Modifier.width(8.dp))
                    DialogButton(label = SAVE, primary = true, onClick = onSave)
                }
            }
            WindowDraggableArea(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .align(Alignment.TopStart),
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun FileList(names: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        names.forEach { name ->
            Text(
                text = "• $name",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DialogButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val bg =
        if (primary) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface
    val fg =
        if (primary) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier
            .height(32.dp),
        color = bg,
        shape = RoundedCornerShape(6.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
