package page.app

import page.runtime.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.State
import java.nio.file.Path
import page.editor.SyntaxLexers
import page.editor.TodoIndex
import page.editor.TodoItem
import page.editor.TodoScanner

class TodoController(
    val workspaceRoot: Path?,
    private val scope: CoroutineScope,
) {
    val index = TodoIndex()
    private val snapshot = mutableStateOf<List<TodoItem>>(emptyList())
    val items: State<List<TodoItem>> get() = snapshot
    private val listener: (TodoIndex) -> Unit = { snapshot.value = it.all() }
    private val perFileDebounce = HashMap<String, Job>()

    init {
        index.addListener(listener)
    }

    fun scanWorkspaceAsync() {
        val root = workspaceRoot ?: return
        scope.launch(Dispatchers.IO) {
            val scanned = TodoScanner.scanWorkspace(root)
            val byFile = scanned.groupBy { it.uri }
            index.replaceAll(byFile)
        }
    }

    fun updateFile(path: Path, text: String) {
        val uri = path.toUri().toString()
        val lexer = SyntaxLexers.forPath(path) ?: run {
            index.removeFile(uri)
            return
        }
        perFileDebounce[uri]?.cancel()
        perFileDebounce[uri] = scope.launch(Dispatchers.Default) {
            delay(150)
            val items = TodoScanner.scanText(uri, text, lexer)
            index.setFile(uri, items)
        }
    }

    fun removeFile(path: Path) {
        val uri = path.toUri().toString()
        perFileDebounce.remove(uri)?.cancel()
        index.removeFile(uri)
    }

    fun shutdown() {
        index.removeListener(listener)
        scope.cancel()
    }
}

@Composable
fun rememberTodoController(workspaceRoot: Path?): TodoController {
    val controller = remember(workspaceRoot) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        TodoController(workspaceRoot, scope)
    }
    DisposableEffect(controller) { onDispose { controller.shutdown() } }
    return controller
}
