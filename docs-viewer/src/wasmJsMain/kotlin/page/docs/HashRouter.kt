package page.docs

import kotlinx.browser.window

@JsFun("() => window.location.hash")
private external fun jsHash(): String

@JsFun("(u) => history.replaceState(null, '', u)")
private external fun jsReplace(u: String)

fun currentRawHash(): String = jsHash().removePrefix("#")

fun replaceRawHash(raw: String) = jsReplace(if (raw.isEmpty()) "#" else "#$raw")

fun onHashChange(handler: () -> Unit) {
    window.addEventListener("hashchange") { handler() }
}
