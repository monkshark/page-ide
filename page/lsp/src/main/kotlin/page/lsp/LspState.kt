package page.lsp

enum class LspState {
    NOT_STARTED,
    STARTING,
    INITIALIZED,
    SHUTTING_DOWN,
    EXITED,
    FAILED,
}
