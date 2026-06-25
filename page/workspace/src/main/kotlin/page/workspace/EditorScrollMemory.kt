package page.workspace

import java.nio.file.Path

data class EditorScrollSnapshot(val vertical: Int, val horizontal: Int)

object EditorScrollMemory {

    fun get(store: Map<Path, EditorScrollSnapshot>, path: Path?): EditorScrollSnapshot? =
        path?.let { store[it] }

    fun put(
        store: Map<Path, EditorScrollSnapshot>,
        path: Path?,
        snapshot: EditorScrollSnapshot,
    ): Map<Path, EditorScrollSnapshot> {
        if (path == null) return store
        if (snapshot.vertical < 0 || snapshot.horizontal < 0) return store
        val current = store[path]
        if (current == snapshot) return store
        return store + (path to snapshot)
    }

    fun clear(
        store: Map<Path, EditorScrollSnapshot>,
        path: Path,
    ): Map<Path, EditorScrollSnapshot> = if (path in store) store - path else store

    fun clearAll(
        store: Map<Path, EditorScrollSnapshot>,
        paths: Collection<Path>,
    ): Map<Path, EditorScrollSnapshot> {
        if (paths.isEmpty()) return store
        val keys = paths.toSet()
        if (store.keys.none { it in keys }) return store
        return store.filterKeys { it !in keys }
    }
}
