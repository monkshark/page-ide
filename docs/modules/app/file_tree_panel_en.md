# FileTreePanel

> 한국어: [file_tree_panel.md](https://monkshark.github.io/PAGE_IDE/#modules/app/file_tree_panel.md)

> `page/app/src/main/kotlin/page/app/FileTreePanel.kt` — Left sidebar (file tree)

Renders `FileTree.listTree(root, expanded)` in a `LazyColumn`. Click a chevron to toggle a directory, click a file to open it as a tab.

---

## Signature

```kotlin
@Composable
fun FileTreePanel(
    root: Path?,
    expanded: Set<Path>,
    activePath: Path?,
    onToggle: (Path) -> Unit,
    onOpen: (Path) -> Unit,
    modifier: Modifier = Modifier,
)
```

| Parameter | Meaning |
|---|---|
| `root` | Project root — `null` shows the empty hint |
| `expanded` | Set of currently-expanded directories (owned upstream) |
| `activePath` | Path of the active tab — the matching row gets the selected tint |
| `onToggle(path)` | Directory click → toggles `expanded` |
| `onOpen(path)` | File click → `openOrFocus` |

---

## Constants

```kotlin
private val RowHeight = 22.dp
private val ChevronWidth = 14.dp
private val IndentStep = 14.dp
private val EdgePadding = 8.dp
```

`IndentStep` is the per-depth indent. The chevron column is fixed-width regardless of depth so the file/folder glyphs stay aligned.

---

## Render flow

```kotlin
Surface(surfaceVariant) {
    Column {
        SectionHeader   // "PROJECT"
        HorizontalDivider
        if (root == null) EmptyTreeHint
        else LazyColumn {
            items(nodes, key = { it.path.toString() }) { TreeRow(...) }
        }
    }
}
```

`nodes = remember(root, expanded) { FileTree.listTree(root, expanded) }` — recomputed only when `root` or `expanded` changes.

`key` is the path string, so unchanged rows are reused on expand/collapse — no flicker.

---

## `TreeRow`

```kotlin
@Composable
private fun TreeRow(
    node: FileTreeNode,
    isActive: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
)
```

| State | Background |
|---|---|
| `isActive` (same file as the active tab) | `primary 16%` |
| Hover | `onSurface 6%` |
| Otherwise | Transparent |

| Chevron | |
|---|---|
| Directory + expanded | `▾` |
| Directory + collapsed | `▸` |
| File | Blank space the width of a chevron — keeps alignment |

Text is 12sp, directories use `Medium`, files use `Normal`. `maxLines = 1` + `Ellipsis` — long names truncate.

---

## `EmptyTreeHint`

```kotlin
"No folder open"
"Press Ctrl+Shift+O"
```

Two-line hint when `root == null`. Showing the open-folder shortcut where the empty state lives makes the entry point obvious.

---

## `SectionHeader`

The "PROJECT" caption: `10sp`, `FontWeight.Medium`, `letterSpacing = 0.8sp` — small caption tone.

---

## Usage

| Location | Purpose |
|---|---|
| Left of `page.app.Main` (`sidebarWidth = 260.dp`) | Tree + toggle/open callbacks |

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
