# LineMove

> 한국어: [line_move.md](https://monkshark.github.io/page-ide/#modules/editor/line_move.md)

> `page/editor/src/main/kotlin/page/editor/LineMove.kt` — Line move / duplicate

Core logic for Alt+↑ / Alt+↓ (and future Shift+Alt+↑·↓). Treats the entire selection as one block and swaps it with the line above/below.

---

## `moveUp` / `moveDown`

```kotlin
fun moveUp(edit: TextEdit): TextEdit?
fun moveDown(edit: TextEdit): TextEdit?
```

Swaps the selected line block with the line directly above/below. The selection follows along (feels like dragging code with you).

| Boundary case | Return |
|---|---|
| `moveUp` and the first line is already row 0 | `null` (caller ignores) |
| `moveDown` and the last line is EOF | `null` |

---

## `duplicateUp` / `duplicateDown`

```kotlin
fun duplicateUp(edit: TextEdit): TextEdit
fun duplicateDown(edit: TextEdit): TextEdit
```

Duplicates the selected block above or below.

| Function | Behavior |
|---|---|
| `duplicateUp` | Copy goes above, caret/selection stays on the original (now below) |
| `duplicateDown` | Copy goes below, caret/selection moves to the copy (now below) |

Uses `lastEnd - firstStart + 1` (block length including the trailing newline) as the caret offset.

---

## Line boundaries

```kotlin
private fun lineStart(text: String, offset: Int): Int
private fun lineEnd(text: String, offset: Int): Int
```

Boundaries are determined by `\n` only. On CRLF inputs, `\r` rides along as part of the line end (no special handling).

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.EditorPanel` `onPreviewKeyEvent` | Alt+Up → `moveUp`, Alt+Down → `moveDown` |

Shift+Alt+Up / Down → `duplicateUp` / `duplicateDown` wiring is planned.

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
