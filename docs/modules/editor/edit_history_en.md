# EditHistory / EditSnapshot

> 한국어: [edit_history.md](https://monkshark.github.io/page-ide/#modules/editor/edit_history.md)

> `page/editor/src/main/kotlin/page/editor/EditHistory.kt` — undo/redo stack (per tab)

Immutable data structure. Each tab's `OpenTab.history` holds a separate instance so undo never bleeds between tabs.

---

## `EditSnapshot`

```kotlin
data class EditSnapshot(val text: String, val caret: Int)
```

Undo/redo unit. Captures the full text + caret position together. Even for large files, JVM `String` immutability shares storage, so the memory cost stays low.

---

## `EditHistory`

```kotlin
data class EditHistory(
    val past: List<EditSnapshot> = emptyList(),
    val future: List<EditSnapshot> = emptyList(),
)
```

`past` is the undo stack, `future` is the redo stack. A new edit clears `future`.

| Constant | Value | Description |
|---|---|---|
| `MAX_SIZE` | `1000` | Max length of `past`. Oldest snapshot drops first when exceeded |

---

## `pushBeforeChange`

```kotlin
fun pushBeforeChange(prev: EditSnapshot, maxSize: Int = MAX_SIZE): EditHistory
```

Pushes the state just before a new edit onto `past` and clears `future`. If the incoming snapshot equals `past.last()`, it's ignored — duplicates don't stack.

---

## `undo` / `redo`

```kotlin
fun undo(current: EditSnapshot): Pair<EditHistory, EditSnapshot>?
fun redo(current: EditSnapshot): Pair<EditHistory, EditSnapshot>?
```

`current` is the currently displayed state. `undo` pops `past.last()` and pushes `current` onto `future`. `redo` reverses it. Returns `null` when the relevant stack is empty.

Returns `(new EditHistory, snapshot to restore)` — the caller must consume both and apply them to tab state (see `TabBook.undoOnActive`).

---

## Usage

| Location | Purpose |
|---|---|
| `OpenTab.history` | Held per tab |
| `TabBook.pushHistoryOnActive` | Pushes the previous state from `onValueChange` |
| `TabBook.undoOnActive` / `redoOnActive` | Ctrl+Z / Ctrl+Shift+Z (Ctrl+Y) handlers |

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
