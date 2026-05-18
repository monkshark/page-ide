package page.app

const val CURRENT_FILE_ID = "__current_file__"

data class RunConfig(
    val id: String,
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val workingDir: String? = null,
    val env: Map<String, String> = emptyMap(),
) {
    fun isRunnable(): Boolean = command.isNotBlank()
}

data class RunConfigsState(
    val configs: List<RunConfig> = emptyList(),
    val activeId: String? = null,
) {
    val active: RunConfig? get() = configs.firstOrNull { it.id == activeId }

    val isCurrentFileActive: Boolean get() = activeId == CURRENT_FILE_ID

    fun add(config: RunConfig): RunConfigsState {
        if (configs.any { it.id == config.id }) return this
        val newActive = activeId ?: config.id
        return copy(configs = configs + config, activeId = newActive)
    }

    fun remove(id: String): RunConfigsState {
        val newConfigs = configs.filterNot { it.id == id }
        val newActive = when {
            activeId != id -> activeId
            newConfigs.isEmpty() -> null
            else -> newConfigs.first().id
        }
        return copy(configs = newConfigs, activeId = newActive)
    }

    fun update(config: RunConfig): RunConfigsState {
        val idx = configs.indexOfFirst { it.id == config.id }
        if (idx < 0) return this
        return copy(configs = configs.toMutableList().also { it[idx] = config })
    }

    fun select(id: String): RunConfigsState {
        if (id == CURRENT_FILE_ID) return copy(activeId = CURRENT_FILE_ID)
        if (configs.none { it.id == id }) return this
        return copy(activeId = id)
    }
}
