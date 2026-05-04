# LineNumberGutter

> 한국어: [line_number_gutter.md](https://monkshark.github.io/PAGE_IDE/#modules/app/line_number_gutter.md)

> `page/app/src/main/kotlin/page/app/LineNumberGutter.kt` — Editor's left-side line numbers

The gutter sitting next to the `EditorPanel` body. The current line is bright, every other line is muted.

---

## Signature

```kotlin
@Composable
internal fun LineNumberGutter(
    lineCount: Int,
    currentLine: Int,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
)
```

| Parameter | Meaning |
|---|---|
| `lineCount` | Number of rows to render (`TextBuffer.lineCount`) |
| `currentLine` | Line under the caret (`TextBuffer.lineColOf(caret).line`) — used for the highlight |
| `textStyle` | Same font / line-height as the body — passing the body style verbatim keeps gutter rows pixel-aligned with body rows |

---

## Colors

| Line | Color |
|---|---|
| `line == currentLine` | `colorScheme.onBackground` (highlight) |
| Otherwise | `colorScheme.onSurfaceVariant` (muted) |

---

## Layout

`fillMaxWidth()` + `TextAlign.End` keeps the digits right-aligned as the count grows. The column uses `IntrinsicSize.Max`, so width is determined by the widest number (`lineCount`).

The 16dp top/bottom padding must match the body's top padding so that gutter rows line up with body rows.

---

## Non-goals

- Click-to-navigate on line number — caret movement still happens via body clicks.
- Folding / breakpoint marks — added when the gutter grows into a real column.

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.EditorPanel` | `Row { LineNumberGutter(...); editorBody }` left column |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
