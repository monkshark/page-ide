package page.app.mvi

internal class IdeEffectHandler {

    private var sink: ((IdeEvent, AppState, AppState) -> Unit)? = null

    fun bind(handler: (IdeEvent, AppState, AppState) -> Unit) {
        sink = handler
    }

    fun handle(event: IdeEvent, prev: AppState, next: AppState) {
        sink?.invoke(event, prev, next)
    }
}
