# MarkdownFence

> 한국어: [markdown_fence.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/markdown_fence.md)

> `page/editor/src/main/kotlin/page/editor/MarkdownFence.kt` — Detects whether the caret is inside a code fence

Inside Markdown ```` ``` ```` / `~~~` fences, behaviors like `AutoClose` / `Indent` should be disabled — typing prose-style code samples gets messy otherwise. This file provides the single detection function.

---

## `isInsideFence`

```kotlin
fun isInsideFence(text: String, caret: Int): Boolean
```

Walks line by line from the start, tracking fence state. Returns the state on the line containing the caret.

| Case | Return |
|---|---|
| Caret reached while outside a fence | `false` |
| Caret reached while inside a fence (excluding the open/close lines themselves) | `true` |
| Reached EOF still inside a fence | The last `inFence` value |

If the caret lands on a *fence boundary line* (the line with the opening/closing chars), the result is `false` — Markdown semantics treat that line as *outside* the fence.

---

## `parseFenceLine`

```kotlin
private fun parseFenceLine(text: String, lineStart: Int, lineEnd: Int): Pair<Char, Int>?
```

Follows CommonMark's fence rules.

| Rule | Value |
|---|---|
| Leading spaces | Up to 3 |
| Fence char | `` ` `` or `~` |
| Fence length | 3 or more in a row |

Remembers the opening fence's *char* and *length*; a closer must match the char and be at least as long.

---

## Non-goals

- Inline code (\`...\`) — single-line backtick spans not handled
- Indented code blocks (4+ space indent) — only fenced blocks are recognized

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.EditorPanel` (in `.md` files) | Guard to disable `AutoClose` / `Indent` |

> The actual callsite in `EditorPanel` is planned — the module itself is ready.

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
