package page.atlas.analyzer

enum class ProjectAnalysisStage { DISCOVERING, DECLARATIONS, RELATIONSHIPS, ACTIVE_FILE }

data class ProjectAnalysisProgress(
    val stage: ProjectAnalysisStage,
    val completed: Int = 0,
    val total: Int = 0,
) {
    val label: String get() {
        val stageLabel = when (stage) {
            ProjectAnalysisStage.DISCOVERING -> "Finding source files…"
            ProjectAnalysisStage.DECLARATIONS -> "Indexing declarations"
            ProjectAnalysisStage.RELATIONSHIPS -> "Connecting dependencies"
            ProjectAnalysisStage.ACTIVE_FILE -> "Analyzing active file…"
        }
        return if (total > 0) "$stageLabel · $completed / $total files" else stageLabel
    }
}
