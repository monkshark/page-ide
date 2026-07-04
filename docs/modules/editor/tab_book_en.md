# TabBook / OpenTab

> 한국어: [tab_book.md](https://monkshark.github.io/page-ide/#modules/editor/tab_book.md)

> `page/editor/src/main/kotlin/page/editor/TabBook.kt` — Tab collection + active index

Immutable data structure. Each tab carries its own text / saved text / caret / history, so per-tab state never mixes.

---

## `OpenTab`

```kotlin
data class OpenTab(
    val path: Path,
    val text: String,
    val savedText: String = text,
    val caret: Int = 0,
    val history: EditHistory = EditHistory(),
) {
    val dirty: Boolean get() = text != savedText
}
```

| Field | Meaning |
|---|---|
| `path` | File path (also the tab identity) |
| `text` | Current body |
| `savedText` | Body as of the last save |
| `caret` | Last caret position (restored on tab switch) |
| `history` | undo/redo stack scoped to this tab |

`dirty` is `true` when current body ≠ saved body. The signal that drives the unsaved-changes dialog on `Ctrl+W`.

---

## `TabBook`

```kotlin
data class TabBook(
    val tabs: List<OpenTab> = emptyList(),
    val activeIndex: Int = -1,
)
```

Tab list + active index. `activeIndex == -1` means no active tab (zero tabs).

---

## `openOrFocus`

```kotlin
fun openOrFocus(path: Path, text: String): TabBook
```

If a tab with the same path already exists, just activates it; otherwise appends a new tab and activates the last. Prevents duplicate tabs for the same file.

---

## `close` / `closeActive`

```kotlin
fun close(index: Int): TabBook
fun closeActive(): TabBook
```

Removes the tab and adjusts `activeIndex`.

| Close position | New active index |
|---|---|
| `index < activeIndex` | `activeIndex - 1` |
| `index == activeIndex` | `index.coerceAtMost(lastIndex)` (next tab or last) |
| `index > activeIndex` | Unchanged |

Closing the last tab returns an empty `TabBook()`.

---

## `updateActive`

```kotlin
fun updateActive(text: String, caret: Int): TabBook
```

Updates `text` / `caret` on the active tab. Returns itself when nothing changed (avoids needless recomposition).

---

## `pushHistoryOnActive` / `undoOnActive` / `redoOnActive`

```kotlin
fun pushHistoryOnActive(prev: EditSnapshot): TabBook
fun undoOnActive(current: EditSnapshot): Pair<TabBook, EditSnapshot>?
fun redoOnActive(current: EditSnapshot): Pair<TabBook, EditSnapshot>?
```

Wrappers that apply `EditHistory` operations to the active tab and re-stitch the result. On success, undo/redo returns `(new TabBook, snapshot to restore)`.

---

## `markActiveSaved`

```kotlin
fun markActiveSaved(): TabBook
```

Sets `savedText = text` on the active tab → flips `dirty` to `false`. Called after `Ctrl+S`.

---

## `move`

```kotlin
fun move(from: Int, to: Int): TabBook
```

Drag-reorder. Adjusts `activeIndex` accordingly.

| Move case | New active index |
|---|---|
| The active tab itself was moved | `to` |
| Active tab is inside the swept range | Shifted by one |
| Otherwise | Unchanged |

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.Main` `book: TabBook` | Window state |
| `page.app.TabBar` | Renders chips from `book.tabs` / `activeIndex` |
| `Ctrl+O` / `Ctrl+W` / `Ctrl+S` handlers | `openOrFocus` / `closeActive` / `markActiveSaved` |

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
