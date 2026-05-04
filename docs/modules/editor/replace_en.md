# Replace

> 한국어: [replace.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/replace.md)

> `page/editor/src/main/kotlin/page/editor/Replace.kt` — Search match replacement

Takes match ranges produced by `SearchState.matches` and replaces them in the text. Finding matches lives in `SearchState`; replacement only is here.

---

## `Result`

```kotlin
data class Result(val text: String, val caret: Int, val replacedCount: Int)
```

Replacement result.

| Field | Meaning |
|---|---|
| `text` | New text after replacement |
| `caret` | Caret position after replacement |
| `replacedCount` | Actual number of replaced matches (0 is possible) |

---

## `applyCurrent`

```kotlin
fun applyCurrent(text: String, range: IntRange, replacement: String): Result
```

Replaces a single match (`range`) with `replacement`. The caret lands at the end of the replacement (`start + replacement.length`) — a natural spot to step to the next match.

`replacedCount` is always `1`.

---

## `applyAll`

```kotlin
fun applyAll(text: String, matches: List<IntRange>, replacement: String): Result
```

Replaces every match in one pass. Uses `StringBuilder` to splice the unchanged segments between matches, producing the new text in a single pass.

If `matches` is empty, returns the original text with `replacedCount = 0`. The caret is reset to `0` — after a *replace all*, the caret position is ambiguous, so this keeps it simple.

Assumes `matches` is *non-overlapping* and *ascending* — `SearchState.findAll` already guarantees that.

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.SearchBar` "Replace" | `applyCurrent(text, search.active, replace)` |
| `page.app.SearchBar` "Replace All" | `applyAll(text, search.matches, replace)` |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
