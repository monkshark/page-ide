# AutoClose / TextEdit

> 한국어: [auto_close.md](https://monkshark.github.io/PAGE_IDE/#modules/editor/auto_close.md)

> `page/editor/src/main/kotlin/page/editor/AutoClose.kt` — Bracket / quote auto-close + edit unit

A pure function called from `onValueChange`. Takes the previous state (`old`) and the new state (`new`) and returns the transformed result.

---

## `TextEdit`

```kotlin
data class TextEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart,
) {
    constructor(text: String, caret: Int) : this(text, caret, caret)
    val caret: Int get() = selectionStart
}
```

Single edit unit. `selectionStart == selectionEnd` means caret only, no selection. `caret` aliases `selectionStart`.

---

## `AutoClose.apply`

```kotlin
fun apply(old: TextEdit, new: TextEdit): TextEdit
```

Tries three transformations in order. Falls through to `new` unchanged if none matches.

| Branch | Condition | Action |
|---|---|---|
| Wrap selection | `old` has a range, `new` inserts one char in its place | Wrap the selection with the matching closer (`(text)`, `"text"`) |
| Pair delete | Caret had matched pair `()` etc, backspace removed only the opener | Remove the closer too so no orphan remains |
| Auto-close | One char inserted, the char is an opener | Insert the closer next, place caret in between |

---

## Pair definitions

```kotlin
private val pairs = mapOf(
    '(' to ')',
    '[' to ']',
    '{' to '}',
    '"' to '"',
    '\'' to '\'',
)
```

To add new pairs (markdown ``` fences, Korean quotes), extend the map and tighten the guards.

---

## Guards

| Guard | Behavior |
|---|---|
| Next char is alnum/`_` | Skip auto-close (so typing `"` mid-identifier doesn't break `myVar"foo"`) |
| Char before quote is alnum/`_` | Skip auto-close (handles `don't`, `it's` in prose) |
| Typing a closer when caret is on the same closer | Don't insert; advance caret by one (overtype) |

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.EditorPanel` | First transform stage in `BasicTextField.onValueChange` |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
