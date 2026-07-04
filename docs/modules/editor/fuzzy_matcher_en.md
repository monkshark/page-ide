# FuzzyMatcher

> 한국어: [fuzzy_matcher.md](https://monkshark.github.io/page-ide/#modules/editor/fuzzy_matcher.md)

> `page/editor/src/main/kotlin/page/editor/FuzzyMatcher.kt` — Subsequence fuzzy match + score

Pure helper used by `Ctrl+P` Quick Open to rank filename/path candidates. If every char of the query appears in the target in order (not necessarily consecutively) it matches; otherwise `null`.

---

## `match`

```kotlin
fun match(query: String, target: String): Match?
```

| Input | Result |
|---|---|
| `query` empty | `Match(0, [])` |
| `target.length < query.length` | `null` |
| Not a subsequence | `null` |
| Match | `Match(score, indices)` — `indices[i]` is where `query[i]` landed in `target` |

Case-insensitive — comparison is done in `lowercase()`, bonuses peek at the original char.

---

## Score bonuses

Consecutive matches / word boundaries / start-of-string combine into the sort key.

| Bonus | Condition |
|---|---|
| `+15 + streak·5` | Char immediately after the previous match (consecutive streak) |
| `+30` | Word boundary start (`/`, `\`, `.`, `_`, `-`, ` `, or lower→upper camelCase break) |
| `+20` | Index 0 of target |
| `+5` | Same case as the query char |
| `+1` | Per match (baseline) |
| `-(target.length - query.length)` | Length penalty (shorter targets edge ahead) |

Same flavor as IntelliJ / VS Code "search anything" — the exact weights don't matter; what does is the qualitative ranking: consecutive beats scattered, word boundary beats mid-word.

---

## `Match`

```kotlin
data class Match(val score: Int, val indices: IntArray)
```

`indices` are reused by the UI for highlighting — `QuickOpenDialog` just turns them into a set and paints those glyph positions in the accent color.

`equals`/`hashCode` are overridden to use `IntArray.contentEquals` since the `data class` defaults compare by reference, which breaks tests.

---

## Used by

| Location | Role |
|---|---|
| `page.editor.QuickOpen.rank` | Sort the file index by query and trim |
| `page.app.QuickOpenDialog` | Use `Match.indices` to highlight matched glyphs |

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
