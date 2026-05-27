package page.runtime

import java.nio.file.Path

object RunConfigStore {
    const val FILE_NAME = "run-configs.json"

    private data class Persisted(
        val configs: List<RunConfig> = emptyList(),
        val activeId: String? = null,
    )

    fun load(workspaceRoot: Path): RunConfigsState {
        val persisted = PageIdeStore.read(workspaceRoot, FILE_NAME, Persisted::class.java)
            ?: return RunConfigsState()
        val sanitized = persisted.configs.filter { it.id.isNotBlank() && it.command.isNotBlank() }
        val active = persisted.activeId?.takeIf { id ->
            id == CURRENT_FILE_ID || sanitized.any { it.id == id }
        } ?: sanitized.firstOrNull()?.id
        return RunConfigsState(configs = sanitized, activeId = active)
    }

    fun save(workspaceRoot: Path, state: RunConfigsState) {
        PageIdeStore.write(
            workspaceRoot,
            FILE_NAME,
            Persisted(configs = state.configs, activeId = state.activeId),
        )
    }
}
