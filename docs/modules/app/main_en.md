# Main

> 한국어: [main.md](https://monkshark.github.io/PAGE_IDE/#modules/app/main.md)

> `page/app/src/main/kotlin/page/app/Main.kt` — `application { ... }` entry point + window state

The top-level that wires window / tabs / sidebar / search / save dialog together. `TabBook`, `TextFieldValue`, `rootDir`, `expanded`, `sidebarWidth`, `search`, `pendingClose` all live here.

---

## Window state

```kotlin
var book by remember { mutableStateOf(TabBook()) }
var editorValue by remember { mutableStateOf(TextFieldValue("")) }
var rootDir by remember { mutableStateOf<Path?>(null) }
var expanded by remember { mutableStateOf<Set<Path>>(emptySet()) }
var sidebarWidth by remember { mutableStateOf(260.dp) }
var search by remember { mutableStateOf<SearchState?>(null) }
var pendingClose by remember { mutableStateOf<PendingClose?>(null) }
```

| Field | Meaning |
|---|---|
| `book` | All tabs + active index (immutable — every mutation produces a new instance) |
| `editorValue` | Active tab's body / caret (Compose `TextFieldValue`). Resyncs on tab switch |
| `rootDir` / `expanded` | Sidebar tree — root + the set of expanded directories |
| `sidebarWidth` | Adjusted by the resize handle. Defaults to `260.dp` |
| `search` | `null` hides the search bar |
| `pendingClose` | A dirty-close request. Non-null shows the dialog |

---

## Sync between active tab and editorValue

```kotlin
LaunchedEffect(book.activeIndex, book.tabs.size) {
    val active = book.tabs.getOrNull(book.activeIndex)
    editorValue = active?.let {
        TextFieldValue(it.text, TextRange(it.caret))
    } ?: TextFieldValue("")
    search = search?.retargetedFor(active?.text)
}
```

On tab switch / open, swap `editorValue` to the new tab's body and caret. If search is open, recompute matches against the new body.

---

## Key handlers (summary)

| Name | Role |
|---|---|
| `openInTab(path, text)` | `book = book.openOrFocus(path, text)` — same-path tab is just activated |
| `openFile()` | `FileDialogs.open` → read text → `openInTab` |
| `openFolder()` | `FileDialogs.openDirectory` → set `rootDir` |
| `saveFile()` | Write active tab text to disk and `book.markActiveSaved()` |
| `requestCloseTab(i)` / `closeActiveTab()` | If dirty, `pendingClose = PendingClose.Tab(i)`; else close immediately |
| `requestExit()` | If any tab is dirty, `pendingClose = PendingClose.App`; else `exitApplication()` |
| `openSearch()` / `openReplace()` / `closeSearch()` | Toggle `search` |
| `onSearchNext` / `onSearchPrev` | Move match + `moveCaretToActiveMatch` |
| `onReplace` / `onReplaceAll` | Run `Replace.applyCurrent` / `applyAll` and update body |
| `doUndo` / `doRedo` | Restore `editorValue` from `book.undoOnActive(...)` / `redoOnActive(...)` |

---

## `handleShortcut`

```kotlin
private fun handleShortcut(e: KeyEvent): Boolean
```

Window-level shortcut handler. Keys that should defer to the search bar pass through with `if (search != null) false`.

| Key | Action |
|---|---|
| `Ctrl+O` | `openFile` |
| `Ctrl+Shift+O` | `openFolder` |
| `Ctrl+S` | `saveFile` |
| `Ctrl+W` | `closeActiveTab` |
| `Ctrl+F` | `openSearch` |
| `Ctrl+R` | `openReplace` |
| `Ctrl+Z` | Pass through if search is open, else `doUndo` |
| `Ctrl+Shift+Z` / `Ctrl+Y` | `doRedo` |
| `Esc` | `closeSearch` (only when search is open) |

---

## `windowTitle`

```kotlin
private fun windowTitle(path: Path?): String =
    "${path?.fileName ?: "Untitled"} — ${PageIdentity.NAME}"
```

Active tab's filename + ` — PAGE`; falls back to `Untitled` when there are no tabs. `PageIdentity.NAME` keeps the brand string in one place rather than duplicated here.

---

## Private composables

| Name | Role |
|---|---|
| `Shell` | Horizontal split of sidebar + body + status bar |
| `TitleBar` | Top of the undecorated window — draggable area + menu buttons |
| `ResizeHandle` | Drag handle that resizes the sidebar |

---

## Usage

This file is the entry point for `page:app`. It packages as `mainClassName = "page.app.MainKt"`.

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
