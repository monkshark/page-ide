# SearchState

> 한국어: [search_state.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/search_state.md)

> `page/editor/src/main/kotlin/page/editor/SearchState.kt` — Search / replace state

Immutable data class holding the entire search-bar state. `null` means the bar is closed; an instance means open.

---

## Fields

```kotlin
data class SearchState(
    val query: String = "",
    val replace: String = "",
    val replaceVisible: Boolean = false,
    val caseSensitive: Boolean = false,
    val matches: List<IntRange> = emptyList(),
    val activeMatchIndex: Int = -1,
)
```

| Field | Meaning |
|---|---|
| `query` | Search query |
| `replace` | Replace text |
| `replaceVisible` | When `true`, the second row (replace input + buttons) is shown |
| `caseSensitive` | Case-sensitivity toggle (the `Aa` chip) |
| `matches` | Match ranges found in the body |
| `activeMatchIndex` | Index of the currently highlighted match (used via `matches.getOrNull`) |

---

## Derived properties

```kotlin
val isActive: Boolean get() = query.isNotEmpty()
val active: IntRange? get() = matches.getOrNull(activeMatchIndex)
```

`isActive` means *a query is present* (the bar can be open with an empty query). `active` is the range of the active match, or `null`.

---

## Transform methods (with*)

| Method | Behavior |
|---|---|
| `withQuery(text, query)` | Updates the query → re-finds matches → resets active index to 0 |
| `withReplace(value)` | Updates only the replace text |
| `withReplaceVisible(value)` | Shows/hides the replace row |
| `withCaseSensitive(text, value)` | Toggles, then re-runs the search with the same query |
| `retarget(text)` | When body changes, re-finds matches and picks the nearest match to the previous active position as the new active |

`retarget` is the load-bearing one — it preserves the *position the user was looking at* across edits and replacements.

---

## Cursor movement

```kotlin
fun next(): SearchState
fun prev(): SearchState
```

Cycles `activeMatchIndex` (last → 0, 0 → last). With no matches, returns itself.

---

## `findAll` (private)

```kotlin
private fun findAll(text: String, query: String, caseSensitive: Boolean): List<IntRange>
```

Uses `String.regionMatches` to scan; collects *non-overlapping* matches by skipping forward by `query.length` after a hit (so searching `aa` in `aaa` yields one match `[0..1]`, not both `[0..1]`, `[1..2]`).

Case folding is handled in one line via `ignoreCase = !caseSensitive`. No regex.

---

## Non-goals

- Regex search — planned (separate toggle)
- Whole-word search — planned (toggle)
- Multi-line / multi-cursor search — not supported

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.Main` `search: SearchState?` | Window state |
| `page.app.SearchBar` | Every input goes through a `SearchState` transform |
| `CombinedHighlightTransformation` | Uses `matches` + `activeMatchIndex` to apply match / active-match backgrounds |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
