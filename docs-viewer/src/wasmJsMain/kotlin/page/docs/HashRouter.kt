package page.docs

import kotlinx.browser.window

@JsFun("() => window.location.hash")
private external fun jsHash(): String

@JsFun("(u) => history.replaceState(null, '', u)")
private external fun jsReplace(u: String)

fun currentSlug(): String = jsHash().removePrefix("#")

fun replaceSlug(slug: String) = jsReplace(if (slug.isEmpty()) "#" else "#$slug")

fun onHashChange(handler: () -> Unit) {
    window.addEventListener("hashchange") { handler() }
}
