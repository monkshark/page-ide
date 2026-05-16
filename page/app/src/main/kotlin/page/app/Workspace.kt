package page.app

import com.google.gson.reflect.TypeToken
import java.nio.file.Path

data class WorkspaceFile(
    val version: Int = 1,
    val palette: String? = null,
)

object WorkspaceStore {
    const val FILE_NAME = "workspace.json"

    fun load(workspaceRoot: Path): WorkspaceFile {
        val type = object : TypeToken<WorkspaceFile>() {}.type
        return PageIdeStore.readType<WorkspaceFile>(workspaceRoot, FILE_NAME, type) ?: WorkspaceFile()
    }

    fun save(workspaceRoot: Path, file: WorkspaceFile) {
        PageIdeStore.write(workspaceRoot, FILE_NAME, file)
    }
}
