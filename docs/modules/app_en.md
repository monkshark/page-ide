# app

> `page/app/` — composition layer / entry point, Compose panels, shortcuts, dialogs

The place that wires `page:editor`'s pure logic and `page:ui`'s design tokens into actual screens, key events, and file dialogs. Depends on every other module, but no other module depends on `app` (it's the leaf of the dependency graph)

> 한국어: [app.md](https://monkshark.github.io/PAGE_IDE/#modules/app.md)

---

## Dependencies

| Kind | Content |
|---|---|
| External | Compose Multiplatform Desktop, Compose Material3, Skia (SVG / image decode), Swing (`JFileChooser`) |
| Internal | `page:core`, `page:editor`, `page:ui` |

The most dependencies, since this is the composition layer. The hard rule is the inverse: *no module may depend on `app`* — once that flips, the entry point becomes ambiguous

---

## Entry point

### `Main.kt`

```kotlin
fun main() = application { ... }
```

A single `Window` inside Compose's `application`. The window holds the following state at the top level.

| State | Type | Role |
|---|---|---|
| `book` | `TabBook` | Open tabs and active index |
| `editorValue` | `TextFieldValue` | Current editor text + selection |
| `rootDir` | `Path?` | Sidebar root folder |
| `expanded` | `Set<Path>` | Expanded directories in sidebar |
| `sidebarWidth` | `Dp` | Sidebar width (drag to resize) |
| `search` | `SearchState?` | Search / replace state (`null` = bar closed) |
| `pendingClose` | `PendingClose?` | Trigger for the unsaved-changes dialog |

When the active tab switches, `LaunchedEffect(book.activeIndex, book.tabs.size)` syncs `editorValue` to the new tab's `text` / `caret`. A single `BasicTextField` instance is reused across tabs, with its *value* swapped — tab switching stays cheap

### Shortcut matrix

`handleShortcut` is wired to both `onPreviewKeyEvent` and `onKeyEvent`

| Key | Action |
|---|---|
| Ctrl+O | Open file (`FileDialogs.open`) |
| Ctrl+Shift+O | Open folder (`FileDialogs.openDirectory`) |
| Ctrl+S | Save (`FileDocument.save` → `markActiveSaved`) |
| Ctrl+W | Close active tab (shows `UnsavedChangesDialog` if dirty) |
| Ctrl+F | Open search bar |
| Ctrl+R | Open search-replace bar |
| Ctrl+Z | Undo (passes through when search bar is focused → input field's own undo wins) |
| Ctrl+Shift+Z / Ctrl+Y | Redo |
| Esc | Close search bar |

The pass-through branch when the search bar is open is load-bearing — without it, deleting one character of the search query would rewind the entire body ([devlog #4](https://monkshark.github.io/p/page-ide-undo-per-file/) for the full story)

---

## Panels / Composables

### `EditorPanel`

```kotlin
@Composable
fun EditorPanel(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    search: SearchState?,
    lexer: SyntaxLexer?,
    activePath: Path?,
    ...
)
```

The body editor. Wraps a `BasicTextField` and routes every `onValueChange` through `AutoClose`, `Indent.maybeUnindentClosingBrace`, and `Indent.maybeApplyEnter` in order. `onPreviewKeyEvent` intercepts Tab / Shift+Tab / Enter / Backspace / Alt+Up·Down and delegates to `Indent` / `LineMove`

A `TextBuffer` is kept via `remember(value.text)` — the line index is built once and shared by the line-number gutter, status bar, and current-line highlight

`CombinedHighlightTransformation` paints token colors, search-match backgrounds, and bracket-match backgrounds in a single pass (one `VisualTransformation` step keeps `BasicTextField`'s caret mapping intact)

### `FileTreePanel`

```kotlin
@Composable
fun FileTreePanel(
    root: Path?,
    expanded: Set<Path>,
    selectedFile: Path?,
    onToggle: (Path) -> Unit,
    onOpenFile: (Path) -> Unit,
    ...
)
```

The left sidebar. Renders `FileTree.listTree(root, expanded)` into a `LazyColumn`. Directories trigger `onToggle`, files trigger `onOpenFile`. When `root == null`, shows a "Press Ctrl+Shift+O" hint

### `TabBar`

```kotlin
@Composable
fun TabBar(
    book: TabBook,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
)
```

Top tab strip. Dirty tabs show a `●` dot instead of `×`, swapping to `×` on hover. Drag-reorder uses `awaitEachGesture` + `touchSlop` to distinguish a click from a drag — `onMove` only fires after slop is crossed. Wheel scroll is mapped to horizontal scroll

### `LineNumberGutter`

```kotlin
@Composable
internal fun LineNumberGutter(
    lineCount: Int,
    currentLine: Int,
    textStyle: TextStyle,
)
```

Left line-number gutter. `IntrinsicSize.Max` sizes the gutter to the widest number → the gutter doesn't jump around past 1000 lines. Current line uses `onBackground`, others use `onSurfaceVariant`

### `SearchBar`

```kotlin
@Composable
fun SearchBar(state: SearchState, ...)
```

Top search-replace bar. Two-row layout — row 1: query input + match counter (`1 / 12`) + case-sensitive toggle (`Aa`) + prev / next / close chips. Row 2 (replace mode only): replace input + "바꾸기" / "전부 바꾸기" buttons. Esc closes, Enter advances / replaces

The `onWindowShortcut(e)` delegate is the load-bearing line. Keys the input doesn't consume are forwarded to the window shortcut handler — global shortcuts like Ctrl+S keep working even while the search bar has focus

### `PreviewPanel`

```kotlin
@Composable
fun PreviewPanel(path: Path, kind: FileKind, ...)
```

Image / SVG preview. `FileKind.IMAGE` decodes via Skia `Image.makeFromEncoded`; `FileKind.SVG` loads via `SVGDOM`. Wheel-scroll zoom, +/− buttons, and label click resets to 100%. First entry uses 70% fit (downscale large images while preserving ratio). PNG / JPG / SVG all share the same `ImageViewer` so zoom / pan behavior stays consistent

### `UnsavedChangesDialog`

```kotlin
@Composable
internal fun UnsavedChangesDialog(
    fileNames: List<String>,
    isAppExit: Boolean,
    onSave / onDiscard / onCancel: () -> Unit,
)
```

Unsaved-changes confirmation dialog. An `undecorated` `DialogWindow` with a `WindowDraggableArea` for its custom drag area. Key bindings: `Y` = save, `N` = discard, `Esc` = cancel. Triggered by the main window's `onCloseRequest` — when `book.tabs.any { dirty }`, `pendingClose = PendingClose.App` and the dialog shows

---

## Helpers

### `FileDialogs`

```kotlin
object FileDialogs {
    fun open(parent: Frame): Path?
    fun saveAs(parent: Frame, suggested: String? = null): Path?
    fun openDirectory(parent: Frame): Path?
}
```

Swing `JFileChooser` wrapper. The simplest way to get an OS-native file dialog out of Compose Desktop. If we ever need true OS-native dialogs (Windows COM, macOS NSOpenPanel), they'll slot in behind the same signatures

### `PendingClose`

```kotlin
internal sealed interface PendingClose {
    data class Tab(val index: Int) : PendingClose
    data object App : PendingClose
}
```

The kind of `UnsavedChangesDialog` trigger. `Tab(idx)` closes a specific tab; `App` exits the app — the dialog text ("Save?" vs "Discard and exit?") and the post-save / post-discard branches are decided by the variant

---

## Screen layout

```
Window
├── TitleBar (PAGE · v0.1.0 · active file path)
└── Row
    ├── FileTreePanel (sidebar)
    ├── ResizeHandle (drag to resize sidebar)
    └── Column
        ├── TabBar
        ├── EditorPanel | PreviewPanel  ← branched by active tab's FileKind
        │   └── (inside EditorPanel) SearchBar + LineNumberGutter + BasicTextField + StatusBar
        └── (UnsavedChangesDialog lives in a separate DialogWindow)
```

---

## Planned

| Panel | Module |
|---|---|
| Pair chat panel | `page:pair` (LLMProvider adapters) |
| Atlas code graph | `page:atlas` |
| Echo timeline | `page:echo` |
| Git diff / staging | `page:git` |
| Terminal / build output | `page:runtime` |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
- [Architecture](https://monkshark.github.io/PAGE_IDE/#guides/architecture_en.md)
