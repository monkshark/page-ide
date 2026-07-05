package page.shared.docs

data class DocRef(val path: String?, val heading: String?)

fun parseDocHash(raw: String): DocRef {
    val h = raw.removePrefix("#").trim()
    if (h.isEmpty()) return DocRef(null, null)
    val idx = h.indexOf('#')
    if (idx < 0) return DocRef(h, null)
    val path = h.substring(0, idx).ifEmpty { null }
    val heading = h.substring(idx + 1).ifEmpty { null }
    return DocRef(path, heading)
}

fun buildDocHash(path: String?, heading: String?): String {
    val p = path?.trim().orEmpty()
    val hd = heading?.trim().orEmpty()
    return when {
        p.isEmpty() -> if (hd.isEmpty()) "" else hd
        hd.isEmpty() -> p
        else -> "$p#$hd"
    }
}
