# SearchBar

> 한국어: [search_bar.md](https://monkshark.github.io/PAGE_IDE/#modules/app/search_bar.md)

> `page/app/src/main/kotlin/page/app/SearchBar.kt` — Editor's find/replace strip

Takes a `SearchState` and renders the query input, match counter, case toggle, prev/next, and (when expanded) the replace input + `Replace` / `Replace All`.

---

## Signature

```kotlin
@Composable
fun SearchBar(
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onToggleCase: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    onWindowShortcut: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
)
```

| Parameter | Meaning |
|---|---|
| `state` | Current search state — query / replace / matches / activeMatchIndex / replaceVisible |
| `onWindowShortcut` | Forwards keys the search bar doesn't handle to the parent window — keeps global shortcuts like `Ctrl+S` working |

---

## Layout

| Row | Contents |
|---|---|
| 1st | Query input · `n / total` counter · `Aa` case toggle · `<` prev · `>` next · `×` close |
| 2nd (optional) | Replace input · `바꾸기` (Replace) · `전부 바꾸기` (Replace All) |

The 2nd row only renders when `state.replaceVisible` is `true` — toggled by `Ctrl+R`.

---

## Auto focus

```kotlin
LaunchedEffect(state.replaceVisible) {
    if (state.replaceVisible) replaceFocus.requestFocus() else queryFocus.requestFocus()
}
```

Opening the replace row puts focus in the replace input; closing it returns focus to the query input.

---

## Key handling

| Field | Key | Action |
|---|---|---|
| Query | `Enter` | `onNext` |
| Query | `Shift+Enter` | `onPrev` |
| Query | `Esc` | `onClose` |
| Replace | `Enter` | `onReplace` |
| Replace | `Esc` | `onClose` |
| Both | other | Delegated via `onWindowShortcut(e)` |

The delegation is essential: shortcuts unrelated to search (e.g., `Ctrl+S`) must reach the window key handler unblocked.

---

## Helpers

| Name | Role |
|---|---|
| `InputBox` | Box + border for an input field (`220 × 24 dp`) |
| `CounterLabel` | Empty when query is empty, `0 matches` for none, else `n / total` |
| `ToggleChip` | `Aa` case toggle (primary tone when active) |
| `IconChip` | `<` `>` `×` (single glyph + hover bg) |
| `TextChip` | Replace / Replace All (text label + hover bg, `enabled = matches.isNotEmpty()`) |

---

## Usage

| Location | Purpose |
|---|---|
| `page.app.Main` shows it above the editor when `search != null` | Find/replace input |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
