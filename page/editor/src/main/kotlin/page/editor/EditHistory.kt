package page.editor

data class EditSnapshot(val text: String, val caret: Int)

data class EditHistory(
    val past: List<EditSnapshot> = emptyList(),
    val future: List<EditSnapshot> = emptyList(),
) {
    fun pushBeforeChange(prev: EditSnapshot, maxSize: Int = MAX_SIZE): EditHistory {
        if (past.lastOrNull() == prev) return EditHistory(past, emptyList())
        val grown = past + prev
        val capped = if (grown.size > maxSize) grown.subList(grown.size - maxSize, grown.size) else grown
        return EditHistory(capped.toList(), emptyList())
    }

    fun undo(current: EditSnapshot): Pair<EditHistory, EditSnapshot>? {
        val last = past.lastOrNull() ?: return null
        return EditHistory(past.dropLast(1), future + current) to last
    }

    fun redo(current: EditSnapshot): Pair<EditHistory, EditSnapshot>? {
        val last = future.lastOrNull() ?: return null
        return EditHistory(past + current, future.dropLast(1)) to last
    }

    companion object {
        const val MAX_SIZE = 1000
    }
}
