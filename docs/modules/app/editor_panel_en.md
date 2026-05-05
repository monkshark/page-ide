# EditorPanel

> 한국어: [editor_panel.md](https://monkshark.github.io/PAGE_IDE/#modules/app/editor_panel.md)

> `page/app/src/main/kotlin/page/app/EditorPanel.kt` — Editor body

A single `BasicTextField` with a line-number gutter, token coloring, match highlights, bracket match, current-line background, and a status bar layered on top.

---

## Signature

```kotlin
@Composable
fun EditorPanel(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    activePath: Path?,
    lexer: SyntaxLexer?,
    search: SearchState?,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    pushHistory: (EditSnapshot) -> Unit,
    modifier: Modifier = Modifier,
)
```

| Parameter | Meaning |
|---|---|
| `value` | Text + selection — vanilla Compose `TextFieldValue` |
| `lexer` | `null` ⇒ no token coloring (body tone only) |
| `search` | Drives match / active-match highlights |
| `pushHistory(prev)` | Pushes a pre-edit snapshot — actual undo/redo dispatch lives in `Main`'s `doUndo` / `doRedo` |

---

## TextBuffer & tokens

```kotlin
val buffer = remember(value.text) { TextBuffer(value.text) }
val tokens = remember(value.text, lexer) { lexer?.tokenize(value.text).orEmpty() }
```

`buffer` and `tokens` are both keyed on `value.text` — recomputed only when text changes. The line-number gutter, status bar, and current-line highlight all read from the same `buffer`.

---

## Bracket match

```kotlin
val bracketMatch = remember(value.text, value.selection.start, value.selection.end) {
    if (value.selection.collapsed) BracketMatch.find(value.text, value.selection.start) else null
}
```

Only matches when the selection is collapsed (i.e., a single caret). During a selection it stays `null` to avoid visual noise.

---

## Current-line background

```kotlin
val currentLineBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
.drawBehind { ... }
```

A thin rectangle at the y range of `buffer.lineColOf(caret).line`. Painted with `drawBehind` so the color sits under the glyphs.

---

## Scroll lock after focus

```kotlin
when focusGainVersion ↑, LaunchedEffect(focusGainVersion) {
    delay(250)
    scrollState.scrollTo(scrollState.value, MutatePriority.PreventUserInput)
}
```

Right after a tab switch, `BasicTextField` likes to drag the caret into view. We pin the scroll position once during the first 250ms to ignore that. `MutatePriority.PreventUserInput` means real user scroll still wins.

---

## `onValueChange` chain

```kotlin
val after = AutoClose.apply(value, next)
val unindented = Indent.maybeUnindentClosingBrace(after)
val final = Indent.maybeApplyEnter(unindented)
onValueChange(final)
```

Each keystroke runs through three post-processors: `AutoClose` for matched brackets/quotes, `Indent` for closing-brace alignment and Enter-driven auto-indent.

---

## Key handling (`onPreviewKeyEvent`)

| Key | Action |
|---|---|
| `Alt+Up/Down` | `LineMove.moveUp/moveDown` |
| `Alt+Shift+Up/Down` | `duplicateUp/duplicateDown` |
| `Tab` | Inside a Markdown fence ⇒ `handleLiteralTab`; otherwise indent |
| `Shift+Tab` | `handleShiftTab` (outdent) |
| `Enter` | `handleEnter` (auto-indent) |
| `Backspace` | `handleBackspace`; if it returns `null`, default behavior runs |

`MarkdownFence.isInsideFence` decides the `Tab` branch — code blocks need a literal tab so the WYSIWYG output stays intact.

---

## Mouse clicks (double / triple)

```kotlin
.pointerInput(Unit) {
    awaitPointerEventScope {
        ... only Press events on PointerEventPass.Final
        clickCount = if (now - lastClickTime < 400 && close && clickCount < 3) clickCount + 1 else 1
    }
}
```

`onTextLayout` captures the `TextLayoutResult`; `getOffsetForPosition` converts the click coordinates to a text offset.

| Click | Action |
|---|---|
| Double | `WordBoundary.wordRangeAt(text, offset)` — selects the same-class run (no-op on whitespace / newline) |
| Triple | `WordBoundary.lineRangeAt(text, offset)` — line start to just before `\n` |

`PointerEventPass.Final` runs after `BasicTextField`'s built-in pointer logic, so we override its result. The sequence only counts within 400 ms and 8 px; outside that the counter resets.

---

## `CombinedHighlightTransformation`

A single `VisualTransformation` paints three layers in order:

1. Token colors (`colorFor(kind)`; `PUNCT` returns `null` to keep body color).
2. Match backgrounds — active match vs. regular match.
3. Bracket-match background.

`OffsetMapping.Identity` — character count never changes, so the identity mapping is enough.

---

## `EditorStatusBar`

A thin bottom row: `Ln {line+1}, Col {col+1}` · line count · char count. The numbers come straight from `buffer.lineColOf(caret)`.

---

## Font

| Property | Value |
|---|---|
| Family | `EditorFontFamily` |
| Size | `14sp` |
| Line height | `20sp` |
| Line alignment | `LineHeightStyle(Center, Trim.None)` |

`Trim.None` keeps the same line height on the first/last line — perfect alignment with the gutter.

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.Main`'s body slot | Pipes the active tab's text / lexer / search through |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
