# TextBuffer / LineCol

> 한국어: [text_buffer.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/text_buffer.md)

> `page/editor/src/main/kotlin/page/editor/TextBuffer.kt` — `StringBuilder` wrapper + line/column coordinates

Provides `offset` ↔ `(line, col)` conversion and line-level access. Today, the line-number gutter, status bar, and current-line highlight in `EditorPanel` all read from the same index.

---

## `LineCol`

```kotlin
data class LineCol(val line: Int, val col: Int)
```

Line/column coordinate. Both 0-indexed (`(0, 0)` = first char of the first line).

---

## Construction

```kotlin
class TextBuffer(initial: String = "")
```

Wraps a `StringBuilder`. An empty buffer (`length = 0`) still has `lineCount = 1` (one empty line).

---

## Methods

| Method | Behavior |
|---|---|
| `length` / `lineCount` / `text()` | Length, line count, full string snapshot |
| `lineAt(index)` | Text of the line (excluding newline). Out-of-range → `IllegalArgumentException` |
| `insert(offset, text)` | Insert at offset |
| `delete(start, end)` | Delete range (`end` exclusive) |
| `insertAt(line, col, text)` | Line/col-coordinate insert (internally converts via `offsetOf`) |
| `deleteAt(startLine, startCol, endLine, endCol)` | Line/col-coordinate delete |
| `offsetOf(line, col)` | Coord → offset |
| `lineColOf(offset)` | Offset → coord |

Every mutating method has `require` bounds checks — bad arguments throw immediately so caller bugs surface fast.

---

## Line offset computation

```kotlin
private fun lineStartOffset(line: Int): Int
private fun lineEndOffset(line: Int): Int
```

Counts `\n` from the start each time. *O(N)* on large files, so a cached line index would be needed under heavy use — today `EditorPanel`'s `remember(value.text)` builds it once per text snapshot, which is enough.

---

## Non-goals

- Incremental update / rope / piece table — keeps direct compatibility with `BasicTextField`'s `String`
- Multi-caret / multi-selection coordinates — current `TextEdit` carries a single selection pair

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.EditorPanel` | `remember(value.text) { TextBuffer(...) }` builds the line index once and shares it |
| `LineNumberGutter` | `lineCount` for the gutter row count |
| `EditorStatusBar` | `lineColOf(caret)` for the row/col indicator |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
