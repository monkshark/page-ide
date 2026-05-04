# BracketMatch

> 한국어: [bracket_match.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/bracket_match.md)

> `page/editor/src/main/kotlin/page/editor/BracketMatch.kt` — Find the matching bracket near the caret

For an opener, scan right with a depth counter; for a closer, scan left. The data source for the bracket-match highlight.

---

## `find`

```kotlin
fun find(text: String, caret: Int): Pair<Int, Int>?
```

Checks the char immediately before the caret first, then the char immediately after. Returns `null` if neither is a bracket. The pair is normalized as `(opener position, closer position)`.

| Char at caret | Action |
|---|---|
| Opener (`(` `[` `{`) | Scan right, depth +1 on same kind, −1 on matching kind, return at 0 |
| Closer (`)` `]` `}`) | Same logic, scan left |
| Other | Try the next candidate (char after caret) |

---

## Depth scan

```kotlin
private fun scan(text: String, from: Int, same: Char, target: Char, dir: Int): Int?
```

Starts at `depth = 1`. +1 on same char, −1 on the matching kind. Returns the index when depth hits 0. Returns `null` if the end is reached without a match.

Brackets inside strings/comments are counted — character match only, no token awareness. A trade-off: cheap and accurate enough.

---

## Pair definitions

```kotlin
private val openers = mapOf('(' to ')', '[' to ']', '{' to '}')
private val closers = openers.entries.associate { (k, v) -> v to k }
```

Quotes (`"`, `'`) are intentionally excluded — depth is ambiguous when the same char is both opener and closer.

---

## Usage

| Location | Purpose |
|---|---|
| `CombinedHighlightTransformation` in `page.app.EditorPanel` | Adds a background color to the matched pair around the caret |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
