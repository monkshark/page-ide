package page.app

internal sealed interface PendingClose {
    data class Tab(val index: Int) : PendingClose
    data object App : PendingClose
}
