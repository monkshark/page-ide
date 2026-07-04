# Indent

> 한국어: [indent.md](https://monkshark.github.io/page-ide/#modules/editor/indent.md)

> `page/editor/src/main/kotlin/page/editor/Indent.kt` — Indent / dedent / smart-Enter

A bundle of pure functions for Tab / Shift+Tab / Enter / Backspace and auto-alignment of `}` / `]` / `)`.

---

## Constants

| Constant | Value | Description |
|---|---|---|
| `TAB_UNIT` | `4` | One indent unit = 4 spaces |
| `TAB_SPACES` | `"    "` | Four-space literal |
| `indentTriggers` | `{`, `(`, `[`, `:` | If the char right before Enter is one of these, indent one level further |
| `matchingClosers` | `{→}`, `(→)`, `[→]` | Enter inside an empty pair triggers split indent |
| `unindentChars` | `}`, `]`, `)` | Typing one of these dedents the line by one step |

---

## `handleTab`

```kotlin
fun handleTab(edit: TextEdit): TextEdit
```

| State | Action |
|---|---|
| Caret only | Insert spaces up to the next 4-column boundary |
| Single-line selection | Add one `TAB_SPACES` at the start of the line (selection preserved) |
| Multi-line selection | Add `TAB_SPACES` at the start of every line |

---

## `handleShiftTab`

```kotlin
fun handleShiftTab(edit: TextEdit): TextEdit
```

Dedents every selected line by one step (up to `TAB_UNIT` spaces). If the line has fewer leading spaces, removes only what's there.

---

## `handleLiteralTab`

```kotlin
fun handleLiteralTab(edit: TextEdit): TextEdit
```

Inserts a literal `\t`. For files where the user actually wants a tab character (Makefile, TSV).

---

## `handleBackspace`

```kotlin
fun handleBackspace(edit: TextEdit): TextEdit?
```

Only acts when the chars before the caret are whitespace-only indent. Deletes back to the previous 4-column boundary in one step. Returns `null` (selection / start of line / non-space inside indent) so the caller falls back to the default backspace.

---

## `maybeApplyEnter`

```kotlin
fun maybeApplyEnter(old: TextEdit, new: TextEdit): TextEdit
```

Called from `onValueChange`. Verifies `new` is exactly `old` with a single `\n` inserted, then delegates to `handleEnter`. Acts as a guard so IME / Hangul composition / paste cases don't trigger accidentally.

---

## `handleEnter`

```kotlin
fun handleEnter(edit: TextEdit): TextEdit
```

Enter key core logic.

| Case | Action |
|---|---|
| Caret between matched pair (`{|}`, `[|]`, `(|)`) | Split into two lines — middle line gets extra indent, closer goes to its own line |
| Char before caret is in `indentTriggers` | Indent the next line one step further |
| Otherwise | Copy the previous line's leading whitespace |

---

## `maybeUnindentClosingBrace`

```kotlin
fun maybeUnindentClosingBrace(old: TextEdit, new: TextEdit): TextEdit
```

After typing `}` `]` `)`, if the entire line is whitespace + that one char, dedent by one (`TAB_UNIT`) so the closer aligns with the opener.

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.EditorPanel` `onValueChange` | Passes through `maybeUnindentClosingBrace`, `maybeApplyEnter` |
| `page.app.EditorPanel` `onPreviewKeyEvent` | Tab → `handleTab`, Shift+Tab → `handleShiftTab`, Backspace → `handleBackspace` |

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
