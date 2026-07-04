package page.docs

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

@JsFun("(url) => fetch(url).then(r => { if (!r.ok) throw new Error('HTTP ' + r.status + ' for ' + url); return r.text(); })")
private external fun jsFetchText(url: String): Promise<JsString>

private suspend fun <T : JsAny?> Promise<T>.awaitJs(): T = suspendCoroutine { cont ->
    this.then<JsAny?>(
        { value -> cont.resume(value); null },
        { err -> cont.resumeWithException(RuntimeException(err.toString())); null },
    )
}

suspend fun fetchText(url: String): String = jsFetchText(url).awaitJs().toString()
