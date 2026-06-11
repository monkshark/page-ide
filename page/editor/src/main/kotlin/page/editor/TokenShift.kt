package page.editor

object TokenShift {
    fun shift(tokens: List<Token>, oldText: String, newText: String): List<Token> {
        if (oldText == newText || tokens.isEmpty()) return tokens
        val limit = minOf(oldText.length, newText.length)
        var prefix = 0
        while (prefix < limit && oldText[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (suffix < limit - prefix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) suffix++
        val oldEditEnd = oldText.length - suffix
        val delta = newText.length - oldText.length
        val out = ArrayList<Token>(tokens.size)
        for (t in tokens) {
            when {
                t.endExclusive <= prefix -> out += t
                t.start >= oldEditEnd -> out += Token(t.kind, (t.start + delta)..(t.range.last + delta))
            }
        }
        return out
    }
}
