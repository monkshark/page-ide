# FoldRegions

> 한국어: [fold_regions.md](https://monkshark.github.io/page-ide/#modules/editor/fold_regions.md)
>
> `page/editor/src/main/kotlin/page/editor/FoldRegions.kt` — Code-fold region detection + display-mapping math

Finds foldable `{`/`}` blocks and produces the segments / offset mappings / click hit-test used to collapse a folded body into a ` ... }` placeholder.

---

## Types

```kotlin
data class Region(val startLine: Int, val endLine: Int)
data class Segment(val origStart: Int, val origEnd: Int, val replacement: String)
```

| Type | Meaning |
|---|---|
| `Region` | Folding candidate — 0-indexed line numbers of the opening `{` and closing `}` |
| `Segment` | Display-substitution unit — replaces `text[origStart, origEnd)` with `replacement` |

---

## `detect`

```kotlin
fun detect(text: String): List<Region>
```

Description: Pulls every foldable `{`/`}` block out of the body.

Flow:

1. On `{` push the current line onto a stack:
   ```kotlin
   c == '{' -> { stack.addLast(line); i++ }
   ```

2. On `}` pop the matching line; emit a `Region` only when it lives on a different line:
   ```kotlin
   val openLine = stack.removeLastOrNull()
   if (openLine != null && openLine < line) {
       regions.add(Region(openLine, line))
   }
   ```
   Same-line `{ ... }` is dropped — folding it has no value.

3. Braces inside the following contexts are ignored during the scan:
   - String literals (`"..."`, `'...'`) — backslash escape honored
   - Line comments (`// ...`)
   - Block comments (`/* ... */`) — newline counter is kept in sync

Returns: `List<Region>` sorted by `startLine`. Unmatched braces are dropped.

---

## `segmentsFor`

```kotlin
fun segmentsFor(text: String, foldedRegions: Collection<Region>): List<Segment>
```

Description: Converts folded regions into segments ready for `VisualTransformation`.

Flow:

1. Build the line-start offsets:
   ```kotlin
   val lineStarts = mutableListOf(0)
   for (i in text.indices) if (text[i] == '\n') lineStarts.add(i + 1)
   ```

2. Compute the cut range per region:
   - `origStart` = `\n` index at end of the start line (or `text.length`)
   - `origEnd` = one past the `\n` after the end line (or `text.length`) — the entire end line is consumed.

3. Pick the placeholder:
   ```kotlin
   val replacement = if (hasTrailingNewline) " ... }\n" else " ... }"
   ```
   The closing `}` is included so the start line's `{` plus the placeholder visually reads as `{ ... }`.

4. Nested folds collapse into the outermost — once an outer fold is in, the inner is already inside the cut range:
   ```kotlin
   if (origStart < lastConsumedEnd) continue
   ```

Returns: Non-overlapping `List<Segment>` sorted by `origStart`.

---

## `originalToTransformed` / `transformedToOriginal`

```kotlin
fun originalToTransformed(segments: List<Segment>, original: Int): Int
fun transformedToOriginal(segments: List<Segment>, transformed: Int): Int
```

Description: Bidirectional `OffsetMapping` — drops directly into a Compose `VisualTransformation`.

| Position | Mapping |
|---|---|
| Before a fold | Subtract the cumulative `savings` so far |
| Inside a fold (original) | Snap to the fold's start (`origStart`) |
| Left half of the placeholder (transformed) | Maps to `origStart` (just after `{`) |
| Right half of the placeholder (transformed) | Maps to `origEnd` (just after `}`) |
| After a fold | Subtract `savings` and continue checking |

Splitting the placeholder at its midpoint pins clicks on the `}` side to the row after the fold — that suppresses spurious bracket matching and lets a drag through the placeholder cleanly cover the whole folded block.

`savings` = the running total of `(origEnd - origStart) - replacement.length`.

---

## `foldedRegionAt`

```kotlin
fun foldedRegionAt(
    text: String,
    foldedRegions: Collection<Region>,
    transformedOffset: Int,
): Region?
```

Description: Returns the region whose placeholder `...` covers a given display offset — only the dots are click-active; `{`, surrounding spaces, and `}` miss so drag selection can start from them.

Flow:

1. Convert regions to segments and check which segment's `[dotsStart, dotsStart+3)` (the `...` substring) contains the click.
2. Match that segment's `origStart` to the first sorted region with `lineEnd(r.startLine) == origStart` (= the outermost match).

Returns: The matching `Region` when the click hits `...`; `null` for `{`, spaces, `}`, or anything outside the placeholder.

---

## Used by

| Location | Purpose |
|---|---|
| `page.app.EditorPanel` | `detect` → drives gutter toggles / `segmentsFor` + the mapping pair → folding inside `CombinedHighlightTransformation` / `foldedRegionAt` → unfold via body placeholder click |

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
