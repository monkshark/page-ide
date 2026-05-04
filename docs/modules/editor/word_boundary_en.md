# WordBoundary

> 한국어: [word_boundary.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/word_boundary.md)

> `page/editor/src/main/kotlin/page/editor/WordBoundary.kt` — Word-wise caret motion / deletion

Boundary math for `Ctrl+←/→` (move), `Ctrl+Shift+←/→` (select), `Ctrl+Backspace/Delete` (word delete). VS Code-style classes — word (alnum + `_`) / punctuation / horizontal space / newline.

---

## `nextBoundary`

```kotlin
fun nextBoundary(text: String, offset: Int): Int
```

Returns the next word boundary to the right of `offset`.

| Case | Behavior |
|---|---|
| Horizontal space (` `, `\t`) | Skipped first |
| Next char is `\n` | If the caret was directly on it, advances one (jumps the newline). If reached after skipping spaces, stops just before |
| Next char is word/punct | Consumes the run of the same class |

---

## `prevBoundary`

```kotlin
fun prevBoundary(text: String, offset: Int): Int
```

Mirror image. Skips trailing horizontal space backward, then groups one class run.

---

## `deleteWordBackward` / `deleteWordForward`

```kotlin
fun deleteWordBackward(edit: TextEdit): TextEdit?
fun deleteWordForward(edit: TextEdit): TextEdit?
```

Only act on a single caret (`selectionStart == selectionEnd`). With a selection they return `null` so the caller falls back to default Backspace/Delete (delete selection).

| Input | Result |
|---|---|
| `hello world|` + `Ctrl+Backspace` | `hello |` |
| `|hello world` + `Ctrl+Delete` | `| world` |

`Ctrl+Backspace` swallowing trailing spaces is intentional — matches VS Code / IntelliJ.

---

## `CharClass`

```kotlin
private enum class CharClass { WORD, PUNCT }
```

Internal classification. Horizontal space and newline are handled separately, so they're not enum members.

| Class | Chars |
|---|---|
| `WORD` | `Char.isLetterOrDigit()` or `_` (so a camelCase identifier sticks together) |
| `PUNCT` | Everything else |

---

## Usage

| Location | Keys |
|---|---|
| `page.app.EditorPanel` `handleWordShortcut` | `Ctrl+←/→`, `Ctrl+Shift+←/→`, `Ctrl+Backspace`, `Ctrl+Delete` |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
