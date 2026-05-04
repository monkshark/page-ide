package page.editor

data class SearchState(
    val query: String = "",
    val replace: String = "",
    val replaceVisible: Boolean = false,
    val caseSensitive: Boolean = false,
    val matches: List<IntRange> = emptyList(),
    val activeMatchIndex: Int = -1,
) {
    val isActive: Boolean get() = query.isNotEmpty()
    val active: IntRange? get() = matches.getOrNull(activeMatchIndex)

    fun withQuery(text: String, query: String): SearchState {
        if (query.isEmpty()) return copy(query = "", matches = emptyList(), activeMatchIndex = -1)
        val found = findAll(text, query, caseSensitive)
        val newActive = if (found.isEmpty()) -1 else 0
        return copy(query = query, matches = found, activeMatchIndex = newActive)
    }

    fun withReplace(value: String): SearchState = copy(replace = value)

    fun withReplaceVisible(value: Boolean): SearchState = copy(replaceVisible = value)

    fun withCaseSensitive(text: String, value: Boolean): SearchState {
        if (caseSensitive == value) return this
        val updated = copy(caseSensitive = value)
        return updated.withQuery(text, query)
    }

    fun retarget(text: String): SearchState {
        if (query.isEmpty()) return this
        val previousActive = active
        val found = findAll(text, query, caseSensitive)
        if (found.isEmpty()) return copy(matches = emptyList(), activeMatchIndex = -1)
        val newActive = if (previousActive == null) {
            0
        } else {
            val target = previousActive.first
            val nearest = found.indexOfFirst { it.first >= target }
            if (nearest >= 0) nearest else 0
        }
        return copy(matches = found, activeMatchIndex = newActive)
    }

    fun next(): SearchState {
        if (matches.isEmpty()) return this
        val n = (activeMatchIndex + 1) % matches.size
        return copy(activeMatchIndex = n)
    }

    fun prev(): SearchState {
        if (matches.isEmpty()) return this
        val n = if (activeMatchIndex <= 0) matches.lastIndex else activeMatchIndex - 1
        return copy(activeMatchIndex = n)
    }

    private companion object {
        fun findAll(text: String, query: String, caseSensitive: Boolean): List<IntRange> {
            if (query.isEmpty() || text.isEmpty()) return emptyList()
            val out = mutableListOf<IntRange>()
            val len = query.length
            var i = 0
            while (i <= text.length - len) {
                val matched = text.regionMatches(i, query, 0, len, ignoreCase = !caseSensitive)
                if (matched) {
                    out.add(i until i + len)
                    i += len
                } else {
                    i += 1
                }
            }
            return out
        }
    }
}
